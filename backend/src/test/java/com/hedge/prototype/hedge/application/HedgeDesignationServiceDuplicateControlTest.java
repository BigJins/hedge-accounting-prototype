package com.hedge.prototype.hedge.application;

import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDesignationRequest;
import com.hedge.prototype.hedge.adapter.web.dto.HedgedItemRequest;
import com.hedge.prototype.hedge.application.port.HedgedItemRepository;
import com.hedge.prototype.hedge.application.port.HedgeRelationshipRepository;
import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.hedge.domain.common.HedgedItemType;
import com.hedge.prototype.hedge.domain.common.HedgedRisk;
import com.hedge.prototype.hedge.domain.common.HedgeStatus;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import com.hedge.prototype.hedge.domain.model.HedgedItem;
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.hedge.domain.policy.EligibilityCheckResult;
import com.hedge.prototype.journal.application.port.JournalEntryRepository;
import com.hedge.prototype.valuation.application.port.CrsContractRepository;
import com.hedge.prototype.valuation.application.port.FxForwardContractRepository;
import com.hedge.prototype.valuation.application.port.IrsContractRepository;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * HedgeDesignationService.designate() — FX Forward 활성 중복 지정 차단 단위 테스트.
 *
 * <p>[이력 보존 vs 현재 상태 통제 구분]
 * <ul>
 *   <li><b>이력 보존 (Append-Only)</b>: DISCONTINUED / MATURED 상태로 종료된 위험회피관계
 *       레코드는 K-IFRS 1109호 6.5.6(자발적 취소 불가 원칙)에 따라 영구 보존됩니다.
 *       동일 계약에 대해 과거에 종료된 관계가 있더라도 신규 지정은 허용됩니다.</li>
 *   <li><b>현재 상태 통제</b>: 동일 FX Forward 계약에 DESIGNATED(활성) 관계가 이미 존재하는
 *       경우, 신규 지정은 HD_005로 차단됩니다. 기존 관계를 먼저 중단한 후 재지정해야 합니다.</li>
 * </ul>
 *
 * <p>검증 케이스:
 * <ol>
 *   <li>동일 계약 DESIGNATED 중 재지정 시도 → HD_005 BusinessException</li>
 *   <li>과거에 DISCONTINUED된 관계 + 동일 계약 신규 지정 → 성공 (이력 보존, 신규 허용)</li>
 * </ol>
 *
 * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
 * @see K-IFRS 1109호 6.5.6   (자발적 취소 불가 원칙)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HedgeDesignationService.designate() — FX Forward 활성 중복 지정 차단 (HD_005)")
class HedgeDesignationServiceDuplicateControlTest {

    // =========================================================================
    // Mock 의존성
    // =========================================================================

    @Mock
    private HedgeRelationshipRepository hedgeRelationshipRepository;

    @Mock
    private HedgedItemRepository hedgedItemRepository;

    @Mock
    private FxForwardContractRepository fxForwardContractRepository;

    @Mock
    private IrsContractRepository irsContractRepository;

    @Mock
    private CrsContractRepository crsContractRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    private HedgeDesignationService sut;

    // =========================================================================
    // 공통 픽스처
    // =========================================================================

    private static final String CONTRACT_ID        = "FWD-2026-001";
    private static final String EXISTING_HR_ID     = "HR-2026-EXISTING";
    private static final LocalDate DESIGNATION_DATE = LocalDate.of(2026, 4, 24);
    private static final LocalDate MATURITY_DATE    = LocalDate.of(2026, 12, 31);

    @BeforeEach
    void setUp() {
        sut = new HedgeDesignationService(
                hedgeRelationshipRepository,
                hedgedItemRepository,
                fxForwardContractRepository,
                irsContractRepository,
                crsContractRepository,
                eventPublisher,
                journalEntryRepository
        );
    }

    /**
     * ACTIVE FxForwardContract 스텁을 생성합니다.
     * validateForHedgeDesignation()이 예외 없이 통과하도록 만기일을 미래로 설정합니다.
     */
    private FxForwardContract buildActiveContract() {
        return FxForwardContract.designate(
                CONTRACT_ID,
                new BigDecimal("10000000"),
                new BigDecimal("1300.0000"),
                DESIGNATION_DATE,
                MATURITY_DATE,
                DESIGNATION_DATE,
                "테스트은행",
                CreditRating.AA
        );
    }

    /**
     * DESIGNATED 상태의 기존 위험회피관계 스텁을 생성합니다.
     * 동일 FX Forward 계약(CONTRACT_ID)을 사용 중인 활성 관계입니다.
     */
    private HedgeRelationship buildDesignatedRelationship() {
        FxForwardContract instrument = buildActiveContract();
        HedgedItem hedgedItem = HedgedItem.of(
                "HI-EXISTING-001",
                HedgedItemType.FORECAST_TRANSACTION,
                "USD",
                new BigDecimal("10000000"),
                new BigDecimal("13000000000"),
                MATURITY_DATE,
                "기존상대방",
                CreditRating.A,
                null, null,
                "기존 USD 예상거래"
        );
        EligibilityCheckResult eligibility = HedgeRelationship.performEligibilityCheck(
                hedgedItem, instrument, new BigDecimal("1.00"));

        return HedgeRelationship.designate(
                EXISTING_HR_ID,
                HedgeType.CASH_FLOW,
                HedgedRisk.FOREIGN_CURRENCY,
                DESIGNATION_DATE.minusMonths(3),
                MATURITY_DATE,
                new BigDecimal("1.00"),
                "기존 외화 위험관리 목적",
                "기존 통화선도 매도 헤지 전략",
                CONTRACT_ID,
                "HI-EXISTING-001",
                eligibility
        );
    }

    /**
     * 신규 지정 요청 DTO를 생성합니다.
     * instrumentType=FX_FORWARD, instrumentContractId=CONTRACT_ID로 설정됩니다.
     */
    private HedgeDesignationRequest buildDesignationRequest() {
        HedgedItemRequest hedgedItemRequest = new HedgedItemRequest(
                HedgedItemType.FORECAST_TRANSACTION,
                "USD",
                new BigDecimal("10000000"),
                new BigDecimal("13000000000"),
                MATURITY_DATE,
                "신규상대방",
                CreditRating.BBB,
                null,
                null,
                "신규 USD 예상거래"
        );
        return new HedgeDesignationRequest(
                HedgeType.CASH_FLOW,
                HedgedRisk.FOREIGN_CURRENCY,
                DESIGNATION_DATE,
                MATURITY_DATE,
                new BigDecimal("1.00"),
                "신규 외화 위험관리 목적",
                "신규 통화선도 매도 헤지 전략",
                hedgedItemRequest,
                InstrumentType.FX_FORWARD,
                CONTRACT_ID,
                null
        );
    }

    // =========================================================================
    // 케이스 1: 동일 계약 활성 중복 지정 → HD_005 예외
    // =========================================================================

    @Nested
    @DisplayName("케이스 1: 동일 FX Forward 계약이 이미 DESIGNATED 상태인 경우 — 재지정 차단")
    class Case1_DuplicateDesignationBlocked {

        @Test
        @DisplayName("HD_005 예외 발생 — errorCode, 기존 관계 ID, contractId 메시지 포함")
        void designate_sameActiveContract_throwsHd005() {
            // given
            FxForwardContract activeContract = buildActiveContract();
            HedgeRelationship existingDesignated = buildDesignatedRelationship();

            given(fxForwardContractRepository.findById(CONTRACT_ID))
                    .willReturn(Optional.of(activeContract));
            // 동일 계약에 DESIGNATED 관계가 이미 존재
            given(hedgeRelationshipRepository.findByFxForwardContractIdAndStatus(
                    CONTRACT_ID, HedgeStatus.DESIGNATED))
                    .willReturn(Optional.of(existingDesignated));

            HedgeDesignationRequest request = buildDesignationRequest();

            // when & then
            assertThatThrownBy(() -> sut.designate(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo("HD_005");
                        assertThat(bex.getMessage()).contains(EXISTING_HR_ID);
                        assertThat(bex.getMessage()).contains(CONTRACT_ID);
                        assertThat(bex.getMessage()).contains("discontinue");
                    });
        }

        @Test
        @DisplayName("HD_005 예외 발생 시 신규 HedgeRelationship 저장 없음")
        void designate_sameActiveContract_noSaveOccurs() {
            // given
            FxForwardContract activeContract = buildActiveContract();
            HedgeRelationship existingDesignated = buildDesignatedRelationship();

            given(fxForwardContractRepository.findById(CONTRACT_ID))
                    .willReturn(Optional.of(activeContract));
            given(hedgeRelationshipRepository.findByFxForwardContractIdAndStatus(
                    CONTRACT_ID, HedgeStatus.DESIGNATED))
                    .willReturn(Optional.of(existingDesignated));

            HedgeDesignationRequest request = buildDesignationRequest();

            // when
            assertThatThrownBy(() -> sut.designate(request))
                    .isInstanceOf(BusinessException.class);

            // then — 신규 지정 레코드가 저장되어서는 안 됨
            then(hedgeRelationshipRepository).should(never()).save(any(HedgeRelationship.class));
            then(hedgedItemRepository).should(never()).save(any(HedgedItem.class));
        }
    }

    // =========================================================================
    // 케이스 2: 과거 DISCONTINUED 관계 존재 + 신규 지정 → 허용 (이력 보존)
    // =========================================================================

    @Nested
    @DisplayName("케이스 2: 동일 계약에 DISCONTINUED 이력만 있는 경우 — 신규 지정 허용")
    class Case2_DiscontinuedHistoryAllowsNewDesignation {

        @Test
        @DisplayName("DESIGNATED 관계 없음 → 신규 지정 성공 (DISCONTINUED 이력은 보존된 채로)")
        void designate_noActiveRelationship_succeedsPreservingHistory() {
            // given
            FxForwardContract activeContract = buildActiveContract();

            given(fxForwardContractRepository.findById(CONTRACT_ID))
                    .willReturn(Optional.of(activeContract));
            // DESIGNATED 상태 관계 없음 → 이전에 DISCONTINUED된 관계만 있을 뿐
            given(hedgeRelationshipRepository.findByFxForwardContractIdAndStatus(
                    CONTRACT_ID, HedgeStatus.DESIGNATED))
                    .willReturn(Optional.empty());

            // 저장 스텁 — 실제 로직이 save()를 호출하도록
            given(hedgedItemRepository.save(any(HedgedItem.class)))
                    .willAnswer(inv -> inv.getArgument(0));
            given(hedgeRelationshipRepository.save(any(HedgeRelationship.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            HedgeDesignationRequest request = buildDesignationRequest();

            // when & then — 예외 없이 정상 완료
            assertThatCode(() -> sut.designate(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("신규 HedgeRelationship 저장 발생 — 이력 보존 원칙에 따라 과거 이력 삭제 없음")
        void designate_noActiveRelationship_newRecordSavedNoDeletion() {
            // given
            FxForwardContract activeContract = buildActiveContract();

            given(fxForwardContractRepository.findById(CONTRACT_ID))
                    .willReturn(Optional.of(activeContract));
            given(hedgeRelationshipRepository.findByFxForwardContractIdAndStatus(
                    CONTRACT_ID, HedgeStatus.DESIGNATED))
                    .willReturn(Optional.empty());
            given(hedgedItemRepository.save(any(HedgedItem.class)))
                    .willAnswer(inv -> inv.getArgument(0));
            given(hedgeRelationshipRepository.save(any(HedgeRelationship.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            HedgeDesignationRequest request = buildDesignationRequest();

            // when
            sut.designate(request);

            // then
            // 신규 레코드 저장 확인
            then(hedgeRelationshipRepository).should(atLeastOnce()).save(any(HedgeRelationship.class));
            // 기존 이력 삭제는 없어야 함 (Append-Only 원칙)
            then(hedgeRelationshipRepository).should(never()).delete(any(HedgeRelationship.class));
            then(hedgeRelationshipRepository).should(never()).deleteById(any());
        }
    }
}
