package com.hedge.prototype.valuation.domain.fxforward;

import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.valuation.domain.common.DayCountConvention;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static java.util.Objects.requireNonNull;

/**
 * 통화선도 공정가치 계산 도메인 클래스 (순수 도메인 로직).
 *
 * <p>이자율 평형 이론(IRP, Interest Rate Parity)에 기반한
 * 통화선도 공정가치 계산을 담당합니다.
 * DB 접근 없음 — 단위 테스트 100% 목표.
 *
 * <h3>핵심 공식</h3>
 * <pre>
 * 선물환율 = S₀ × (1 + r_KRW × T/365) / (1 + r_USD × T/360)
 * 현가계수 = 1 / (1 + r_KRW × T/365)
 * 공정가치 = (선물환율 - 계약환율) × 명목원금(USD) × 현가계수
 * </pre>
 *
 * <h3>Day Count Convention</h3>
 * <ul>
 *   <li>KRW — Actual/365 Fixed: 한국 CD금리·국고채 자금시장 표준. 윤년 무관 분모 365 고정.</li>
 *   <li>USD — Actual/360: USD SOFR·구LIBOR Money Market 국제 표준. 윤년 무관 분모 360 고정.</li>
 * </ul>
 * Actual/Actual(윤년 적용)은 국고채 장기물 쿠폰 계산에만 해당되며,
 * FX Forward IRP 계산에는 적용하지 않습니다.
 *
 * @see K-IFRS 1113호 72~90항 (Level 2 — 관측가능한 투입변수 기반 평가기법)
 * @see K-IFRS 1113호 61~66항 (시장참여자 가격결정기법 및 투입변수 적용 — Level 2 근거)
 * @see K-IFRS 1113호 89항   (관측가능한 투입변수 우선 원칙)
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 — 위험회피수단 공정가치 측정)
 * @see K-IFRS 1109호 예시 주석 37 (일수 계산방법 차이 — 헤지 비효과성 원인 인정)
 */
@Slf4j
public class FxForwardPricing {

    private static final int RATE_SCALE = 6;
    private static final int FORWARD_RATE_SCALE = 4;
    private static final int AMOUNT_SCALE = 2;

    // 유틸리티 클래스 — 인스턴스화 불필요
    private FxForwardPricing() {}

    /**
     * IRP 기반 선물환율 계산.
     *
     * <p>단순이자 방식 (1년 이하 계약 표준).
     * 공식: S₀ × (1 + r_KRW × T/365) / (1 + r_USD × T/360)
     *
     * @param spotRate      현물환율 (KRW/USD, 양수)
     * @param krwRate       원화 무위험이자율 (소수, 예: 0.035) — Day Count: Actual/365 Fixed
     * @param usdRate       달러 무위험이자율 (소수, 예: 0.053) — Day Count: Actual/360
     * @param remainingDays 잔존일수 (양수)
     * @return 현재 선물환율 (KRW/USD, scale=4)
     * @see K-IFRS 1113호 (공정가치 측정 — 관측가능한 투입변수)
     * @see DayCountConvention#ACT_365 (KRW)
     * @see DayCountConvention#ACT_360 (USD)
     */
    public static BigDecimal calculateForwardRate(
            BigDecimal spotRate,
            BigDecimal krwRate,
            BigDecimal usdRate,
            int remainingDays) {

        validateSpotRate(spotRate);
        validateInterestRate(krwRate, "원화이자율");
        validateInterestRate(usdRate, "달러이자율");
        validateRemainingDays(remainingDays);

        // 1 + r_KRW × T/365 (KRW: Actual/365 Fixed)
        BigDecimal krwFactor = BigDecimal.ONE
                .add(krwRate.multiply(DayCountConvention.ACT_365.fraction(remainingDays, RATE_SCALE)));

        // 1 + r_USD × T/360 (USD: Actual/360)
        BigDecimal usdFactor = BigDecimal.ONE
                .add(usdRate.multiply(DayCountConvention.ACT_360.fraction(remainingDays, RATE_SCALE)));

        // S₀ × krwFactor / usdFactor
        BigDecimal forwardRate = spotRate
                .multiply(krwFactor)
                .divide(usdFactor, FORWARD_RATE_SCALE, RoundingMode.HALF_UP);

        log.debug("선물환율 계산 완료: remainingDays={}", remainingDays);

        return forwardRate;
    }

    /**
     * 현가계수(Discount Factor) 계산.
     *
     * <p>공식: 1 / (1 + r_KRW × T/365)
     *
     * @param krwRate       KRW 무위험이자율 — Actual/365 Fixed 기준
     * @param remainingDays 잔존일수
     * @return 현가계수 (scale=6)
     * @see DayCountConvention#ACT_365 (KRW)
     */
    public static BigDecimal calculateDiscountFactor(BigDecimal krwRate, int remainingDays) {
        validateInterestRate(krwRate, "원화이자율");
        validateRemainingDays(remainingDays);

        // 1 + r_KRW × T/365 (KRW: Actual/365 Fixed)
        BigDecimal denominator = BigDecimal.ONE
                .add(krwRate.multiply(DayCountConvention.ACT_365.fraction(remainingDays, RATE_SCALE)));

        return BigDecimal.ONE.divide(denominator, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 통화선도 공정가치 계산.
     *
     * <p>공식: (현재선물환율 - 계약선물환율) × 명목원금(USD) × 현가계수
     *
     * <p>결과가 음수이면 위험회피수단이 손실 포지션임을 의미합니다.
     * (달러 약세 시 USD 선도매도 포지션 손실)
     *
     * @param currentForwardRate  현재 선물환율 (IRP 산출)
     * @param contractForwardRate 계약 선물환율 (헤지 지정일 확정)
     * @param notionalAmountUsd   명목원금 (USD)
     * @param discountFactor      현가계수
     * @return 공정가치 (KRW, scale=2)
     * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 — 위험회피수단 공정가치 측정)
     */
    public static BigDecimal calculateFairValue(
            BigDecimal currentForwardRate,
            BigDecimal contractForwardRate,
            BigDecimal notionalAmountUsd,
            BigDecimal discountFactor) {

        requireNonNull(currentForwardRate, "현재 선물환율은 필수입니다.");
        requireNonNull(contractForwardRate, "계약 선물환율은 필수입니다.");
        requireNonNull(notionalAmountUsd, "명목원금은 필수입니다.");
        requireNonNull(discountFactor, "현가계수는 필수입니다.");

        // (현재선물환율 - 계약선물환율) × 명목원금 × 현가계수
        BigDecimal rateDiff = currentForwardRate.subtract(contractForwardRate);
        BigDecimal fairValue = rateDiff
                .multiply(notionalAmountUsd)
                .multiply(discountFactor)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        log.debug("공정가치 계산 완료: sign={}", fairValue.signum() >= 0 ? "양수(평가익)" : "음수(평가손)");

        return fairValue;
    }

    // -----------------------------------------------------------------------
    // 유효성 검증 (private static)
    // -----------------------------------------------------------------------

    private static void validateSpotRate(BigDecimal spotRate) {
        if (spotRate == null || spotRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("FX_002", "현물환율은 0보다 커야 합니다.");
        }
    }

    private static void validateInterestRate(BigDecimal rate, String rateName) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("FX_003", rateName + "은 0 이상이어야 합니다.");
        }
    }

    private static void validateRemainingDays(int remainingDays) {
        if (remainingDays <= 0) {
            throw new BusinessException("FX_001", "잔존일수는 0보다 커야 합니다. 만기 초과 계약입니다.");
        }
    }
}
