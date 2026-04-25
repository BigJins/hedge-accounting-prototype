package com.hedge.prototype.hedge.application;

import com.hedge.prototype.hedge.adapter.web.dto.HedgeDiscontinuationRequest;
import com.hedge.prototype.hedge.application.event.HedgeDesignatedEvent;
import com.hedge.prototype.hedge.application.port.HedgedItemRepository;
import com.hedge.prototype.hedge.application.port.HedgeRelationshipRepository;
import com.hedge.prototype.journal.application.port.JournalEntryRepository;
import com.hedge.prototype.valuation.application.port.CrsContractRepository;
import com.hedge.prototype.valuation.application.port.FxForwardContractRepository;
import com.hedge.prototype.valuation.application.port.IrsContractRepository;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.hedge.domain.common.EligibilityStatus;
import com.hedge.prototype.hedge.domain.common.HedgeDiscontinuationReason;
import com.hedge.prototype.hedge.domain.common.HedgedItemType;
import com.hedge.prototype.hedge.domain.common.HedgedRisk;
import com.hedge.prototype.hedge.domain.common.HedgeStatus;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.model.HedgedItem;
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.hedge.domain.policy.ConditionResult;
import com.hedge.prototype.hedge.domain.policy.EligibilityCheckResult;
import com.hedge.prototype.journal.domain.JournalEntry;
import com.hedge.prototype.journal.domain.ReclassificationReason;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * HedgeDesignationService.discontinue() — CFH 중단 후 OCI 후속 처리 단위 테스트.
 *
 * <p>K-IFRS 1109호 6.5.12 규정에 따른 5가지 시나리오를 검증합니다:
 * <ul>
 *   <li>케이스 A: CFH + forecastTransactionExpected=true  → 분개 미생성, DISCONTINUED</li>
 *   <li>케이스 B: CFH + forecastTransactionExpected=false + OCI 잔액 500만 → 재분류 분개 생성</li>
 *   <li>케이스 C: CFH + forecastTransactionExpected=false + OCI 잔액 ZERO → 분개 미생성 + 로그</li>
 *   <li>케이스 D: CFH + forecastTransactionExpected=null  → BusinessException(HD_017)</li>
 *   <li>케이스 E: FVH 중단 → OCI 분기 진입 안 함, 분개 미생성</li>
 * </ul>
 *
 * @see K-IFRS 1109호 6.5.7  (현금흐름 헤지 중단)
 * @see K-IFRS 1109호 6.5.12 (CFH 중단 후 OCI 후속 처리)
 * @see K-IFRS 1109호 6.5.12(2) (예상거래 발생불가 시 즉시 P&L 재분류)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HedgeDesignationService.discontinue() — CFH OCI 후속 처리 (K-IFRS 1109호 6.5.12)")
class HedgeDesignationServiceCfhDiscontinuationTest {

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

    private static final String HEDGE_RELATIONSHIP_ID = "HR-2026-001";
    private static final LocalDate DISCONTINUATION_DATE = LocalDate.of(2026, 4, 23);
    private static final BigDecimal OCI_BALANCE_5M = new BigDecimal("5000000");

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
     * 테스트용 CFH 위험회피관계 스텁을 생성합니다.
     *
     * <p>FxForwardContract를 통해 지정된 CFH DESIGNATED 상태의 HedgeRelationship입니다.
     *
     * @return 테스트용 HedgeRelationship (CFH, DESIGNATED)
     */
    private HedgeRelationship buildCfhRelationship() {
        FxForwardContract instrument = FxForwardContract.designate(
                "INS-2026-001",
                new BigDecimal("10000000"),
                new BigDecimal("1350.0000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1),
                "테스트은행",
                CreditRating.AA
        );

        HedgedItem hedgedItem = HedgedItem.of(
                "HI-2026-001",
                HedgedItemType.FORECAST_TRANSACTION,
                "USD",
                new BigDecimal("10000000"),
                new BigDecimal("13500000000"),
                LocalDate.of(2026, 12, 31),
                "테스트상대방",
                CreditRating.A,
                null, null,
                "USD 예상거래"
        );

        EligibilityCheckResult eligibility = HedgeRelationship.performEligibilityCheck(
                hedgedItem, instrument, new BigDecimal("1.00"));

        return HedgeRelationship.designate(
                HEDGE_RELATIONSHIP_ID,
                HedgeType.CASH_FLOW,
                HedgedRisk.FOREIGN_CURRENCY,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("1.00"),
                "외화 위험관리 목적",
                "통화선도 매도 헤지 전략",
                "INS-2026-001",
                "HI-2026-001",
                eligibility
        );
    }

    /**
     * 테스트용 FVH 위험회피관계 스텁을 생성합니다.
     */
    private HedgeRelationship buildFvhRelationship() {
        FxForwardContract instrument = FxForwardContract.designate(
                "INS-2026-002",
                new BigDecimal("10000000"),
                new BigDecimal("1350.0000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1),
                "테스트은행B",
                CreditRating.AA
        );

        HedgedItem hedgedItem = HedgedItem.of(
                "HI-2026-002",
                HedgedItemType.FX_DEPOSIT,
                "USD",
                new BigDecimal("10000000"),
                new BigDecimal("13500000000"),
                LocalDate.of(2026, 12, 31),
                "테스트상대방B",
                CreditRating.A,
                null, null,
                "USD 예금"
        );

        EligibilityCheckResult eligibility = HedgeRelationship.performEligibilityCheck(
                hedgedItem, instrument, new BigDecimal("1.00"));

        return HedgeRelationship.designate(
                "HR-2026-002",
                HedgeType.FAIR_VALUE,
                HedgedRisk.FOREIGN_CURRENCY,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("1.00"),
                "공정가치 외화 위험관리",
                "통화선도 매도 헤지",
                "INS-2026-002",
                "HI-2026-002",
                eligibility
        );
    }

    // =========================================================================
    // 케이스 A: CFH + forecastTransactionExpected=true → 분개 미생성
    // =========================================================================

    @Nested
    @DisplayName("케이스 A: CFH 중단 + 예상거래 여전히 발생 가능 (forecastTransactionExpected=true)")
    class CaseA_ForecastStillExpected {

        @Test
        @DisplayName("OCI 유지 — 분개 저장하지 않음, status=DISCONTINUED")
        void cfh_forecastExpected_true_noJournalCreated() {
            // given
            HedgeRelationship cfhRelationship = buildCfhRelationship();
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(cfhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(cfhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    DISCONTINUATION_DATE,
                    HedgeDiscontinuationReason.RISK_MANAGEMENT_OBJECTIVE_CHANGED,
                    "위험관리 목적 변경으로 중단",
                    true,          // 예상거래 여전히 발생 가능 → OCI 유지
                    OCI_BALANCE_5M,
                    "FX_GAIN_PL"
            );

            // when
            sut.discontinue(HEDGE_RELATIONSHIP_ID, request);

            // then
            then(journalEntryRepository).should(never()).save(any(JournalEntry.class));
            // 중단 상태 저장은 반드시 발생해야 함
            then(hedgeRelationshipRepository).should(times(1)).save(cfhRelationship);
            // 중단 후 상태 확인
            assertThat(cfhRelationship.getStatus()).isEqualTo(HedgeStatus.DISCONTINUED);
        }
    }

    // =========================================================================
    // 케이스 B: CFH + forecastTransactionExpected=false + OCI 잔액 500만
    // =========================================================================

    @Nested
    @DisplayName("케이스 B: CFH 중단 + 예상거래 발생 불가 + OCI 잔액 500만 (6.5.12(2))")
    class CaseB_TransactionNoLongerExpected_WithOci {

        @Test
        @DisplayName("OCI 재분류 분개 1건 저장, reason=TRANSACTION_NO_LONGER_EXPECTED")
        void cfh_forecastExpected_false_ociBalance_5M_journalCreated() {
            // given
            HedgeRelationship cfhRelationship = buildCfhRelationship();
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(cfhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(cfhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    DISCONTINUATION_DATE,
                    HedgeDiscontinuationReason.HEDGE_ITEM_NO_LONGER_EXISTS,
                    "예상거래 취소로 중단",
                    false,         // 예상거래 발생 불가 → 즉시 P&L 재분류
                    OCI_BALANCE_5M,
                    "FX_GAIN_PL"
            );

            // when
            sut.discontinue(HEDGE_RELATIONSHIP_ID, request);

            // then
            ArgumentCaptor<JournalEntry> journalCaptor = ArgumentCaptor.forClass(JournalEntry.class);
            then(journalEntryRepository).should(times(1)).save(journalCaptor.capture());

            JournalEntry savedEntry = journalCaptor.getValue();
            assertThat(savedEntry.getHedgeRelationshipId()).isEqualTo(HEDGE_RELATIONSHIP_ID);
            assertThat(savedEntry.getReclassificationReason())
                    .isEqualTo(ReclassificationReason.TRANSACTION_NO_LONGER_EXPECTED);
            assertThat(savedEntry.getAmount()).isEqualByComparingTo(OCI_BALANCE_5M);
            assertThat(savedEntry.getEntryDate()).isEqualTo(DISCONTINUATION_DATE);
        }

        @Test
        @DisplayName("OCI 양수(이익): 차변=CFHR_OCI, 대변=FX_GAIN_PL")
        void cfh_positiveOciBalance_debitOci_creditPlAccount() {
            // given — OCI 잔액 양수(이익 포지션)
            HedgeRelationship cfhRelationship = buildCfhRelationship();
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(cfhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(cfhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    DISCONTINUATION_DATE,
                    HedgeDiscontinuationReason.ELIGIBILITY_CRITERIA_NOT_MET,
                    "적격요건 미충족",
                    false,
                    new BigDecimal("3000000"),  // 양수 OCI
                    "FX_GAIN_PL"
            );

            // when
            sut.discontinue(HEDGE_RELATIONSHIP_ID, request);

            // then — 양수 OCI: 차변=CFHR_OCI, 대변=FX_GAIN_PL (OCI 이익 P&L 재분류)
            ArgumentCaptor<JournalEntry> captor = ArgumentCaptor.forClass(JournalEntry.class);
            then(journalEntryRepository).should(times(1)).save(captor.capture());

            JournalEntry entry = captor.getValue();
            assertThat(entry.getDebitAccount().name()).isEqualTo("CFHR_OCI");
            assertThat(entry.getCreditAccount().name()).isEqualTo("FX_GAIN_PL");
        }

        @Test
        @DisplayName("OCI 음수(손실): 차변=FX_LOSS_PL, 대변=CFHR_OCI")
        void cfh_negativeOciBalance_debitPlAccount_creditOci() {
            // given — OCI 잔액 음수(손실 포지션)
            HedgeRelationship cfhRelationship = buildCfhRelationship();
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(cfhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(cfhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    DISCONTINUATION_DATE,
                    HedgeDiscontinuationReason.HEDGE_INSTRUMENT_EXPIRED,
                    "헤지수단 만기",
                    false,
                    new BigDecimal("-2000000"),  // 음수 OCI (손실)
                    "FX_LOSS_PL"
            );

            // when
            sut.discontinue(HEDGE_RELATIONSHIP_ID, request);

            // then — 음수 OCI: 차변=FX_LOSS_PL, 대변=CFHR_OCI (OCI 손실 P&L 재분류)
            ArgumentCaptor<JournalEntry> captor = ArgumentCaptor.forClass(JournalEntry.class);
            then(journalEntryRepository).should(times(1)).save(captor.capture());

            JournalEntry entry = captor.getValue();
            assertThat(entry.getDebitAccount().name()).isEqualTo("FX_LOSS_PL");
            assertThat(entry.getCreditAccount().name()).isEqualTo("CFHR_OCI");
        }
    }

    // =========================================================================
    // 케이스 C: CFH + forecastTransactionExpected=false + OCI 잔액 ZERO
    // =========================================================================

    @Nested
    @DisplayName("케이스 C: CFH 중단 + 예상거래 발생 불가 + OCI 잔액 ZERO → 분개 미생성 + 로그")
    class CaseC_TransactionNoLongerExpected_ZeroOci {

        @Test
        @DisplayName("OCI 잔액 ZERO — 분개 저장하지 않음, 상태는 DISCONTINUED")
        void cfh_forecastExpected_false_ociBalanceZero_noJournalCreated() {
            // given
            HedgeRelationship cfhRelationship = buildCfhRelationship();
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(cfhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(cfhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    DISCONTINUATION_DATE,
                    HedgeDiscontinuationReason.RISK_MANAGEMENT_OBJECTIVE_CHANGED,
                    "OCI 없음 상태에서 중단",
                    false,
                    BigDecimal.ZERO,     // OCI 잔액 ZERO → 분개 미생성
                    "FX_GAIN_PL"
            );

            // when
            sut.discontinue(HEDGE_RELATIONSHIP_ID, request);

            // then
            then(journalEntryRepository).should(never()).save(any(JournalEntry.class));
            assertThat(cfhRelationship.getStatus()).isEqualTo(HedgeStatus.DISCONTINUED);
        }

        @Test
        @DisplayName("OCI 잔액 null → ZERO로 처리, 분개 미생성")
        void cfh_forecastExpected_false_ociBalanceNull_treatedAsZero() {
            // given
            HedgeRelationship cfhRelationship = buildCfhRelationship();
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(cfhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(cfhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    DISCONTINUATION_DATE,
                    HedgeDiscontinuationReason.RISK_MANAGEMENT_OBJECTIVE_CHANGED,
                    "OCI 잔액 미제공",
                    false,
                    null,            // OCI 잔액 null → ZERO로 처리
                    null
            );

            // when
            sut.discontinue(HEDGE_RELATIONSHIP_ID, request);

            // then — 분개 없음 (null은 ZERO 취급)
            then(journalEntryRepository).should(never()).save(any(JournalEntry.class));
        }
    }

    // =========================================================================
    // 케이스 D: CFH + forecastTransactionExpected=null → BusinessException(HD_017)
    // =========================================================================

    @Nested
    @DisplayName("케이스 D: CFH 중단 + forecastTransactionExpected=null → HD_017 예외")
    class CaseD_CfhWithNullForecast {

        @Test
        @DisplayName("HD_017 예외 발생 — CFH 중단 시 forecastTransactionExpected 필수")
        void cfh_forecastExpected_null_throwsHD017() {
            // given
            HedgeRelationship cfhRelationship = buildCfhRelationship();
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(cfhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(cfhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    DISCONTINUATION_DATE,
                    HedgeDiscontinuationReason.RISK_MANAGEMENT_OBJECTIVE_CHANGED,
                    "forecastTransactionExpected 누락",
                    null,            // null → HD_017 예외
                    OCI_BALANCE_5M,
                    "FX_GAIN_PL"
            );

            // when & then
            assertThatThrownBy(() -> sut.discontinue(HEDGE_RELATIONSHIP_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo("HD_017");
                        assertThat(be.getMessage()).contains("forecastTransactionExpected");
                        assertThat(be.getMessage()).contains("6.5.12");
                    });
        }

        @Test
        @DisplayName("HD_017 발생 시 분개 저장 없음 — 트랜잭션 롤백 전제")
        void cfh_forecastExpected_null_noJournalSaved() {
            // given
            HedgeRelationship cfhRelationship = buildCfhRelationship();
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(cfhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(cfhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    DISCONTINUATION_DATE,
                    HedgeDiscontinuationReason.RISK_MANAGEMENT_OBJECTIVE_CHANGED,
                    "누락",
                    null,
                    OCI_BALANCE_5M,
                    "FX_GAIN_PL"
            );

            // when
            assertThatCode(() -> sut.discontinue(HEDGE_RELATIONSHIP_ID, request))
                    .isInstanceOf(BusinessException.class);

            // then
            then(journalEntryRepository).should(never()).save(any(JournalEntry.class));
        }
    }

    // =========================================================================
    // 케이스 E: FVH 중단 → OCI 분기 진입 안 함
    // =========================================================================

    @Nested
    @DisplayName("케이스 E: FVH(공정가치헤지) 중단 → OCI 분기 진입 안 함")
    class CaseE_FvhDiscontinuation {

        @Test
        @DisplayName("FVH 중단 — forecastTransactionExpected 관계없이 분개 미생성")
        void fvh_discontinuation_noOciBranchEntered() {
            // given
            HedgeRelationship fvhRelationship = buildFvhRelationship();
            given(hedgeRelationshipRepository.findById("HR-2026-002"))
                    .willReturn(Optional.of(fvhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(fvhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    DISCONTINUATION_DATE,
                    HedgeDiscontinuationReason.ELIGIBILITY_CRITERIA_NOT_MET,
                    "FVH 중단",
                    null,            // FVH에는 forecastTransactionExpected 불필요 (null 허용)
                    OCI_BALANCE_5M,
                    "FX_GAIN_PL"
            );

            // when — FVH 중단 시 HD_017 예외 없이 정상 처리되어야 함
            assertThatCode(() -> sut.discontinue("HR-2026-002", request))
                    .doesNotThrowAnyException();

            // then — OCI 분기 진입 없음, 분개 저장 없음
            then(journalEntryRepository).should(never()).save(any(JournalEntry.class));
        }

        @Test
        @DisplayName("FVH 중단 후 status=DISCONTINUED, 분개 0건")
        void fvh_discontinuation_statusDiscontinued_zeroJournals() {
            // given
            HedgeRelationship fvhRelationship = buildFvhRelationship();
            given(hedgeRelationshipRepository.findById("HR-2026-002"))
                    .willReturn(Optional.of(fvhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(fvhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    DISCONTINUATION_DATE,
                    HedgeDiscontinuationReason.RISK_MANAGEMENT_OBJECTIVE_CHANGED,
                    "위험관리 목적 변경",
                    null,
                    null,
                    null
            );

            // when
            sut.discontinue("HR-2026-002", request);

            // then
            assertThat(fvhRelationship.getStatus()).isEqualTo(HedgeStatus.DISCONTINUED);
            then(journalEntryRepository).should(never()).save(any(JournalEntry.class));
        }
    }

    // =========================================================================
    // 공통: 중단 날짜 미지정 시 오늘 날짜 사용
    // =========================================================================

    @Nested
    @DisplayName("공통: discontinuationDate=null이면 오늘 날짜 사용")
    class CommonDiscontinuationDateResolution {

        @Test
        @DisplayName("discontinuationDate=null → today 기준으로 중단 처리")
        void discontinuationDate_null_usesToday() {
            // given
            HedgeRelationship cfhRelationship = buildCfhRelationship();
            given(hedgeRelationshipRepository.findById(HEDGE_RELATIONSHIP_ID))
                    .willReturn(Optional.of(cfhRelationship));
            given(hedgeRelationshipRepository.save(any())).willReturn(cfhRelationship);

            HedgeDiscontinuationRequest request = new HedgeDiscontinuationRequest(
                    null,            // discontinuationDate 미지정 → 오늘 날짜
                    HedgeDiscontinuationReason.RISK_MANAGEMENT_OBJECTIVE_CHANGED,
                    "날짜 미지정 테스트",
                    true,
                    BigDecimal.ZERO,
                    null
            );

            // when
            assertThatCode(() -> sut.discontinue(HEDGE_RELATIONSHIP_ID, request))
                    .doesNotThrowAnyException();

            // then — 중단일이 오늘로 설정됨
            assertThat(cfhRelationship.getDiscontinuationDate()).isEqualTo(LocalDate.now());
        }
    }
}

