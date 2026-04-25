/**
 * 헤지회계 자동 분개 관련 타입 정의.
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 회계처리)
 * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 OCI/P&L 분리)
 * @see K-IFRS 1109호 6.5.11(다) (OCI 재분류 조정)
 * @see K-IFRS 1107호 (헤지회계 공시 — 분개 정보)
 */

/** 분개 유형 */
export type JournalEntryType =
  | 'FAIR_VALUE_HEDGE_INSTRUMENT'
  | 'FAIR_VALUE_HEDGE_ITEM'
  | 'IRS_FVH_AMORTIZATION'
  | 'CASH_FLOW_HEDGE_EFFECTIVE'
  | 'CASH_FLOW_HEDGE_INEFFECTIVE'
  | 'OCI_RECLASSIFICATION'
  | 'HEDGE_DISCONTINUATION'
  | 'REVERSING_ENTRY'

/** OCI 재분류 사유 */
export type ReclassificationReason =
  | 'TRANSACTION_REALIZED'
  | 'HEDGE_DISCONTINUED'
  | 'TRANSACTION_NO_LONGER_EXPECTED'

/**
 * 분개 생성 요청 DTO.
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 — FVH 필드)
 * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 — CFH 필드)
 * @see K-IFRS 1109호 6.5.11(다) (OCI 재분류 — 선택 필드)
 */
export interface JournalEntryRequest {
  /** 위험회피관계 ID (예: HR-2026-001) */
  hedgeRelationshipId: string
  /** 분개 기준일 (보고기간 말 또는 거래 발생일) */
  entryDate: string
  /** 위험회피 유형 */
  hedgeType: 'FAIR_VALUE' | 'CASH_FLOW'
  /**
   * 헤지수단 공정가치 변동 (양수 = 이익, 음수 = 손실).
   * @see K-IFRS 1109호 6.5.8(가)
   */
  instrumentFvChange: number
  /**
   * 피헤지항목 공정가치 변동 (양수 = 상승, 음수 = 하락).
   * @see K-IFRS 1109호 6.5.8(나)
   */
  hedgedItemFvChange: number
  /**
   * 유효 부분 금액 (CFH 전용).
   * @see K-IFRS 1109호 6.5.11(가)
   */
  effectiveAmount?: number
  /**
   * 비유효 부분 금액 (CFH 전용).
   * @see K-IFRS 1109호 6.5.11(나)
   */
  ineffectiveAmount?: number
  /**
   * OCI 재분류 여부.
   * @see K-IFRS 1109호 6.5.11(다)
   */
  isReclassification?: boolean
  /** OCI 재분류 금액 (isReclassification=true 시 필수) */
  reclassificationAmount?: number
  /** OCI 재분류 사유 */
  reclassificationReason?: ReclassificationReason
  /** 최초 OCI 인식일 (재분류 분개 추적용, 선택) */
  originalOciEntryDate?: string
  /** 대응 P&L 계정 코드 (AccountCode enum 이름) */
  plAccountCode?: string
}

/**
 * IRS FVH 장부조정상각 분개 생성 요청 DTO.
 *
 * @see K-IFRS 1109호 6.5.9 (공정가치위험회피 — 피헤지항목 장부금액 조정 상각)
 */
export interface IrsAmortizationRequest {
  /** 위험회피관계 ID */
  hedgeRelationshipId: string
  /** 상각 기준일 (YYYY-MM-DD) */
  amortizationDate: string
  /**
   * 누적 장부조정잔액 (양수 = 조정 증가, 음수 = 조정 감소).
   * @remarks 백엔드 BigDecimal
   */
  cumulativeAdjBalance: number
  /**
   * 잔여 기간 수 (회차 수, 예: 남은 분기 수).
   * 상각액 = cumulativeAdjBalance / remainingPeriods
   */
  remainingPeriods: number
}

/**
 * 분개 응답 DTO.
 *
 * @see K-IFRS 1107호 (헤지회계 공시 — 분개 정보)
 */
export interface JournalEntryResponse {
  /** 분개 ID */
  journalEntryId: number
  /** 위험회피관계 ID */
  hedgeRelationshipId: string
  /** 분개 기준일 */
  entryDate: string
  /** 분개 유형 */
  entryType: JournalEntryType
  /** 차변 계정 코드 (AccountCode enum 이름) */
  debitAccount: string
  /** 차변 계정 한글명 */
  debitAccountName: string
  /** 차변 금액 (포맷: #,##0.00) */
  formattedDebitAmount: string
  /** 대변 계정 코드 (AccountCode enum 이름) */
  creditAccount: string
  /** 대변 계정 한글명 */
  creditAccountName: string
  /** 대변 금액 (포맷: #,##0.00) */
  formattedCreditAmount: string
  /** 분개 금액 (원본) */
  amount: number
  /** 적요 */
  description: string
  /** K-IFRS 근거 조항 */
  ifrsReference: string
  /** OCI 재분류 사유 (재분류 분개에만 값 있음) */
  reclassificationReason?: ReclassificationReason | null
  /** 최초 OCI 인식일 (재분류 분개에만 값 있음) */
  originalOciEntryDate?: string | null
  /** 이 분개를 역분개한 분개 ID */
  cancelledByEntryId?: number | null
  /** 이 역분개가 취소하는 원 분개 ID */
  cancelsEntryId?: number | null
  /** 생성일시 */
  createdAt: string
  /** 수정일시 */
  updatedAt?: string
}
