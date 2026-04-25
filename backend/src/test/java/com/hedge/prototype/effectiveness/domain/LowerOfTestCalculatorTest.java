package com.hedge.prototype.effectiveness.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lower of Test 부호 방향성 테스트.
 *
 * <p>핵심 검증: 위험회피수단이 손실일 때 OCI 인식 금액도 음수(OCI 감소)여야 한다.
 * 기존 {@link LowerOfTestCalculator#calculateEffectivePortion}은 절댓값만 반환하여
 * 수단 손실 시 OCI Reserve 잔액 방향이 역전되는 버그가 있었다.
 * {@link LowerOfTestCalculator#calculateSignedEffectivePortion}이 이를 수정한다.
 *
 * @see K-IFRS 1109호 6.5.11⑴ (유효 부분 부호는 수단 방향을 따름)
 * @see K-IFRS 1109호 6.5.11⑷㈐ (OCI 적립금 차손 규정)
 * @see K-IFRS 1109호 BC6.280 (Lower of Test — 규모 제한 장치)
 */
@DisplayName("LowerOfTestCalculator 부호 방향성 테스트")
class LowerOfTestCalculatorTest {

    // -----------------------------------------------------------------------
    // calculateSignedEffectivePortion 테스트
    // -----------------------------------------------------------------------

    /**
     * 케이스 A: 수단 이익, 과소헤지 — OCI 양수 증가.
     *
     * <p>수단누적=+480만, 대상누적=-500만 (반대방향 정상)
     * Lower of Test: MIN(480, 500) = 480
     * 수단 양수 → effectiveAmount = +480만 (OCI 증가)
     * 과소헤지(수단 절대값 480 &lt; 대상 절대값 500) → ineffective = 0
     *
     * @see K-IFRS 1109호 6.5.11⑴ (유효 부분 OCI 인식 방향)
     */
    @Test
    @DisplayName("케이스A: 수단 이익, 과소헤지 — OCI 양수, 비유효 0")
    void caseA_instrumentGain_underHedge() {
        BigDecimal instrumentCumulative = new BigDecimal("4800000");
        BigDecimal hedgedItemCumulative = new BigDecimal("-5000000");

        BigDecimal effectiveAmount = LowerOfTestCalculator.calculateSignedEffectivePortion(
                instrumentCumulative, hedgedItemCumulative);

        BigDecimal ineffectiveAmount = LowerOfTestCalculator.calculateSignedIneffectivePortion(
                instrumentCumulative, effectiveAmount);

        // 유효 부분: +480만 (양수 → OCI 증가)
        assertThat(effectiveAmount).isEqualByComparingTo(new BigDecimal("4800000.00"));
        // 비유효 부분: 0 (과소헤지 — 초과분 없음)
        assertThat(ineffectiveAmount).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    /**
     * 케이스 B: 수단 손실, 과소헤지 — OCI 음수 감소 (핵심 버그 케이스).
     *
     * <p>수단누적=-480만, 대상누적=+500만 (반대방향 정상)
     * Lower of Test: MIN(480, 500) = 480
     * 수단 음수 → effectiveAmount = -480만 (OCI 감소)
     * 과소헤지(수단 절대값 480 &lt; 대상 절대값 500) → ineffective = 0
     *
     * <p>버그 전: calculateEffectivePortion이 +480만을 반환하여
     * OCI Reserve 잔액이 잘못된 방향(증가)으로 누적되었다.
     * 버그 후: calculateSignedEffectivePortion이 -480만을 반환하여
     * OCI Reserve 잔액이 올바르게 감소한다.
     *
     * @see K-IFRS 1109호 6.5.11⑴ (수단 손실 시 OCI 감소)
     * @see K-IFRS 1109호 6.5.11⑷㈐ (OCI 적립금 차손 규정)
     * @see K-IFRS 1109호 BC6.280 (Lower of Test는 규모 제한 장치 — 부호 미변환)
     */
    @Test
    @DisplayName("케이스B: 수단 손실, 과소헤지 — OCI 음수 (핵심 버그 케이스)")
    void caseB_instrumentLoss_underHedge_coreBugCase() {
        BigDecimal instrumentCumulative = new BigDecimal("-4800000");
        BigDecimal hedgedItemCumulative = new BigDecimal("5000000");

        BigDecimal effectiveAmount = LowerOfTestCalculator.calculateSignedEffectivePortion(
                instrumentCumulative, hedgedItemCumulative);

        BigDecimal ineffectiveAmount = LowerOfTestCalculator.calculateSignedIneffectivePortion(
                instrumentCumulative, effectiveAmount);

        // 유효 부분: -480만 (음수 → OCI 감소)
        assertThat(effectiveAmount).isEqualByComparingTo(new BigDecimal("-4800000.00"));
        // 비유효 부분: 0 (과소헤지 — 초과분 없음)
        assertThat(ineffectiveAmount).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    /**
     * 케이스 C: 수단 이익, 과대헤지 — OCI 양수, 비유효 양수.
     *
     * <p>수단누적=+600만, 대상누적=-480만 (반대방향, 과대헤지)
     * Lower of Test: MIN(600, 480) = 480
     * 수단 양수 → effectiveAmount = +480만 (OCI 증가)
     * 과대헤지 초과분 = 600 - 480 = 120 → ineffectiveAmount = +120만 (P/L 이익)
     *
     * @see K-IFRS 1109호 6.5.11⑵ (과대헤지 비효과적 부분 즉시 P/L)
     */
    @Test
    @DisplayName("케이스C: 수단 이익, 과대헤지 — OCI 양수, 비유효 양수")
    void caseC_instrumentGain_overHedge() {
        BigDecimal instrumentCumulative = new BigDecimal("6000000");
        BigDecimal hedgedItemCumulative = new BigDecimal("-4800000");

        BigDecimal effectiveAmount = LowerOfTestCalculator.calculateSignedEffectivePortion(
                instrumentCumulative, hedgedItemCumulative);

        BigDecimal ineffectiveAmount = LowerOfTestCalculator.calculateSignedIneffectivePortion(
                instrumentCumulative, effectiveAmount);

        // 유효 부분: +480만 (양수 → OCI 증가)
        assertThat(effectiveAmount).isEqualByComparingTo(new BigDecimal("4800000.00"));
        // 비유효 부분: +120만 (양수 → P/L 이익)
        assertThat(ineffectiveAmount).isEqualByComparingTo(new BigDecimal("1200000.00"));
    }

    /**
     * 케이스 D: 수단 손실, 과대헤지 — OCI 음수, 비유효 음수.
     *
     * <p>수단누적=-600만, 대상누적=+480만 (반대방향, 과대헤지)
     * Lower of Test: MIN(600, 480) = 480
     * 수단 음수 → effectiveAmount = -480만 (OCI 감소)
     * 과대헤지 초과분 = 600 - 480 = 120 → ineffectiveAmount = -120만 (P/L 손실)
     *
     * @see K-IFRS 1109호 6.5.11⑵ (손실 과대헤지 비효과적 부분 P/L 손실)
     */
    @Test
    @DisplayName("케이스D: 수단 손실, 과대헤지 — OCI 음수, 비유효 음수")
    void caseD_instrumentLoss_overHedge() {
        BigDecimal instrumentCumulative = new BigDecimal("-6000000");
        BigDecimal hedgedItemCumulative = new BigDecimal("4800000");

        BigDecimal effectiveAmount = LowerOfTestCalculator.calculateSignedEffectivePortion(
                instrumentCumulative, hedgedItemCumulative);

        BigDecimal ineffectiveAmount = LowerOfTestCalculator.calculateSignedIneffectivePortion(
                instrumentCumulative, effectiveAmount);

        // 유효 부분: -480만 (음수 → OCI 감소)
        assertThat(effectiveAmount).isEqualByComparingTo(new BigDecimal("-4800000.00"));
        // 비유효 부분: -120만 (음수 → P/L 손실)
        assertThat(ineffectiveAmount).isEqualByComparingTo(new BigDecimal("-1200000.00"));
    }

    // -----------------------------------------------------------------------
    // 기존 메서드 하위 호환성 검증 — calculateEffectivePortion은 항상 양수
    // -----------------------------------------------------------------------

    /**
     * 기존 calculateEffectivePortion은 항상 양수(절댓값)를 반환한다.
     * 다른 곳에서 사용 중일 수 있으므로 동작이 변경되지 않았음을 검증.
     */
    @Test
    @DisplayName("기존 calculateEffectivePortion은 항상 양수(절댓값) 반환 — 하위 호환성")
    void legacyCalculateEffectivePortion_alwaysPositive() {
        BigDecimal gain = LowerOfTestCalculator.calculateEffectivePortion(
                new BigDecimal("5000000"), new BigDecimal("-4800000"));
        BigDecimal loss = LowerOfTestCalculator.calculateEffectivePortion(
                new BigDecimal("-5000000"), new BigDecimal("4800000"));

        assertThat(gain).isEqualByComparingTo(new BigDecimal("4800000.00"));
        assertThat(loss).isEqualByComparingTo(new BigDecimal("4800000.00"));
    }

    /**
     * 수단누적 = 0 인 경우 — 헤지수단 변동 없음, OCI 변동 없음.
     */
    @Test
    @DisplayName("수단누적 0 — effectiveAmount=0, ineffective=0")
    void instrumentCumulativeZero() {
        BigDecimal instrumentCumulative = BigDecimal.ZERO;
        BigDecimal hedgedItemCumulative = new BigDecimal("3000000");

        BigDecimal effectiveAmount = LowerOfTestCalculator.calculateSignedEffectivePortion(
                instrumentCumulative, hedgedItemCumulative);
        BigDecimal ineffectiveAmount = LowerOfTestCalculator.calculateSignedIneffectivePortion(
                instrumentCumulative, effectiveAmount);

        assertThat(effectiveAmount).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(ineffectiveAmount).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    /**
     * 완전 헤지(수단 = 대상 정확히 상쇄) — 비유효 0.
     */
    @Test
    @DisplayName("완전 헤지 — effectiveAmount=수단 절댓값, ineffective=0")
    void perfectHedge() {
        BigDecimal instrumentCumulative = new BigDecimal("-1000000");
        BigDecimal hedgedItemCumulative = new BigDecimal("1000000");

        BigDecimal effectiveAmount = LowerOfTestCalculator.calculateSignedEffectivePortion(
                instrumentCumulative, hedgedItemCumulative);
        BigDecimal ineffectiveAmount = LowerOfTestCalculator.calculateSignedIneffectivePortion(
                instrumentCumulative, effectiveAmount);

        assertThat(effectiveAmount).isEqualByComparingTo(new BigDecimal("-1000000.00"));
        assertThat(ineffectiveAmount).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }
}
