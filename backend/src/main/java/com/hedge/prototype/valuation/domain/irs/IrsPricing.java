package com.hedge.prototype.valuation.domain.irs;

import com.hedge.prototype.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static java.util.Objects.requireNonNull;

/**
 * 이자율스왑(IRS) 공정가치 계산 순수 도메인 클래스.
 *
 * <p>시장이자율 커브를 기반으로 IRS의 고정 다리(Fixed Leg)와 변동 다리(Floating Leg)
 * 현재가치를 각각 계산하고, 그 차이로 IRS 공정가치를 산출합니다.
 * DB 접근 없음 — 단위 테스트 100% 목표. Spring Bean 아님 (@Service 금지).
 *
 * <h3>핵심 공식</h3>
 * <pre>
 * 할인계수: df_i = 1 / (1 + r × t_i/365)
 * 고정 다리 PV = fixedRate × notional × Σ(df_i)
 *             단, 결제 주기별 분할 적용 (분기=4회/년, 반기=2회/년, 연간=1회/년)
 * 변동 다리 PV = currentFloatingRate × notional × df_1
 *             단, 1기간 기준 근사치 (단기 IRS 표준)
 *
 * IRS FV (Receive Fixed / Pay Floating): 고정다리PV - 변동다리PV
 * IRS FV (Pay Fixed / Receive Floating): 변동다리PV - 고정다리PV
 * </pre>
 *
 * <h3>PoC 단순화 가정</h3>
 * <ul>
 *   <li>플랫(Flat) 이자율 커브 가정 — 모든 기간에 동일 할인율 적용</li>
 *   <li>변동 다리는 1기간 근사치 방법 사용 (단일 forward rate 추정)</li>
 *   <li>결제 주기별 이자 분할 적용 (실제 날짜 기반 아닌 균등 분할)</li>
 * </ul>
 *
 * @see K-IFRS 1113호 72~90항 (Level 2 — 관측가능한 투입변수 기반 평가기법)
 * @see K-IFRS 1113호 61~66항 (시장참여자 가격결정기법 적용 원칙)
 * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — IRS 평가손익 처리)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — IRS 유효부분 OCI)
 */
@Slf4j
public class IrsPricing {

    private static final int RATE_SCALE = 6;
    private static final int AMOUNT_SCALE = 2;
    private static final int DF_SCALE = 8;

    /** QUARTERLY 분기 결제 횟수/년 */
    private static final int QUARTERLY_PERIODS = 4;
    /** SEMI_ANNUAL 반기 결제 횟수/년 */
    private static final int SEMI_ANNUAL_PERIODS = 2;
    /** ANNUAL 연간 결제 횟수/년 */
    private static final int ANNUAL_PERIODS = 1;

    // 유틸리티 클래스 — 인스턴스화 불필요
    private IrsPricing() {}

    /**
     * 고정 다리(Fixed Leg) 현재가치 계산.
     *
     * <p>각 결제 기간의 이자를 현재가치로 할인합니다.
     * 공식: fixedRate × notional × Σ df_i
     * 여기서 df_i = 1/(1 + discountRate × (i × periodDays)/365)
     *
     * @param notional          명목금액 (KRW)
     * @param fixedRate         고정금리 (소수 표현 — 예: 0.035)
     * @param remainingTermDays 잔존일수
     * @param settlementFrequency 결제 주기 ("QUARTERLY", "SEMI_ANNUAL", "ANNUAL")
     * @param discountRate      할인율 (소수 표현)
     * @return 고정 다리 현재가치 (KRW)
     * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 금리 커브)
     */
    public static BigDecimal calculateFixedLegPv(
            BigDecimal notional,
            BigDecimal fixedRate,
            int remainingTermDays,
            String settlementFrequency,
            BigDecimal discountRate) {

        validatePositive(notional, "명목금액");
        validateNonNegative(fixedRate, "고정금리");
        validateRemainingDays(remainingTermDays);
        requireNonNull(settlementFrequency, "결제 주기는 필수입니다.");
        validateNonNegative(discountRate, "할인율");

        int periodsPerYear = resolvePeriodsPerYear(settlementFrequency);
        int daysPerPeriod = 365 / periodsPerYear;
        // 잔존 결제 횟수 (올림 처리: 만기까지 남은 쿠폰 포함)
        int totalPeriods = (remainingTermDays + daysPerPeriod - 1) / daysPerPeriod;
        if (totalPeriods <= 0) totalPeriods = 1;

        // 기간별 이자 (쿠폰) — 연 고정금리를 결제 횟수로 분할
        BigDecimal periodRate = fixedRate.divide(new BigDecimal(periodsPerYear), RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal coupon = notional.multiply(periodRate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        BigDecimal fixedLegPv = BigDecimal.ZERO;
        for (int i = 1; i <= totalPeriods; i++) {
            int periodDays = Math.min(i * daysPerPeriod, remainingTermDays);
            BigDecimal df = calculateDiscountFactor(discountRate, periodDays);
            fixedLegPv = fixedLegPv.add(coupon.multiply(df).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        }

        log.debug("고정 다리 PV 계산: totalPeriods={}, fixedLegPv={}", totalPeriods, fixedLegPv);
        return fixedLegPv.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 변동 다리(Floating Leg) 현재가치 계산.
     *
     * <p>현재 변동금리(기준 + 스프레드)를 1기간 근사치로 현재가치를 산출합니다.
     * 공식: currentFloatingRate × notional × df_1기간
     *
     * @param notional             명목금액 (KRW)
     * @param currentFloatingRate  현재 변동금리 (기준금리 + 스프레드, 소수 표현)
     * @param remainingTermDays    잔존일수
     * @param settlementFrequency  결제 주기
     * @param discountRate         할인율
     * @return 변동 다리 현재가치 (KRW)
     * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 시장변동금리)
     */
    public static BigDecimal calculateFloatingLegPv(
            BigDecimal notional,
            BigDecimal currentFloatingRate,
            int remainingTermDays,
            String settlementFrequency,
            BigDecimal discountRate) {

        validatePositive(notional, "명목금액");
        validateNonNegative(currentFloatingRate, "변동금리");
        validateRemainingDays(remainingTermDays);
        requireNonNull(settlementFrequency, "결제 주기는 필수입니다.");
        validateNonNegative(discountRate, "할인율");

        int periodsPerYear = resolvePeriodsPerYear(settlementFrequency);
        int daysPerPeriod = 365 / periodsPerYear;
        int periodDays = Math.min(daysPerPeriod, remainingTermDays);

        // 변동 다리: 1기간 쿠폰 × 1기간 할인계수 (단기 IRS 근사)
        BigDecimal periodRate = currentFloatingRate.divide(new BigDecimal(periodsPerYear), RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal coupon = notional.multiply(periodRate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal df = calculateDiscountFactor(discountRate, periodDays);
        BigDecimal floatingLegPv = coupon.multiply(df).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        log.debug("변동 다리 PV 계산: periodDays={}, floatingLegPv={}", periodDays, floatingLegPv);
        return floatingLegPv;
    }

    /**
     * IRS 공정가치 계산.
     *
     * <p>고정지급/변동수취(payFixed=true) 구조: FV = 변동다리PV - 고정다리PV
     * 변동지급/고정수취(payFixed=false) 구조: FV = 고정다리PV - 변동다리PV
     *
     * <p>FV가 양수이면 우리 측 유리 포지션(자산), 음수이면 불리 포지션(부채)입니다.
     *
     * @param fixedLegPv             고정 다리 현재가치
     * @param floatingLegPv          변동 다리 현재가치
     * @param payFixedReceiveFloating true=고정지급/변동수취, false=변동지급/고정수취
     * @return IRS 공정가치 (KRW)
     * @see K-IFRS 1113호 9항 (측정일 기준 공정가치)
     * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — 위험회피수단 공정가치 측정)
     */
    public static BigDecimal calculateFairValue(
            BigDecimal fixedLegPv,
            BigDecimal floatingLegPv,
            boolean payFixedReceiveFloating) {

        requireNonNull(fixedLegPv, "고정 다리 현재가치는 필수입니다.");
        requireNonNull(floatingLegPv, "변동 다리 현재가치는 필수입니다.");

        BigDecimal fairValue;
        if (payFixedReceiveFloating) {
            // Pay Fixed, Receive Floating: 변동수취 - 고정지급
            fairValue = floatingLegPv.subtract(fixedLegPv).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        } else {
            // Pay Floating, Receive Fixed: 고정수취 - 변동지급
            fairValue = fixedLegPv.subtract(floatingLegPv).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }

        log.debug("IRS 공정가치 계산: payFixed={}, fairValue={}", payFixedReceiveFloating, fairValue);
        return fairValue;
    }

    /**
     * 할인계수(Discount Factor) 계산.
     *
     * <p>공식: df = 1 / (1 + r × T/365)
     * KRW IRS 표준: ACT/365 기준
     *
     * @param discountRate  할인율 (소수 표현)
     * @param days          할인 기간(일수)
     * @return 할인계수 (scale=8)
     * @see K-IFRS 1113호 72항 (관측가능한 투입변수: 무위험이자율)
     */
    public static BigDecimal calculateDiscountFactor(BigDecimal discountRate, int days) {
        validateNonNegative(discountRate, "할인율");
        if (days <= 0) {
            return BigDecimal.ONE;
        }

        // 1 + r × T/365 (KRW IRS: ACT/365)
        BigDecimal timeFraction = new BigDecimal(days).divide(new BigDecimal("365"), RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal denominator = BigDecimal.ONE.add(discountRate.multiply(timeFraction));
        return BigDecimal.ONE.divide(denominator, DF_SCALE, RoundingMode.HALF_UP);
    }

    // -----------------------------------------------------------------------
    // 내부 헬퍼
    // -----------------------------------------------------------------------

    /**
     * 결제 주기 문자열을 연간 결제 횟수로 변환합니다.
     *
     * @param settlementFrequency "QUARTERLY", "SEMI_ANNUAL", "ANNUAL"
     * @return 연간 결제 횟수
     */
    static int resolvePeriodsPerYear(String settlementFrequency) {
        return switch (settlementFrequency.toUpperCase()) {
            case "QUARTERLY" -> QUARTERLY_PERIODS;
            case "SEMI_ANNUAL" -> SEMI_ANNUAL_PERIODS;
            case "ANNUAL" -> ANNUAL_PERIODS;
            default -> throw new BusinessException("IRS_005",
                    "지원하지 않는 결제 주기입니다: " + settlementFrequency
                    + " (QUARTERLY, SEMI_ANNUAL, ANNUAL 중 하나를 사용하세요.)",
                    HttpStatus.BAD_REQUEST);
        };
    }

    private static void validatePositive(BigDecimal value, String fieldName) {
        requireNonNull(value, fieldName + "은(는) 필수입니다.");
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("IRS_001", fieldName + "은(는) 0보다 커야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private static void validateNonNegative(BigDecimal value, String fieldName) {
        requireNonNull(value, fieldName + "은(는) 필수입니다.");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("IRS_002", fieldName + "은(는) 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private static void validateRemainingDays(int remainingDays) {
        if (remainingDays <= 0) {
            throw new BusinessException("IRS_004", "잔존일수는 0보다 커야 합니다. 만기 초과 계약입니다.",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
