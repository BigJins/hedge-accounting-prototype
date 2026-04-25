package com.hedge.prototype.journal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * IrsFairValueHedgeJournalGenerator 단위 테스트.
 *
 * <p>검증 항목:
 * <ul>
 *   <li>IRS FVH 이익 시나리오 — 금리 상승, IRS 이익, 채권 하락</li>
 *   <li>IRS FVH 손실 시나리오 — 금리 하락, IRS 손실, 채권 상승</li>
 *   <li>요구사항 시나리오 (IRS_HEDGE_REQUIREMENTS.md 8절) — 390M / -386M / +4M</li>
 *   <li>FX Forward 회귀 방지 — FVH Generator가 독립적으로 동작함을 확인</li>
 *   <li>입력 검증 — null 파라미터 시 NullPointerException</li>
 * </ul>
 *
 * @see K-IFRS 1109호 6.5.8     (공정가치위험회피 회계처리)
 * @see K-IFRS 1109호 6.5.8(가) (IRS 헤지수단 P&L 인식)
 * @see K-IFRS 1109호 6.5.8(나) (채권 피헤지항목 장부금액 조정)
 * @see K-IFRS 1109호 6.5.9     (채권 장부조정 상각)
 */
@DisplayName("IrsFairValueHedgeJournalGenerator — K-IFRS 1109호 6.5.8 IRS FVH 분개 단위 테스트")
class IrsFairValueHedgeJournalGeneratorTest {

    private static final String HEDGE_ID = "HR-IRS-2026-001";
    private static final LocalDate ENTRY_DATE = LocalDate.of(2026, 3, 31);

    // -----------------------------------------------------------------------
    // 금리 상승 시나리오: IRS 이익 + 채권 하락
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("금리 상승 시나리오 — IRS 이익(+), 채권 하락(-)")
    class RateRiseScenario {

        private static final BigDecimal IRS_GAIN   = new BigDecimal("390000000");  // IRS +390M
        private static final BigDecimal BOND_LOSS  = new BigDecimal("-386000000"); // 채권 -386M

        @Test
        @DisplayName("분개 2건 생성 — 헤지수단(IRS) + 피헤지항목(채권)")
        void generates_two_entries() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_GAIN, BOND_LOSS);
            assertThat(entries).hasSize(2);
        }

        @Test
        @DisplayName("헤지수단 분개 — 유형: FAIR_VALUE_HEDGE_INSTRUMENT")
        void instrumentEntry_hasCorrectType() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_GAIN, BOND_LOSS);
            JournalEntry instrument = entries.get(0);
            assertThat(instrument.getEntryType()).isEqualTo(JournalEntryType.FAIR_VALUE_HEDGE_INSTRUMENT);
        }

        @Test
        @DisplayName("헤지수단 분개 — 금리 상승 IRS 이익: 차변=DRV_ASSET, 대변=DRV_GAIN_PL")
        void instrumentEntry_rateRise_debitDrvAsset_creditDrvGainPl() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_GAIN, BOND_LOSS);
            JournalEntry instrument = entries.get(0);
            assertThat(instrument.getDebitAccount()).isEqualTo(AccountCode.DRV_ASSET);
            assertThat(instrument.getCreditAccount()).isEqualTo(AccountCode.DRV_GAIN_PL);
        }

        @Test
        @DisplayName("헤지수단 분개 — 금액 절대값(390M) 일치")
        void instrumentEntry_amount_equals_absIrsGain() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_GAIN, BOND_LOSS);
            assertThat(entries.get(0).getAmount())
                    .isEqualByComparingTo(IRS_GAIN.abs());
        }

        @Test
        @DisplayName("피헤지항목 분개 — 유형: FAIR_VALUE_HEDGE_ITEM")
        void hedgedItemEntry_hasCorrectType() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_GAIN, BOND_LOSS);
            JournalEntry hedgedItem = entries.get(1);
            assertThat(hedgedItem.getEntryType()).isEqualTo(JournalEntryType.FAIR_VALUE_HEDGE_ITEM);
        }

        @Test
        @DisplayName("피헤지항목 분개 — 채권 하락: 차변=HEDGE_LOSS_PL, 대변=HEDGED_ITEM_ADJ")
        void hedgedItemEntry_bondFall_debitHedgeLoss_creditHedgedItemAdj() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_GAIN, BOND_LOSS);
            JournalEntry hedgedItem = entries.get(1);
            assertThat(hedgedItem.getDebitAccount()).isEqualTo(AccountCode.HEDGE_LOSS_PL);
            assertThat(hedgedItem.getCreditAccount()).isEqualTo(AccountCode.HEDGED_ITEM_ADJ);
        }

        @Test
        @DisplayName("피헤지항목 분개 — 금액 절대값(386M) 일치")
        void hedgedItemEntry_amount_equals_absBondLoss() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_GAIN, BOND_LOSS);
            assertThat(entries.get(1).getAmount())
                    .isEqualByComparingTo(BOND_LOSS.abs());
        }

        @Test
        @DisplayName("두 분개의 hedgeRelationshipId가 동일하다")
        void both_entries_share_hedgeRelationshipId() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_GAIN, BOND_LOSS);
            assertThat(entries).allMatch(e -> HEDGE_ID.equals(e.getHedgeRelationshipId()));
        }

        @Test
        @DisplayName("두 분개의 entryDate가 동일하다")
        void both_entries_share_entryDate() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_GAIN, BOND_LOSS);
            assertThat(entries).allMatch(e -> ENTRY_DATE.equals(e.getEntryDate()));
        }

        @Test
        @DisplayName("차변과 대변 계정이 서로 다르다 (분개 무결성)")
        void each_entry_debit_differs_from_credit() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_GAIN, BOND_LOSS);
            entries.forEach(e ->
                    assertThat(e.getDebitAccount()).isNotEqualTo(e.getCreditAccount()));
        }
    }

    // -----------------------------------------------------------------------
    // 금리 하락 시나리오: IRS 손실 + 채권 상승
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("금리 하락 시나리오 — IRS 손실(-), 채권 상승(+)")
    class RateFallScenario {

        private static final BigDecimal IRS_LOSS   = new BigDecimal("-200000000");  // IRS -200M
        private static final BigDecimal BOND_RISE  = new BigDecimal("195000000");   // 채권 +195M

        @Test
        @DisplayName("헤지수단 분개 — 금리 하락 IRS 손실: 차변=DRV_LOSS_PL, 대변=DRV_LIAB")
        void instrumentEntry_rateFall_debitDrvLoss_creditDrvLiab() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_LOSS, BOND_RISE);
            JournalEntry instrument = entries.get(0);
            assertThat(instrument.getDebitAccount()).isEqualTo(AccountCode.DRV_LOSS_PL);
            assertThat(instrument.getCreditAccount()).isEqualTo(AccountCode.DRV_LIAB);
        }

        @Test
        @DisplayName("헤지수단 분개 — 금액 절대값(200M) 일치")
        void instrumentEntry_amount_equals_absIrsLoss() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_LOSS, BOND_RISE);
            assertThat(entries.get(0).getAmount())
                    .isEqualByComparingTo(IRS_LOSS.abs());
        }

        @Test
        @DisplayName("피헤지항목 분개 — 채권 상승: 차변=HEDGED_ITEM_ADJ, 대변=HEDGE_GAIN_PL")
        void hedgedItemEntry_bondRise_debitHedgedItemAdj_creditHedgeGain() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_LOSS, BOND_RISE);
            JournalEntry hedgedItem = entries.get(1);
            assertThat(hedgedItem.getDebitAccount()).isEqualTo(AccountCode.HEDGED_ITEM_ADJ);
            assertThat(hedgedItem.getCreditAccount()).isEqualTo(AccountCode.HEDGE_GAIN_PL);
        }

        @Test
        @DisplayName("피헤지항목 분개 — 금액 절대값(195M) 일치")
        void hedgedItemEntry_amount_equals_absBondRise() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_LOSS, BOND_RISE);
            assertThat(entries.get(1).getAmount())
                    .isEqualByComparingTo(BOND_RISE.abs());
        }
    }

    // -----------------------------------------------------------------------
    // 요구사항 시나리오 (IRS_HEDGE_REQUIREMENTS.md §8)
    // 10B KRW 고정금리채권, 3Y, 쿠폰 3%, CD91 3%→4.5% (1Q2026)
    // IRS FV = +390M, 채권 FV조정 = -386M, 비효과 = +4M
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("요구사항 시나리오 — IRS_HEDGE_REQUIREMENTS.md §8 (1Q2026)")
    class RequirementsScenario {

        private static final BigDecimal IRS_FV_CHANGE   = new BigDecimal("390000000");
        private static final BigDecimal BOND_FV_CHANGE  = new BigDecimal("-386000000");

        @Test
        @DisplayName("IRS FV 390M — 헤지수단 분개 차변=DRV_ASSET(390M), 대변=DRV_GAIN_PL")
        void irsFvGain_390M_debitDrvAsset() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_FV_CHANGE, BOND_FV_CHANGE);
            JournalEntry instrument = entries.get(0);
            assertThat(instrument.getDebitAccount()).isEqualTo(AccountCode.DRV_ASSET);
            assertThat(instrument.getCreditAccount()).isEqualTo(AccountCode.DRV_GAIN_PL);
            assertThat(instrument.getAmount()).isEqualByComparingTo(new BigDecimal("390000000"));
        }

        @Test
        @DisplayName("채권 FV -386M — 피헤지항목 분개 차변=HEDGE_LOSS_PL(386M), 대변=HEDGED_ITEM_ADJ")
        void bondFvLoss_386M_debitHedgeLossAdj() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_FV_CHANGE, BOND_FV_CHANGE);
            JournalEntry hedgedItem = entries.get(1);
            assertThat(hedgedItem.getDebitAccount()).isEqualTo(AccountCode.HEDGE_LOSS_PL);
            assertThat(hedgedItem.getCreditAccount()).isEqualTo(AccountCode.HEDGED_ITEM_ADJ);
            assertThat(hedgedItem.getAmount()).isEqualByComparingTo(new BigDecimal("386000000"));
        }

        @Test
        @DisplayName("비효과성 = IRS(390M) + 채권(-386M) = +4M이 두 분개에 자동 반영됨")
        void ineffectiveness_4M_implicitlyRecognised() {
            // K-IFRS 1109호 6.5.8: 두 분개의 P&L 금액 차이가 비효과성
            // 헤지수단 이익 390M(대변 DRV_GAIN_PL) - 피헤지항목 손실 386M(차변 HEDGE_LOSS_PL) = +4M 순이익
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_FV_CHANGE, BOND_FV_CHANGE);

            BigDecimal instrumentPl = entries.get(0).getAmount();   // +390M (P&L 이익)
            BigDecimal hedgedItemPl = entries.get(1).getAmount();    // +386M (P&L 손실 인식 금액)

            // 순 비효과성 = 390M - 386M = 4M
            BigDecimal netIneffectiveness = instrumentPl.subtract(hedgedItemPl);
            assertThat(netIneffectiveness).isEqualByComparingTo(new BigDecimal("4000000"));
        }

        @Test
        @DisplayName("헤지수단 분개 K-IFRS 참조에 '6.5.8(가)'와 'IRS FVH'가 포함된다")
        void instrumentEntry_ifrsReference_containsIrsFvhTag() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_FV_CHANGE, BOND_FV_CHANGE);
            String ref = entries.get(0).getIfrsReference();
            assertThat(ref).contains("6.5.8(가)");
            assertThat(ref).containsIgnoringCase("IRS FVH");
        }

        @Test
        @DisplayName("피헤지항목 분개 K-IFRS 참조에 '6.5.8(나)', '6.5.9', 'IRS FVH'가 포함된다")
        void hedgedItemEntry_ifrsReference_containsBondAdjTag() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE, IRS_FV_CHANGE, BOND_FV_CHANGE);
            String ref = entries.get(1).getIfrsReference();
            assertThat(ref).contains("6.5.8(나)");
            assertThat(ref).contains("6.5.9");
            assertThat(ref).containsIgnoringCase("IRS FVH");
        }
    }

    // -----------------------------------------------------------------------
    // FX Forward 회귀 방지
    // FairValueHedgeJournalGenerator와 IrsFairValueHedgeJournalGenerator가
    // 독립적으로 다른 적요(description)를 생성함을 확인
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("FX Forward 회귀 방지 — 기존 FVH 분개와 독립")
    class FxForwardRegression {

        @Test
        @DisplayName("IRS FVH 헤지수단 분개 적요에 'IRS(금리스왑)'이 포함된다")
        void irsInstrumentEntry_description_mentionsIrs() {
            List<JournalEntry> entries = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE,
                    new BigDecimal("100000"),
                    new BigDecimal("-95000"));
            assertThat(entries.get(0).getDescription()).contains("IRS(금리스왑)");
        }

        @Test
        @DisplayName("FX FVH 헤지수단 분개 적요에 '파생상품'이 포함된다 (FX 경로 유지 확인)")
        void fxInstrumentEntry_description_mentionsDerivative() {
            List<JournalEntry> fxEntries = FairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE,
                    new BigDecimal("100000"),
                    new BigDecimal("-95000"));
            assertThat(fxEntries.get(0).getDescription()).contains("파생상품");
        }

        @Test
        @DisplayName("IRS FVH와 FX FVH의 계정과목 코드는 동일 (공통 구조)")
        void irs_and_fx_share_account_codes_for_gain_scenario() {
            List<JournalEntry> irs = IrsFairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE,
                    new BigDecimal("100000"), new BigDecimal("-95000"));
            List<JournalEntry> fx = FairValueHedgeJournalGenerator.generate(
                    HEDGE_ID, ENTRY_DATE,
                    new BigDecimal("100000"), new BigDecimal("-95000"));

            // 계정과목 구조는 K-IFRS 6.5.8 공통이므로 동일해야 함
            assertThat(irs.get(0).getDebitAccount()).isEqualTo(fx.get(0).getDebitAccount());
            assertThat(irs.get(0).getCreditAccount()).isEqualTo(fx.get(0).getCreditAccount());
            assertThat(irs.get(1).getDebitAccount()).isEqualTo(fx.get(1).getDebitAccount());
            assertThat(irs.get(1).getCreditAccount()).isEqualTo(fx.get(1).getCreditAccount());
        }
    }

    // -----------------------------------------------------------------------
    // 입력 검증
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("입력 검증 — null 파라미터")
    class InputValidation {

        @Test
        @DisplayName("hedgeRelationshipId=null → NullPointerException")
        void nullHedgeRelationshipId_throws() {
            assertThatNullPointerException().isThrownBy(() ->
                    IrsFairValueHedgeJournalGenerator.generate(
                            null, ENTRY_DATE,
                            new BigDecimal("100000"), new BigDecimal("-95000")));
        }

        @Test
        @DisplayName("entryDate=null → NullPointerException")
        void nullEntryDate_throws() {
            assertThatNullPointerException().isThrownBy(() ->
                    IrsFairValueHedgeJournalGenerator.generate(
                            HEDGE_ID, null,
                            new BigDecimal("100000"), new BigDecimal("-95000")));
        }

        @Test
        @DisplayName("instrumentFvChange=null → NullPointerException")
        void nullInstrumentFvChange_throws() {
            assertThatNullPointerException().isThrownBy(() ->
                    IrsFairValueHedgeJournalGenerator.generate(
                            HEDGE_ID, ENTRY_DATE,
                            null, new BigDecimal("-95000")));
        }

        @Test
        @DisplayName("hedgedItemFvChange=null → NullPointerException")
        void nullHedgedItemFvChange_throws() {
            assertThatNullPointerException().isThrownBy(() ->
                    IrsFairValueHedgeJournalGenerator.generate(
                            HEDGE_ID, ENTRY_DATE,
                            new BigDecimal("100000"), null));
        }
    }
}
