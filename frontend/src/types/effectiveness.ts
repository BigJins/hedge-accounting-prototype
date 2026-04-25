/**
 * 유효성 테스트 관련 타입 정의.
 *
 * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 평가)
 * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 비효과성 P&L)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 헤지 OCI/P&L 분리)
 */

/** Dollar-offset 테스트 방법 유형 */
export type EffectivenessTestType =
  | 'DOLLAR_OFFSET_PERIODIC'
  | 'DOLLAR_OFFSET_CUMULATIVE'

/** 위험회피 유형 */
export type HedgeType = 'FAIR_VALUE' | 'CASH_FLOW'

/** 유효성 테스트 판정 결과 */
export type EffectivenessTestResult = 'PASS' | 'WARNING' | 'FAIL'

/** 필요 조치 유형 */
export type ActionRequired = 'NONE' | 'REBALANCE' | 'DISCONTINUE'

/** 위험회피수단 유형 */
export type InstrumentType = 'FX_FORWARD' | 'IRS' | 'CRS'

/**
 * 유효성 테스트 실행 요청 DTO.
 *
 * @see K-IFRS 1109호 B6.4.12 (유효성 평가 방법)
 * @see K-IFRS 1109호 B6.4.13 (Dollar-offset 방법)
 */
export interface EffectivenessTestRequest {
  /** 위험회피관계 ID (예: HR-2026-001) */
  hedgeRelationshipId: string
  /** 유효성 평가 기준일 — 매 보고기간 말 */
  testDate: string
  /** Dollar-offset 테스트 방법 */
  testType: EffectivenessTestType
  /** 위험회피 유형 */
  hedgeType: HedgeType
  /** 위험회피수단 공정가치 당기 변동액 (음수 허용) */
  instrumentFvChange: number
  /** 피헤지항목 현재가치 당기 변동액 */
  hedgedItemPvChange: number
  /** 위험회피수단 유형 (선택 — 백엔드 nullable) */
  instrumentType?: InstrumentType
}

/**
 * 유효성 테스트 결과 응답 DTO.
 *
 * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 테스트)
 * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 비효과성 P&L)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 헤지 OCI/P&L 분리)
 */
export interface EffectivenessTestResponse {
  /** 유효성 테스트 레코드 ID */
  effectivenessTestId: number
  /** 위험회피관계 ID */
  hedgeRelationshipId: string
  /** 평가 기준일 */
  testDate: string
  /** Dollar-offset 테스트 방법 */
  testType: EffectivenessTestType
  /** 위험회피 유형 */
  hedgeType: HedgeType
  /** 위험회피수단 당기 변동액 */
  instrumentFvChange: number
  /** 피헤지항목 당기 변동액 */
  hedgedItemPvChange: number
  /** 위험회피수단 누적 변동액 */
  instrumentFvCumulative: number
  /** 피헤지항목 누적 변동액 */
  hedgedItemPvCumulative: number
  /**
   * Dollar-offset 유효성 비율 (부호 포함).
   * 음수 = 반대방향(정상), 양수 = 동방향(비정상).
   */
  effectivenessRatio: number
  /** 판정 결과 (PASS / FAIL) */
  testResult: EffectivenessTestResult
  /**
   * 유효 부분.
   * 공정가치 헤지: 분석용 (P&L 인식). 현금흐름 헤지: OCI 인식 금액.
   */
  effectiveAmount: number
  /**
   * 비효과적 부분 — P&L 즉시 인식.
   * 공정가치 헤지: 부호 있는 순합계. 현금흐름 헤지: 과대헤지 초과분.
   */
  ineffectiveAmount: number
  /**
   * 당기 OCI 인식액 (현금흐름위험회피적립금 당기분).
   * 현금흐름 헤지에서만 유효. 공정가치 헤지 시 null.
   *
   * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피적립금)
   */
  ociReserveBalance: number | null
  /** 필요 조치 (NONE / REBALANCE / DISCONTINUE) */
  actionRequired: ActionRequired
  /** 실패 사유 (PASS 시 null) */
  failureReason: string | null
  /**
   * 위험회피수단 유형 (IRS FVH 2단계 — 백엔드에서 nullable 반환).
   * IRS 선택 시 결과 화면에 §6.5.8 / §6.5.9 안내 표시.
   */
  instrumentType?: InstrumentType | null
  /** 레코드 생성 시각 */
  createdAt: string
}
