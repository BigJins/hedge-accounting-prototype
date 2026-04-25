package com.hedge.prototype.journal.adapter.web.dto;

import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 헤지회계 분개 생성 요청 DTO.
 *
 * <p>공정가치 위험회피(FVH)와 현금흐름 위험회피(CFH) 분개 생성에
 * 모두 사용되는 통합 요청 객체입니다.
 *
 * <h3>Validation 계약</h3>
 *
 * <p><b>공통 필수 (Bean Validation — hedgeType 무관하게 항상 검증)</b>
 * <ul>
 *   <li>{@code hedgeRelationshipId} — {@code @NotBlank}</li>
 *   <li>{@code entryDate}           — {@code @NotNull}</li>
 *   <li>{@code hedgeType}           — {@code @NotNull}</li>
 * </ul>
 *
 * <p><b>유형별 필수 (서비스 계층 검증 — hedgeType 분기 후 적용)</b>
 * <ul>
 *   <li>hedgeType=FAIR_VALUE: {@code instrumentFvChange}, {@code hedgedItemFvChange} — null 불가</li>
 *   <li>hedgeType=CASH_FLOW:  {@code effectiveAmount}, {@code ineffectiveAmount}     — null 불가</li>
 * </ul>
 * 위 유형별 필드는 {@code @NotNull} Bean Validation을 사용하지 않습니다.
 * hedgeType에 따라 불필요한 유형의 필드는 null로 전송해도 무방하며,
 * 서비스({@code JournalEntryService})가 hedgeType 분기 후 검증합니다.
 *
 * <p><b>OCI 재분류 추가 필드 (isReclassification=true 시 서비스 계층 검증)</b>
 * <ul>
 *   <li>{@code reclassificationAmount}  — null 불가</li>
 *   <li>{@code reclassificationReason}  — null/blank 불가, 유효한 enum 이름이어야 함</li>
 *   <li>{@code plAccountCode}           — null/blank 불가, 유효한 enum 이름이어야 함</li>
 *   <li>{@code originalOciEntryDate}    — 선택</li>
 * </ul>
 *
 * @see K-IFRS 1109호 6.5.8     (공정가치위험회피 — FVH 필드)
 * @see K-IFRS 1109호 6.5.11    (현금흐름위험회피 — CFH 필드)
 * @see K-IFRS 1109호 6.5.11(다) (OCI 재분류 — 선택 필드)
 */
public record JournalEntryRequest(

        /**
         * 위험회피관계 ID.
         *
         * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정)
         */
        @NotBlank(message = "위험회피관계 ID는 필수입니다.")
        String hedgeRelationshipId,

        /**
         * 분개 기준일 (보고기간 말 또는 거래 발생일).
         */
        @NotNull(message = "분개 기준일은 필수입니다.")
        LocalDate entryDate,

        /**
         * 위험회피 유형 (FAIR_VALUE / CASH_FLOW).
         */
        @NotNull(message = "위험회피 유형은 필수입니다.")
        HedgeType hedgeType,

        /**
         * 헤지수단 공정가치 변동 (FVH 필수, CFH 무시).
         * 양수 = 이익, 음수 = 손실.
         *
         * <p>Bean Validation 대신 서비스 계층({@code JournalEntryService#validateFairValueFields})에서
         * hedgeType=FAIR_VALUE 시 null 여부를 검증합니다.
         *
         * @see K-IFRS 1109호 6.5.8(가)
         */
        BigDecimal instrumentFvChange,

        /**
         * 피헤지항목 공정가치 변동 (FVH 필수, CFH 무시).
         * 양수 = 상승, 음수 = 하락.
         *
         * <p>Bean Validation 대신 서비스 계층({@code JournalEntryService#validateFairValueFields})에서
         * hedgeType=FAIR_VALUE 시 null 여부를 검증합니다.
         *
         * @see K-IFRS 1109호 6.5.8(나)
         */
        BigDecimal hedgedItemFvChange,

        /**
         * 유효 부분 금액 (CFH 전용, FVH 요청 시 무시).
         * 양수 = 이익, 음수 = 손실.
         *
         * <p>Bean Validation 대신 서비스 계층({@code JournalEntryService#validateCashFlowFields})에서
         * hedgeType=CASH_FLOW 시 null 여부를 검증합니다.
         *
         * @see K-IFRS 1109호 6.5.11(가)
         */
        BigDecimal effectiveAmount,

        /**
         * 비유효 부분 금액 (CFH 전용, FVH 요청 시 무시).
         * 양수 = 이익, 음수 = 손실, 0 = 비유효 없음.
         *
         * <p>Bean Validation 대신 서비스 계층({@code JournalEntryService#validateCashFlowFields})에서
         * hedgeType=CASH_FLOW 시 null 여부를 검증합니다. 비유효 부분이 없는 경우 {@code BigDecimal.ZERO}를
         * 전달하십시오.
         *
         * @see K-IFRS 1109호 6.5.11(나)
         */
        BigDecimal ineffectiveAmount,

        /**
         * OCI 재분류 여부 (true이면 재분류 분개 추가 생성).
         * null이면 false로 처리.
         *
         * @see K-IFRS 1109호 6.5.11(다)
         */
        Boolean isReclassification,

        /**
         * OCI 재분류 금액 (isReclassification=true 시 필수).
         * 양수 = OCI 이익→P&L, 음수 = OCI 손실→P&L.
         */
        BigDecimal reclassificationAmount,

        /**
         * OCI 재분류 사유 문자열 (ReclassificationReason enum 이름).
         * 예: "TRANSACTION_REALIZED"
         *
         * @see com.hedge.prototype.journal.domain.ReclassificationReason
         */
        String reclassificationReason,

        /**
         * 최초 OCI 인식일 (재분류 분개 추적용, 선택).
         */
        LocalDate originalOciEntryDate,

        /**
         * 대응 P&L 계정 코드 문자열 (AccountCode enum 이름).
         * 예: "FX_GAIN_PL", "INTEREST_INCOME"
         *
         * @see com.hedge.prototype.journal.domain.AccountCode
         */
        String plAccountCode,

        /**
         * 위험회피수단 유형 (null = FX_FORWARD 하위호환).
         *
         * <p>1단계(FX Forward) 자동 분개 경로에서는 null로 전달됩니다.
         * 2단계(IRS) 자동 분개 경로에서는 {@link InstrumentType#IRS}로 전달됩니다.
         * {@link com.hedge.prototype.journal.application.JournalEntryService}가
         * instrumentType을 읽어 IRS 전용 분개 생성기를 라우팅합니다.
         *
         * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 — IRS FVH 분개 라우팅)
         */
        InstrumentType instrumentType   // null → FX_FORWARD 하위호환
) {

    public JournalEntryRequest(
            String hedgeRelationshipId,
            LocalDate entryDate,
            HedgeType hedgeType,
            BigDecimal instrumentFvChange,
            BigDecimal hedgedItemFvChange,
            BigDecimal effectiveAmount,
            BigDecimal ineffectiveAmount,
            Boolean isReclassification,
            BigDecimal reclassificationAmount,
            String reclassificationReason,
            LocalDate originalOciEntryDate,
            String plAccountCode) {
        this(
                hedgeRelationshipId,
                entryDate,
                hedgeType,
                instrumentFvChange,
                hedgedItemFvChange,
                effectiveAmount,
                ineffectiveAmount,
                isReclassification,
                reclassificationAmount,
                reclassificationReason,
                originalOciEntryDate,
                plAccountCode,
                null
        );
    }

    /**
     * 유효성 테스트 후 공정가치 위험회피(FVH) 자동 분개 생성용 팩토리 메서드.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param entryDate           분개 기준일 (유효성 테스트 평가일)
     * @param instrumentFvChange  헤지수단 공정가치 변동
     * @param hedgedItemFvChange  피헤지항목 공정가치(현가) 변동
     * @return 자동 분개 생성용 요청 객체
     * @see K-IFRS 1109호 6.5.8 (공정가치헤지 회계처리)
     */
    public static JournalEntryRequest forAutoGenerationFvh(
            String hedgeRelationshipId,
            java.time.LocalDate entryDate,
            java.math.BigDecimal instrumentFvChange,
            java.math.BigDecimal hedgedItemFvChange) {
        return new JournalEntryRequest(
                hedgeRelationshipId,
                entryDate,
                HedgeType.FAIR_VALUE,
                instrumentFvChange,
                hedgedItemFvChange,
                null, null, null, null, null, null, null,
                null   // instrumentType=null → FX_FORWARD 하위호환
        );
    }

    /**
     * 유효성 테스트 후 현금흐름 위험회피(CFH) 자동 분개 생성용 팩토리 메서드.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param entryDate           분개 기준일 (유효성 테스트 평가일)
     * @param instrumentFvChange  헤지수단 공정가치 변동
     * @param hedgedItemFvChange  피헤지항목 공정가치(현가) 변동
     * @param effectiveAmount     유효 부분 금액 (OCI 인식)
     * @param ineffectiveAmount   비유효 부분 금액 (즉시 P&L)
     * @return 자동 분개 생성용 요청 객체
     * @see K-IFRS 1109호 6.5.11 (현금흐름헤지 회계처리)
     */
    public static JournalEntryRequest forAutoGenerationCfh(
            String hedgeRelationshipId,
            java.time.LocalDate entryDate,
            java.math.BigDecimal instrumentFvChange,
            java.math.BigDecimal hedgedItemFvChange,
            java.math.BigDecimal effectiveAmount,
            java.math.BigDecimal ineffectiveAmount) {
        return new JournalEntryRequest(
                hedgeRelationshipId,
                entryDate,
                HedgeType.CASH_FLOW,
                instrumentFvChange,
                hedgedItemFvChange,
                effectiveAmount,
                ineffectiveAmount,
                null, null, null, null, null,
                null   // instrumentType=null → FX_FORWARD 하위호환
        );
    }

    /**
     * 유효성 테스트 후 IRS 공정가치 위험회피(IRS FVH) 자동 분개 생성용 팩토리 메서드.
     *
     * <p>2단계 엔진 전용. IRS(금리스왑) + 고정금리채권 공정가치 위험회피에 사용됩니다.
     * {@link com.hedge.prototype.journal.application.JournalEntryService}가
     * {@code instrumentType=IRS}를 읽어 {@code IrsFairValueHedgeJournalGenerator}로 라우팅합니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param entryDate           분개 기준일 (유효성 테스트 평가일)
     * @param instrumentFvChange  IRS 공정가치 변동 (양수=이익, 음수=손실)
     * @param hedgedItemFvChange  채권 공정가치 변동 (양수=상승, 음수=하락)
     * @return IRS FVH 자동 분개 생성용 요청 객체
     * @see K-IFRS 1109호 6.5.8     (공정가치위험회피 IRS 회계처리)
     * @see K-IFRS 1109호 6.5.8(가) (IRS 헤지수단 P&L 인식)
     * @see K-IFRS 1109호 6.5.8(나) (채권 피헤지항목 장부금액 조정)
     * @see K-IFRS 1109호 6.5.9     (채권 장부조정 상각)
     */
    public static JournalEntryRequest forAutoGenerationIrsFvh(
            String hedgeRelationshipId,
            java.time.LocalDate entryDate,
            java.math.BigDecimal instrumentFvChange,
            java.math.BigDecimal hedgedItemFvChange) {
        return new JournalEntryRequest(
                hedgeRelationshipId,
                entryDate,
                HedgeType.FAIR_VALUE,
                instrumentFvChange,
                hedgedItemFvChange,
                null, null, null, null, null, null, null,
                InstrumentType.IRS   // IRS FVH 분개 경로로 라우팅
        );
    }

    /**
     * @deprecated hedgeType 인자를 무시하고 항상 FVH 요청을 반환하는 버그가 있었습니다.
     *             {@link #forAutoGenerationFvh} 또는 {@link #forAutoGenerationCfh}를 사용하십시오.
     * @throws UnsupportedOperationException 항상 — 잘못된 호출을 컴파일 후 조기에 감지하기 위함
     */
    @Deprecated
    public static JournalEntryRequest forAutoGeneration(
            String hedgeRelationshipId,
            java.time.LocalDate entryDate,
            HedgeType hedgeType,
            java.math.BigDecimal instrumentFvChange,
            java.math.BigDecimal hedgedItemFvChange) {
        throw new UnsupportedOperationException(
                "forAutoGeneration()은 hedgeType 인자를 무시하는 버그가 있어 제거되었습니다. " +
                "hedgeType에 따라 forAutoGenerationFvh() 또는 forAutoGenerationCfh()를 사용하십시오.");
    }
}
