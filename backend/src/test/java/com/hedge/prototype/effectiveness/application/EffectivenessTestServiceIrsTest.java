package com.hedge.prototype.effectiveness.application;

import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.effectiveness.adapter.web.dto.EffectivenessTestRequest;
import com.hedge.prototype.effectiveness.application.port.EffectivenessTestRepository;
import com.hedge.prototype.effectiveness.domain.ActionRequired;
import com.hedge.prototype.effectiveness.domain.EffectivenessTest;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestResult;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestType;
import com.hedge.prototype.hedge.application.port.HedgeRelationshipRepository;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * IRS 유효성 테스트 통합 검증.
 *
 * <p>요구사항 문서(IRS_HEDGE_REQUIREMENTS.md) §8 검증 시나리오 기반.
 *
 * <p>검증 항목:
 * <ol>
 *   <li>IRS FVH — 금리 상승, PASS, ratio ≈ -1.01, ineffectiveAmount = +4,000,000</li>
 *   <li>IRS CFH — OCI 적립금 누적, Lower of Test 적용</li>
 *   <li>IRS FVH — WARNING (80~125% 이탈)</li>
 *   <li>IRS FVH — FAIL (동방향)</li>
 *   <li>IRS + ET_005: 허용되지 않는 hedgeType 조합 차단</li>
 *   <li>instrumentType=null → FX_FORWARD 기본값 하위호환 (기존 동작 regression)</li>
 * </ol>
 *
 * <p>TODO: RAG 교차검증 필요
 * <ul>
 *   <li>K-IFRS 1109호 B6.4.12 (매 보고기간 말 Dollar-offset)</li>
 *   <li>K-IFRS 1109호 B6.4.13 (Dollar-offset 방법 — 수단 유형 무관)</li>
 *   <li>K-IFRS 1109호 6.5.8  (IRS FVH 비효과성 P&L)</li>
 *   <li>K-IFRS 1109호 6.5.11 (IRS CFH OCI/P&L — Lower of Test)</li>
 * </ul>
 *
 * @see IRS_HEDGE_REQUIREMENTS.md §4 (유효성 테스트 방식)
 * @see IRS_HEDGE_REQUIREMENTS.md §8 (검증 시나리오)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IRS 유효성 테스트 서비스 — 2단계 P1")
class EffectivenessTestServiceIrsTest {

    // ── 공통 상수 ─────────────────────────────────────────────────────────────

    private static final String HR_FVH = "HR-IRS-FVH-001";   // IRS FVH 관계
    private static final String HR_CFH = "HR-IRS-CFH-001";   // IRS CFH 관계
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 6, 30);

    // 시나리오 수치 (IRS_HEDGE_REQUIREMENTS.md §8)
    // 명목: 10,000,000,000원 / fixedRate 3% / floatingRate 4.5% / 잔존 2.75년 반기결제
    private static final BigDecimal IRS_FV_CHANGE      = new BigDecimal("390000000.00");   // IRS 평가이익
    private static final BigDecimal BOND_PV_CHANGE     = new BigDecimal("-386000000.00");  // 채권 장부가치 조정 (헤지귀속분)
    // ratio = 390M / (-386M) = -1.010363...  → PASS (반대방향 + 참고범위 이내)

    // ── Mock / Service ────────────────────────────────────────────────────────

    @Mock private EffectivenessTestRepository effectivenessTestRepository;
    @Mock private HedgeRelationshipRepository hedgeRelationshipRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private HedgeRelationship fvhRelationship;
    @Mock private HedgeRelationship cfhRelationship;
    @Mock private EffectivenessTest mockSavedTest;

    private EffectivenessTestService service;

    @BeforeEach
    void setUp() {
        service = new EffectivenessTestService(
                effectivenessTestRepository,
                hedgeRelationshipRepository,
                eventPublisher);
    }

    // =========================================================================
    // § IRS FVH 시나리오
    // =========================================================================

    @Nested
    @DisplayName("IRS FVH — 공정가치 위험회피")
    class IrsFvhTests {

        @BeforeEach
        void mockFvhRelationship() {
            given(fvhRelationship.getHedgeType()).willReturn(HedgeType.FAIR_VALUE);
            given(hedgeRelationshipRepository.findById(HR_FVH))
                    .willReturn(Optional.of(fvhRelationship));
            given(effectivenessTestRepository.findTopByHedgeRelationshipIdOrderByTestDateDesc(HR_FVH))
                    .willReturn(Optional.empty());
            given(effectivenessTestRepository.save(any())).willReturn(mockSavedTest);
        }

        @Test
        @DisplayName("IRS FVH PASS — ratio ≈ -1.01, ineffectiveAmount = +4,000,000, OCI = null")
        void irsAvhPass_ratioCaseBond() {
            // given — §8 시나리오 수치
            EffectivenessTestRequest request = irsRequest(
                    HR_FVH, HedgeType.FAIR_VALUE, IRS_FV_CHANGE, BOND_PV_CHANGE);

            ArgumentCaptor<EffectivenessTest> captor = ArgumentCaptor.forClass(EffectivenessTest.class);
            given(effectivenessTestRepository.save(captor.capture())).willReturn(mockSavedTest);

            // when
            service.runTest(request);

            // then
            EffectivenessTest saved = captor.getValue();

            // ratio = +390M / (-386M) = -1.0104...
            assertThat(saved.getEffectivenessRatio())
                    .as("Dollar-offset ratio는 음수(반대방향)여야 합니다")
                    .isNegative();
            assertThat(saved.getEffectivenessRatio().abs())
                    .as("ratio 절대값은 참고범위(0.80~1.25) 이내여야 합니다")
                    .isBetween(new BigDecimal("0.80"), new BigDecimal("1.25"));

            assertThat(saved.getTestResult())
                    .as("PASS — 반대방향 + 참고범위 이내")
                    .isEqualTo(EffectivenessTestResult.PASS);

            assertThat(saved.getActionRequired())
                    .as("PASS → NONE")
                    .isEqualTo(ActionRequired.NONE);

            // FVH 비효과성 = instrumentFvChange + hedgedItemPvChange = +390M + (-386M) = +4M
            // TODO: RAG 교차검증 필요 — K-IFRS 1109호 6.5.8 (부호 유지 필수, .abs() 금지)
            assertThat(saved.getIneffectiveAmount())
                    .as("비효과적 부분 = +4,000,000 (IRS 초과 이익 → P&L)")
                    .isEqualByComparingTo(new BigDecimal("4000000.00"));

            assertThat(saved.getOciReserveBalance())
                    .as("FVH는 OCI 적립금 없음 (K-IFRS 1109호 6.5.8)")
                    .isNull();

            assertThat(saved.getInstrumentType())
                    .as("instrumentType=IRS가 저장되어야 합니다")
                    .isEqualTo(InstrumentType.IRS);

            // 누적값 — 최초 테스트이므로 당기 변동 = 누적
            assertThat(saved.getInstrumentFvCumulative())
                    .isEqualByComparingTo(IRS_FV_CHANGE);
            assertThat(saved.getHedgedItemPvCumulative())
                    .isEqualByComparingTo(BOND_PV_CHANGE);
        }

        @Test
        @DisplayName("IRS FVH 2회차 — 누적값이 이전 이력에서 이어서 계산된다")
        void irsFvh_secondTest_cumulatesFromPreviousRecord() {
            // given — 1회차 누적 이미 저장된 상태 mock
            BigDecimal prevCumInstrument = new BigDecimal("200000000.00");
            BigDecimal prevCumHedgedItem = new BigDecimal("-198000000.00");

            EffectivenessTest prevTest = buildMockTest(
                    HedgeType.FAIR_VALUE, prevCumInstrument, prevCumHedgedItem, null);
            given(effectivenessTestRepository.findTopByHedgeRelationshipIdOrderByTestDateDesc(HR_FVH))
                    .willReturn(Optional.of(prevTest));

            ArgumentCaptor<EffectivenessTest> captor = ArgumentCaptor.forClass(EffectivenessTest.class);
            given(effectivenessTestRepository.save(captor.capture())).willReturn(mockSavedTest);

            BigDecimal deltaInstrument = new BigDecimal("50000000.00");
            BigDecimal deltaHedgedItem = new BigDecimal("-49000000.00");

            EffectivenessTestRequest request = irsRequest(
                    HR_FVH, HedgeType.FAIR_VALUE, deltaInstrument, deltaHedgedItem);

            // when
            service.runTest(request);

            // then — 누적 = 이전 + 당기
            EffectivenessTest saved = captor.getValue();
            assertThat(saved.getInstrumentFvCumulative())
                    .as("누적 수단 변동 = 이전 200M + 당기 50M = 250M")
                    .isEqualByComparingTo(prevCumInstrument.add(deltaInstrument));
            assertThat(saved.getHedgedItemPvCumulative())
                    .as("누적 피헤지 변동 = 이전 -198M + 당기 -49M = -247M")
                    .isEqualByComparingTo(prevCumHedgedItem.add(deltaHedgedItem));
        }

        @Test
        @DisplayName("IRS FVH WARNING — ratio 절대값 > 1.25 (과대헤지)")
        void irsFvh_warning_ratioOutOfRange() {
            // given — IRS 변동이 채권 변동의 1.4배 → ratio ≈ -1.40
            BigDecimal largeIrsFvChange  = new BigDecimal("560000000.00");
            BigDecimal bondPvChange      = new BigDecimal("-400000000.00");

            ArgumentCaptor<EffectivenessTest> captor = ArgumentCaptor.forClass(EffectivenessTest.class);
            given(effectivenessTestRepository.save(captor.capture())).willReturn(mockSavedTest);

            EffectivenessTestRequest request = irsRequest(
                    HR_FVH, HedgeType.FAIR_VALUE, largeIrsFvChange, bondPvChange);

            // when
            service.runTest(request);

            // then
            EffectivenessTest saved = captor.getValue();
            assertThat(saved.getTestResult())
                    .as("ratio |1.40| > 1.25 → WARNING (참고범위 이탈)")
                    .isEqualTo(EffectivenessTestResult.WARNING);
            assertThat(saved.getActionRequired())
                    .as("WARNING → REBALANCE 검토 (K-IFRS 1109호 6.5.5)")
                    .isEqualTo(ActionRequired.REBALANCE);
        }

        @Test
        @DisplayName("IRS FVH FAIL — 동방향 (ratio > 0)")
        void irsFvh_fail_sameDirection() {
            // given — IRS와 채권 모두 손실 → 동방향 → ratio 양수 → FAIL
            BigDecimal irsFvChange   = new BigDecimal("-200000000.00");  // IRS 손실
            BigDecimal bondPvChange  = new BigDecimal("-180000000.00");  // 채권도 손실 (동방향)

            ArgumentCaptor<EffectivenessTest> captor = ArgumentCaptor.forClass(EffectivenessTest.class);
            given(effectivenessTestRepository.save(captor.capture())).willReturn(mockSavedTest);

            EffectivenessTestRequest request = irsRequest(
                    HR_FVH, HedgeType.FAIR_VALUE, irsFvChange, bondPvChange);

            // when
            service.runTest(request);

            // then
            EffectivenessTest saved = captor.getValue();
            assertThat(saved.getEffectivenessRatio())
                    .as("동방향이면 ratio가 양수여야 합니다")
                    .isPositive();
            assertThat(saved.getTestResult())
                    .as("동방향 → FAIL (K-IFRS 1109호 6.5.6 중단 검토)")
                    .isEqualTo(EffectivenessTestResult.FAIL);
            assertThat(saved.getActionRequired())
                    .as("FAIL → DISCONTINUE")
                    .isEqualTo(ActionRequired.DISCONTINUE);
        }
    }

    // =========================================================================
    // § IRS CFH 시나리오
    // =========================================================================

    @Nested
    @DisplayName("IRS CFH — 현금흐름 위험회피")
    class IrsCfhTests {

        @BeforeEach
        void mockCfhRelationship() {
            given(cfhRelationship.getHedgeType()).willReturn(HedgeType.CASH_FLOW);
            given(hedgeRelationshipRepository.findById(HR_CFH))
                    .willReturn(Optional.of(cfhRelationship));
            given(effectivenessTestRepository.findTopByHedgeRelationshipIdOrderByTestDateDesc(HR_CFH))
                    .willReturn(Optional.empty());
            given(effectivenessTestRepository.save(any())).willReturn(mockSavedTest);
        }

        @Test
        @DisplayName("IRS CFH PASS — OCI 적립금이 당기 유효분으로 계산된다")
        void irsCfh_pass_ociAccumulated() {
            // given — IRS CFH: 변동금리부채 헤지, 금리 상승 시 IRS 평가이익 → OCI 적립
            // instrumentFvChange: IRS 당기 평가이익
            // hedgedItemPvChange: 변동금리부채 현재가치 당기 변동 (반대방향)
            // TODO: RAG 교차검증 필요 — K-IFRS 1109호 6.5.11 (Lower of Test OCI/P&L 분리)
            BigDecimal irsGain      = new BigDecimal("150000000.00");   // IRS 평가이익
            BigDecimal debtPvChange = new BigDecimal("-148000000.00");  // 부채 PV 변동 (반대)

            ArgumentCaptor<EffectivenessTest> captor = ArgumentCaptor.forClass(EffectivenessTest.class);
            given(effectivenessTestRepository.save(captor.capture())).willReturn(mockSavedTest);

            EffectivenessTestRequest request = irsRequest(
                    HR_CFH, HedgeType.CASH_FLOW, irsGain, debtPvChange);

            // when
            service.runTest(request);

            // then
            EffectivenessTest saved = captor.getValue();
            assertThat(saved.getTestResult())
                    .as("PASS — ratio 이내")
                    .isEqualTo(EffectivenessTestResult.PASS);

            // CFH: OCI 적립금은 null이 아니어야 함 (K-IFRS 1109호 6.5.11)
            assertThat(saved.getOciReserveBalance())
                    .as("CFH — OCI 적립금이 기록되어야 합니다 (K-IFRS 1109호 6.5.11)")
                    .isNotNull();

            // 첫 기간이므로 OCI 적립금 = effectiveAmount (이전 잔액 없음)
            assertThat(saved.getOciReserveBalance())
                    .as("최초 OCI 적립금 = 당기 유효분")
                    .isEqualByComparingTo(saved.getEffectiveAmount());

            assertThat(saved.getInstrumentType())
                    .as("instrumentType=IRS 저장")
                    .isEqualTo(InstrumentType.IRS);
        }

        @Test
        @DisplayName("IRS CFH 2회차 — OCI 적립금이 이전 잔액에서 누적된다")
        void irsCfh_secondTest_ociCumulates() {
            // given — 1회차 OCI 잔액 mock
            BigDecimal prevOci = new BigDecimal("100000000.00");
            BigDecimal prevCumInstrument = new BigDecimal("150000000.00");
            BigDecimal prevCumHedgedItem = new BigDecimal("-148000000.00");

            EffectivenessTest prevTest = buildMockTest(
                    HedgeType.CASH_FLOW, prevCumInstrument, prevCumHedgedItem, prevOci);
            given(effectivenessTestRepository.findTopByHedgeRelationshipIdOrderByTestDateDesc(HR_CFH))
                    .willReturn(Optional.of(prevTest));

            ArgumentCaptor<EffectivenessTest> captor = ArgumentCaptor.forClass(EffectivenessTest.class);
            given(effectivenessTestRepository.save(captor.capture())).willReturn(mockSavedTest);

            BigDecimal irsGain2      = new BigDecimal("80000000.00");
            BigDecimal debtPvChange2 = new BigDecimal("-79000000.00");

            EffectivenessTestRequest request = irsRequest(
                    HR_CFH, HedgeType.CASH_FLOW, irsGain2, debtPvChange2);

            // when
            service.runTest(request);

            // then
            EffectivenessTest saved = captor.getValue();

            // OCI 누적 = 이전 잔액 + 당기 유효분 (K-IFRS 1109호 6.5.11)
            assertThat(saved.getOciReserveBalance())
                    .as("OCI 누적 = 이전 100M + 당기 유효분")
                    .isGreaterThan(prevOci);
        }
    }

    // =========================================================================
    // § ET_005: IRS 조합 검증
    // =========================================================================

    @Nested
    @DisplayName("ET_005 — IRS instrumentType 조합 검증")
    class IrsCombinationValidationTests {

        /**
         * IRS + FAIR_VALUE 또는 IRS + CASH_FLOW 이외의 조합은 없지만,
         * HedgeType 열거형에 미래 추가될 항목(예: NET_INVESTMENT)에 대비한 방어 로직 검증.
         * 현재 HedgeType은 FAIR_VALUE / CASH_FLOW만 있으므로 이 테스트는
         * 향후 열거형 확장 시 회귀 방지 목적으로 작성합니다.
         *
         * TODO: RAG 교차검증 필요 — K-IFRS 1109호 6.2.1 (IRS 적격성), 6.3.7 (금리위험)
         */
        @Test
        @DisplayName("IRS + FAIR_VALUE 조합은 ET_005 예외 없이 통과한다")
        void irs_fairValueCombination_noException() {
            given(fvhRelationship.getHedgeType()).willReturn(HedgeType.FAIR_VALUE);
            given(hedgeRelationshipRepository.findById(HR_FVH))
                    .willReturn(Optional.of(fvhRelationship));
            given(effectivenessTestRepository.findTopByHedgeRelationshipIdOrderByTestDateDesc(HR_FVH))
                    .willReturn(Optional.empty());
            given(effectivenessTestRepository.save(any())).willReturn(mockSavedTest);

            EffectivenessTestRequest request = irsRequest(
                    HR_FVH, HedgeType.FAIR_VALUE, IRS_FV_CHANGE, BOND_PV_CHANGE);

            // then — 예외 없이 정상 실행
            service.runTest(request);
            then(effectivenessTestRepository).should().save(any());
        }

        @Test
        @DisplayName("IRS + CASH_FLOW 조합은 ET_005 예외 없이 통과한다")
        void irs_cashFlowCombination_noException() {
            given(cfhRelationship.getHedgeType()).willReturn(HedgeType.CASH_FLOW);
            given(hedgeRelationshipRepository.findById(HR_CFH))
                    .willReturn(Optional.of(cfhRelationship));
            given(effectivenessTestRepository.findTopByHedgeRelationshipIdOrderByTestDateDesc(HR_CFH))
                    .willReturn(Optional.empty());
            given(effectivenessTestRepository.save(any())).willReturn(mockSavedTest);

            EffectivenessTestRequest request = irsRequest(
                    HR_CFH, HedgeType.CASH_FLOW,
                    new BigDecimal("100000000.00"), new BigDecimal("-98000000.00"));

            // then — 예외 없이 정상 실행
            service.runTest(request);
            then(effectivenessTestRepository).should().save(any());
        }
    }

    // =========================================================================
    // § 하위호환 — instrumentType=null → FX_FORWARD
    // =========================================================================

    @Nested
    @DisplayName("하위호환 — instrumentType null은 FX_FORWARD로 처리")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("instrumentType=null 요청은 FX_FORWARD로 저장되어 기존 동작을 유지한다")
        void nullInstrumentType_savesAsFxForward() {
            // given — 1단계 기존 클라이언트: instrumentType 미전달
            given(fvhRelationship.getHedgeType()).willReturn(HedgeType.FAIR_VALUE);
            given(hedgeRelationshipRepository.findById(HR_FVH))
                    .willReturn(Optional.of(fvhRelationship));
            given(effectivenessTestRepository.findTopByHedgeRelationshipIdOrderByTestDateDesc(HR_FVH))
                    .willReturn(Optional.empty());

            ArgumentCaptor<EffectivenessTest> captor = ArgumentCaptor.forClass(EffectivenessTest.class);
            given(effectivenessTestRepository.save(captor.capture())).willReturn(mockSavedTest);

            // instrumentType = null (1단계 요청 형식)
            EffectivenessTestRequest request = new EffectivenessTestRequest(
                    HR_FVH,
                    TEST_DATE,
                    EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                    HedgeType.FAIR_VALUE,
                    IRS_FV_CHANGE,
                    BOND_PV_CHANGE,
                    null  // instrumentType 미전달
            );

            // when
            service.runTest(request);

            // then — FX_FORWARD로 저장 (기존 동작 유지)
            EffectivenessTest saved = captor.getValue();
            assertThat(saved.getInstrumentType())
                    .as("null 입력 → FX_FORWARD 저장 (1단계 하위호환)")
                    .isEqualTo(InstrumentType.FX_FORWARD);
        }

        @Test
        @DisplayName("instrumentType=FX_FORWARD 명시 요청은 정상 처리된다 — regression")
        void fxForwardExplicit_processesNormally() {
            given(fvhRelationship.getHedgeType()).willReturn(HedgeType.FAIR_VALUE);
            given(hedgeRelationshipRepository.findById(HR_FVH))
                    .willReturn(Optional.of(fvhRelationship));
            given(effectivenessTestRepository.findTopByHedgeRelationshipIdOrderByTestDateDesc(HR_FVH))
                    .willReturn(Optional.empty());

            ArgumentCaptor<EffectivenessTest> captor = ArgumentCaptor.forClass(EffectivenessTest.class);
            given(effectivenessTestRepository.save(captor.capture())).willReturn(mockSavedTest);

            EffectivenessTestRequest request = new EffectivenessTestRequest(
                    HR_FVH,
                    TEST_DATE,
                    EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                    HedgeType.FAIR_VALUE,
                    new BigDecimal("100000000.00"),
                    new BigDecimal("-98000000.00"),
                    InstrumentType.FX_FORWARD
            );

            service.runTest(request);

            assertThat(captor.getValue().getInstrumentType())
                    .isEqualTo(InstrumentType.FX_FORWARD);
            assertThat(captor.getValue().getTestResult())
                    .isEqualTo(EffectivenessTestResult.PASS);
        }
    }

    // =========================================================================
    // § 헬퍼 팩토리
    // =========================================================================

    /**
     * IRS 유효성 테스트 요청 생성 헬퍼.
     */
    private EffectivenessTestRequest irsRequest(
            String hedgeRelationshipId,
            HedgeType hedgeType,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemPvChange) {

        return new EffectivenessTestRequest(
                hedgeRelationshipId,
                TEST_DATE,
                EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                hedgeType,
                instrumentFvChange,
                hedgedItemPvChange,
                InstrumentType.IRS
        );
    }

    /**
     * 이전 이력 mock 엔티티 생성 헬퍼.
     *
     * @param hedgeType           헤지 유형
     * @param instrumentCumulative 수단 누적값
     * @param hedgedItemCumulative 피헤지항목 누적값
     * @param ociBalance           OCI 잔액 (CFH만, null 허용)
     */
    private EffectivenessTest buildMockTest(
            HedgeType hedgeType,
            BigDecimal instrumentCumulative,
            BigDecimal hedgedItemCumulative,
            BigDecimal ociBalance) {

        return EffectivenessTest.of(
                "HR-PREV",
                TEST_DATE.minusMonths(3),
                EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                hedgeType,
                instrumentCumulative,
                hedgedItemCumulative,
                instrumentCumulative,
                hedgedItemCumulative,
                new BigDecimal("-1.010000"),
                EffectivenessTestResult.PASS,
                hedgedItemCumulative.abs(),
                BigDecimal.ZERO,
                ociBalance,
                ActionRequired.NONE,
                null,
                InstrumentType.IRS
        );
    }
}
