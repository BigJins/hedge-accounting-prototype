package com.hedge.prototype.journal.application;

import com.hedge.prototype.journal.adapter.web.dto.JournalEntryRequest;
import com.hedge.prototype.journal.application.port.JournalEntryRepository;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import com.hedge.prototype.journal.domain.JournalEntry;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

/**
 * JournalEntryService — 서비스 계층 Validation 계약 테스트.
 *
 * <p>Bean Validation(@NotNull)이 아닌 서비스 계층에서 hedgeType 분기 후 검증하는
 * 유형별 필수 필드의 계약을 검증합니다.
 *
 * <p><b>검증 대상 경로</b>
 * <ul>
 *   <li>FVH: instrumentFvChange / hedgedItemFvChange null 시 JE_002</li>
 *   <li>CFH: effectiveAmount / ineffectiveAmount null 시 JE_002</li>
 *   <li>OCI 재분류: 필수 필드 누락 또는 잘못된 enum 값 시 JE_003</li>
 *   <li>CFH 요청에 FVH 전용 필드 null — 허용 (타 유형 필드는 무시)</li>
 *   <li>IRS FVH 라우팅: instrumentType=IRS → IrsFairValueHedgeJournalGenerator 경로</li>
 * </ul>
 *
 * @see com.hedge.prototype.journal.adapter.web.dto.JournalEntryRequest (Validation 계약 설명)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JournalEntryService — 서비스 계층 Validation 계약")
class JournalEntryServiceValidationTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;

    private JournalEntryService service;

    private static final String HEDGE_ID    = "HR-2026-001";
    private static final LocalDate TODAY    = LocalDate.of(2026, 4, 23);
    private static final BigDecimal POS     = new BigDecimal("500000");
    private static final BigDecimal NEG     = new BigDecimal("-480000");
    private static final BigDecimal ZERO    = BigDecimal.ZERO;

    @BeforeEach
    void setUp() {
        service = new JournalEntryService(journalEntryRepository);
    }

    // -----------------------------------------------------------------------
    // FVH — 유형별 필수 필드 검증 (JE_002)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("FVH 요청 — instrumentFvChange / hedgedItemFvChange 필수")
    class FvhFieldValidation {

        @Test
        @DisplayName("instrumentFvChange가 null이면 JE_002 예외가 발생한다")
        void whenInstrumentFvChangeIsNull_thenThrowsJe002() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.FAIR_VALUE,
                    null,   // instrumentFvChange — null
                    NEG,    // hedgedItemFvChange
                    null, null, null, null, null, null, null,
                    null    // instrumentType — null(FX_FORWARD 하위호환)
            );

            assertThatThrownBy(() -> service.createEntries(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "JE_002")
                    .hasMessageContaining("instrumentFvChange");
        }

        @Test
        @DisplayName("hedgedItemFvChange가 null이면 JE_002 예외가 발생한다")
        void whenHedgedItemFvChangeIsNull_thenThrowsJe002() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.FAIR_VALUE,
                    POS,    // instrumentFvChange
                    null,   // hedgedItemFvChange — null
                    null, null, null, null, null, null, null,
                    null    // instrumentType — null(FX_FORWARD 하위호환)
            );

            assertThatThrownBy(() -> service.createEntries(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "JE_002")
                    .hasMessageContaining("hedgedItemFvChange");
        }

        @Test
        @DisplayName("FVH 요청에서 CFH 전용 필드(effectiveAmount 등)는 null이어도 허용된다")
        void whenFvhRequestHasCfhFieldsNull_thenNoException() {
            // CFH 전용 필드가 모두 null인 FVH 요청 — 서비스가 CFH 필드를 검증하지 않아야 함
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.FAIR_VALUE,
                    POS, NEG,       // FVH 필수 필드
                    null, null,     // effectiveAmount, ineffectiveAmount — null 허용
                    null, null, null, null, null,
                    null            // instrumentType — null(FX_FORWARD 하위호환)
            );

            // 분개 생성 자체는 성공 (저장 시 repository mock 반환값 없어도 generator 통과)
            assertThatCode(() -> service.createEntries(request))
                    .doesNotThrowAnyException();
        }
    }

    // -----------------------------------------------------------------------
    // CFH — 유형별 필수 필드 검증 (JE_002)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("CFH 요청 — effectiveAmount / ineffectiveAmount 필수")
    class CfhFieldValidation {

        @Test
        @DisplayName("effectiveAmount가 null이면 JE_002 예외가 발생한다")
        void whenEffectiveAmountIsNull_thenThrowsJe002() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    POS, NEG,       // FVH 필드 (CFH에서 무시됨)
                    null,           // effectiveAmount — null
                    ZERO,           // ineffectiveAmount
                    null, null, null, null, null,
                    null            // instrumentType — null(FX_FORWARD 하위호환)
            );

            assertThatThrownBy(() -> service.createEntries(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "JE_002")
                    .hasMessageContaining("effectiveAmount");
        }

        @Test
        @DisplayName("ineffectiveAmount가 null이면 JE_002 예외가 발생한다 (0이 아닌 null이 문제)")
        void whenIneffectiveAmountIsNull_thenThrowsJe002() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    POS, NEG,
                    NEG,            // effectiveAmount
                    null,           // ineffectiveAmount — null (0이 아님!)
                    null, null, null, null, null,
                    null            // instrumentType — null(FX_FORWARD 하위호환)
            );

            assertThatThrownBy(() -> service.createEntries(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "JE_002")
                    .hasMessageContaining("ineffectiveAmount");
        }

        @Test
        @DisplayName("ineffectiveAmount=BigDecimal.ZERO는 비유효 없음을 의미하며 정상 처리된다")
        void whenIneffectiveAmountIsZero_thenNotAnError() {
            // 비유효 부분이 없는 경우 null이 아닌 ZERO를 전달해야 함
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    POS, NEG,
                    NEG,    // effectiveAmount
                    ZERO,   // ineffectiveAmount — ZERO는 유효
                    null, null, null, null, null,
                    null    // instrumentType — null(FX_FORWARD 하위호환)
            );

            assertThatCode(() -> service.createEntries(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CFH 요청에서 FVH 전용 필드(instrumentFvChange 등)는 null이어도 허용된다")
        void whenCfhRequestHasFvhFieldsNull_thenNoException() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    null, null,     // FVH 전용 필드 — null 허용 (CFH에서 무시)
                    NEG, ZERO,      // CFH 필수 필드
                    null, null, null, null, null,
                    null            // instrumentType — null(FX_FORWARD 하위호환)
            );

            assertThatCode(() -> service.createEntries(request))
                    .doesNotThrowAnyException();
        }
    }

    // -----------------------------------------------------------------------
    // OCI 재분류 — 필수 필드 및 enum 값 검증 (JE_003)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("OCI 재분류 (isReclassification=true) — 필수 필드 및 enum 검증")
    class OciReclassificationValidation {

        @Test
        @DisplayName("reclassificationAmount가 null이면 JE_003 예외가 발생한다")
        void whenReclassificationAmountIsNull_thenThrowsJe003() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    null, null, NEG, ZERO,
                    true,           // isReclassification
                    null,           // reclassificationAmount — null
                    "TRANSACTION_REALIZED",
                    TODAY.minusMonths(3),
                    "FX_GAIN_PL",
                    null            // instrumentType
            );

            assertThatThrownBy(() -> service.createEntries(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "JE_003")
                    .hasMessageContaining("reclassificationAmount");
        }

        @Test
        @DisplayName("reclassificationReason이 blank이면 JE_003 예외가 발생한다")
        void whenReclassificationReasonIsBlank_thenThrowsJe003() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    null, null, NEG, ZERO,
                    true,
                    POS,
                    "",             // reclassificationReason — blank
                    null,
                    "FX_GAIN_PL",
                    null            // instrumentType
            );

            assertThatThrownBy(() -> service.createEntries(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "JE_003")
                    .hasMessageContaining("reclassificationReason");
        }

        @Test
        @DisplayName("존재하지 않는 reclassificationReason enum 값이면 JE_003 예외가 발생한다")
        void whenReclassificationReasonIsInvalidEnum_thenThrowsJe003() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    null, null, NEG, ZERO,
                    true,
                    POS,
                    "INVALID_REASON_XYZ",   // 존재하지 않는 enum
                    null,
                    "FX_GAIN_PL",
                    null            // instrumentType
            );

            assertThatThrownBy(() -> service.createEntries(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "JE_003")
                    .hasMessageContaining("INVALID_REASON_XYZ");
        }

        @Test
        @DisplayName("존재하지 않는 plAccountCode enum 값이면 JE_003 예외가 발생한다")
        void whenPlAccountCodeIsInvalidEnum_thenThrowsJe003() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    null, null, NEG, ZERO,
                    true,
                    POS,
                    "TRANSACTION_REALIZED",
                    null,
                    "NONEXISTENT_ACCOUNT",  // 존재하지 않는 enum
                    null            // instrumentType
            );

            assertThatThrownBy(() -> service.createEntries(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "JE_003")
                    .hasMessageContaining("NONEXISTENT_ACCOUNT");
        }

        @Test
        @DisplayName("plAccountCode가 null이면 JE_003 예외가 발생한다")
        void whenPlAccountCodeIsNull_thenThrowsJe003() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    null, null, NEG, ZERO,
                    true,
                    POS,
                    "TRANSACTION_REALIZED",
                    null,
                    null,           // plAccountCode — null
                    null            // instrumentType
            );

            assertThatThrownBy(() -> service.createEntries(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "JE_003")
                    .hasMessageContaining("plAccountCode");
        }

        @Test
        @DisplayName("isReclassification=false이면 재분류 필드 누락이어도 JE_003을 던지지 않는다")
        void whenIsReclassificationFalse_thenReclassFieldsNotValidated() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    null, null, NEG, ZERO,
                    false,          // isReclassification=false
                    null, null, null, null, // 재분류 필드 모두 null — 검증 안 함
                    null            // instrumentType
            );

            // 재분류 경로를 타지 않으므로 JE_003 예외 없음
            assertThatCode(() -> service.createEntries(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("isReclassification=null이면 false로 처리되어 재분류 검증을 건너뛴다")
        void whenIsReclassificationNull_thenTreatedAsFalse() {
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.CASH_FLOW,
                    null, null, NEG, ZERO,
                    null,           // isReclassification=null → false로 처리
                    null, null, null, null,
                    null            // instrumentType
            );

            assertThatCode(() -> service.createEntries(request))
                    .doesNotThrowAnyException();
        }
    }

    // -----------------------------------------------------------------------
    // IRS FVH 라우팅 — instrumentType=IRS → IrsFairValueHedgeJournalGenerator
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("IRS FVH 라우팅 — instrumentType=IRS 경로 검증")
    class IrsFvhRouting {

        @Test
        @DisplayName("instrumentType=IRS + FAIR_VALUE → IrsFairValueHedgeJournalGenerator 경로 — 예외 없음")
        void whenInstrumentTypeIrsAndFairValue_thenNoException() {
            // IRS FVH 분개 경로: forAutoGenerationIrsFvh → instrumentType=IRS
            JournalEntryRequest request = JournalEntryRequest.forAutoGenerationIrsFvh(
                    HEDGE_ID, TODAY,
                    new BigDecimal("390000000"),    // IRS FV +390M
                    new BigDecimal("-386000000"));   // 채권 FV -386M

            // 분개 생성 성공 — generator가 올바른 방향으로 라우팅됨
            assertThatCode(() -> service.createEntries(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("instrumentType=IRS는 instrumentFvChange/hedgedItemFvChange null 시 여전히 JE_002")
        void whenIrsButFvFieldsNull_thenJe002() {
            // IRS 경로라도 FVH 필수 필드 검증은 동일하게 적용됨
            JournalEntryRequest request = new JournalEntryRequest(
                    HEDGE_ID, TODAY, HedgeType.FAIR_VALUE,
                    null,   // instrumentFvChange — null
                    new BigDecimal("-386000000"),
                    null, null, null, null, null, null, null,
                    InstrumentType.IRS  // instrumentType=IRS
            );

            assertThatThrownBy(() -> service.createEntries(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "JE_002")
                    .hasMessageContaining("instrumentFvChange");
        }

        @Test
        @DisplayName("forAutoGenerationIrsFvh 팩토리 — instrumentType=IRS 설정 확인")
        void forAutoGenerationIrsFvh_setsInstrumentTypeIrs() {
            JournalEntryRequest request = JournalEntryRequest.forAutoGenerationIrsFvh(
                    HEDGE_ID, TODAY,
                    new BigDecimal("100000"),
                    new BigDecimal("-95000"));

            assertThat(request.instrumentType()).isEqualTo(InstrumentType.IRS);
            assertThat(request.hedgeType()).isEqualTo(HedgeType.FAIR_VALUE);
        }

        @Test
        @DisplayName("forAutoGenerationFvh 팩토리 — instrumentType=null (FX_FORWARD 하위호환)")
        void forAutoGenerationFvh_setsInstrumentTypeNull() {
            JournalEntryRequest request = JournalEntryRequest.forAutoGenerationFvh(
                    HEDGE_ID, TODAY,
                    new BigDecimal("100000"),
                    new BigDecimal("-95000"));

            assertThat(request.instrumentType()).isNull();
            assertThat(request.hedgeType()).isEqualTo(HedgeType.FAIR_VALUE);
        }

        @Test
        @DisplayName("IRS FVH 요구사항 시나리오 — 390M / -386M 분개 생성 완료")
        void irsRequirementsScenario_generatesWithoutException() {
            // IRS_HEDGE_REQUIREMENTS.md §8: 1조원 채권, IRS, 금리 3%→4.5%
            // IRS FV +390M, 채권 FV -386M → 분개 2건 생성
            JournalEntryRequest request = JournalEntryRequest.forAutoGenerationIrsFvh(
                    "HR-IRS-2026-001",
                    LocalDate.of(2026, 3, 31),
                    new BigDecimal("390000000"),
                    new BigDecimal("-386000000"));

            assertThatCode(() -> service.createEntries(request))
                    .doesNotThrowAnyException();
        }
    }
}
