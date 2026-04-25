export type { PageResponse } from './common'

// ─── IRS 공통 타입 ────────────────────────────────────────────────────────────

/** 일수 계산 관행 */
export type DayCountConvention = 'ACT_365' | 'ACT_360'

/** IRS 신용등급 */
export type CreditRating = 'AAA' | 'AA' | 'A' | 'BBB' | 'BB' | 'B' | 'CCC' | 'D'

/**
 * IRS 계약 정보 응답 — 백엔드 IrsContract 엔티티 대응.
 *
 * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — IRS 헤지수단)
 */
export interface IrsContractResponse {
  contractId: string
  /** 명목금액 (KRW) — 백엔드 BigDecimal */
  notionalAmount: number
  /** 고정금리 — 소수 표현 (예: 0.035 = 3.5%) — 백엔드 BigDecimal */
  fixedRate: number
  /** 변동금리 기준지수 (예: CD_91D, SOFR) */
  floatingRateIndex: string
  /** 변동금리 스프레드 — nullable — 백엔드 BigDecimal */
  floatingSpread: number | null
  contractDate: string             // YYYY-MM-DD
  maturityDate: string
  /** true=고정지급/변동수취(CFH), false=변동지급/고정수취(FVH) */
  payFixedReceiveFloating: boolean
  /** 결제 주기: QUARTERLY | SEMI_ANNUAL | ANNUAL */
  settlementFrequency: string
  dayCountConvention: DayCountConvention
  counterpartyName: string | null
  counterpartyCreditRating: CreditRating | null
  status: 'ACTIVE' | 'MATURED' | 'TERMINATED'
  hedgeRelationshipId: string | null
  createdAt: string
  updatedAt: string
}

/**
 * IRS 계약 등록 요청 — 백엔드 IrsContractRequest 대응.
 */
export interface IrsContractRequest {
  contractId: string
  notionalAmount: number
  fixedRate: number
  floatingRateIndex: string
  floatingSpread?: number | null
  contractDate: string
  maturityDate: string
  payFixedReceiveFloating: boolean
  settlementFrequency: string
  dayCountConvention: DayCountConvention
  counterpartyName?: string | null
  counterpartyCreditRating?: CreditRating | null
}

/**
 * IRS 공정가치 평가 요청 — 백엔드 IrsValuationRequest 대응.
 *
 * @see K-IFRS 1113호 72~90항 (Level 2 관측가능 투입변수)
 */
export interface IrsValuationRequest {
  contractId: string
  valuationDate: string
  /** 현재 변동금리 (소수 표현 — 예: 0.0425 = 4.25%) */
  currentFloatingRate: number
  /** 할인율 무위험이자율 (소수 표현 — 예: 0.0380) */
  discountRate: number
  /** 명목금액 오버라이드 — nullable (null이면 계약에서 조회) */
  notionalAmount?: number | null
}

/**
 * IRS 공정가치 평가 응답 — 백엔드 IrsValuationResponse 대응.
 *
 * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 평가손익 P&L 인식)
 * @see K-IFRS 1113호 (Level 2 공정가치 측정)
 */
export interface IrsValuationResponse {
  valuationId: number
  contractId: string
  valuationDate: string
  /** 고정 다리 현재가치 — 백엔드 BigDecimal */
  fixedLegPv: number
  /** 변동 다리 현재가치 — 백엔드 BigDecimal */
  floatingLegPv: number
  /** 공정가치 = floatingLegPv − fixedLegPv (FVH 기준) — 백엔드 BigDecimal */
  fairValue: number
  /** 공정가치 변동액 (전기 대비) — 백엔드 BigDecimal */
  fairValueChange: number
  /** 할인율 — 백엔드 BigDecimal */
  discountRate: number
  /** 잔존일수 (평가기준일 → 만기일) */
  remainingTermDays: number
  fairValueLevel: 'LEVEL_1' | 'LEVEL_2' | 'LEVEL_3'
  createdAt: string
}

/**
 * 통화선도 계약 정보 응답 — 백엔드 FxForwardContractResponse 대응
 */
export interface FxForwardContractResponse {
  contractId: string
  /** 백엔드 BigDecimal */
  notionalAmountUsd: number
  /** 백엔드 BigDecimal */
  contractForwardRate: number
  contractDate: string             // YYYY-MM-DD
  maturityDate: string
  hedgeDesignationDate: string
  status: 'ACTIVE' | 'MATURED' | 'TERMINATED'
  createdAt: string
}

/**
 * 통화선도 공정가치 평가 요청 — 백엔드 FxForwardValuationRequest 대응
 *
 * @remarks
 * BigDecimal 필드 (notionalAmountUsd, contractForwardRate, spotRate,
 * krwInterestRate, usdInterestRate): 백엔드 Jackson 기본 설정상 숫자
 * 리터럴로 직렬화됩니다. 본개발 시 string 전환 및 parseFloat 변환
 * 레이어 추가를 권장합니다.
 */
export interface FxForwardValuationRequest {
  contractId: string
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  notionalAmountUsd: number
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  contractForwardRate: number
  contractDate: string        // YYYY-MM-DD
  maturityDate: string
  hedgeDesignationDate: string
  /** K-IFRS 1109호 B6.4.7 신용위험 지배 판단 */
  counterpartyCreditRating: 'AAA' | 'AA' | 'A' | 'BBB' | 'BB' | 'B' | 'CCC' | 'D'
  valuationDate: string
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  spotRate: number
  /** 백엔드 BigDecimal — 소수 (예: 0.035) — 본개발 시 string 전환 필요 */
  krwInterestRate: number
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  usdInterestRate: number
}

/**
 * 통화선도 공정가치 평가 응답 — 백엔드 FxForwardValuationResponse 대응
 *
 * @remarks
 * BigDecimal 필드 (notionalAmountUsd, contractForwardRate, spotRate,
 * krwInterestRate, usdInterestRate, currentForwardRate, fairValue,
 * previousFairValue, fairValueChange): 백엔드 Jackson 기본 설정상 숫자
 * 리터럴로 직렬화됩니다. 본개발 시 string 전환 및 API 응답 변환
 * 레이어에서 parseFloat 처리를 권장합니다.
 */
export interface FxForwardValuationResponse {
  valuationId: number
  contractId: string
  valuationDate: string
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  spotRate: number
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  krwInterestRate: number
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  usdInterestRate: number
  remainingDays: number
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  currentForwardRate: number
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  fairValue: number
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  previousFairValue: number
  /** 백엔드 BigDecimal — 본개발 시 string 전환 필요 */
  fairValueChange: number
  fairValueLevel: 'LEVEL_1' | 'LEVEL_2' | 'LEVEL_3'
  createdAt: string
}
