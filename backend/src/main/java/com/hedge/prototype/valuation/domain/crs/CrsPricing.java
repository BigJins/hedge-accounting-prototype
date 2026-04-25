package com.hedge.prototype.valuation.domain.crs;

import com.hedge.prototype.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static java.util.Objects.requireNonNull;

/**
 * 통화스왑(CRS) 공정가치 계산 순수 도메인 클래스.
 *
 * <p>환율·금리 커브를 기반으로 CRS의 원화 다리(KRW Leg)와 외화 다리(Foreign Leg)
 * 현재가치를 각각 계산하고, 그 차이로 CRS 공정가치를 산출합니다.
 * DB 접근 없음 — 단위 테스트 100% 목표. Spring Bean 아님 (@Service 금지).
 *
 * <h3>핵심 공식</h3>
 * <pre>
 * 할인계수: df_i = 1 / (1 + r × t_i/365)
 *
 * 외화 다리 PV (원화 환산):
 *   = Σ(foreignCoupon_i × spotRate × df_i) + foreignNotional × spotRate × df_n
 *
 * 원화 다리 PV:
 *   = Σ(krwCoupon_i × df_i) + krwNotional × df_n
 *
 * CRS FV = 외화다리PV(원화환산) - 원화다리PV
 * </pre>
 *
 * <h3>PoC 단순화 가정</h3>
 * <ul>
 *   <li>플랫(Flat) 이자율 커브 가정 — 원화/외화 모두 단일 할인율 적용</li>
 *   <li>원화 다리는 krwDiscountRate, 외화 다리는 foreignDiscountRate 사용</li>
 *   <li>결제 주기별 균등 분할 (실제 날짜 기반 아님)</li>
 *   <li>원금 재교환은 만기(n기간)에 각 통화 원금 반환으로 처리</li>
 * </ul>
 *
 * @see K-IFRS 1113호 72~90항 (Level 2 — 관측가능한 투입변수 기반 평가기법)
 * @see K-IFRS 1113호 61~66항 (시장참여자 가격결정기법 적용 원칙)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — CRS 유효부분 OCI)
 * @see K-IFRS 1109호 B6.4.9 (통화스왑의 헤지비율 산정)
 */
@Slf4j
public class CrsPricing {

    private static final int RATE_SCALE = 6;
    private static final int AMOUNT_SCALE = 2;
    private static final int DF_SCALE = 8;

    // 유틸리티 클래스 — 인스턴스화 불필요
    private CrsPricing() {}

    /**
     * 외화 다리(Foreign Leg) 현재가치 계산 (원화 환산).
     *
     * <p>공식: Σ(foreignCoupon_i × spotRate × df_i) + foreignNotional × spotRate × df_n
     * 여기서 df_i = 1/(1 + foreignDiscountRate × t_i/365)
     *
     * @param foreignNotional      외화 원금
     * @param foreignCouponRate    외화 이자율 (소수 표현 — 고정금리 또는 현재 변동금리)
     * @param spotRate             평가기준일 환율 (KRW/외화)
     * @param remainingTermDays    잔존일수
     * @param settlementFrequency  결제 주기 ("QUARTERLY", "SEMI_ANNUAL", "ANNUAL")
     * @param foreignDiscountRate  외화 할인율 (소수 표현)
     * @return 외화 다리 현재가치 원화 환산 (KRW)
     * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 외화 금리 커브 + 환율)
     */
    public static BigDecimal calculateForeignLegPv(
            BigDecimal foreignNotional,
            BigDecimal foreignCouponRate,
            BigDecimal spotRate,
            int remainingTermDays,
            String settlementFrequency,
            BigDecimal foreignDiscountRate) {

        validatePositive(foreignNotional, "외화 원금");
        validateNonNegative(foreignCouponRate, "외화 이자율");
        validatePositive(spotRate, "환율");
        validateRemainingDays(remainingTermDays);
        requireNonNull(settlementFrequency, "결제 주기는 필수입니다.");
        validateNonNegative(foreignDiscountRate, "외화 할인율");

        int periodsPerYear = resolvePeriodsPerYear(settlementFrequency);
        int daysPerPeriod = 365 / periodsPerYear;
        int totalPeriods = (remainingTermDays + daysPerPeriod - 1) / daysPerPeriod;
        if (totalPeriods <= 0) totalPeriods = 1;

        // 기간별 외화 이자 쿠폰 (원화 환산)
        BigDecimal periodRate = foreignCouponRate.divide(new BigDecimal(periodsPerYear), RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal foreignCouponKrw = foreignNotional.multiply(periodRate).multiply(spotRate)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        BigDecimal foreignLegPv = BigDecimal.ZERO;
        for (int i = 1; i <= totalPeriods; i++) {
            int periodDays = Math.min(i * daysPerPeriod, remainingTermDays);
            BigDecimal df = calculateDiscountFactor(foreignDiscountRate, periodDays);
            foreignLegPv = foreignLegPv.add(foreignCouponKrw.multiply(df).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        }

        // 만기 외화 원금 재교환 현재가치 (원화 환산)
        int maturityDays = remainingTermDays;
        BigDecimal dfMaturity = calculateDiscountFactor(foreignDiscountRate, maturityDays);
        BigDecimal principalKrw = foreignNotional.multiply(spotRate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        foreignLegPv = foreignLegPv.add(principalKrw.multiply(dfMaturity).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));

        log.debug("외화 다리 PV 계산(원화 환산): totalPeriods={}, foreignLegPv={}", totalPeriods, foreignLegPv);
        return foreignLegPv.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 원화 다리(KRW Leg) 현재가치 계산.
     *
     * <p>공식: Σ(krwCoupon_i × df_i) + krwNotional × df_n
     * 여기서 df_i = 1/(1 + krwDiscountRate × t_i/365)
     *
     * @param krwNotional         원화 원금 (KRW)
     * @param krwCouponRate       원화 이자율 (소수 표현 — 고정금리 또는 현재 변동금리)
     * @param remainingTermDays   잔존일수
     * @param settlementFrequency 결제 주기
     * @param krwDiscountRate     원화 할인율 (소수 표현)
     * @return 원화 다리 현재가치 (KRW)
     * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 원화 금리 커브)
     */
    public static BigDecimal calculateKrwLegPv(
            BigDecimal krwNotional,
            BigDecimal krwCouponRate,
            int remainingTermDays,
            String settlementFrequency,
            BigDecimal krwDiscountRate) {

        validatePositive(krwNotional, "원화 원금");
        validateNonNegative(krwCouponRate, "원화 이자율");
        validateRemainingDays(remainingTermDays);
        requireNonNull(settlementFrequency, "결제 주기는 필수입니다.");
        validateNonNegative(krwDiscountRate, "원화 할인율");

        int periodsPerYear = resolvePeriodsPerYear(settlementFrequency);
        int daysPerPeriod = 365 / periodsPerYear;
        int totalPeriods = (remainingTermDays + daysPerPeriod - 1) / daysPerPeriod;
        if (totalPeriods <= 0) totalPeriods = 1;

        // 기간별 원화 이자 쿠폰
        BigDecimal periodRate = krwCouponRate.divide(new BigDecimal(periodsPerYear), RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal coupon = krwNotional.multiply(periodRate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        BigDecimal krwLegPv = BigDecimal.ZERO;
        for (int i = 1; i <= totalPeriods; i++) {
            int periodDays = Math.min(i * daysPerPeriod, remainingTermDays);
            BigDecimal df = calculateDiscountFactor(krwDiscountRate, periodDays);
            krwLegPv = krwLegPv.add(coupon.multiply(df).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        }

        // 만기 원화 원금 재교환 현재가치
        BigDecimal dfMaturity = calculateDiscountFactor(krwDiscountRate, remainingTermDays);
        krwLegPv = krwLegPv.add(krwNotional.multiply(dfMaturity).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));

        log.debug("원화 다리 PV 계산: totalPeriods={}, krwLegPv={}", totalPeriods, krwLegPv);
        return krwLegPv.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * CRS 공정가치 계산.
     *
     * <p>공식: CRS FV = 외화다리PV(원화환산) - 원화다리PV
     *
     * <p>FV가 양수이면 우리 측 유리 포지션(자산), 음수이면 불리 포지션(부채)입니다.
     * 외화차입금 헤지(CRS 수취: 외화 지급 + 원화 수취)에서
     * 환율 하락 시 외화 다리 PV 감소 → FV 음수로 헤지 손실 발생합니다.
     *
     * @param foreignLegPvKrw 외화 다리 현재가치 (원화 환산)
     * @param krwLegPv        원화 다리 현재가치
     * @return CRS 공정가치 (KRW)
     * @see K-IFRS 1113호 9항 (측정일 기준 공정가치)
     * @see K-IFRS 1109호 B6.4.9 (통화스왑 공정가치 측정)
     */
    public static BigDecimal calculateFairValue(BigDecimal foreignLegPvKrw, BigDecimal krwLegPv) {
        requireNonNull(foreignLegPvKrw, "외화 다리 현재가치는 필수입니다.");
        requireNonNull(krwLegPv, "원화 다리 현재가치는 필수입니다.");

        BigDecimal fairValue = foreignLegPvKrw.subtract(krwLegPv).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        log.debug("CRS 공정가치 계산: fairValue={}", fairValue);
        return fairValue;
    }

    /**
     * 할인계수(Discount Factor) 계산 — ACT/365 기준.
     *
     * <p>공식: df = 1 / (1 + r × T/365)
     *
     * @param discountRate 할인율 (소수 표현)
     * @param days         할인 기간(일수)
     * @return 할인계수 (scale=8)
     * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 무위험이자율)
     */
    public static BigDecimal calculateDiscountFactor(BigDecimal discountRate, int days) {
        validateNonNegative(discountRate, "할인율");
        if (days <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal timeFraction = new BigDecimal(days).divide(new BigDecimal("365"), RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal denominator = BigDecimal.ONE.add(discountRate.multiply(timeFraction));
        return BigDecimal.ONE.divide(denominator, DF_SCALE, RoundingMode.HALF_UP);
    }

    // -----------------------------------------------------------------------
    // 내부 헬퍼
    // -----------------------------------------------------------------------

    /**
     * 결제 주기 문자열을 연간 결제 횟수로 변환합니다.
     */
    static int resolvePeriodsPerYear(String settlementFrequency) {
        return switch (settlementFrequency.toUpperCase()) {
            case "QUARTERLY" -> 4;
            case "SEMI_ANNUAL" -> 2;
            case "ANNUAL" -> 1;
            default -> throw new BusinessException("CRS_005",
                    "지원하지 않는 결제 주기입니다: " + settlementFrequency
                    + " (QUARTERLY, SEMI_ANNUAL, ANNUAL 중 하나를 사용하세요.)",
                    HttpStatus.BAD_REQUEST);
        };
    }

    private static void validatePositive(BigDecimal value, String fieldName) {
        requireNonNull(value, fieldName + "은(는) 필수입니다.");
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("CRS_001", fieldName + "은(는) 0보다 커야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private static void validateNonNegative(BigDecimal value, String fieldName) {
        requireNonNull(value, fieldName + "은(는) 필수입니다.");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("CRS_002", fieldName + "은(는) 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private static void validateRemainingDays(int remainingDays) {
        if (remainingDays <= 0) {
            throw new BusinessException("CRS_004", "잔존일수는 0보다 커야 합니다. 만기 초과 계약입니다.",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
