package com.hedge.prototype.valuation.domain.bond;

import com.hedge.prototype.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static java.util.Objects.requireNonNull;

/**
 * 원화 고정금리채권(KRW_FIXED_BOND) 헤지귀속 공정가치 변동 계산 순수 도메인 클래스.
 *
 * <p>공정가치 위험회피(FVH)에서 피헤지항목(채권)의 헤지위험(금리위험) 귀속 공정가치 변동을 계산합니다.
 * DB 접근 없음 — 단위 테스트 100% 목표. Spring Bean 아님 (@Service 금지).
 *
 * <h3>핵심 계산 원칙</h3>
 * <pre>
 * 헤지귀속 공정가치 변동 = PV(현재 시장금리) - PV(지정일 시장금리)
 *
 * 채권 PV(r) = Σ_{i=1}^{n} [coupon × df(r, t_i)] + notional × df(r, t_n)
 *
 * 여기서:
 *   coupon   = notional × couponRate / periodsPerYear
 *   df(r, t) = 1 / (1 + r × t/365)        — KRW ACT/365 Fixed
 *   t_i      = i × daysPerPeriod           — 균등 분할 (PoC 단순화)
 *   t_n      = remainingDays               — 최종 기간은 실제 잔존일수 사용
 *   n        = ceil(remainingDays / daysPerPeriod)
 * </pre>
 *
 * <h3>PoC 단순화 가정</h3>
 * <ul>
 *   <li>플랫(Flat) 이자율 커브 — 모든 기간에 동일 할인율 적용</li>
 *   <li>결제 기간 균등 분할 (실제 결제일 기반 일수 계산은 REQ-VAL-003 2단계 목표)</li>
 *   <li>신용위험 귀속분 분리 생략 — 금리위험 100% 귀속 가정 (B6.5.1~B6.5.5)</li>
 * </ul>
 *
 * <h3>재사용 경로</h3>
 * <ul>
 *   <li>FVH IRS 유효성 테스트: hedgedItemPvChange 입력값으로 사용</li>
 *   <li>FVH IRS 분개 생성: hedgedItemAdjustment 입력값으로 사용</li>
 * </ul>
 *
 * <!-- TODO(RAG 재검증): RAG 복구 후 아래 조항 원문과 교차 검증 필요 -->
 * @see <a href="#">K-IFRS 1109호 6.5.8 (공정가치 위험회피 회계처리 — 피헤지항목 장부가치 조정)</a>
 * @see <a href="#">K-IFRS 1109호 6.5.9 (피헤지항목 장부금액 조정 상각)</a>
 * @see <a href="#">K-IFRS 1109호 B6.5.1~B6.5.5 (헤지위험 귀속 공정가치 변동 산정)</a>
 * @see <a href="#">K-IFRS 1113호 72~90항 (Level 2 — 관측가능한 투입변수 기반 평가기법)</a>
 */
@Slf4j
public class BondHedgedItemPricing {

    private static final int RATE_SCALE  = 8;
    private static final int AMOUNT_SCALE = 2;
    private static final int DF_SCALE     = 10;

    /** KRW IRS/채권 표준 day count 분모 (ACT/365 Fixed). */
    private static final BigDecimal KRW_DAY_COUNT = new BigDecimal("365");

    // 유틸리티 클래스 — 인스턴스화 불필요
    private BondHedgedItemPricing() {}

    // -----------------------------------------------------------------------
    // 핵심 공개 API
    // -----------------------------------------------------------------------

    /**
     * 채권 현금흐름의 현재가치(PV) 계산.
     *
     * <p>쿠폰 현금흐름과 원금 상환의 현재가치를 합산합니다.
     * KRW IRS 표준 ACT/365 Fixed 기준으로 할인합니다.
     *
     * <pre>
     * PV(r) = Σ_{i=1}^{n} [coupon × df(r, t_i)] + notional × df(r, t_n)
     * coupon = notional × couponRate / periodsPerYear
     * df(r, t) = 1 / (1 + r × t / 365)
     * </pre>
     *
     * @param notional            채권 액면금액 (KRW, 양수)
     * @param couponRate          연 쿠폰금리 (소수 — 예: 0.03 = 3.0%)
     * @param remainingDays       잔존일수 (평가기준일 → 만기일)
     * @param settlementFrequency 이자 지급 주기 ("SEMI_ANNUAL", "QUARTERLY", "ANNUAL")
     * @param discountRate        할인율 (시장금리, 소수 — 예: 0.045 = 4.5%)
     * @return 채권 현재가치 (KRW, scale=2)
     * @throws BusinessException BOND_001 — notional ≤ 0
     * @throws BusinessException BOND_002 — couponRate < 0
     * @throws BusinessException BOND_003 — remainingDays ≤ 0
     * @throws BusinessException BOND_004 — discountRate < 0
     * @throws BusinessException BOND_005 — 지원하지 않는 결제 주기
     *
     * <!-- TODO(RAG 재검증): K-IFRS 1113호 72항 — 관측가능한 투입변수 사용 의무 교차 검증 필요 -->
     * @see <a href="#">K-IFRS 1113호 72항 (Level 2 — 관측가능한 투입변수)</a>
     * @see <a href="#">K-IFRS 1109호 6.5.8 (공정가치 위험회피 — 피헤지항목 평가)</a>
     */
    public static BigDecimal calculateBondPv(
            BigDecimal notional,
            BigDecimal couponRate,
            int remainingDays,
            String settlementFrequency,
            BigDecimal discountRate) {

        validatePositive(notional, "채권 액면금액", "BOND_001");
        validateNonNegative(couponRate, "쿠폰금리", "BOND_002");
        validateRemainingDays(remainingDays);
        validateNonNegative(discountRate, "할인율", "BOND_004");

        int periodsPerYear = resolvePeriodsPerYear(settlementFrequency);
        int daysPerPeriod  = Math.toIntExact(Math.round(KRW_DAY_COUNT.doubleValue() / periodsPerYear));
        int totalPeriods   = (remainingDays + daysPerPeriod - 1) / daysPerPeriod;
        if (totalPeriods <= 0) totalPeriods = 1;

        // 쿠폰 = 연 쿠폰금리 / 지급횟수 × 명목금액
        BigDecimal periodCouponRate = couponRate.divide(
                new BigDecimal(periodsPerYear), RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal coupon = notional
                .multiply(periodCouponRate)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        // 쿠폰 현금흐름 PV 합산
        BigDecimal couponPvSum = BigDecimal.ZERO;
        for (int i = 1; i <= totalPeriods; i++) {
            // 각 기간의 실제 일수 — 마지막 기간은 잔존일수 기준
            int periodDays = (i < totalPeriods) ? (i * daysPerPeriod) : remainingDays;
            BigDecimal df = discountFactor(discountRate, periodDays);
            couponPvSum = couponPvSum.add(
                    coupon.multiply(df).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        }

        // 원금 PV — 만기 할인계수 적용
        BigDecimal principalDf = discountFactor(discountRate, remainingDays);
        BigDecimal principalPv = notional
                .multiply(principalDf)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        BigDecimal bondPv = couponPvSum.add(principalPv)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        log.debug("채권 PV 계산: remainingDays={}, totalPeriods={}, discountRate={}, bondPv={}",
                remainingDays, totalPeriods, discountRate, bondPv);
        return bondPv;
    }

    /**
     * 헤지귀속 공정가치 변동 계산.
     *
     * <p>FVH에서 피헤지항목(채권)의 헤지위험(금리위험) 귀속 공정가치 변동을 산출합니다.
     * 채권 전체 공정가치 변동이 아닌, 금리위험에 귀속되는 부분만 인식합니다.
     *
     * <pre>
     * 헤지귀속 FV 변동 = PV(현재 시장금리) - PV(지정일 시장금리)
     *   양수: 금리 하락 → 채권 가치 상승 (평가이익, 장부가치 조정 증가)
     *   음수: 금리 상승 → 채권 가치 하락 (평가손실, 장부가치 조정 감소)
     * </pre>
     *
     * <p><b>PoC 단순화</b>: 신용위험 귀속분 분리 생략.
     * 금리위험만을 헤지하는 경우 채권 공정가치 변동을 금리위험 귀속으로 단순 가정합니다.
     * (실무에서는 신용 스프레드 변동분을 제외해야 함 — K-IFRS 1109호 B6.5.1~B6.5.5 교차 검증 필요)
     *
     * @param notional               채권 액면금액 (KRW, 양수)
     * @param couponRate             연 쿠폰금리 (소수)
     * @param remainingDays          잔존일수 (평가기준일 → 만기일)
     * @param settlementFrequency    이자 지급 주기
     * @param designationDiscountRate 지정일 시장금리 (할인율, 소수)
     * @param currentDiscountRate    현재 시장금리 (할인율, 소수)
     * @return 헤지귀속 공정가치 변동 = PV(현재) - PV(지정일) (KRW, scale=2)
     *
     * <!-- TODO(RAG 재검증): K-IFRS 1109호 B6.5.1~B6.5.5 신용위험 귀속분 분리 방법 검증 필요 -->
     * <!-- TODO(RAG 재검증): K-IFRS 1109호 6.5.9 장부금액 조정 상각 처리 방법 검증 필요 -->
     * @see <a href="#">K-IFRS 1109호 6.5.8 (공정가치 위험회피 회계처리)</a>
     * @see <a href="#">K-IFRS 1109호 B6.5.1~B6.5.5 (헤지위험 귀속 공정가치 변동 산정)</a>
     */
    public static BigDecimal calculateHedgeAttributedFvChange(
            BigDecimal notional,
            BigDecimal couponRate,
            int remainingDays,
            String settlementFrequency,
            BigDecimal designationDiscountRate,
            BigDecimal currentDiscountRate) {

        // 파라미터 유효성은 calculateBondPv 내부에서 검증됨
        BigDecimal pvAtDesignation = calculateBondPv(
                notional, couponRate, remainingDays, settlementFrequency, designationDiscountRate);
        BigDecimal pvAtCurrent = calculateBondPv(
                notional, couponRate, remainingDays, settlementFrequency, currentDiscountRate);

        BigDecimal fvChange = pvAtCurrent.subtract(pvAtDesignation)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        log.info("헤지귀속 FV 변동 계산: pvAtDesignation={}, pvAtCurrent={}, fvChange={}",
                pvAtDesignation, pvAtCurrent, fvChange);
        return fvChange;
    }

    // -----------------------------------------------------------------------
    // 내부 헬퍼
    // -----------------------------------------------------------------------

    /**
     * 할인계수 계산 (KRW ACT/365 Fixed).
     *
     * <pre>df(r, t) = 1 / (1 + r × t / 365)</pre>
     *
     * @see <a href="#">K-IFRS 1113호 72항 (Level 2 관측가능 투입변수)</a>
     */
    static BigDecimal discountFactor(BigDecimal rate, int days) {
        if (days <= 0) return BigDecimal.ONE;
        BigDecimal timeFraction = new BigDecimal(days).divide(KRW_DAY_COUNT, RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal denominator  = BigDecimal.ONE.add(rate.multiply(timeFraction));
        return BigDecimal.ONE.divide(denominator, DF_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 결제 주기 문자열을 연간 지급 횟수로 변환.
     *
     * @param settlementFrequency "QUARTERLY", "SEMI_ANNUAL", "ANNUAL"
     * @return 연간 지급 횟수
     * @throws BusinessException BOND_005 — 지원하지 않는 결제 주기
     */
    static int resolvePeriodsPerYear(String settlementFrequency) {
        requireNonNull(settlementFrequency, "결제 주기는 필수입니다.");
        return switch (settlementFrequency.toUpperCase()) {
            case "QUARTERLY"   -> 4;
            case "SEMI_ANNUAL" -> 2;
            case "ANNUAL"      -> 1;
            default -> throw new BusinessException("BOND_005",
                    "지원하지 않는 결제 주기입니다: " + settlementFrequency
                    + " (QUARTERLY, SEMI_ANNUAL, ANNUAL 중 하나를 사용하세요.)",
                    HttpStatus.BAD_REQUEST);
        };
    }

    private static void validatePositive(BigDecimal value, String fieldName, String code) {
        requireNonNull(value, fieldName + "은(는) 필수입니다.");
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(code, fieldName + "은(는) 0보다 커야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private static void validateNonNegative(BigDecimal value, String fieldName, String code) {
        requireNonNull(value, fieldName + "은(는) 필수입니다.");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(code, fieldName + "은(는) 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private static void validateRemainingDays(int days) {
        if (days <= 0) {
            throw new BusinessException("BOND_003",
                    "잔존일수는 0보다 커야 합니다. 만기가 평가기준일과 같거나 이전입니다.",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
