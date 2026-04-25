package com.hedge.prototype.hedge.application;

import com.hedge.prototype.journal.adapter.web.dto.JournalEntryRequest;
import com.hedge.prototype.journal.application.JournalEntryUseCase;
import com.hedge.prototype.effectiveness.application.port.EffectivenessTestRepository;
import com.hedge.prototype.hedge.application.port.HedgeRelationshipRepository;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.effectiveness.domain.ActionRequired;
import com.hedge.prototype.effectiveness.domain.EffectivenessTest;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestResult;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestType;
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * HedgeRebalancingService 단위 테스트.
 *
 * <p>EventHandler 테스트와 중복될 수 있는 영역이라도 "재조정 서비스 자체의 책임"에 해당하는
 * 아래 분기들은 이 테스트에서 직접 보호합니다.
 *
 * <ul>
 *   <li>테스트/관계 mismatch 방어 — 저장/분개 부작용 없음</li>
 *   <li>재조정 전 비효과성 금액에 따른 분개 생성 스킵 vs 실행 (B6.5.8)</li>
 *   <li>FAIR_VALUE / CASH_FLOW 에 따라 FVH/CFH 분개 요청 형태 분기</li>
 *   <li>Dollar-offset 비율 기반 목표 헤지비율 계산이 ±20% 클램프 범위 내로 산출</li>
 *   <li>재조정 결과가 관계 엔티티에 반영되고(save) 사유 문자열이 감사용으로 기록됨</li>
 * </ul>
 *
 * @see K-IFRS 1109호 6.5.5 (재조정 의무)
 * @see K-IFRS 1109호 B6.5.8 (재조정 전 비효과성 당기손익 인식 선행)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HedgeRebalancingService — 재조정 핵심 분기 단위 테스트")
class HedgeRebalancingServiceTest {

    @Mock
    private HedgeRelationshipRepository hedgeRelationshipRepository;

    @Mock
    private EffectivenessTestRepository effectivenessTestRepository;

    @Mock
    private JournalEntryUseCase journalEntryUseCase;

    @Mock
    private HedgeRelationship hedgeRelationship;

    private HedgeRebalancingService service;

    private static final Long TEST_ID = 1L;
    private static final String HEDGE_RELATIONSHIP_ID = "HR-2026-001";
    private static final LocalDate REBALANCING_DATE = LocalDate.of(2026, 4, 30);

    @BeforeEach
    void setUp() {
        service = new HedgeRebalancingService(
                hedgeRelationshipRepository,
                effectivenessTestRepository,
                journalEntryUseCase);
    }

    // -----------------------------------------------------------------------
    // 방어: 테스트/관계 mismatch 및 존재하지 않는 엔티티
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("방어 분기 — mismatch / not-found 는 저장·분개를 유발하지 않는다")
    class GuardClauseTest {

        @Test
        @DisplayName("다른 hedge relationship 에 속한 테스트로는 리밸런싱할 수 없고 save/createEntries 도 호출되지 않는다")
        void processRebalancing_rejectsMismatchedEffectivenessTest_andHasNoSideEffects() {
            // given — 테스트가 가리키는 헤지관계와 요청 파라미터가 다름
            EffectivenessTest test = buildTest(
                    "HR-OTHER-001",
                    HedgeType.FAIR_VALUE,
                    new BigDecimal("5000"),
                    new BigDecimal("-1.052632"),
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE);
            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(test));

            // when / then
            assertThatThrownBy(() -> service.processRebalancing(
                    TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("hedgeRelationshipId");

            // 관계는 로드조차 되지 않아야 하고, 분개도 저장도 없어야 한다
            then(hedgeRelationshipRepository).should(never()).findById(anyString());
            then(hedgeRelationshipRepository).should(never()).save(any());
            then(journalEntryUseCase).should(never()).createEntries(any());
        }

        @Test
        @DisplayName("유효성 테스트가 존재하지 않으면 BusinessException(ET_002) 이 발생하고 부작용이 없다")
        void processRebalancing_whenTestNotFound_throwsAndDoesNotTouchSideEffects() {
            // given
            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> service.processRebalancing(
                    TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(String.valueOf(TEST_ID));

            then(hedgeRelationshipRepository).should(never()).findById(anyString());
            then(hedgeRelationshipRepository).should(never()).save(any());
            then(journalEntryUseCase).should(never()).createEntries(any());
        }

        @Test
        @DisplayName("위험회피관계가 존재하지 않으면 BusinessException(HD_009) 이 발생하고 분개도 없다")
        void processRebalancing_whenRelationshipNotFound_throwsAndDoesNotCreateJournal() {
            // given
            EffectivenessTest test = buildTest(
                    HEDGE_RELATIONSHIP_ID,
                    HedgeType.FAIR_VALUE,
                    new BigDecimal("5000"),
                    new BigDecimal("-1.052632"),
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE);
            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(test));
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> service.processRebalancing(
                    TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(HEDGE_RELATIONSHIP_ID);

            // 관계 로드 실패 시점에 분개 생성은 아직 일어나선 안 됨
            then(journalEntryUseCase).should(never()).createEntries(any());
            then(hedgeRelationshipRepository).should(never()).save(any());
        }
    }

    // -----------------------------------------------------------------------
    // B6.5.8: 재조정 전 비효과성 분개 생성/생략
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("재조정 전 비효과성 (B6.5.8) — ineffectiveAmount 분기")
    class PreRebalancingIneffectivenessTest {

        @Test
        @DisplayName("ineffectiveAmount == 0 → 분개 생성 생략, 하지만 재조정 자체는 진행된다")
        void whenIneffectiveAmountZero_thenSkipsJournalButStillRebalances() {
            // given — 비효과성 0 + WARNING/REBALANCE 시나리오
            EffectivenessTest test = buildTest(
                    HEDGE_RELATIONSHIP_ID,
                    HedgeType.FAIR_VALUE,
                    BigDecimal.ZERO,                         // ineffectiveAmount = 0
                    new BigDecimal("-1.052632"),
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE);
            stubRelationship(new BigDecimal("1.0000"), HedgeType.FAIR_VALUE);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(test));
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(hedgeRelationship));

            // when
            service.processRebalancing(TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE);

            // then — B6.5.8: 비효과성이 0 이면 분개를 만들지 않는다
            then(journalEntryUseCase).should(never()).createEntries(any());

            // then — 재조정 자체(비율 갱신 + 저장)는 정상 진행되어야 한다
            then(hedgeRelationship).should(times(1)).rebalance(any(BigDecimal.class), anyString());
            then(hedgeRelationshipRepository).should(times(1)).save(hedgeRelationship);
        }

        @Test
        @DisplayName("ineffectiveAmount == null → 분개 생성 생략, 재조정은 계속 진행")
        void whenIneffectiveAmountNull_thenSkipsJournalButStillRebalances() {
            // given — null 은 0 과 동등하게 취급되어야 한다
            EffectivenessTest test = buildTest(
                    HEDGE_RELATIONSHIP_ID,
                    HedgeType.FAIR_VALUE,
                    null,                                    // ineffectiveAmount = null
                    new BigDecimal("-1.052632"),
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE);
            stubRelationship(new BigDecimal("1.0000"), HedgeType.FAIR_VALUE);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(test));
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(hedgeRelationship));

            // when
            service.processRebalancing(TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE);

            // then
            then(journalEntryUseCase).should(never()).createEntries(any());
            then(hedgeRelationship).should(times(1)).rebalance(any(BigDecimal.class), anyString());
            then(hedgeRelationshipRepository).should(times(1)).save(hedgeRelationship);
        }

        @Test
        @DisplayName("FAIR_VALUE + ineffectiveAmount != 0 → FVH 분개 요청으로 createEntries 1회 호출")
        void whenFairValueWithIneffectiveness_thenCreatesFvhJournalEntry() {
            // given
            BigDecimal instrumentFv = new BigDecimal("100000");
            BigDecimal hedgedItemPv = new BigDecimal("-95000");
            EffectivenessTest test = buildTest(
                    HEDGE_RELATIONSHIP_ID,
                    HedgeType.FAIR_VALUE,
                    instrumentFv,
                    hedgedItemPv,
                    new BigDecimal("5000"),                  // ineffectiveAmount != 0
                    new BigDecimal("-1.052632"),
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE);
            stubRelationship(new BigDecimal("1.0000"), HedgeType.FAIR_VALUE);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(test));
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(hedgeRelationship));

            // when
            service.processRebalancing(TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE);

            // then — 정확히 1회, FVH 형식으로 호출되어야 한다
            ArgumentCaptor<JournalEntryRequest> captor =
                    ArgumentCaptor.forClass(JournalEntryRequest.class);
            then(journalEntryUseCase).should(times(1)).createEntries(captor.capture());

            JournalEntryRequest captured = captor.getValue();
            assertThat(captured.hedgeType()).isEqualTo(HedgeType.FAIR_VALUE);
            assertThat(captured.hedgeRelationshipId()).isEqualTo(HEDGE_RELATIONSHIP_ID);
            assertThat(captured.entryDate()).isEqualTo(REBALANCING_DATE);
            assertThat(captured.instrumentFvChange()).isEqualByComparingTo(instrumentFv);
            assertThat(captured.hedgedItemFvChange()).isEqualByComparingTo(hedgedItemPv);
            // FVH 팩토리는 effectiveAmount/ineffectiveAmount 필드를 채우지 않는다
            assertThat(captured.effectiveAmount()).isNull();
            assertThat(captured.ineffectiveAmount()).isNull();
        }

        @Test
        @DisplayName("CASH_FLOW + ineffectiveAmount != 0 → CFH 분개 요청으로 createEntries 1회 호출")
        void whenCashFlowWithIneffectiveness_thenCreatesCfhJournalEntry() {
            // given
            BigDecimal instrumentFv = new BigDecimal("80000");
            BigDecimal hedgedItemPv = new BigDecimal("-70000");
            BigDecimal effective = new BigDecimal("70000");
            BigDecimal ineffective = new BigDecimal("10000");

            EffectivenessTest test = buildTest(
                    HEDGE_RELATIONSHIP_ID,
                    HedgeType.CASH_FLOW,
                    instrumentFv,
                    hedgedItemPv,
                    effective,
                    ineffective,
                    new BigDecimal("-1.142857"),
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE);
            stubRelationship(new BigDecimal("1.0000"), HedgeType.CASH_FLOW);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(test));
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(hedgeRelationship));

            // when
            service.processRebalancing(TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE);

            // then
            ArgumentCaptor<JournalEntryRequest> captor =
                    ArgumentCaptor.forClass(JournalEntryRequest.class);
            then(journalEntryUseCase).should(times(1)).createEntries(captor.capture());

            JournalEntryRequest captured = captor.getValue();
            assertThat(captured.hedgeType()).isEqualTo(HedgeType.CASH_FLOW);
            assertThat(captured.hedgeRelationshipId()).isEqualTo(HEDGE_RELATIONSHIP_ID);
            assertThat(captured.entryDate()).isEqualTo(REBALANCING_DATE);
            // CFH 팩토리는 effective/ineffective 금액을 함께 실어야 한다
            assertThat(captured.effectiveAmount()).isEqualByComparingTo(effective);
            assertThat(captured.ineffectiveAmount()).isEqualByComparingTo(ineffective);
        }
    }

    // -----------------------------------------------------------------------
    // 재조정 비율 계산 — ±20% 클램프 및 관계 엔티티 반영
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("재조정 비율 계산 — ±20% 클램프 범위 내로 산출되고 관계에 반영된다")
    class RebalancingRatioTest {

        @Test
        @DisplayName("over-hedge (eff=-1.052632, 현재=1.00) → 비율이 현재비율 × 1/|eff| 로 정상 조정된다")
        void whenMildOverHedge_thenAdjustsRatioWithinClamp() {
            // given — Dollar-offset ≈ 105% → 이상치 아님, 조정계수 ≈ 0.95
            EffectivenessTest test = buildTest(
                    HEDGE_RELATIONSHIP_ID,
                    HedgeType.FAIR_VALUE,
                    new BigDecimal("5000"),
                    new BigDecimal("-1.052632"),
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE);
            stubRelationship(new BigDecimal("1.0000"), HedgeType.FAIR_VALUE);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(test));
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(hedgeRelationship));

            // when
            service.processRebalancing(TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE);

            // then — rebalance() 에 전달된 신규 비율이 [0.80, 1.20] 범위 이내, 감소 방향이어야 함
            ArgumentCaptor<BigDecimal> ratioCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            then(hedgeRelationship).should(times(1))
                    .rebalance(ratioCaptor.capture(), reasonCaptor.capture());

            BigDecimal newRatio = ratioCaptor.getValue();
            assertThat(newRatio).isBetween(new BigDecimal("0.80"), new BigDecimal("1.20"));
            // over-hedge 이므로 새 비율은 현재(1.00) 보다 작아야 한다
            assertThat(newRatio).isLessThan(new BigDecimal("1.0000"));

            // 감사 추적용 사유 문자열에 참고 정보가 남아야 한다
            assertThat(reasonCaptor.getValue())
                    .contains("재조정")
                    .contains("6.5.5");

            // 재조정된 관계는 저장되어야 한다
            then(hedgeRelationshipRepository).should(times(1)).save(hedgeRelationship);
        }

        @Test
        @DisplayName("극단 under-hedge (eff=-0.50) → 현재비율 대비 +20% 상한으로 클램프된다")
        void whenHeavyUnderHedge_thenClampsAtUpper20Percent() {
            // given — eff=0.50 이면 adjustment=2.00, 클램프 상한이 반드시 적용되어야 함
            EffectivenessTest test = buildTest(
                    HEDGE_RELATIONSHIP_ID,
                    HedgeType.FAIR_VALUE,
                    new BigDecimal("5000"),
                    new BigDecimal("-0.50"),
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE);
            BigDecimal currentRatio = new BigDecimal("1.0000");
            stubRelationship(currentRatio, HedgeType.FAIR_VALUE);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(test));
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(hedgeRelationship));

            // when
            service.processRebalancing(TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE);

            // then — 현재 비율 대비 최대 ±20% 범위를 넘지 않아야 한다
            ArgumentCaptor<BigDecimal> ratioCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            then(hedgeRelationship).should(times(1))
                    .rebalance(ratioCaptor.capture(), anyString());

            BigDecimal newRatio = ratioCaptor.getValue();
            assertThat(newRatio).isEqualByComparingTo(new BigDecimal("1.2000"));
            then(hedgeRelationshipRepository).should(times(1)).save(hedgeRelationship);
        }

        @Test
        @DisplayName("극단 over-hedge (eff=-3.00) → 현재비율 대비 -20% 하한으로 클램프된다")
        void whenHeavyOverHedge_thenClampsAtLower20Percent() {
            // given — eff=3.00 이면 adjustment ≈ 0.333, 하한 0.80 (= 1.00×0.8) 로 클램프
            EffectivenessTest test = buildTest(
                    HEDGE_RELATIONSHIP_ID,
                    HedgeType.FAIR_VALUE,
                    new BigDecimal("5000"),
                    new BigDecimal("-3.00"),
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE);
            stubRelationship(new BigDecimal("1.0000"), HedgeType.FAIR_VALUE);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(test));
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(hedgeRelationship));

            // when
            service.processRebalancing(TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE);

            // then
            ArgumentCaptor<BigDecimal> ratioCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            then(hedgeRelationship).should(times(1))
                    .rebalance(ratioCaptor.capture(), anyString());

            assertThat(ratioCaptor.getValue()).isEqualByComparingTo(new BigDecimal("0.8000"));
        }

        @Test
        @DisplayName("effectivenessRatio == null → 현재 비율을 그대로 유지하여 재조정한다")
        void whenEffectivenessRatioNull_thenKeepsCurrentRatio() {
            // given
            BigDecimal currentRatio = new BigDecimal("1.0000");
            EffectivenessTest test = buildTest(
                    HEDGE_RELATIONSHIP_ID,
                    HedgeType.FAIR_VALUE,
                    BigDecimal.ZERO,                         // ineffectiveAmount 0 → 분개 생략
                    null,                                    // effectivenessRatio null
                    EffectivenessTestResult.WARNING,
                    ActionRequired.REBALANCE);
            stubRelationship(currentRatio, HedgeType.FAIR_VALUE);

            given(effectivenessTestRepository.findById(TEST_ID)).willReturn(Optional.of(test));
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(hedgeRelationship));

            // when
            service.processRebalancing(TEST_ID, HEDGE_RELATIONSHIP_ID, REBALANCING_DATE);

            // then — 현재 비율 그대로 전달되어야 한다
            ArgumentCaptor<BigDecimal> ratioCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            then(hedgeRelationship).should(times(1))
                    .rebalance(ratioCaptor.capture(), anyString());
            assertThat(ratioCaptor.getValue()).isEqualByComparingTo(currentRatio);

            then(hedgeRelationshipRepository).should(times(1)).save(hedgeRelationship);
            then(journalEntryUseCase).should(never()).createEntries(any());
        }
    }

    // -----------------------------------------------------------------------
    // 테스트 픽스처
    // -----------------------------------------------------------------------

    private void stubRelationship(BigDecimal hedgeRatio, HedgeType hedgeType) {
        given(hedgeRelationship.getHedgeRatio()).willReturn(hedgeRatio);
        given(hedgeRelationship.getHedgeType()).willReturn(hedgeType);
        given(hedgeRelationship.getHedgeRelationshipId()).willReturn(HEDGE_RELATIONSHIP_ID);
    }

    /**
     * effectiveAmount 를 명시하지 않고 간소화된 시그니처로 EffectivenessTest 를 만드는 헬퍼.
     * ineffectiveAmount 만 중요한 FAIR_VALUE 시나리오에서 사용한다.
     */
    private EffectivenessTest buildTest(
            String hedgeRelationshipId,
            HedgeType hedgeType,
            BigDecimal ineffectiveAmount,
            BigDecimal effectivenessRatio,
            EffectivenessTestResult testResult,
            ActionRequired actionRequired) {
        return buildTest(
                hedgeRelationshipId,
                hedgeType,
                new BigDecimal("100000"),
                new BigDecimal("-95000"),
                new BigDecimal("95000"),
                ineffectiveAmount,
                effectivenessRatio,
                testResult,
                actionRequired);
    }

    /**
     * 4-argument 버전 — (instrumentFv, hedgedItemPv, ineffectiveAmount, effectivenessRatio) 지정.
     * effectiveAmount 는 |instrumentFv| 와 |hedgedItemPv| 중 작은 값으로 자동 산출.
     */
    private EffectivenessTest buildTest(
            String hedgeRelationshipId,
            HedgeType hedgeType,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemPvChange,
            BigDecimal ineffectiveAmount,
            BigDecimal effectivenessRatio,
            EffectivenessTestResult testResult,
            ActionRequired actionRequired) {
        BigDecimal effectiveAmount = instrumentFvChange.abs().min(hedgedItemPvChange.abs());
        return buildTest(
                hedgeRelationshipId,
                hedgeType,
                instrumentFvChange,
                hedgedItemPvChange,
                effectiveAmount,
                ineffectiveAmount,
                effectivenessRatio,
                testResult,
                actionRequired);
    }

    /**
     * 가장 풍부한 EffectivenessTest 팩토리 — 주요 필드를 모두 명시적으로 받는다.
     * effectivenessTestId 는 DB 가 채우는 필드라 ReflectionTestUtils 로 셋팅한다
     * ({@code buildRebalancingReason()} 이 {@code %d} 포맷을 사용하므로 null 금지).
     */
    private EffectivenessTest buildTest(
            String hedgeRelationshipId,
            HedgeType hedgeType,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemPvChange,
            BigDecimal effectiveAmount,
            BigDecimal ineffectiveAmount,
            BigDecimal effectivenessRatio,
            EffectivenessTestResult testResult,
            ActionRequired actionRequired) {
        EffectivenessTest test = EffectivenessTest.of(
                hedgeRelationshipId,
                REBALANCING_DATE,
                EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                hedgeType,
                instrumentFvChange,
                hedgedItemPvChange,
                instrumentFvChange,          // cumulative = periodic (단일 기간 가정)
                hedgedItemPvChange,
                effectivenessRatio,
                testResult,
                effectiveAmount,
                ineffectiveAmount,
                hedgeType == HedgeType.CASH_FLOW ? effectiveAmount : null,
                actionRequired,
                testResult == EffectivenessTestResult.PASS ? null : "테스트용 사유",
                null);   // instrumentType=null → FX_FORWARD 하위호환 (1단계 테스트 데이터)

        // buildRebalancingReason() 이 "%d" 포맷을 사용하므로 ID 를 반드시 셋팅해야 한다
        ReflectionTestUtils.setField(test, "effectivenessTestId", TEST_ID);
        return test;
    }
}
