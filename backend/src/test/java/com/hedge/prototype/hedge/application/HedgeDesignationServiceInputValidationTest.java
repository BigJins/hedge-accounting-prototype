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
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.journal.application.port.JournalEntryRepository;
import com.hedge.prototype.valuation.application.port.CrsContractRepository;
import com.hedge.prototype.valuation.application.port.FxForwardContractRepository;
import com.hedge.prototype.valuation.application.port.IrsContractRepository;
import com.hedge.prototype.valuation.domain.irs.IrsContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;

/**
 * HedgeDesignationService — 1단계 입력 정합성 방어 로직 단위 테스트.
 *
 * <p>저장 전 선제 차단 케이스:
 * <ol>
 *   <li>HD_002 — 계약 ID 미입력</li>
 *   <li>HD_014 — itemType이 hedgeType을 지원하지 않음 (KRW_FIXED_BOND + CASH_FLOW)</li>
 *   <li>HD_015 — itemType이 hedgedRisk를 지원하지 않음 (FORECAST_TRANSACTION + INTEREST_RATE)</li>
 *   <li>HD_016 — IRS/CRS 활성 중복 지정 차단</li>
 * </ol>
 *
 * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별 및 문서화 의무)
 * @see K-IFRS 1109호 6.3.7   (위험 구성요소 지정 — 허용 조합)
 * @see K-IFRS 1109호 6.5.2   (위험회피관계 3종류)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HedgeDesignationService — 1단계 입력 정합성 방어 로직")
class HedgeDesignationServiceInputValidationTest {

    @Mock private HedgeRelationshipRepository hedgeRelationshipRepository;
    @Mock private HedgedItemRepository hedgedItemRepository;
    @Mock private FxForwardContractRepository fxForwardContractRepository;
    @Mock private IrsContractRepository irsContractRepository;
    @Mock private CrsContractRepository crsContractRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private JournalEntryRepository journalEntryRepository;

    @Mock private IrsContract mockIrsContract;
    @Mock private HedgeRelationship existingRelationship;

    private HedgeDesignationService sut;

    private static final LocalDate D   = LocalDate.of(2026, 4, 24);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

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

    // -----------------------------------------------------------------------
    // HD_002: 계약 ID 미입력
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("HD_002 — instrumentContractId와 fxForwardContractId 모두 null이면 저장 전 차단")
    void designate_nullContractId_throwsHd002() {
        HedgeDesignationRequest request = buildRequest(
                HedgedItemType.KRW_FIXED_BOND,
                HedgeType.FAIR_VALUE,
                HedgedRisk.INTEREST_RATE,
                InstrumentType.IRS,
                null,   // instrumentContractId
                null    // fxForwardContractId
        );

        assertThatThrownBy(() -> sut.designate(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo("HD_002"));

        then(hedgeRelationshipRepository).should(never()).save(any());
        then(hedgedItemRepository).should(never()).save(any());
    }

    // -----------------------------------------------------------------------
    // HD_014: itemType과 hedgeType 불일치
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("HD_014 — KRW_FIXED_BOND(FAIR_VALUE 전용)에 CASH_FLOW 지정 시도 → 저장 전 차단")
    void designate_krwFixedBondWithCashFlow_throwsHd014() {
        // KRW_FIXED_BOND는 FAIR_VALUE만 허용, CASH_FLOW 지정은 K-IFRS 6.5.2 위반
        HedgeDesignationRequest request = buildRequest(
                HedgedItemType.KRW_FIXED_BOND,
                HedgeType.CASH_FLOW,          // ← 허용되지 않는 hedgeType
                HedgedRisk.INTEREST_RATE,
                InstrumentType.IRS,
                "IRS-001",
                null
        );

        assertThatThrownBy(() -> sut.designate(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException bex = (BusinessException) e;
                    assertThat(bex.getErrorCode()).isEqualTo("HD_014");
                    assertThat(bex.getMessage()).contains("KRW_FIXED_BOND");
                    assertThat(bex.getMessage()).contains("CASH_FLOW");
                });

        then(hedgeRelationshipRepository).should(never()).save(any());
        then(hedgedItemRepository).should(never()).save(any());
    }

    // -----------------------------------------------------------------------
    // HD_015: itemType과 hedgedRisk 불일치
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("HD_015 — FORECAST_TRANSACTION(FOREIGN_CURRENCY 전용)에 INTEREST_RATE 지정 시도 → 저장 전 차단")
    void designate_forecastTransactionWithInterestRateRisk_throwsHd015() {
        // FORECAST_TRANSACTION은 FOREIGN_CURRENCY만 허용, INTEREST_RATE는 K-IFRS 6.3.7 위반
        // FX_FORWARD 경로이므로 통화 검증 통과를 위해 USD 사용
        HedgeDesignationRequest request = buildRequestWithCurrency(
                HedgedItemType.FORECAST_TRANSACTION,
                HedgeType.CASH_FLOW,
                HedgedRisk.INTEREST_RATE,     // ← 허용되지 않는 hedgedRisk
                InstrumentType.FX_FORWARD,
                "FWD-001",
                null,
                "USD"
        );

        assertThatThrownBy(() -> sut.designate(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException bex = (BusinessException) e;
                    assertThat(bex.getErrorCode()).isEqualTo("HD_015");
                    assertThat(bex.getMessage()).contains("FORECAST_TRANSACTION");
                    assertThat(bex.getMessage()).contains("INTEREST_RATE");
                });

        then(hedgeRelationshipRepository).should(never()).save(any());
        then(hedgedItemRepository).should(never()).save(any());
    }

    // -----------------------------------------------------------------------
    // HD_016: IRS/CRS 활성 중복 지정 차단
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("HD_016 — 동일 IRS 계약이 이미 DESIGNATED 상태이면 재지정 차단")
    void designate_duplicateIrsDesignation_throwsHd016() {
        // given
        given(irsContractRepository.findById("IRS-001"))
                .willReturn(Optional.of(mockIrsContract));
        given(existingRelationship.getHedgeRelationshipId())
                .willReturn("HR-2026-EXISTING");
        given(hedgeRelationshipRepository.findByInstrumentIdAndStatus("IRS-001", HedgeStatus.DESIGNATED))
                .willReturn(Optional.of(existingRelationship));

        HedgeDesignationRequest request = buildRequest(
                HedgedItemType.KRW_FIXED_BOND,
                HedgeType.FAIR_VALUE,
                HedgedRisk.INTEREST_RATE,
                InstrumentType.IRS,
                "IRS-001",
                null
        );

        // when & then
        assertThatThrownBy(() -> sut.designate(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException bex = (BusinessException) e;
                    assertThat(bex.getErrorCode()).isEqualTo("HD_016");
                    assertThat(bex.getMessage()).contains("IRS-001");
                    assertThat(bex.getMessage()).contains("HR-2026-EXISTING");
                });

        then(hedgeRelationshipRepository).should(never()).save(any(HedgeRelationship.class));
        then(hedgedItemRepository).should(never()).save(any());
    }

    // -----------------------------------------------------------------------
    // 헬퍼 — 요청 DTO 생성
    // -----------------------------------------------------------------------

    /**
     * 기본 통화(KRW) 요청 생성 헬퍼 — IRS/CRS 경로용 (통화 검증 없음).
     */
    private HedgeDesignationRequest buildRequest(
            HedgedItemType itemType,
            HedgeType hedgeType,
            HedgedRisk hedgedRisk,
            InstrumentType instrumentType,
            String instrumentContractId,
            String fxForwardContractId) {
        return buildRequestWithCurrency(
                itemType, hedgeType, hedgedRisk, instrumentType,
                instrumentContractId, fxForwardContractId, "KRW");
    }

    private HedgeDesignationRequest buildRequestWithCurrency(
            HedgedItemType itemType,
            HedgeType hedgeType,
            HedgedRisk hedgedRisk,
            InstrumentType instrumentType,
            String instrumentContractId,
            String fxForwardContractId,
            String currency) {

        HedgedItemRequest hedgedItemRequest = new HedgedItemRequest(
                itemType,
                currency,
                new BigDecimal("10000000"),
                null,
                END,
                "테스트거래상대방",
                CreditRating.A,
                null, null,
                "테스트 항목"
        );
        return new HedgeDesignationRequest(
                hedgeType,
                hedgedRisk,
                D,
                END,
                new BigDecimal("1.00"),
                "테스트 위험관리 목적",
                "테스트 헤지 전략",
                hedgedItemRequest,
                instrumentType,
                instrumentContractId,
                fxForwardContractId
        );
    }
}
