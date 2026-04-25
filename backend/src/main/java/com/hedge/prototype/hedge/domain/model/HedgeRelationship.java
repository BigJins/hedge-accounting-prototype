package com.hedge.prototype.hedge.domain.model;

import com.hedge.prototype.common.audit.BaseAuditEntity;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.hedge.domain.common.EligibilityStatus;
import com.hedge.prototype.hedge.domain.common.HedgeDiscontinuationReason;
import com.hedge.prototype.hedge.domain.common.HedgedRisk;
import com.hedge.prototype.hedge.domain.common.HedgeStatus;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import com.hedge.prototype.hedge.domain.policy.ConditionResult;
import com.hedge.prototype.hedge.domain.policy.EligibilityCheckResult;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

/**
 * 위험회피관계(Hedge Relationship) 엔티티.
 *
 * <p>위험회피대상항목({@link HedgedItem})과 위험회피수단({@link FxForwardContract})을
 * 연결하는 핵심 도메인 엔티티입니다.
 * K-IFRS 1109호 6.4.1에 따른 3가지 적격요건 검증 로직을 직접 보유합니다.
 *
 * <p><b>생성 방법</b>: {@link #designate} 팩토리 메서드만 사용.
 * Builder, public 생성자 사용 금지.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
 * @see K-IFRS 1109호 6.4.1(2) (위험회피관계 공식 지정·문서화 의무)
 * @see K-IFRS 1109호 6.5.2 (위험회피관계 3종류: 공정가치/현금흐름/해외사업장순투자)
 * @see K-IFRS 1109호 6.5.5 (헤지비율 재조정)
 * @see K-IFRS 1109호 6.5.6 (위험회피관계 자발적 취소 불가 원칙)
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "hedge_relationships")
public class HedgeRelationship extends BaseAuditEntity {

    // -----------------------------------------------------------------------
    // 상수 — 적격요건 검증 범위
    // -----------------------------------------------------------------------

    /**
     * 경제적 관계 조건: 명목금액 커버율 하한 (50%).
     *
     * @see K-IFRS 1109호 6.4.1(3)(가) (경제적 관계 존재)
     */
    private static final BigDecimal NOTIONAL_COVERAGE_LOWER = new BigDecimal("0.50");

    /**
     * 경제적 관계 조건: 명목금액 커버율 상한 (200%).
     *
     * @see K-IFRS 1109호 6.4.1(3)(가) (경제적 관계 존재)
     */
    private static final BigDecimal NOTIONAL_COVERAGE_UPPER = new BigDecimal("2.00");

    /**
     * 헤지비율 위험관리 목적 부합성 검토 참고 하한 (80%).
     *
     * <p><b>주의</b>: K-IFRS 1109호 B6.4.9~B6.4.11에 따라 헤지비율 적격요건의 핵심은
     * "위험관리 목적에 부합하는 비율"인지 여부이며, 80% 미만이라고 하여 자동 FAIL 처리하면 안 됩니다.
     * 이 값은 재조정(Rebalancing) 검토를 권고하는 참고 범위 하한으로만 사용합니다.
     *
     * @see K-IFRS 1109호 B6.4.9~B6.4.11 (헤지비율 산정 원칙 — 위험관리 목적 부합성)
     * @see K-IFRS 1109호 BC6.234 (80~125% 정량 기준 폐지)
     */
    private static final BigDecimal HEDGE_RATIO_REFERENCE_LOWER = new BigDecimal("0.80");

    /**
     * 헤지비율 위험관리 목적 부합성 검토 참고 상한 (125%).
     *
     * <p><b>주의</b>: K-IFRS 1109호 B6.4.9~B6.4.11에 따라 헤지비율 적격요건의 핵심은
     * "위험관리 목적에 부합하는 비율"인지 여부이며, 125% 초과라고 하여 자동 FAIL 처리하면 안 됩니다.
     * 이 값은 재조정(Rebalancing) 검토를 권고하는 참고 범위 상한으로만 사용합니다.
     *
     * @see K-IFRS 1109호 B6.4.9~B6.4.11 (헤지비율 산정 원칙 — 위험관리 목적 부합성)
     * @see K-IFRS 1109호 BC6.234 (80~125% 정량 기준 폐지)
     */
    private static final BigDecimal HEDGE_RATIO_REFERENCE_UPPER = new BigDecimal("1.25");

    // -----------------------------------------------------------------------
    // 식별자
    // -----------------------------------------------------------------------

    @Id
    @Column(name = "hedge_relationship_id", length = 50)
    private String hedgeRelationshipId;

    // -----------------------------------------------------------------------
    // 헤지 유형 및 위험
    // -----------------------------------------------------------------------

    /**
     * 위험회피 유형 (FAIR_VALUE / CASH_FLOW).
     * 해외사업장순투자는 PoC 범위 외 제외.
     *
     * @see K-IFRS 1109호 6.5.2 (위험회피관계 3종류)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "hedge_type", nullable = false, length = 20)
    private HedgeType hedgeType;

    /**
     * 회피 대상 위험 유형 (FOREIGN_CURRENCY / INTEREST_RATE / COMMODITY 등).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "hedged_risk", nullable = false, length = 30)
    private HedgedRisk hedgedRisk;

    // -----------------------------------------------------------------------
    // 지정 기간
    // -----------------------------------------------------------------------

    /** 헤지 지정일 — K-IFRS 6.4.1(2) 공식 지정 시점 */
    @Column(name = "designation_date", nullable = false)
    private LocalDate designationDate;

    /** 위험회피기간 시작일 */
    @Column(name = "hedge_period_start", nullable = false)
    private LocalDate hedgePeriodStart;

    /** 위험회피기간 종료일 */
    @Column(name = "hedge_period_end", nullable = false)
    private LocalDate hedgePeriodEnd;

    // -----------------------------------------------------------------------
    // 헤지비율
    // -----------------------------------------------------------------------

    /**
     * 헤지비율 — 예: 1.00 = 100%.
     *
     * @see K-IFRS 1109호 6.4.1(3)(다) (헤지비율 요건)
     * @see K-IFRS 1109호 B6.4.9~B6.4.11 (헤지비율 산정 지침)
     */
    @Column(name = "hedge_ratio", nullable = false, precision = 8, scale = 6)
    private BigDecimal hedgeRatio;

    // -----------------------------------------------------------------------
    // 상태 필드
    // -----------------------------------------------------------------------

    /**
     * 위험회피관계 상태.
     *
     * @see K-IFRS 1109호 6.5.5 (재조정)
     * @see K-IFRS 1109호 6.5.6 (자발적 취소 불가)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HedgeStatus status;

    /**
     * 적격요건 검증 상태.
     * ELIGIBLE(충족) / INELIGIBLE(미충족) / PENDING(검증 전)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_status", nullable = false, length = 20)
    private EligibilityStatus eligibilityStatus;

    // -----------------------------------------------------------------------
    // 적격요건 검증 결과 저장 (JSON)
    // -----------------------------------------------------------------------

    /** 조건 1 (경제적 관계) 검증 결과 요약 — 저장용 */
    @Column(name = "economic_relation_result", length = 10)
    private String economicRelationResult;

    /** 조건 1 상세 내역 */
    @Column(name = "economic_relation_details", length = 500)
    private String economicRelationDetails;

    /** 조건 2 (신용위험) 검증 결과 요약 */
    @Column(name = "credit_risk_result", length = 10)
    private String creditRiskResult;

    /** 조건 2 상세 내역 */
    @Column(name = "credit_risk_details", length = 500)
    private String creditRiskDetails;

    /** 조건 3 (헤지비율) 검증 결과 요약 */
    @Column(name = "hedge_ratio_result", length = 10)
    private String hedgeRatioResult;

    /** 조건 3 상세 내역 */
    @Column(name = "hedge_ratio_details", length = 500)
    private String hedgeRatioDetails;

    // -----------------------------------------------------------------------
    // 문서화 필드 (K-IFRS 6.4.1(2))
    // -----------------------------------------------------------------------

    /**
     * 위험관리 목적 — 문서화 의무 필드.
     *
     * @see K-IFRS 1109호 6.4.1(2) (위험관리 목적 및 전략 문서화)
     */
    @Column(name = "risk_management_objective", nullable = false, length = 1000)
    private String riskManagementObjective;

    /**
     * 위험회피 전략 기술 — 문서화 의무 필드.
     *
     * @see K-IFRS 1109호 6.4.1(2) (위험회피전략 문서화)
     */
    @Column(name = "hedge_strategy", nullable = false, length = 1000)
    private String hedgeStrategy;

    // -----------------------------------------------------------------------
    // 헤지수단 연계 (FxForwardContract FK)
    // -----------------------------------------------------------------------

    /**
     * 위험회피수단 계약 ID (FK → FxForwardContract.contractId).
     * 하위 호환성을 위해 유지되며, instrumentType=FX_FORWARD인 경우 사용됩니다.
     *
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별 및 문서화)
     */
    @Column(name = "fx_forward_contract_id", length = 50)
    private String fxForwardContractId;

    /**
     * 위험회피수단 유형 — 다형성 지원.
     * FX_FORWARD, IRS, CRS 중 하나.
     *
     * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", length = 20)
    private InstrumentType instrumentType;

    /**
     * 위험회피수단 ID — instrumentType에 따른 해당 계약 ID.
     * FX_FORWARD → FxForwardContract.contractId
     * IRS → IrsContract.contractId
     * CRS → CrsContract.contractId
     *
     * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별 및 문서화)
     */
    @Column(name = "instrument_id", length = 50)
    private String instrumentId;

    // -----------------------------------------------------------------------
    // 헤지대상 연계 (HedgedItem FK)
    // -----------------------------------------------------------------------

    /**
     * 위험회피대상항목 ID (FK → HedgedItem.hedgedItemId).
     *
     * @see K-IFRS 1109호 6.4.1(2) (위험회피대상항목 식별 및 문서화)
     */
    @Column(name = "hedged_item_id", nullable = false, length = 50)
    private String hedgedItemId;

    // -----------------------------------------------------------------------
    // 헤지 중단 필드
    // -----------------------------------------------------------------------

    /**
     * 헤지회계 중단일 (nullable — 중단 시에만 설정).
     *
     * @see K-IFRS 1109호 6.5.6 (자발적 취소 불가 — 위험관리 목적 변경 시만 중단)
     */
    @Column(name = "discontinuation_date")
    private LocalDate discontinuationDate;

    /**
     * 헤지 중단 사유 코드 (nullable — 중단 시에만 설정).
     *
     * <p>K-IFRS 1109호 6.5.6에 따라 허용된 사유 코드만 저장됩니다.
     * 자발적 중단({@link HedgeDiscontinuationReason#VOLUNTARY_DISCONTINUATION})은 허용되지 않습니다.
     *
     * @see K-IFRS 1109호 6.5.6 (위험관리 목적 변경 사유 기록 — 감사 추적)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "discontinuation_reason", length = 60)
    private HedgeDiscontinuationReason discontinuationReason;

    /**
     * 헤지 중단 사유 상세 설명 (nullable — 중단 시에만 설정).
     *
     * <p>사유 코드({@link #discontinuationReason}) 외에 구체적인 사유를 자유 형식으로 기록합니다.
     * K-IFRS 1109호 6.5.6 감사 추적 요건 충족을 위한 서술형 기록.
     */
    @Column(name = "discontinuation_details", length = 500)
    private String discontinuationDetails;

    // -----------------------------------------------------------------------
    // 팩토리 메서드
    // -----------------------------------------------------------------------

    /**
     * 위험회피관계 지정 — 적격요건 검증 결과를 포함하여 생성합니다.
     *
     * <p>K-IFRS 1109호 6.4.1(2): 위험회피관계를 공식적으로 지정하고
     * 위험관리 목적 및 위험회피전략을 문서화해야 합니다.
     *
     * @param hedgeRelationshipId    위험회피관계 식별자 (예: HR-2026-001)
     * @param hedgeType              위험회피 유형 (FAIR_VALUE / CASH_FLOW)
     * @param hedgedRisk             회피 대상 위험
     * @param designationDate        헤지 지정일
     * @param hedgePeriodEnd         위험회피기간 종료일
     * @param hedgeRatio             헤지비율 (예: 1.00 = 100%)
     * @param riskManagementObjective 위험관리 목적 (문서화)
     * @param hedgeStrategy          위험회피 전략 (문서화)
     * @param fxForwardContractId    위험회피수단 계약 ID
     * @param hedgedItemId           위험회피대상항목 ID (FK → HedgedItem)
     * @param eligibilityCheckResult 적격요건 검증 결과
     * @return 위험회피관계 엔티티
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
     */
    public static HedgeRelationship designate(
            String hedgeRelationshipId,
            HedgeType hedgeType,
            HedgedRisk hedgedRisk,
            LocalDate designationDate,
            LocalDate hedgePeriodEnd,
            BigDecimal hedgeRatio,
            String riskManagementObjective,
            String hedgeStrategy,
            String fxForwardContractId,
            String hedgedItemId,
            EligibilityCheckResult eligibilityCheckResult) {

        requireNonNull(hedgeRelationshipId, "위험회피관계 식별자는 필수입니다.");
        requireNonNull(hedgeType, "위험회피 유형은 필수입니다.");
        requireNonNull(hedgedRisk, "회피 대상 위험은 필수입니다.");
        requireNonNull(designationDate, "헤지 지정일은 필수입니다.");
        requireNonNull(hedgePeriodEnd, "위험회피기간 종료일은 필수입니다.");
        requireNonNull(hedgeRatio, "헤지비율은 필수입니다.");
        requireNonNull(riskManagementObjective, "위험관리 목적은 필수입니다.");
        requireNonNull(hedgeStrategy, "위험회피 전략은 필수입니다.");
        requireNonNull(fxForwardContractId, "위험회피수단 계약 ID는 필수입니다.");
        requireNonNull(hedgedItemId, "위험회피대상항목 ID는 필수입니다.");
        requireNonNull(eligibilityCheckResult, "적격요건 검증 결과는 필수입니다.");

        HedgeRelationship hr = new HedgeRelationship();
        hr.hedgeRelationshipId = hedgeRelationshipId;
        hr.hedgeType = hedgeType;
        hr.hedgedRisk = hedgedRisk;
        hr.designationDate = designationDate;
        hr.hedgePeriodStart = designationDate;
        hr.hedgePeriodEnd = hedgePeriodEnd;
        hr.hedgeRatio = hedgeRatio;
        hr.riskManagementObjective = riskManagementObjective;
        hr.hedgeStrategy = hedgeStrategy;
        hr.fxForwardContractId = fxForwardContractId;
        hr.hedgedItemId = hedgedItemId;
        // 하위 호환: FX Forward 지정 시 instrumentType/instrumentId도 동기화
        hr.instrumentType = InstrumentType.FX_FORWARD;
        hr.instrumentId = fxForwardContractId;

        // 적격요건 결과 저장
        hr.applyEligibilityResult(eligibilityCheckResult);

        log.info("위험회피관계 지정: hedgeRelationshipId={}, hedgeType={}, eligibilityStatus={}",
                hedgeRelationshipId, hedgeType, hr.eligibilityStatus);
        return hr;
    }

    /**
     * 위험회피관계 지정 (IRS/CRS 등 다형성 헤지수단 버전).
     *
     * <p>K-IFRS 1109호 6.4.1(2): 위험회피관계를 공식적으로 지정하고
     * 위험관리 목적 및 위험회피전략을 문서화해야 합니다.
     *
     * @param hedgeRelationshipId    위험회피관계 식별자
     * @param hedgeType              위험회피 유형 (FAIR_VALUE / CASH_FLOW)
     * @param hedgedRisk             회피 대상 위험
     * @param designationDate        헤지 지정일
     * @param hedgePeriodEnd         위험회피기간 종료일
     * @param hedgeRatio             헤지비율
     * @param riskManagementObjective 위험관리 목적
     * @param hedgeStrategy          위험회피 전략
     * @param instrumentType         위험회피수단 유형 (FX_FORWARD / IRS / CRS)
     * @param instrumentId           위험회피수단 계약 ID
     * @param hedgedItemId           위험회피대상항목 ID
     * @param eligibilityCheckResult 적격요건 검증 결과
     * @return 위험회피관계 엔티티
     * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성)
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     */
    public static HedgeRelationship designateWithInstrument(
            String hedgeRelationshipId,
            HedgeType hedgeType,
            HedgedRisk hedgedRisk,
            LocalDate designationDate,
            LocalDate hedgePeriodEnd,
            BigDecimal hedgeRatio,
            String riskManagementObjective,
            String hedgeStrategy,
            InstrumentType instrumentType,
            String instrumentId,
            String hedgedItemId,
            EligibilityCheckResult eligibilityCheckResult) {

        requireNonNull(hedgeRelationshipId, "위험회피관계 식별자는 필수입니다.");
        requireNonNull(hedgeType, "위험회피 유형은 필수입니다.");
        requireNonNull(hedgedRisk, "회피 대상 위험은 필수입니다.");
        requireNonNull(designationDate, "헤지 지정일은 필수입니다.");
        requireNonNull(hedgePeriodEnd, "위험회피기간 종료일은 필수입니다.");
        requireNonNull(hedgeRatio, "헤지비율은 필수입니다.");
        requireNonNull(riskManagementObjective, "위험관리 목적은 필수입니다.");
        requireNonNull(hedgeStrategy, "위험회피 전략은 필수입니다.");
        requireNonNull(instrumentType, "위험회피수단 유형은 필수입니다.");
        requireNonNull(instrumentId, "위험회피수단 계약 ID는 필수입니다.");
        requireNonNull(hedgedItemId, "위험회피대상항목 ID는 필수입니다.");
        requireNonNull(eligibilityCheckResult, "적격요건 검증 결과는 필수입니다.");

        HedgeRelationship hr = new HedgeRelationship();
        hr.hedgeRelationshipId = hedgeRelationshipId;
        hr.hedgeType = hedgeType;
        hr.hedgedRisk = hedgedRisk;
        hr.designationDate = designationDate;
        hr.hedgePeriodStart = designationDate;
        hr.hedgePeriodEnd = hedgePeriodEnd;
        hr.hedgeRatio = hedgeRatio;
        hr.riskManagementObjective = riskManagementObjective;
        hr.hedgeStrategy = hedgeStrategy;
        hr.instrumentType = instrumentType;
        hr.instrumentId = instrumentId;
        hr.hedgedItemId = hedgedItemId;
        // FX_FORWARD 하위 호환
        if (instrumentType == InstrumentType.FX_FORWARD) {
            hr.fxForwardContractId = instrumentId;
        }

        hr.applyEligibilityResult(eligibilityCheckResult);

        log.info("위험회피관계 지정(다형성): hedgeRelationshipId={}, instrumentType={}, eligibilityStatus={}",
                hedgeRelationshipId, instrumentType, hr.eligibilityStatus);
        return hr;
    }

    // -----------------------------------------------------------------------
    // 도메인 비즈니스 메서드 — 적격요건 검증 (K-IFRS 1109호 6.4.1)
    // -----------------------------------------------------------------------

    /**
     * K-IFRS 1109호 6.4.1 적격요건 종합 검증 (정적 메서드 — 지정 전 검증용).
     *
     * <p>서비스 계층에서 HedgeRelationship 생성 전에 호출하는 정적 팩토리 스타일 검증 메서드입니다.
     * 내부적으로 인스턴스 메서드 {@link #validateEligibility}에 위임합니다.
     *
     * @param hedgedItem        위험회피대상항목
     * @param instrument        위험회피수단 (통화선도 계약)
     * @param hedgeRatioToCheck 검증할 헤지비율
     * @return 종합 적격요건 검증 결과
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     */
    public static EligibilityCheckResult performEligibilityCheck(
            HedgedItem hedgedItem,
            FxForwardContract instrument,
            BigDecimal hedgeRatioToCheck) {

        // 검증 전용 임시 인스턴스 — 도메인 메서드 호출을 위한 최소 구성
        HedgeRelationship validator = new HedgeRelationship();
        return validator.validateEligibility(hedgedItem, instrument, hedgeRatioToCheck);
    }

    /**
     * K-IFRS 1109호 6.4.1 적격요건 종합 검증.
     *
     * <p>3가지 조건을 순서대로 검증하여 {@link EligibilityCheckResult}를 반환합니다.
     * 중간에 실패가 발생해도 모든 조건을 검증 후 종합 결과를 반환합니다(fail-fast 금지).
     * 이를 통해 프론트엔드가 어떤 조건이 왜 실패했는지를 상세히 표시할 수 있습니다.
     *
     * @param hedgedItem          위험회피대상항목
     * @param hedgingInstrument   위험회피수단 (통화선도 계약)
     * @param hedgeRatioToCheck   검증할 헤지비율 (예: 1.00 = 100%)
     * @return 종합 적격요건 검증 결과
     * @see K-IFRS 1109호 6.4.1(3)(가) (경제적 관계 — B6.4.1 참조)
     * @see K-IFRS 1109호 6.4.1(3)(나) (신용위험 지배적 아님 — B6.4.7~B6.4.8 참조)
     * @see K-IFRS 1109호 6.4.1(3)(다) (헤지비율 — B6.4.9~B6.4.11 참조)
     */
    public EligibilityCheckResult validateEligibility(
            HedgedItem hedgedItem,
            FxForwardContract hedgingInstrument,
            BigDecimal hedgeRatioToCheck) {

        requireNonNull(hedgedItem, "위험회피대상항목은 필수입니다.");
        requireNonNull(hedgingInstrument, "위험회피수단은 필수입니다.");
        requireNonNull(hedgeRatioToCheck, "헤지비율은 필수입니다.");

        // 조건 1: 경제적 관계 존재 (fail-fast 없이 모두 검증)
        ConditionResult condition1 = checkEconomicRelationship(hedgedItem, hedgingInstrument);

        // 조건 2: 신용위험 지배적 아님
        ConditionResult condition2 = checkCreditRiskNotDominant(
                hedgedItem.getCounterpartyCreditRating(),
                hedgingInstrument.getCounterpartyCreditRating()
        );

        // 조건 3: 헤지비율 적절
        ConditionResult condition3 = checkHedgeRatio(hedgeRatioToCheck);

        return EligibilityCheckResult.of(
                condition1,
                condition2,
                condition3,
                hedgeRatioToCheck,
                LocalDateTime.now()
        );
    }

    private boolean isOppositeDirection(HedgedItem item, FxForwardContract instrument) {
        return switch (item.getItemType()) {
            case FX_DEPOSIT, FOREIGN_BOND, FORECAST_TRANSACTION -> instrument.offsetsForeignCurrencyAsset();
            case FOREIGN_BORROWING -> !instrument.offsetsForeignCurrencyAsset();
            case KRW_FIXED_BOND, KRW_FLOATING_BOND -> true;
        };
    }

    /**
     * 조건 1: 경제적 관계 존재 검증.
     *
     * <p>위험회피수단의 공정가치/현금흐름 변동이 위험회피대상항목의 변동과
     * 반대 방향으로 움직이는 관계가 존재해야 합니다.
     *
     * <p>검증 항목:
     * <ol>
     *   <li>기초변수 동일성 — 동일 통화 확인</li>
     *   <li>명목금액 커버율 — 50% ≤ 커버율 ≤ 200%</li>
     *   <li>만기 방향성 — 헤지수단 만기 ≥ 헤지대상 만기</li>
     *   <li>반대 방향 움직임 — 통화선도 매도 포지션 기준 항상 반대 방향 성립</li>
     * </ol>
     *
     * @param item       위험회피대상항목
     * @param instrument 위험회피수단 (통화선도)
     * @return 조건 1 검증 결과
     * @see K-IFRS 1109호 6.4.1(3)(가) (경제적 관계 존재)
     * @see K-IFRS 1109호 B6.4.1 (위험회피효과 정의 — 반대 방향 상계)
     */
    public ConditionResult checkEconomicRelationship(
            HedgedItem item, FxForwardContract instrument) {

        requireNonNull(item, "위험회피대상항목은 필수입니다.");
        requireNonNull(instrument, "위험회피수단은 필수입니다.");

        // 명목금액 커버율 계산 (헤지수단 / 헤지대상)
        // BigDecimal 필수, RoundingMode 명시
        BigDecimal coverageRatio = instrument.getNotionalAmountUsd()
                .divide(item.getNotionalAmount(), 6, RoundingMode.HALF_UP);

        boolean notionalCoverageOk = coverageRatio.compareTo(NOTIONAL_COVERAGE_LOWER) >= 0
                && coverageRatio.compareTo(NOTIONAL_COVERAGE_UPPER) <= 0;

        // 만기 방향성: 헤지수단 만기 ≥ 헤지대상 만기
        boolean maturityOk = !instrument.getMaturityDate().isBefore(item.getMaturityDate());

        // 기초변수 동일성: 헤지대상 통화와 헤지수단 통화(USD) 동일 여부
        // FxForwardContract는 USD/KRW 고정이므로 헤지대상이 USD인지 확인
        boolean underlyingMatch = instrument.getBaseCurrency().equalsIgnoreCase(item.getCurrency());

        // 반대 방향: 통화선도(FX Forward) 구조상 항상 반대 방향 성립
        // (환율 상승 시 외화자산 가치 증가 vs 통화선도 매도 손실)
        boolean oppositeDirection = isOppositeDirection(item, instrument);

        // fail-fast 없이 모든 실패 항목 수집 — 프론트엔드 상세 표시 지원
        java.util.List<String> failures = new java.util.ArrayList<>();

        if (!underlyingMatch) {
            failures.add(String.format("기초변수 불일치: 헤지대상 통화(%s)가 헤지수단 기초통화(USD)와 다릅니다.",
                    item.getCurrency()));
        }

        if (!notionalCoverageOk) {
            failures.add(String.format("명목금액 커버율 %.2f%% — 허용범위 50%%~200%% 이탈 (B6.4.1)",
                    coverageRatio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)));
        }

        if (!maturityOk) {
            failures.add(String.format("만기 방향성 불일치: 헤지수단 만기(%s)가 헤지대상 만기(%s)보다 이전입니다.",
                    instrument.getMaturityDate(), item.getMaturityDate()));
        }

        if (!oppositeDirection) {
            failures.add("반대 방향 불충족: FX Forward 포지션이 헤지대상 외화노출을 상쇄하지 않습니다.");
        }

        if (!failures.isEmpty()) {
            return ConditionResult.fail(
                    String.join(" / ", failures),
                    "K-IFRS 1109호 6.4.1(3)(가), B6.4.1"
            );
        }

        return ConditionResult.pass(
                String.format("명목금액 %.0f%% 매칭 확인 / 만기 일치 (%s) / 반대 방향 성립 (USD/KRW 동일 기초변수)",
                        coverageRatio.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP),
                        item.getMaturityDate()),
                "K-IFRS 1109호 6.4.1(3)(가), B6.4.1"
        );
    }

    /**
     * 조건 2: 신용위험이 경제적 관계로 인한 가치 변동보다 지배적이지 않음 검증.
     *
     * <p>헤지대상 발행자와 헤지수단 거래상대방 모두 투자등급(BBB 이상)인 경우
     * 신용위험이 지배적이지 않다고 판정합니다.
     *
     * @param hedgedItemRating    헤지대상 거래상대방 신용등급
     * @param counterpartyRating  헤지수단 거래상대방 신용등급
     * @return 조건 2 검증 결과
     * @see K-IFRS 1109호 6.4.1(3)(나) (신용위험 지배적 아님)
     * @see K-IFRS 1109호 B6.4.7~B6.4.8 (신용위험 지배 판단 지침)
     */
    public ConditionResult checkCreditRiskNotDominant(
            CreditRating hedgedItemRating, CreditRating counterpartyRating) {

        requireNonNull(hedgedItemRating, "헤지대상 신용등급은 필수입니다.");
        // PoC: 신용등급 미입력 계약은 BBB(투자등급 최저)로 간주
        CreditRating effectiveCounterpartyRating =
                counterpartyRating != null ? counterpartyRating : CreditRating.BBB;

        boolean hedgedItemInvestmentGrade = hedgedItemRating.isInvestmentGrade();
        boolean counterpartyInvestmentGrade = effectiveCounterpartyRating.isInvestmentGrade();

        if (!hedgedItemInvestmentGrade) {
            return ConditionResult.fail(
                    String.format("헤지대상 발행자 신용등급 %s (비투자등급) — 신용위험 지배 가능성 (B6.4.7)",
                            hedgedItemRating),
                    "K-IFRS 1109호 6.4.1(3)(나), B6.4.7~B6.4.8"
            );
        }

        if (!counterpartyInvestmentGrade) {
            return ConditionResult.fail(
                    String.format("헤지수단 거래상대방 신용등급 %s (비투자등급) — 신용위험 지배 가능성 (B6.4.7)",
                            counterpartyRating),
                    "K-IFRS 1109호 6.4.1(3)(나), B6.4.7~B6.4.8"
            );
        }

        return ConditionResult.pass(
                String.format("양측 모두 투자등급 (BBB 이상): 헤지대상 %s / 헤지수단 거래상대방 %s",
                        hedgedItemRating, counterpartyRating),
                "K-IFRS 1109호 6.4.1(3)(나), B6.4.7~B6.4.8"
        );
    }

    /**
     * 조건 3: 헤지비율 적정성 검증.
     *
     * <p>K-IFRS 1109호 6.4.1(3)(다)와 B6.4.9~B6.4.11에 따라
     * 헤지비율 적격요건의 핵심은 "이익 극대화 목적이 아닌 위험관리 목적에 부합하는 비율"인지 여부입니다.
     * 구 K-IFRS 1039호의 Dollar-offset 허용범위(80~125%)를 헤지비율 적격요건에 그대로 이식하는 것은
     * 오류이며, K-IFRS 1109호 BC6.234에 따라 이 수치 범위는 공식 폐지되었습니다.
     *
     * <p>이 메서드는 다음 기준으로 검증합니다:
     * <ul>
     *   <li>양수 비율 여부 확인 (음수·0 비율은 즉시 FAIL)</li>
     *   <li>극단적 비율 확인 (0%~10% 미만 또는 300% 초과: 위험관리 목적 부합성 의심) → FAIL</li>
     *   <li>참고 범위(80~125%) 이탈 여부 → WARNING으로 노출 (재조정 신호, 자동 FAIL 아님)</li>
     *   <li>참고 범위 이내: PASS</li>
     * </ul>
     *
     * <p>헤지비율 = 위험회피수단 명목금액 / 위험회피대상 노출금액
     *
     * @param hedgeRatioToCheck 검증할 헤지비율 (예: 1.00 = 100%)
     * @return 조건 3 검증 결과
     * @see K-IFRS 1109호 6.4.1(3)(다) (헤지비율 적정성 — 위험관리 목적 부합)
     * @see K-IFRS 1109호 B6.4.9~B6.4.11 (헤지비율 산정 원칙)
     * @see K-IFRS 1109호 BC6.234 (80~125% 정량 기준 폐지)
     */
    public ConditionResult checkHedgeRatio(BigDecimal hedgeRatioToCheck) {
        requireNonNull(hedgeRatioToCheck, "헤지비율은 필수입니다.");

        BigDecimal hedgeRatioPercent = hedgeRatioToCheck
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        // 극단적 비율 — 위험관리 목적 부합성 자체가 의심되는 수준 (FAIL)
        // 음수·0: 헤지 구조 오류 / 0%~10% 미만: 형식적 헤지 가능성 / 300% 초과: 과도한 투기적 성격
        // K-IFRS 1109호 B6.4.9: "이익 극대화 목적 배제" 원칙 위반 판단 기준
        BigDecimal EXTREME_LOWER = new BigDecimal("0.10"); // 10% 미만
        BigDecimal EXTREME_UPPER = new BigDecimal("3.00"); // 300% 초과

        if (hedgeRatioToCheck.compareTo(BigDecimal.ZERO) <= 0
                || hedgeRatioToCheck.compareTo(EXTREME_LOWER) < 0) {
            return ConditionResult.fail(
                    String.format("헤지비율 %s%% — 극단적으로 낮은 비율로 위험관리 목적 부합성 의심. "
                                    + "B6.4.9: 이익 극대화 목적이 아닌 위험관리 목적에 부합하는 비율이어야 합니다.",
                            hedgeRatioPercent),
                    "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11"
            );
        }

        if (hedgeRatioToCheck.compareTo(EXTREME_UPPER) > 0) {
            return ConditionResult.fail(
                    String.format("헤지비율 %s%% — 극단적으로 높은 비율로 위험관리 목적 부합성 의심(투기적 성격). "
                                    + "B6.4.9: 이익 극대화 목적이 아닌 위험관리 목적에 부합하는 비율이어야 합니다.",
                            hedgeRatioPercent),
                    "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11"
            );
        }

        // 참고 범위(80~125%) 이탈 — WARNING (재조정 신호, 자동 FAIL 아님)
        // K-IFRS 1109호 BC6.234: 80~125% 정량 기준은 폐지됨, 범위 이탈 자체가 FAIL 사유 아님
        // K-IFRS 1109호 B6.4.9: 위험관리 목적에 부합하면 이 범위 밖이어도 적격
        boolean withinReferenceRange = hedgeRatioToCheck.compareTo(HEDGE_RATIO_REFERENCE_LOWER) >= 0
                && hedgeRatioToCheck.compareTo(HEDGE_RATIO_REFERENCE_UPPER) <= 0;

        if (!withinReferenceRange) {
            String direction = hedgeRatioToCheck.compareTo(HEDGE_RATIO_REFERENCE_LOWER) < 0
                    ? "참고 하한(80%) 미달" : "참고 상한(125%) 초과";
            // WARNING: 범위 이탈이지만 FAIL 아님 — 재조정 문서화 권고
            // ConditionResult.pass()로 반환 + 세부 메시지에 WARNING 표시
            // (ConditionResult가 이진(PASS/FAIL)이므로 PASS로 반환하되 상세에 경고 명시)
            return ConditionResult.pass(
                    String.format("[WARNING] 헤지비율 %s%% — %s. "
                                    + "BC6.234: 이 범위 이탈은 자동 FAIL 사유가 아닙니다. "
                                    + "위험관리 목적 부합성이 유지되면 적격 인정. 재조정(Rebalancing) 검토 권고.",
                            hedgeRatioPercent, direction),
                    "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11, BC6.234"
            );
        }

        return ConditionResult.pass(
                String.format("헤지비율 %s%% — 위험관리 목적 부합 판정. "
                                + "참고 범위(80%%~125%%) 이내. 6.4.1(3)(다) 충족.",
                        hedgeRatioPercent),
                "K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11"
        );
    }

    // -----------------------------------------------------------------------
    // 비즈니스 메서드 — 상태 변경
    // -----------------------------------------------------------------------

    /**
     * 헤지회계 중단.
     *
     * <p>K-IFRS 1109호 6.5.6: 위험회피관계를 자발적으로 취소할 수 없습니다.
     * 위험관리 목적이 변경된 경우 또는 적용조건이 충족되지 않는 경우에만 중단이 허용됩니다.
     *
     * <p>허용 사유:
     * <ul>
     *   <li>{@link HedgeDiscontinuationReason#RISK_MANAGEMENT_OBJECTIVE_CHANGED} — 위험관리 목적 변경</li>
     *   <li>{@link HedgeDiscontinuationReason#HEDGE_INSTRUMENT_EXPIRED} — 헤지수단 만기/소멸</li>
     *   <li>{@link HedgeDiscontinuationReason#HEDGE_ITEM_NO_LONGER_EXISTS} — 피헤지항목 소멸</li>
     *   <li>{@link HedgeDiscontinuationReason#ELIGIBILITY_CRITERIA_NOT_MET} — 적격요건 미충족</li>
     * </ul>
     *
     * <p>차단 사유:
     * <ul>
     *   <li>{@link HedgeDiscontinuationReason#VOLUNTARY_DISCONTINUATION} — 자발적 중단 (6.5.6 명시 금지)</li>
     * </ul>
     *
     * @param discontinuationDate    중단일
     * @param reason                 중단 사유 코드 ({@link HedgeDiscontinuationReason})
     * @param details                중단 상세 사유 서술 (선택, 감사 추적용)
     * @throws BusinessException HD_011 — 이미 중단된 경우
     * @throws BusinessException HD_012 — 자발적 중단 시도 (K-IFRS 1109호 6.5.6 위반)
     * @see K-IFRS 1109호 6.5.6 (위험회피관계 자발적 취소 불가)
     * @see K-IFRS 1109호 6.5.7 (현금흐름 헤지 중단 시 OCI 처리)
     * @see K-IFRS 1109호 B6.5.26 (중단 후 OCI 잔액 처리)
     */
    public void discontinue(
            LocalDate discontinuationDate,
            HedgeDiscontinuationReason reason,
            String details) {

        requireNonNull(discontinuationDate, "중단일은 필수입니다.");
        requireNonNull(reason, "중단 사유 코드는 필수입니다.");

        if (this.status == HedgeStatus.DISCONTINUED) {
            throw new BusinessException("HD_011",
                    "이미 중단된 위험회피관계입니다. hedgeRelationshipId=" + this.hedgeRelationshipId);
        }

        // K-IFRS 1109호 6.5.6: 자발적 중단은 허용되지 않습니다.
        // "위험회피관계 또는 위험회피관계의 일부가 적용조건을 충족하지 않는 경우에만
        //  전진적으로 위험회피회계를 중단한다"
        if (!reason.isAllowed()) {
            throw new BusinessException("HD_012",
                    String.format("자발적 헤지 중단은 허용되지 않습니다 (K-IFRS 1109호 6.5.6). "
                            + "허용 사유: RISK_MANAGEMENT_OBJECTIVE_CHANGED, HEDGE_INSTRUMENT_EXPIRED, "
                            + "HEDGE_ITEM_NO_LONGER_EXISTS, ELIGIBILITY_CRITERIA_NOT_MET. "
                            + "요청 사유: %s. hedgeRelationshipId=%s",
                            reason, this.hedgeRelationshipId));
        }

        this.status = HedgeStatus.DISCONTINUED;
        this.discontinuationDate = discontinuationDate;
        this.discontinuationReason = reason;
        this.discontinuationDetails = details;

        log.info("헤지회계 중단: hedgeRelationshipId={}, 사유코드={}, 상세={}",
                hedgeRelationshipId, reason, details);
    }

    /**
     * 헤지비율 재조정 (Rebalancing).
     *
     * <p>K-IFRS 1109호 6.5.5: 위험관리 목적이 동일하게 유지되는 경우
     * 헤지비율 재조정이 가능하다면 재조정을 해야 합니다 (의무사항).
     * 재조정은 헤지관계를 종료하지 않고 연속성을 유지합니다.
     * 재조정 전 비효과성은 B6.5.8에 따라 당기손익으로 먼저 인식해야 합니다.
     *
     * <p>재조정 후 상태는 {@link HedgeStatus#REBALANCED}로 변경됩니다.
     * 이 메서드는 재조정 이력을 위한 상태 변경만 담당하며,
     * 재조정 전 비효과성 인식은 호출 측에서 선행 처리해야 합니다 (B6.5.8).
     *
     * @param newHedgeRatio     재조정 후 목표 헤지비율
     * @param rebalancingReason 재조정 사유 서술 (감사 추적용)
     * @throws BusinessException HD_015 — DISCONTINUED 상태에서 재조정 불가
     * @throws BusinessException HD_016 — 유효하지 않은 재조정 비율 (극단적 비율)
     * @see K-IFRS 1109호 6.5.5 (헤지비율 재조정 의무)
     * @see K-IFRS 1109호 B6.5.7~B6.5.21 (재조정 상세 지침)
     * @see K-IFRS 1109호 B6.5.8 (재조정 전 비효과성 당기손익 인식 선행)
     */
    public void rebalance(BigDecimal newHedgeRatio, String rebalancingReason) {
        requireNonNull(newHedgeRatio, "재조정 목표 헤지비율은 필수입니다.");
        requireNonNull(rebalancingReason, "재조정 사유는 필수입니다.");

        if (this.status == HedgeStatus.DISCONTINUED) {
            throw new BusinessException("HD_015",
                    String.format("중단된 위험회피관계는 재조정할 수 없습니다. "
                            + "hedgeRelationshipId=%s, K-IFRS 1109호 6.5.5",
                            this.hedgeRelationshipId));
        }

        // 극단적 비율 검증 — 위험관리 목적 부합성 의심 수준 차단
        BigDecimal EXTREME_LOWER = new BigDecimal("0.10");
        BigDecimal EXTREME_UPPER = new BigDecimal("3.00");
        if (newHedgeRatio.compareTo(EXTREME_LOWER) < 0 || newHedgeRatio.compareTo(EXTREME_UPPER) > 0) {
            throw new BusinessException("HD_016",
                    String.format("재조정 목표 헤지비율 %.4f이 허용 범위(10%%~300%%)를 벗어납니다. "
                            + "위험관리 목적 부합성이 의심됩니다. K-IFRS 1109호 B6.4.9",
                            newHedgeRatio));
        }

        BigDecimal previousRatio = this.hedgeRatio;
        this.hedgeRatio = newHedgeRatio;
        this.status = HedgeStatus.REBALANCED;

        log.info("헤지비율 재조정 완료: hedgeRelationshipId={}, 이전비율={}, 신규비율={}, 사유={}",
                hedgeRelationshipId, previousRatio, newHedgeRatio, rebalancingReason);
    }

    // -----------------------------------------------------------------------
    // 내부 헬퍼
    // -----------------------------------------------------------------------

    /**
     * 적격요건 검증 결과를 엔티티 필드에 반영합니다.
     */
    private void applyEligibilityResult(EligibilityCheckResult result) {
        this.economicRelationResult = result.getCondition1EconomicRelationship().isResult() ? "PASS" : "FAIL";
        this.economicRelationDetails = result.getCondition1EconomicRelationship().getDetails();

        this.creditRiskResult = result.getCondition2CreditRisk().isResult() ? "PASS" : "FAIL";
        this.creditRiskDetails = result.getCondition2CreditRisk().getDetails();

        this.hedgeRatioResult = result.getCondition3HedgeRatio().isResult() ? "PASS" : "FAIL";
        this.hedgeRatioDetails = result.getCondition3HedgeRatio().getDetails();

        this.eligibilityStatus = result.isOverallResult()
                ? EligibilityStatus.ELIGIBLE
                : EligibilityStatus.INELIGIBLE;

        this.status = result.isOverallResult()
                ? HedgeStatus.DESIGNATED
                : HedgeStatus.DISCONTINUED; // 적격요건 미충족 시 지정 불가
    }
}
