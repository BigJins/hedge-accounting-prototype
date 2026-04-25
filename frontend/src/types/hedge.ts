export type { PageResponse } from './common'

/**
 * 헤지 지정 및 K-IFRS 적격요건 자동 검증 타입 정의
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용조건)
 * @see K-IFRS 1109호 6.5.2 (위험회피관계 종류)
 *
 * @remarks
 * BigDecimal 필드 (notionalAmount, hedgeRatio 등): 백엔드 Jackson 기본 설정상
 * 숫자 리터럴로 직렬화됩니다. 본개발 시 string 전환 및 parseFloat 변환 레이어
 * 추가를 권장합니다.
 */

// ─── 공통 열거형 ──────────────────────────────────────────────────────────────

/** 위험회피 유형 — K-IFRS 1109호 6.5.2 */
export type HedgeType = 'FAIR_VALUE' | 'CASH_FLOW'

/** 위험회피 위험 유형 */
export type HedgedRisk = 'FOREIGN_CURRENCY' | 'INTEREST_RATE' | 'COMMODITY' | 'CREDIT'

/** 위험회피관계 상태 */
export type HedgeStatus = 'DESIGNATED' | 'DISCONTINUED' | 'REBALANCED' | 'MATURED'

/** 적격요건 검증 상태 */
export type EligibilityStatus = 'ELIGIBLE' | 'INELIGIBLE' | 'PENDING'

/** 조건 검증 결과 */
export type ConditionResultType = 'PASS' | 'FAIL'

/** 신용등급 열거형 — K-IFRS 1109호 B6.4.7~B6.4.8 */
export type CreditRating = 'AAA' | 'AA' | 'A' | 'BBB' | 'BB' | 'B' | 'CCC' | 'D'

/** 헤지대상 항목 유형 */
export type HedgedItemType =
  | 'FX_DEPOSIT'
  | 'FIXED_RATE_BOND'
  | 'FLOATING_RATE_BOND'
  | 'FORECAST_TRANSACTION'
  | 'KRW_FIXED_BOND'        // IRS FVH — 원화 고정금리채권 (2단계)
  | 'OTHER'

/** 위험회피수단 유형 */
export type InstrumentType = 'FX_FORWARD' | 'IRS' | 'CRS'

/** 금리 유형 */
export type InterestRateType = 'FIXED' | 'FLOATING'

// ─── 요청 타입 ────────────────────────────────────────────────────────────────

/**
 * 헤지대상 항목 요청 — HedgedItem 생성 정보
 *
 * @see K-IFRS 1109호 6.5.4 (현금흐름 헤지 대상 항목 적격성)
 * @see K-IFRS 1109호 6.5.3 (공정가치 헤지 대상 항목 적격성)
 */
export interface HedgedItemRequest {
  /** 항목 유형 */
  itemType: HedgedItemType
  /** 통화 코드 (예: USD) */
  currency: string
  /**
   * 명목금액 (USD 기준)
   * @remarks 백엔드 BigDecimal — 본개발 시 string 전환 필요
   */
  notionalAmount: number
  /** 만기일 (YYYY-MM-DD) */
  maturityDate: string
  /** 거래상대방명 (nullable) */
  counterpartyName?: string
  /**
   * 거래상대방 신용등급 — K-IFRS 1109호 B6.4.7~B6.4.8 신용위험 판정 기준
   */
  counterpartyCreditRating: CreditRating
  /** 금리 유형 (nullable) */
  interestRateType?: InterestRateType
  /**
   * 금리 (소수, 예: 0.045 = 4.5%)
   * @remarks 백엔드 BigDecimal — 본개발 시 string 전환 필요
   */
  interestRate?: number
  /** 항목 설명 */
  description?: string
}

/**
 * 헤지 지정 요청 본문
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용조건 — 지정 및 문서화 의무)
 */
export interface HedgeDesignationRequest {
  /** 위험회피 유형 — K-IFRS 1109호 6.5.2 */
  hedgeType: HedgeType
  /** 회피대상 위험 유형 */
  hedgedRisk: HedgedRisk
  /** 헤지 지정일 (YYYY-MM-DD) */
  designationDate: string
  /** 위험회피기간 종료일 (YYYY-MM-DD) */
  hedgePeriodEnd: string
  /**
   * 헤지비율 (예: 1.00 = 100%)
   * 적절 범위: 0.80 ~ 1.25 — K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11
   * @remarks 백엔드 BigDecimal — 본개발 시 string 전환 필요
   */
  hedgeRatio: number
  /** 위험관리 목적 — K-IFRS 1109호 6.4.1(2) 문서화 의무 */
  riskManagementObjective: string
  /** 헤지 전략 기술 — K-IFRS 1109호 6.4.1(2) 문서화 의무 */
  hedgeStrategy: string
  /** 헤지대상 항목 정보 */
  hedgedItem: HedgedItemRequest
  /** 기존 통화선도 계약 ID (FK → FxForwardContract) — null 허용 (IRS 모드 시) */
  fxForwardContractId?: string | null
  /**
   * 위험회피수단 유형 — IRS/CRS 등 2단계 수단 선택 시 명시.
   * null이면 FX_FORWARD로 간주 (하위 호환).
   *
   * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성)
   */
  instrumentType?: InstrumentType | null
  /**
   * 위험회피수단 계약 ID (IRS/CRS 시 사용).
   * instrumentType에 따라 IrsContract / CrsContract의 ID.
   *
   * @see K-IFRS 1109호 6.4.1(2) (위험회피수단 식별 및 문서화)
   */
  instrumentContractId?: string | null
}

// ─── 응답 타입 ────────────────────────────────────────────────────────────────

/**
 * 개별 조건 검증 결과 — K-IFRS 1109호 6.4.1 각 조건별
 */
export interface ConditionResult {
  /** 검증 결과: PASS / FAIL */
  result: ConditionResultType
  /** 상세 설명 (표시용) */
  details: string
  /** 근거 K-IFRS 조항 */
  kifrsReference: string
}

/**
 * 조건 1: 경제적 관계 존재 검증 결과
 * @see K-IFRS 1109호 6.4.1(3)(가), B6.4.1
 * @remarks 백엔드 ConditionResultResponse는 result/details/kifrsReference만 반환.
 *          확장 필드는 향후 백엔드 확장 시 사용.
 */
export interface EconomicRelationshipResult extends ConditionResult {
  /** 기초변수 동일성 여부 */
  underlyingMatch?: boolean
  /**
   * 명목금액 커버율 (예: 1.00 = 100%)
   * @remarks 백엔드 BigDecimal
   */
  notionalCoverageRatio?: number
  /** 만기 일치 여부 */
  maturityMatch?: boolean
  /** 반대 방향 움직임 여부 */
  oppositeDirection?: boolean
}

/**
 * 조건 2: 신용위험 지배적 아님 검증 결과
 * @see K-IFRS 1109호 6.4.1(3)(나), B6.4.7~B6.4.8
 * @remarks 백엔드 ConditionResultResponse는 result/details/kifrsReference만 반환.
 *          확장 필드는 향후 백엔드 확장 시 사용.
 */
export interface CreditRiskResult extends ConditionResult {
  /** 헤지대상 발행자 신용등급 */
  hedgedItemCreditRating?: CreditRating
  /** 헤지수단 거래상대방 신용등급 */
  hedgingInstrumentCreditRating?: CreditRating
  /** 신용위험 지배적 여부 (true = 조건 실패) */
  creditRiskDominant?: boolean
}

/**
 * 조건 3: 헤지비율 적절 검증 결과
 * @see K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11
 * @remarks 백엔드 ConditionResultResponse는 result/details/kifrsReference만 반환.
 *          hedgeRatioValue는 EligibilityCheckResult 최상위 필드로 반환됨.
 */
export interface HedgeRatioResult extends ConditionResult {
  /**
   * 헤지비율 (예: 1.00 = 100%)
   * @remarks 백엔드 BigDecimal
   */
  hedgeRatio?: number
  /** 헤지비율 퍼센트 (예: 100.0) */
  hedgeRatioPercent?: number
  /** 허용 범위 이내 여부 */
  withinAcceptableRange?: boolean
  /**
   * 하한 (0.80)
   * @remarks 백엔드 BigDecimal
   */
  lowerBound?: number
  /**
   * 상한 (1.25)
   * @remarks 백엔드 BigDecimal
   */
  upperBound?: number
}

/**
 * K-IFRS 1109호 6.4.1 적격요건 종합 검증 결과
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용조건 3가지)
 */
export interface EligibilityCheckResult {
  /** 종합 결과: PASS / FAIL */
  overallResult: ConditionResultType
  /** 검증 일시 */
  checkedAt: string
  /** 근거 조항 */
  kifrsReference: string
  /** 조건 1: 경제적 관계 존재 */
  condition1EconomicRelationship: EconomicRelationshipResult
  /** 조건 2: 신용위험 지배적 아님 */
  condition2CreditRisk: CreditRiskResult
  /** 조건 3: 헤지비율 적절 */
  condition3HedgeRatio: HedgeRatioResult
  /**
   * 헤지비율 값 (예: 1.00 = 100%) — 백엔드 EligibilityCheckResultResponse 최상위 필드
   * @remarks 백엔드 BigDecimal
   */
  hedgeRatioValue?: number
}

/** 헤지대상 항목 응답 요약 */
export interface HedgedItemResponse {
  hedgedItemId: string
  itemType: HedgedItemType
  currency: string
  /** @remarks 백엔드 BigDecimal */
  notionalAmount: number
  maturityDate: string
}

/** 헤지수단(통화선도 계약) 응답 요약 */
export interface HedgingInstrumentResponse {
  contractId: string
  /** @remarks 백엔드 BigDecimal */
  contractForwardRate: number
  maturityDate: string
  /** @remarks 백엔드 BigDecimal */
  notionalAmountUsd: number
}

/** 헤지 문서화 요약 */
export interface DocumentationSummary {
  hedgedItem: string
  hedgingInstrument: string
  hedgedRisk: string
  riskManagementObjective: string
  hedgeStrategy: string
  effectivenessAssessmentMethod: string
}

/** API 에러 상세 */
export interface HedgeDesignationError {
  errorCode: string
  message: string
  kifrsReference: string
}

/**
 * 헤지 지정 응답 — POST /api/v1/hedge-relationships
 *
 * @remarks
 * 검증 실패 시 HTTP 422로 반환됨. 이 경우 hedgeRelationshipId는 null,
 * eligibilityStatus는 INELIGIBLE, errors 배열이 채워집니다.
 *
 * @see K-IFRS 1109호 6.4.1
 */
export interface HedgeDesignationResponse {
  /** 생성된 헤지관계 ID (검증 실패 시 null) */
  hedgeRelationshipId: string | null
  /** 지정일 */
  designationDate: string
  /** 위험회피 유형 */
  hedgeType: HedgeType
  /** 적격요건 검증 상태 */
  eligibilityStatus: EligibilityStatus
  /** 적격요건 3조건 검증 결과 */
  eligibilityCheckResult: EligibilityCheckResult
  /** 헤지 문서 자동 생성 여부 */
  documentationGenerated: boolean
  /** 헤지 문서화 요약 (문서 생성 시) */
  documentationSummary?: DocumentationSummary
  /** 헤지대상 항목 요약 */
  hedgedItem: HedgedItemResponse
  /** 헤지수단(통화선도) 요약 */
  hedgingInstrument: HedgingInstrumentResponse
  /** 에러 목록 (검증 실패 시) */
  errors?: HedgeDesignationError[]
}

/**
 * 헤지관계 목록 조회 응답 항목
 */
export interface HedgeRelationshipSummary {
  hedgeRelationshipId: string
  hedgeType: HedgeType
  hedgedRisk: HedgedRisk
  designationDate: string
  hedgePeriodEnd: string
  /** @remarks 백엔드 BigDecimal */
  hedgeRatio: number
  status: HedgeStatus
  eligibilityStatus: EligibilityStatus
  fxForwardContractId: string
  /**
   * 위험회피수단 계약 ID — IRS/CRS 시 백엔드가 반환 (nullable).
   * FX Forward 관계에는 fxForwardContractId를 사용.
   */
  instrumentContractId?: string | null
  /**
   * 위험회피수단 유형 — 백엔드가 반환하거나, hedgedRisk에서 파생.
   * INTEREST_RATE → IRS, FOREIGN_CURRENCY → FX_FORWARD
   */
  instrumentType?: InstrumentType | null
}
