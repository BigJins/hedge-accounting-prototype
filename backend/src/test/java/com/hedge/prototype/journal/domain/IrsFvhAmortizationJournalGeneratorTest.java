package com.hedge.prototype.journal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * IrsFvhAmortizationJournalGenerator 단위 테스트.
 *
 * <p>검증 항목:
 * <ul>
 *   <li>HEDGED_ITEM_ADJ 차변 잔액(양수) 상각 — 채권 상향조정 분개 역전 (손실 인식)</li>
 *   <li>HEDGED_ITEM_ADJ 대변 잔액(음수) 상각 — 채권 하향조정 분개 역전 (이익 인식)</li>
 *   <li>직선법 기간 금액 계산 검증 — 합계 = |cumulativeAdjBalance| 보장</li>
 *   <li>요구사항 시나리오 — 금리 상승(IRS 이익, 채권 -386M) 헤지 중단 후 3기 상각</li>
 *   <li>입력 검증 — null, 잔액=0, remainingPeriods≤0</li>
 * </ul>
 *
 * @see K-IFRS 1109호 §6.5.9     (공정가치헤지 중단 후 장부금액 조정 상각)
 * @see K-IFRS 1109호 §6.5.8(나) (HEDGED_ITEM_ADJ 최초 인식)
 */
@DisplayName("IrsFvhAmortizationJournalGenerator — K-IFRS §6.5.9 상각 분개 단위 테스트")
class IrsFvhAmortizationJournalGeneratorTest {

    private static final String HEDGE_ID    = "HR-IRS-2026-001";
    private static final LocalDate AMORT_DATE = LocalDate.of(2026, 6, 30);

    // -----------------------------------------------------------------------
    // 양수 잔액 시나리오: HEDGED_ITEM_ADJ 차변 잔액 (채권 상향조정 — 금리 하락)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("양수 잔액 — HEDGED_ITEM_ADJ 차변 잔액, 채권 상향조정(금리 하락 시 발생)")
    class PositiveBalanceScenario {

        /**
         * 금리 하락 시 채권 FVH 인식 분개 (최초 인식):
         *   차변 HEDGED_ITEM_ADJ(+195M) / 대변 HEDGE_GAIN_PL
         * → HEDGED_ITEM_ADJ 차변 잔액 = +195M
         *
         * 상각 (§6.5.9):
         *   차변 HEDGE_LOSS_PL / 대변 HEDGED_ITEM_ADJ  (차변 잔액을 줄임)
         */
        private static final BigDecimal POSITIVE_BALANCE = new BigDecimal("195000000"); // +195M

        @Test
        @DisplayName("분개 유형이 IRS_FVH_AMORTIZATION이다")
        void entryType_is_IRS_FVH_AMORTIZATION() {
            JournalEntry entry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, POSITIVE_BALANCE, 3);
            assertThat(entry.getEntryType()).isEqualTo(JournalEntryType.IRS_FVH_AMORTIZATION);
        }

        @Test
        @DisplayName("양수 잔액 상각: 차변=HEDGE_LOSS_PL, 대변=HEDGED_ITEM_ADJ")
        void positiveBalance_debitHedgeLoss_creditHedgedItemAdj() {
            JournalEntry entry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, POSITIVE_BALANCE, 3);
            assertThat(entry.getDebitAccount()).isEqualTo(AccountCode.HEDGE_LOSS_PL);
            assertThat(entry.getCreditAccount()).isEqualTo(AccountCode.HEDGED_ITEM_ADJ);
        }

        @Test
        @DisplayName("직선법 기간 금액 = 195M / 3 = 65M")
        void periodAmount_equals_balanceDividedByPeriods() {
            JournalEntry entry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, POSITIVE_BALANCE, 3);
            // 195_000_000 / 3 = 65_000_000
            assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("65000000"));
        }

        @Test
        @DisplayName("직선법 3기간 합계 = |195M|")
        void threePeriodsSum_equals_absBalance() {
            BigDecimal balance = POSITIVE_BALANCE;
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 3; i >= 1; i--) {
                JournalEntry e = IrsFvhAmortizationJournalGenerator.generate(
                        HEDGE_ID, AMORT_DATE.plusMonths(3 - i), balance, i);
                total = total.add(e.getAmount());
                // 잔액 감소 시뮬레이션
                balance = balance.subtract(e.getAmount());
            }
            assertThat(total).isEqualByComparingTo(POSITIVE_BALANCE);
        }

        @Test
        @DisplayName("hedgeRelationshipId, amortizationDate, ifrsReference 정상 설정")
        void metadata_correct() {
            JournalEntry entry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, POSITIVE_BALANCE, 3);
            assertThat(entry.getHedgeRelationshipId()).isEqualTo(HEDGE_ID);
            assertThat(entry.getEntryDate()).isEqualTo(AMORT_DATE);
            assertThat(entry.getIfrsReference()).contains("6.5.9");
        }
    }

    // -----------------------------------------------------------------------
    // 음수 잔액 시나리오: HEDGED_ITEM_ADJ 대변 잔액 (채권 하향조정 — 금리 상승)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("음수 잔액 — HEDGED_ITEM_ADJ 대변 잔액, 채권 하향조정(금리 상승 시 발생)")
    class NegativeBalanceScenario {

        /**
         * 금리 상승 시 채권 FVH 인식 분개 (최초 인식):
         *   차변 HEDGE_LOSS_PL / 대변 HEDGED_ITEM_ADJ(+386M)
         * → HEDGED_ITEM_ADJ 대변 잔액 = -386M (누계 관점)
         *
         * 상각 (§6.5.9):
         *   차변 HEDGED_ITEM_ADJ / 대변 HEDGE_GAIN_PL  (대변 잔액을 줄임)
         */
        private static final BigDecimal NEGATIVE_BALANCE = new BigDecimal("-386000000"); // -386M

        @Test
        @DisplayName("음수 잔액 상각: 차변=HEDGED_ITEM_ADJ, 대변=HEDGE_GAIN_PL")
        void negativeBalance_debitHedgedItemAdj_creditHedgeGain() {
            JournalEntry entry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, NEGATIVE_BALANCE, 4);
            assertThat(entry.getDebitAccount()).isEqualTo(AccountCode.HEDGED_ITEM_ADJ);
            assertThat(entry.getCreditAccount()).isEqualTo(AccountCode.HEDGE_GAIN_PL);
        }

        @Test
        @DisplayName("직선법 기간 금액 = 386M / 4 = 96.5M (절대값, 양수)")
        void periodAmount_positiveAbsoluteValue() {
            JournalEntry entry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, NEGATIVE_BALANCE, 4);
            // 386_000_000 / 4 = 96_500_000
            assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("96500000"));
            assertThat(entry.getAmount()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("차변과 대변 계정이 서로 다르다 (분개 무결성)")
        void debit_differs_from_credit() {
            JournalEntry entry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, NEGATIVE_BALANCE, 4);
            assertThat(entry.getDebitAccount()).isNotEqualTo(entry.getCreditAccount());
        }
    }

    // -----------------------------------------------------------------------
    // 요구사항 시나리오: 금리 상승 386M 채권 하향조정 → 3기 상각
    // IRS_HEDGE_REQUIREMENTS.md §8: 1조원 채권, 3Y IRS, 금리 3%→4.5%
    // 채권 FV 조정 누계 = -386M → 3기에 걸쳐 직선상각
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("요구사항 시나리오 — -386M 채권 하향조정 3기 직선상각")
    class RequirementsScenario {

        @Test
        @DisplayName("1기: -386M / 3 = -128,666,666.67 (절대값으로 양수 저장)")
        void period1_amount() {
            JournalEntry e1 = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, LocalDate.of(2026, 6, 30),
                    new BigDecimal("-386000000"), 3);
            // 386_000_000 / 3 = 128_666_666.67 (반올림)
            assertThat(e1.getAmount()).isEqualByComparingTo(new BigDecimal("128666666.67"));
        }

        @Test
        @DisplayName("2기: 잔액 갱신 후 2로 나누면 단순 절반")
        void period2_after_first_amortization() {
            // 1기 후 잔액: -386M - (-128,666,666.67) = -257,333,333.33
            BigDecimal remaining = new BigDecimal("-257333333.33");
            JournalEntry e2 = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, LocalDate.of(2026, 9, 30), remaining, 2);
            // 257_333_333.33 / 2 = 128_666_666.67 (반올림)
            assertThat(e2.getAmount()).isEqualByComparingTo(new BigDecimal("128666666.67"));
        }

        @Test
        @DisplayName("3기 상각: remainingPeriods=1 → periodAmount = |잔액|")
        void period3_remainingPeriods1_periodAmountEqualsBalance() {
            BigDecimal finalBalance = new BigDecimal("-128666666.66");
            JournalEntry e3 = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, LocalDate.of(2026, 12, 31), finalBalance, 1);
            assertThat(e3.getAmount()).isEqualByComparingTo(finalBalance.abs());
        }

        @Test
        @DisplayName("모든 기간 분개의 방향이 동일하다 — 차변=HEDGED_ITEM_ADJ, 대변=HEDGE_GAIN_PL")
        void all_periods_same_direction() {
            BigDecimal[] balances = {
                    new BigDecimal("-386000000"),
                    new BigDecimal("-257333333.33"),
                    new BigDecimal("-128666666.67")
            };
            int[] periods = {3, 2, 1};
            for (int i = 0; i < 3; i++) {
                JournalEntry e = IrsFvhAmortizationJournalGenerator.generate(
                        HEDGE_ID, AMORT_DATE.plusMonths(i * 3L), balances[i], periods[i]);
                assertThat(e.getDebitAccount()).isEqualTo(AccountCode.HEDGED_ITEM_ADJ);
                assertThat(e.getCreditAccount()).isEqualTo(AccountCode.HEDGE_GAIN_PL);
            }
        }

        @Test
        @DisplayName("ifrsReference에 'IRS FVH'와 '6.5.9'가 포함된다")
        void ifrsReference_containsIrsFvhAnd659() {
            JournalEntry e = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, new BigDecimal("-386000000"), 3);
            assertThat(e.getIfrsReference()).contains("6.5.9");
            assertThat(e.getIfrsReference()).containsIgnoringCase("IRS FVH");
        }
    }

    // -----------------------------------------------------------------------
    // remainingPeriods=1 특수 케이스 — 마지막 기간
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("remainingPeriods=1 — 마지막 기간 상각")
    class LastPeriodScenario {

        @Test
        @DisplayName("마지막 기간: periodAmount = |cumulativeAdjBalance| 전액")
        void lastPeriod_amount_equalsAbsBalance() {
            BigDecimal finalBalance = new BigDecimal("12345678.90");
            JournalEntry entry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, finalBalance, 1);
            assertThat(entry.getAmount()).isEqualByComparingTo(finalBalance.abs());
        }

        @Test
        @DisplayName("마지막 기간(양수): 차변=HEDGE_LOSS_PL, 대변=HEDGED_ITEM_ADJ")
        void lastPeriod_positive_direction() {
            JournalEntry entry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, new BigDecimal("50000000"), 1);
            assertThat(entry.getDebitAccount()).isEqualTo(AccountCode.HEDGE_LOSS_PL);
            assertThat(entry.getCreditAccount()).isEqualTo(AccountCode.HEDGED_ITEM_ADJ);
        }
    }

    // -----------------------------------------------------------------------
    // 입력 검증
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("입력 검증")
    class InputValidation {

        @Test
        @DisplayName("hedgeRelationshipId=null → NullPointerException")
        void nullHedgeRelationshipId_throws() {
            assertThatNullPointerException().isThrownBy(() ->
                    IrsFvhAmortizationJournalGenerator.generate(
                            null, AMORT_DATE, new BigDecimal("100000"), 3));
        }

        @Test
        @DisplayName("amortizationDate=null → NullPointerException")
        void nullAmortizationDate_throws() {
            assertThatNullPointerException().isThrownBy(() ->
                    IrsFvhAmortizationJournalGenerator.generate(
                            HEDGE_ID, null, new BigDecimal("100000"), 3));
        }

        @Test
        @DisplayName("cumulativeAdjBalance=null → NullPointerException")
        void nullCumulativeAdjBalance_throws() {
            assertThatNullPointerException().isThrownBy(() ->
                    IrsFvhAmortizationJournalGenerator.generate(
                            HEDGE_ID, AMORT_DATE, null, 3));
        }

        @Test
        @DisplayName("cumulativeAdjBalance=0 → IllegalArgumentException (상각 불필요)")
        void zeroCumulativeAdjBalance_throws() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    IrsFvhAmortizationJournalGenerator.generate(
                            HEDGE_ID, AMORT_DATE, BigDecimal.ZERO, 3))
                    .withMessageContaining("0");
        }

        @Test
        @DisplayName("remainingPeriods=0 → IllegalArgumentException")
        void zeroPeriods_throws() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    IrsFvhAmortizationJournalGenerator.generate(
                            HEDGE_ID, AMORT_DATE, new BigDecimal("100000"), 0));
        }

        @Test
        @DisplayName("remainingPeriods=-1 → IllegalArgumentException")
        void negativePeriods_throws() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    IrsFvhAmortizationJournalGenerator.generate(
                            HEDGE_ID, AMORT_DATE, new BigDecimal("100000"), -1));
        }
    }

    // -----------------------------------------------------------------------
    // 분개 무결성 공통 검증
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("분개 무결성 — 모든 시나리오 공통")
    class JournalIntegrity {

        @Test
        @DisplayName("금액은 항상 양수 (절대값 저장)")
        void amount_always_positive() {
            // 양수 잔액
            JournalEntry positiveEntry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, new BigDecimal("100000000"), 2);
            assertThat(positiveEntry.getAmount()).isGreaterThan(BigDecimal.ZERO);

            // 음수 잔액
            JournalEntry negativeEntry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, new BigDecimal("-100000000"), 2);
            assertThat(negativeEntry.getAmount()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("차변 계정 ≠ 대변 계정 (이중분개 무결성)")
        void debit_differs_from_credit_always() {
            JournalEntry e1 = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, new BigDecimal("100000000"), 2);
            JournalEntry e2 = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, new BigDecimal("-100000000"), 2);

            assertThat(e1.getDebitAccount()).isNotEqualTo(e1.getCreditAccount());
            assertThat(e2.getDebitAccount()).isNotEqualTo(e2.getCreditAccount());
        }

        @Test
        @DisplayName("entryType은 IRS_FVH_AMORTIZATION — FVH 인식 분개 유형과 구별됨")
        void entryType_distinguishable_from_recognition_entries() {
            JournalEntry amortEntry = IrsFvhAmortizationJournalGenerator.generate(
                    HEDGE_ID, AMORT_DATE, new BigDecimal("-100000000"), 1);
            assertThat(amortEntry.getEntryType())
                    .isEqualTo(JournalEntryType.IRS_FVH_AMORTIZATION)
                    .isNotEqualTo(JournalEntryType.FAIR_VALUE_HEDGE_ITEM)
                    .isNotEqualTo(JournalEntryType.FAIR_VALUE_HEDGE_INSTRUMENT);
        }
    }
}
