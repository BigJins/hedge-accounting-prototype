package com.hedge.prototype.effectiveness.application.event;

import com.hedge.prototype.journal.adapter.web.dto.JournalEntryRequest;
import com.hedge.prototype.hedge.application.HedgeRebalancingService;
import com.hedge.prototype.journal.application.JournalEntryUseCase;
import com.hedge.prototype.effectiveness.application.port.EffectivenessTestRepository;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.effectiveness.domain.ActionRequired;
import com.hedge.prototype.effectiveness.domain.EffectivenessTest;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestResult;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestType;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * EffectivenessTestCompletedEventHandler 단위 테스트.
 *
 * <p>AVM-015: WARNING + REBALANCE 케이스에서 분개가 이중 생성되지 않음을 검증합니다.
 *
 * <ul>
 *   <li>PASS → EventHandler 분개 생성 1회, RebalancingService 호출 없음</li>
 *   <li>WARNING + REBALANCE → EventHandler 분개 생성 0회, RebalancingService 1회</li>
 *   <li>FAIL + DISCONTINUE → 분개 생성 0회, RebalancingService 호출 없음</li>
 * </ul>
 *
 * @see K-IFRS 1109호 6.5.5 (재조정 의무)
 * @see K-IFRS 1109호 6.5.6 (전진 중단)
 * @see K-IFRS 1109호 6.5.8 (공정가치헤지 회계처리)
 * @see K-IFRS 1109호 B6.5.8 (재조정 전 비효과성 당기손익 인식 선행)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EffectivenessTestCompletedEventHandler — 분개 생성 책임 단위 테스트 (AVM-015)")
class EffectivenessTestCompletedEventHandlerTest {

    @Mock
    private EffectivenessTestRepository effectivenessTestRepository;

    @Mock
    private JournalEntryUseCase journalEntryUseCase;

    @Mock
    private HedgeRebalancingService hedgeRebalancingService;

    private EffectivenessTestCompletedEventHandler handler;

    private static final Long TEST_ID = 1L;
    private static final String HEDGE_RELATIONSHIP_ID = "HR-001";
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        handler = new EffectivenessTestCompletedEventHandler(
                effectivenessTestRepository,
                journalEntryUseCase,
                hedgeRebalancingService);
    }

    // -----------------------------------------------------------------------
    // AVM-015: WARNING + REBALANCE 이중 분개 방지 핵심 테스트
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AVM-015: WARNING + REBALANCE 케이스")
    class WarningAndRebalanceTest {

        /**
         * AVM-015 핵심 케이스: WARNING + REBALANCE 시 분개는 정확히 0회 (RebalancingService 전담).
         *
         * <p>K-IFRS 1109호 B6.5.8: 재조정 전 비효과성 분개는 HedgeRebalancingService.processRebalancing()
         * 내부의 recognizePreRebalancingIneffectiveness()가 단독으로 생성합니다.
         * EventHandler가 분개를 추가로 생성하면 동일 사건에 대해 분개가 2회 저장됩니다.
         */
        @Test
        @DisplayName("WARNING + REBALANCE → EventHandler 분개 생성 0회, RebalancingService.processRebalancing() 정확히 1회")
        void whenWarningAndRebalance_thenJournalEntryCreatedExactlyOnce() {
            // given
            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE,
                    HedgeType.FAIR_VALUE,
                    null);  // null → FX_FORWARD 하위호환

            EffectivenessTest mockTest = buildMockTest(
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE,
                    HedgeType.FAIR_VALUE,
                    new BigDecimal("100000"),
                    new BigDecimal("-95000"),
                    new BigDecimal("5000"));

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(mockTest));

            // when
            handler.handle(event);

            // then — EventHandler는 분개를 생성하지 않는다 (AVM-015 핵심 검증)
            then(journalEntryUseCase).should(never()).createEntries(any());

            // then — RebalancingService.processRebalancing()이 정확히 1회 호출된다
            then(hedgeRebalancingService).should(times(1))
                    .processRebalancing(TEST_ID, HEDGE_RELATIONSHIP_ID, TEST_DATE);
        }

        @Test
        @DisplayName("WARNING + REBALANCE (CFH) → EventHandler 분개 생성 0회, RebalancingService 1회")
        void whenWarningAndRebalanceCfh_thenJournalEntryCreatedExactlyOnce() {
            // given
            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE,
                    HedgeType.CASH_FLOW,
                    null);  // null → FX_FORWARD 하위호환

            EffectivenessTest mockTest = buildMockTest(
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE,
                    HedgeType.CASH_FLOW,
                    new BigDecimal("80000"),
                    new BigDecimal("-70000"),
                    new BigDecimal("10000"));

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(mockTest));

            // when
            handler.handle(event);

            // then — 분개 생성 없음
            then(journalEntryUseCase).should(never()).createEntries(any());

            // then — 재조정 서비스 1회
            then(hedgeRebalancingService).should(times(1))
                    .processRebalancing(TEST_ID, HEDGE_RELATIONSHIP_ID, TEST_DATE);
        }
    }

    // -----------------------------------------------------------------------
    // PASS: EventHandler 분개 생성 전담
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("PASS 케이스 — EventHandler 분개 전담")
    class PassTest {

        @Test
        @DisplayName("PASS (FVH) → 분개 1회 생성, RebalancingService 호출 없음")
        void whenPass_fvh_thenJournalEntryCreatedOnceAndNoRebalancing() {
            // given
            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.PASS,
                    ActionRequired.NONE,
                    HedgeType.FAIR_VALUE,
                    null);  // null → FX_FORWARD 하위호환

            EffectivenessTest mockTest = buildMockTest(
                    EffectivenessTestResult.PASS,
                    ActionRequired.NONE,
                    HedgeType.FAIR_VALUE,
                    new BigDecimal("100000"),
                    new BigDecimal("-98000"),
                    BigDecimal.ZERO);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(mockTest));
            given(journalEntryUseCase.createEntries(any())).willReturn(java.util.List.of());

            // when
            handler.handle(event);

            // then — 분개 1회 생성
            then(journalEntryUseCase).should(times(1)).createEntries(any());

            // then — 재조정 없음
            then(hedgeRebalancingService).should(never())
                    .processRebalancing(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("PASS (CFH) → 분개 1회 생성, RebalancingService 호출 없음")
        void whenPass_cfh_thenJournalEntryCreatedOnce() {
            // given
            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.PASS,
                    ActionRequired.NONE,
                    HedgeType.CASH_FLOW,
                    null);  // null → FX_FORWARD 하위호환

            EffectivenessTest mockTest = buildMockTest(
                    EffectivenessTestResult.PASS,
                    ActionRequired.NONE,
                    HedgeType.CASH_FLOW,
                    new BigDecimal("100000"),
                    new BigDecimal("-98000"),
                    BigDecimal.ZERO);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(mockTest));
            given(journalEntryUseCase.createEntries(any())).willReturn(java.util.List.of());

            // when
            handler.handle(event);

            // then — CFH 분개 생성 1회 (effectiveAmount, ineffectiveAmount 포함)
            ArgumentCaptor<JournalEntryRequest> captor = ArgumentCaptor.forClass(JournalEntryRequest.class);
            then(journalEntryUseCase).should(times(1)).createEntries(captor.capture());

            JournalEntryRequest captured = captor.getValue();
            assertThat(captured.hedgeType()).isEqualTo(HedgeType.CASH_FLOW);
            assertThat(captured.hedgeRelationshipId()).isEqualTo(HEDGE_RELATIONSHIP_ID);
            assertThat(captured.entryDate()).isEqualTo(TEST_DATE);

            then(hedgeRebalancingService).should(never())
                    .processRebalancing(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("PASS (IRS FVH) → instrumentType=IRS 분개 요청 생성, forAutoGenerationIrsFvh 경로")
        void whenPass_irsFvh_thenJournalRequestHasInstrumentTypeIrs() {
            // given — instrumentType=IRS 이벤트
            BigDecimal instrumentFvChange = new BigDecimal("390000000");  // IRS +390M
            BigDecimal hedgedItemPvChange = new BigDecimal("-386000000"); // 채권 -386M

            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.PASS,
                    ActionRequired.NONE,
                    HedgeType.FAIR_VALUE,
                    InstrumentType.IRS);  // 2단계: IRS FVH 분개 경로

            EffectivenessTest mockTest = buildMockTestWithInstrumentType(
                    EffectivenessTestResult.PASS,
                    ActionRequired.NONE,
                    HedgeType.FAIR_VALUE,
                    instrumentFvChange,
                    hedgedItemPvChange,
                    BigDecimal.ZERO,
                    InstrumentType.IRS);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(mockTest));
            given(journalEntryUseCase.createEntries(any())).willReturn(java.util.List.of());

            // when
            handler.handle(event);

            // then — 분개 요청 1회, instrumentType=IRS, hedgeType=FAIR_VALUE
            ArgumentCaptor<JournalEntryRequest> captor = ArgumentCaptor.forClass(JournalEntryRequest.class);
            then(journalEntryUseCase).should(times(1)).createEntries(captor.capture());

            JournalEntryRequest captured = captor.getValue();
            assertThat(captured.hedgeType()).isEqualTo(HedgeType.FAIR_VALUE);
            assertThat(captured.instrumentType()).isEqualTo(InstrumentType.IRS);
            assertThat(captured.hedgeRelationshipId()).isEqualTo(HEDGE_RELATIONSHIP_ID);
            assertThat(captured.entryDate()).isEqualTo(TEST_DATE);
            assertThat(captured.instrumentFvChange()).isEqualByComparingTo(instrumentFvChange);
            assertThat(captured.hedgedItemFvChange()).isEqualByComparingTo(hedgedItemPvChange);

            // then — 재조정 없음
            then(hedgeRebalancingService).should(never())
                    .processRebalancing(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("PASS (FVH) — 분개 요청에 올바른 FVH 필드가 담겨 있어야 한다")
        void whenPass_fvh_thenJournalRequestContainsCorrectFields() {
            // given
            BigDecimal instrumentFvChange = new BigDecimal("100000");
            BigDecimal hedgedItemPvChange = new BigDecimal("-98000");

            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.PASS,
                    ActionRequired.NONE,
                    HedgeType.FAIR_VALUE,
                    null);  // null → FX_FORWARD 하위호환

            EffectivenessTest mockTest = buildMockTest(
                    EffectivenessTestResult.PASS,
                    ActionRequired.NONE,
                    HedgeType.FAIR_VALUE,
                    instrumentFvChange,
                    hedgedItemPvChange,
                    BigDecimal.ZERO);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(mockTest));
            given(journalEntryUseCase.createEntries(any())).willReturn(java.util.List.of());

            // when
            handler.handle(event);

            // then
            ArgumentCaptor<JournalEntryRequest> captor = ArgumentCaptor.forClass(JournalEntryRequest.class);
            then(journalEntryUseCase).should(times(1)).createEntries(captor.capture());

            JournalEntryRequest captured = captor.getValue();
            assertThat(captured.hedgeType()).isEqualTo(HedgeType.FAIR_VALUE);
            assertThat(captured.hedgeRelationshipId()).isEqualTo(HEDGE_RELATIONSHIP_ID);
            assertThat(captured.entryDate()).isEqualTo(TEST_DATE);
            assertThat(captured.instrumentFvChange()).isEqualByComparingTo(instrumentFvChange);
            assertThat(captured.hedgedItemFvChange()).isEqualByComparingTo(hedgedItemPvChange);
        }
    }

    // -----------------------------------------------------------------------
    // FAIL + DISCONTINUE: 기존 흐름 유지
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("FAIL + DISCONTINUE 케이스 — 기존 흐름 유지 검증")
    class FailAndDiscontinueTest {

        @Test
        @DisplayName("FAIL + DISCONTINUE → 분개 생성 0회, RebalancingService 호출 없음")
        void whenFailAndDiscontinue_thenJournalEntryFlowUnchanged() {
            // given
            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.FAIL,
                    ActionRequired.DISCONTINUE,
                    HedgeType.FAIR_VALUE,
                    null);  // null → FX_FORWARD 하위호환

            // when
            handler.handle(event);

            // then — 분개 생성 없음
            then(journalEntryUseCase).should(never()).createEntries(any());

            // then — 재조정 없음
            then(hedgeRebalancingService).should(never())
                    .processRebalancing(anyLong(), anyString(), any());

            // then — DB 조회 없음 (early return)
            then(effectivenessTestRepository).should(never()).findById(anyLong());
        }

        @Test
        @DisplayName("FAIL 단독 (actionRequired=NONE이어도) → 분개 생성 0회")
        void whenFailOnly_thenNoJournalEntry() {
            // given — FAIL 이면 actionRequired 무관하게 early return
            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.FAIL,
                    ActionRequired.NONE,
                    HedgeType.CASH_FLOW,
                    null);  // null → FX_FORWARD 하위호환

            // when
            handler.handle(event);

            // then
            then(journalEntryUseCase).should(never()).createEntries(any());
            then(hedgeRebalancingService).should(never())
                    .processRebalancing(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("DISCONTINUE 단독 (testResult=PASS이어도) → 분개 생성 0회")
        void whenDiscontinueOnly_thenNoJournalEntry() {
            // given — DISCONTINUE이면 testResult 무관하게 early return
            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.PASS,
                    ActionRequired.DISCONTINUE,
                    HedgeType.FAIR_VALUE,
                    null);  // null → FX_FORWARD 하위호환

            // when
            handler.handle(event);

            // then
            then(journalEntryUseCase).should(never()).createEntries(any());
            then(hedgeRebalancingService).should(never())
                    .processRebalancing(anyLong(), anyString(), any());
        }
    }

    // -----------------------------------------------------------------------
    // 예외 경로
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("예외 경로")
    class ExceptionTest {

        @Test
        @DisplayName("WARNING + REBALANCE 시 테스트 결과 DB 미존재 → BusinessException 발생")
        void whenWarningAndRebalanceButTestNotFound_thenThrowsBusinessException() {
            // given
            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE,
                    HedgeType.FAIR_VALUE,
                    null);  // null → FX_FORWARD 하위호환

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> handler.handle(event))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(String.valueOf(TEST_ID));

            // 예외 발생 시 분개도 재조정도 없음
            then(journalEntryUseCase).should(never()).createEntries(any());
            then(hedgeRebalancingService).should(never())
                    .processRebalancing(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("PASS 시 테스트 결과 DB 미존재 → BusinessException 발생")
        void whenPassButTestNotFound_thenThrowsBusinessException() {
            // given
            EffectivenessTestCompletedEvent event = new EffectivenessTestCompletedEvent(
                    TEST_ID,
                    HEDGE_RELATIONSHIP_ID,
                    EffectivenessTestResult.PASS,
                    ActionRequired.NONE,
                    HedgeType.FAIR_VALUE,
                    null);  // null → FX_FORWARD 하위호환

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> handler.handle(event))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(String.valueOf(TEST_ID));
        }
    }

    // -----------------------------------------------------------------------
    // 테스트 픽스처
    // -----------------------------------------------------------------------

    /**
     * 테스트용 EffectivenessTest 엔티티 생성 (instrumentType=null, FX_FORWARD 하위호환).
     *
     * <p>팩토리 메서드 {@link EffectivenessTest#of}를 사용하여
     * 실제 도메인 규칙에 따라 생성합니다.
     */
    private EffectivenessTest buildMockTest(
            EffectivenessTestResult testResult,
            ActionRequired actionRequired,
            HedgeType hedgeType,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemPvChange,
            BigDecimal ineffectiveAmount) {

        return buildMockTestWithInstrumentType(
                testResult, actionRequired, hedgeType,
                instrumentFvChange, hedgedItemPvChange, ineffectiveAmount,
                null);  // null → FX_FORWARD 하위호환 (1단계 테스트 데이터)
    }

    /**
     * 테스트용 EffectivenessTest 엔티티 생성 (instrumentType 명시).
     *
     * <p>IRS FVH 2단계 이벤트 핸들러 테스트에서 사용합니다.
     */
    private EffectivenessTest buildMockTestWithInstrumentType(
            EffectivenessTestResult testResult,
            ActionRequired actionRequired,
            HedgeType hedgeType,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemPvChange,
            BigDecimal ineffectiveAmount,
            InstrumentType instrumentType) {

        BigDecimal effectiveAmount = instrumentFvChange.abs().min(hedgedItemPvChange.abs());

        return EffectivenessTest.of(
                HEDGE_RELATIONSHIP_ID,
                TEST_DATE,
                EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                hedgeType,
                instrumentFvChange,
                hedgedItemPvChange,
                instrumentFvChange,           // cumulative = periodic (첫 기간 가정)
                hedgedItemPvChange,
                new BigDecimal("-0.95"),       // Dollar-offset 비율 예시
                testResult,
                effectiveAmount,
                ineffectiveAmount,
                hedgeType == HedgeType.CASH_FLOW ? effectiveAmount : null,  // ociReserveBalance
                actionRequired,
                testResult == EffectivenessTestResult.PASS ? null : "참고범위 이탈",
                instrumentType);   // null=FX_FORWARD 하위호환, IRS=2단계 경로
    }
}

