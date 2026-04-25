package com.hedge.prototype.journal.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.HedgeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * JournalEntryRequest 팩토리 메서드 및 필드 구조 단위 테스트.
 *
 * <p>핵심 검증 항목:
 * <ul>
 *   <li>forAutoGeneration() — hedgeType 무시 버그 제거 후 UnsupportedOperationException 발생 확인</li>
 *   <li>forAutoGenerationFvh() — FAIR_VALUE 타입, FVH 필드 세팅 확인</li>
 *   <li>forAutoGenerationCfh() — CASH_FLOW 타입, CFH 필드 세팅 확인</li>
 * </ul>
 */
class JournalEntryRequestTest {

    private static final String HEDGE_ID = "HR-2026-001";
    private static final LocalDate ENTRY_DATE = LocalDate.of(2026, 4, 23);
    private static final BigDecimal INSTRUMENT_FV  = new BigDecimal("500000");
    private static final BigDecimal HEDGED_ITEM_FV = new BigDecimal("-480000");
    private static final BigDecimal EFFECTIVE       = new BigDecimal("-480000");
    private static final BigDecimal INEFFECTIVE     = BigDecimal.ZERO;

    // -----------------------------------------------------------------------
    // forAutoGeneration() — 버그 제거 후 예외 발생
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("forAutoGeneration() — hedgeType 무시 버그 제거")
    class DeprecatedForAutoGeneration {

        @Test
        @DisplayName("FAIR_VALUE로 호출해도 UnsupportedOperationException 발생")
        void whenCalledWithFairValue_thenThrowsUnsupportedOperationException() {
            assertThatThrownBy(() ->
                    JournalEntryRequest.forAutoGeneration(
                            HEDGE_ID, ENTRY_DATE, HedgeType.FAIR_VALUE,
                            INSTRUMENT_FV, HEDGED_ITEM_FV))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("forAutoGenerationFvh");
        }

        @Test
        @DisplayName("CASH_FLOW로 호출해도 UnsupportedOperationException 발생 (hedgeType 무시 버그 재현 방지)")
        void whenCalledWithCashFlow_thenThrowsUnsupportedOperationException() {
            assertThatThrownBy(() ->
                    JournalEntryRequest.forAutoGeneration(
                            HEDGE_ID, ENTRY_DATE, HedgeType.CASH_FLOW,
                            INSTRUMENT_FV, HEDGED_ITEM_FV))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("forAutoGenerationCfh");
        }
    }

    // -----------------------------------------------------------------------
    // forAutoGenerationFvh()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("forAutoGenerationFvh() — FVH 자동 분개 요청")
    class ForAutoGenerationFvh {

        @Test
        @DisplayName("hedgeType이 FAIR_VALUE로 설정된다")
        void hedgeTypeIsFairValue() {
            JournalEntryRequest req = JournalEntryRequest.forAutoGenerationFvh(
                    HEDGE_ID, ENTRY_DATE, INSTRUMENT_FV, HEDGED_ITEM_FV);

            assertThat(req.hedgeType()).isEqualTo(HedgeType.FAIR_VALUE);
        }

        @Test
        @DisplayName("FVH 필수 필드가 정확히 세팅된다")
        void fvhFieldsAreSet() {
            JournalEntryRequest req = JournalEntryRequest.forAutoGenerationFvh(
                    HEDGE_ID, ENTRY_DATE, INSTRUMENT_FV, HEDGED_ITEM_FV);

            assertThat(req.hedgeRelationshipId()).isEqualTo(HEDGE_ID);
            assertThat(req.entryDate()).isEqualTo(ENTRY_DATE);
            assertThat(req.instrumentFvChange()).isEqualByComparingTo(INSTRUMENT_FV);
            assertThat(req.hedgedItemFvChange()).isEqualByComparingTo(HEDGED_ITEM_FV);
        }

        @Test
        @DisplayName("CFH 전용 필드(effectiveAmount, ineffectiveAmount)는 null이다")
        void cfhFieldsAreNull() {
            JournalEntryRequest req = JournalEntryRequest.forAutoGenerationFvh(
                    HEDGE_ID, ENTRY_DATE, INSTRUMENT_FV, HEDGED_ITEM_FV);

            assertThat(req.effectiveAmount()).isNull();
            assertThat(req.ineffectiveAmount()).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // forAutoGenerationCfh()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("forAutoGenerationCfh() — CFH 자동 분개 요청")
    class ForAutoGenerationCfh {

        @Test
        @DisplayName("hedgeType이 CASH_FLOW로 설정된다")
        void hedgeTypeIsCashFlow() {
            JournalEntryRequest req = JournalEntryRequest.forAutoGenerationCfh(
                    HEDGE_ID, ENTRY_DATE, INSTRUMENT_FV, HEDGED_ITEM_FV, EFFECTIVE, INEFFECTIVE);

            assertThat(req.hedgeType()).isEqualTo(HedgeType.CASH_FLOW);
        }

        @Test
        @DisplayName("CFH 필수 필드(effectiveAmount, ineffectiveAmount)가 정확히 세팅된다")
        void cfhFieldsAreSet() {
            JournalEntryRequest req = JournalEntryRequest.forAutoGenerationCfh(
                    HEDGE_ID, ENTRY_DATE, INSTRUMENT_FV, HEDGED_ITEM_FV, EFFECTIVE, INEFFECTIVE);

            assertThat(req.effectiveAmount()).isEqualByComparingTo(EFFECTIVE);
            assertThat(req.ineffectiveAmount()).isEqualByComparingTo(INEFFECTIVE);
        }

        @Test
        @DisplayName("손실 케이스(음수 effectiveAmount)도 부호 그대로 세팅된다")
        void lossCase_effectiveAmountPreservesSign() {
            BigDecimal lossEffective = new BigDecimal("-480000");
            JournalEntryRequest req = JournalEntryRequest.forAutoGenerationCfh(
                    HEDGE_ID, ENTRY_DATE, new BigDecimal("-500000"), new BigDecimal("480000"),
                    lossEffective, BigDecimal.ZERO);

            assertThat(req.effectiveAmount().signum()).isNegative();
        }

        @Test
        @DisplayName("FVH와 CFH 팩토리가 서로 다른 hedgeType을 반환한다 (교차 혼동 방지)")
        void fvhAndCfhReturnDifferentHedgeTypes() {
            JournalEntryRequest fvh = JournalEntryRequest.forAutoGenerationFvh(
                    HEDGE_ID, ENTRY_DATE, INSTRUMENT_FV, HEDGED_ITEM_FV);
            JournalEntryRequest cfh = JournalEntryRequest.forAutoGenerationCfh(
                    HEDGE_ID, ENTRY_DATE, INSTRUMENT_FV, HEDGED_ITEM_FV, EFFECTIVE, INEFFECTIVE);

            assertThat(fvh.hedgeType()).isNotEqualTo(cfh.hedgeType());
        }
    }
}
