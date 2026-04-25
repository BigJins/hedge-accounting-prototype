package com.hedge.prototype.effectiveness.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 현금흐름 위험회피 유효성 — Lower of Test 계산기.
 *
 * <p>OCI로 인식할 수 있는 유효 부분의 한도는
 * 피헤지항목 변동 누계와 위험회피수단 변동 누계 중 작은 절대값입니다.
 * 과대헤지(위험회피수단 변동 > 피헤지항목 변동)인 경우에만 비효과적 부분이 발생합니다.
 *
 * <p><b>계산식</b>:
 * <pre>
 *   effective_amount  = MIN( |hedgedItemCumulative|, |instrumentCumulative| )
 *   ineffective_amount = MAX( 0, |instrumentCumulative| - effective_amount )
 *                      = 과대헤지 시에만 양수
 * </pre>
 *
 * @see K-IFRS 1109호 6.5.11⑴ (OCI 인식 한도 — Lower of Test)
 * @see K-IFRS 1109호 6.5.11⑵ (비효과적 부분 당기손익 인식)
 * @see K-IFRS 1109호 BC6.280 (Lower of Test 근거)
 */
public class LowerOfTestCalculator {

    /**
     * 유효(OCI 인식 가능) 부분 계산.
     *
     * <p>OCI로 인식할 수 있는 상한은 피헤지항목 변동 누계 절대값과
     * 위험회피수단 변동 누계 절대값 중 작은 값입니다.
     *
     * @param instrumentCumulative  위험회피수단 공정가치 변동 누계
     * @param hedgedItemCumulative  피헤지항목 현재가치 변동 누계
     * @return OCI 인식 가능 금액 (항상 양수 또는 0)
     * @see K-IFRS 1109호 6.5.11⑴ (현금흐름위험회피 OCI 한도)
     */
    public static BigDecimal calculateEffectivePortion(
            BigDecimal instrumentCumulative,
            BigDecimal hedgedItemCumulative) {

        BigDecimal absInstrument = instrumentCumulative.abs();
        BigDecimal absHedgedItem = hedgedItemCumulative.abs();

        // Lower of Test: 두 절대값 중 작은 것이 OCI 인식 한도
        return absInstrument.min(absHedgedItem).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 비효과적(P&L 즉시 인식) 부분 계산.
     *
     * <p>과대헤지(위험회피수단 변동 절대값 > 피헤지항목 변동 절대값)인 경우에만
     * 초과분이 비효과적 부분으로 P&L에 인식됩니다.
     * 과소헤지인 경우 비효과적 부분은 0입니다.
     *
     * @param instrumentCumulative 위험회피수단 공정가치 변동 누계
     * @param effectivePortion     {@link #calculateEffectivePortion}으로 계산된 유효 부분
     * @return 비효과적 부분 (양수 또는 0 — 과대헤지 시만 양수)
     * @see K-IFRS 1109호 6.5.11⑵ (비효과적 부분 당기손익 인식)
     */
    public static BigDecimal calculateIneffectivePortion(
            BigDecimal instrumentCumulative,
            BigDecimal effectivePortion) {

        BigDecimal absInstrument = instrumentCumulative.abs();
        BigDecimal excess = absInstrument.subtract(effectivePortion);

        // 과대헤지 시만 양수, 과소헤지 시 0
        return excess.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 부호 있는 유효(OCI 인식) 부분 계산.
     *
     * <p>OCI로 인식할 금액의 규모는 {@link #calculateEffectivePortion}(Lower of Test)으로 계산하고,
     * 부호는 위험회피수단 누적 변동({@code instrumentCumulative})의 방향을 따릅니다.
     *
     * <p>K-IFRS 1109호 6.5.11⑴: "위험회피수단의 손익 중 유효한 위험회피로 결정된 부분"을 OCI로 인식.
     * 수단이 이익(양수)이면 OCI 증가(양수), 손실(음수)이면 OCI 감소(음수).
     * BC6.280: Lower of Test는 OCI 인식 한도(규모) 제한 장치이며 부호를 변환하지 않음.
     *
     * @param instrumentCumulative 위험회피수단 공정가치 변동 누계 (부호 포함)
     * @param hedgedItemCumulative 피헤지항목 현재가치 변동 누계 (부호 포함)
     * @return 부호 있는 OCI 인식 금액 (양수=OCI 증가, 음수=OCI 감소)
     * @see K-IFRS 1109호 6.5.11⑴ (유효 부분 OCI 인식 방향)
     * @see K-IFRS 1109호 6.5.11⑷㈐ (OCI 적립금 차손 규정)
     * @see K-IFRS 1109호 BC6.280 (Lower of Test — 규모 제한 장치)
     */
    public static BigDecimal calculateSignedEffectivePortion(
            BigDecimal instrumentCumulative,
            BigDecimal hedgedItemCumulative) {
        BigDecimal magnitude = calculateEffectivePortion(instrumentCumulative, hedgedItemCumulative);
        // 부호는 헤지수단 누적 방향을 따름 (6.5.11⑴)
        return instrumentCumulative.signum() >= 0 ? magnitude : magnitude.negate();
    }

    /**
     * 부호 있는 비효과적(P&L 즉시 인식) 부분 계산.
     *
     * <p>과대헤지 초과분의 규모는 {@link #calculateIneffectivePortion}으로 계산하고,
     * 부호는 위험회피수단 누적 변동({@code instrumentCumulative})의 방향을 따릅니다.
     *
     * <p>K-IFRS 1109호 6.5.11⑵: 비효과적 부분은 즉시 당기손익(P/L)에 인식.
     * 수단이 손실 과대헤지이면 P/L 손실로 인식해야 함.
     *
     * @param instrumentCumulative   위험회피수단 공정가치 변동 누계 (부호 포함)
     * @param signedEffectivePortion {@link #calculateSignedEffectivePortion} 결과
     * @return 부호 있는 비효과적 부분 (양수=P/L 이익, 음수=P/L 손실, 0=과소헤지)
     * @see K-IFRS 1109호 6.5.11⑵ (비효과적 부분 당기손익 인식)
     */
    public static BigDecimal calculateSignedIneffectivePortion(
            BigDecimal instrumentCumulative,
            BigDecimal signedEffectivePortion) {
        BigDecimal absMagnitude = instrumentCumulative.abs()
                .subtract(signedEffectivePortion.abs())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return instrumentCumulative.signum() >= 0 ? absMagnitude : absMagnitude.negate();
    }

    // 유틸리티 클래스 — 인스턴스화 금지
    private LowerOfTestCalculator() {}
}
