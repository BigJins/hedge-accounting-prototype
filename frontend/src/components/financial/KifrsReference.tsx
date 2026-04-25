import { useState } from 'react'

// ─── K-IFRS 조항 원문 데이터 ──────────────────────────────────────────────────

const CLAUSES = {
  'KIFRS1113-72-90': {
    code: 'K-IFRS 1113호 72~90항',
    title: 'Level 2 공정가치 — 관측가능한 투입변수',
    content: `Level 2 투입변수는 자산이나 부채에 대해 직접 또는 간접으로 관측가능한 투입변수(Level 1 투입변수 제외)입니다.

관측가능한 투입변수의 예시:
• 유사한 자산/부채의 시장가격
• 이자율 및 수익률 곡선 (관측가능한 구간)
• 내재변동성
• 신용 스프레드

통화선도의 경우 현물환율(S₀), KRW/USD 시장금리가 Level 2 투입변수에 해당하며, 이자율 평형 이론(IRP)으로 공정가치를 산출합니다.

IRP 공식: F = S₀ × (1 + r_KRW × T/365) / (1 + r_USD × T/360)
  - KRW: Actual/365 Fixed (한국 자금시장 표준)
  - USD: Actual/360 (USD SOFR 국제 표준)`,
  },
  'KIFRS1113-61-66': {
    code: 'K-IFRS 1113호 61~66항',
    title: '시장참여자 가격결정기법 — 평가기법 적용 원칙',
    content: `공정가치를 측정하기 위해 사용하는 평가기법은 관련 상황에서 적절하여야 하며, 공정가치를 측정하기 위해 충분한 데이터를 이용할 수 있어야 합니다.

주요 원칙:
• 시장참여자가 자산의 가격을 결정하거나 부채를 이전할 때 사용하는 가정을 최대한 이용
• 관측가능한 투입변수를 관측불가능한 투입변수보다 우선 사용
• 경우에 따라 하나의 평가기법이 적절할 수 있으나, 다수의 평가기법을 사용할 수도 있음

통화선도 평가 시 적용:
• 이자율 평형 이론(IRP)은 시장참여자들이 실제 통화선도 가격결정에 사용하는 표준 기법
• Day Count Convention(일수 계산 관행)은 시장 표준을 준수해야 함
  - KRW: Actual/365 Fixed (한국 CD금리·국고채 자금시장 표준)
  - USD: Actual/360 (SOFR·구LIBOR Money Market 국제 표준)
• 이는 시장참여자의 실제 가격결정 방식을 반영한 Level 2 투입변수 적용임`,
  },
  'KIFRS1109-6.5.8': {
    code: 'K-IFRS 1109호 6.5.8',
    title: '공정가치위험회피 회계처리',
    content: `공정가치위험회피 회계처리 원칙:

① 위험회피수단의 손익 → 당기손익(P&L) 즉시 인식
② 위험회피대상항목의 장부금액 조정 → 위험회피 위험에 기인한 손익을 당기손익으로 인식

통화선도(위험회피수단)의 공정가치 변동은 즉시 당기손익으로 인식되며, 외화 자산/부채(위험회피대상)의 환율 변동도 대칭적으로 당기손익으로 인식됩니다.

경제적 상쇄 효과가 P&L에 반영되어 순손익 변동이 최소화됩니다.`,
  },
  'KIFRS1107': {
    code: 'K-IFRS 1107호',
    title: '금융상품 공시 — 위험회피 관련',
    content: `K-IFRS 1107호에 따른 위험회피 공시 사항:

• 위험회피 전략 및 위험회피 관계의 특성 설명
• 지정된 위험회피수단의 명목금액 및 공정가치
• 공정가치 수준 분류 (Level 1 / 2 / 3)
• 위험회피 비효과적 부분 금액
• 위험회피 준비금 조정 내역
• 민감도 분석

공정가치위험회피의 경우 위험회피수단 공정가치를 재무상태표에 표시하고, 변동액을 포괄손익계산서에 별도 공시합니다.`,
  },
  'KIFRS1109-6.4.1': {
    code: 'K-IFRS 1109호 6.4.1',
    title: '위험회피회계 적용조건 — 3가지 적격요건',
    content: `위험회피회계를 적용하기 위해서는 다음 세 가지 조건을 모두 충족해야 합니다:

① 조건 1 — 경제적 관계 존재 (6.4.1(3)(가), B6.4.1)
위험회피수단과 위험회피대상항목 사이에 경제적 관계가 존재해야 합니다.
즉, 동일한 기초변수(환율, 금리 등)로 인해 두 항목의 가치 변동이
반대 방향으로 움직이는 관계여야 합니다.

② 조건 2 — 신용위험 지배적 아님 (6.4.1(3)(나), B6.4.7~B6.4.8)
신용위험의 효과가 경제적 관계로 인한 가치 변동보다 지배적이지 않아야
합니다. 양 당사자가 투자등급(BBB 이상)이면 이 조건을 충족합니다.

③ 조건 3 — 헤지비율 적절 (6.4.1(3)(다), B6.4.9~B6.4.11)
위험회피관계의 헤지비율은 실제로 위험을 회피하는 수량의 비율과 같아야
합니다. 적절 범위: 80% ≤ 헤지비율 ≤ 125%.

세 조건 모두 충족 시: eligibilityStatus = ELIGIBLE → 헤지회계 적용 가능`,
  },
  'KIFRS1109-6.4.1-3-ga': {
    code: 'K-IFRS 1109호 6.4.1(3)(가), B6.4.1',
    title: '경제적 관계 존재 — 조건 1',
    content: `경제적 관계(Economic Relationship) 판단 기준:

위험회피수단의 공정가치 또는 현금흐름의 변동이
위험회피대상항목의 공정가치 또는 현금흐름의 변동과
반대 방향으로 움직이는 관계가 존재해야 합니다.

구체적 판단 항목:
• 기초변수(Underlying) 동일성: 동일한 통화쌍, 이자율지표 등
• 명목금액 커버율: 50% ~ 200% 범위 이내
• 만기 방향성: 헤지수단 만기 ≥ 헤지대상 만기
• 반대 방향 움직임: 기초변수 변동 시 서로 반대 방향

통화선도 적용 예시 (USD 예금 + 통화선도 매도):
- 환율 상승 → 예금 원화가치 증가 vs 통화선도 손실 (반대 방향)`,
  },
  'KIFRS1109-6.4.1-3-na': {
    code: 'K-IFRS 1109호 6.4.1(3)(나), B6.4.7~B6.4.8',
    title: '신용위험 지배적 아님 — 조건 2',
    content: `신용위험 판단 기준 (B6.4.7~B6.4.8):

신용위험의 효과가 경제적 관계로 인한 가치 변동보다
지배적이지 않아야 합니다.

투자등급 기준:
• BBB- 이상: 투자등급 (Investment Grade) — 조건 충족
• BB+ 이하: 비투자등급 (Non-Investment Grade) — 신용위험 지배 가능성

검증 항목:
• 헤지대상 발행자 신용등급: BBB 이상 권고
• 헤지수단 거래상대방 신용등급: A- 이상 권고 (은행 등)
• 신용위험 집중도: 동일 그룹 내 내부거래 주의
• 담보/보증 여부: 신용 보강 장치로 신용위험 경감 인정

※ PoC 구현: 외부 신용평가등급 수동 입력 방식
   본개발 시 신용평가사 API 연동 예정`,
  },
  'KIFRS1109-6.4.1-3-da': {
    code: 'K-IFRS 1109호 6.4.1(3)(다), B6.4.9~B6.4.11',
    title: '헤지비율 적절 — 조건 3',
    content: `헤지비율 적절성 판단 기준 (B6.4.9~B6.4.11):

헤지비율 = 위험회피수단 명목금액 / 위험회피대상 노출금액

적절 범위: 80% ≤ 헤지비율 ≤ 125%
※ K-IFRS 1109호 B6.4.12 (Dollar-offset 80~125% 기준 준용)

이상적 헤지비율: 100% (명목금액 1:1 매칭)

주의사항:
• 비효과적인 부분을 의도적으로 줄이기 위한 가중치 불균형은 금지
• 범위 이탈 시 위험관리 목적 동일하면 재조정(Rebalancing) 가능
  → K-IFRS 1109호 6.5.5 (헤지비율 재조정)

데모 시나리오:
헤지수단 $10M / 헤지대상 $10M = 100% → 조건 충족`,
  },
  'KIFRS1109-6.5.11': {
    code: 'K-IFRS 1109호 6.5.11',
    title: '현금흐름위험회피 회계처리 — 유효 부분 OCI',
    content: `현금흐름위험회피(Cash Flow Hedge) 회계처리:

① 유효 부분 → 기타포괄손익(OCI)으로 인식
위험회피수단의 손익 중 유효한 부분은 기타포괄손익(OCI)으로
인식하여 위험회피 적립금(Hedging Reserve)으로 누적합니다.

② 예상거래 발생 시 → OCI 재분류
예상거래가 발생하여 당기손익에 영향을 미칠 때,
OCI에 인식된 금액을 당기손익으로 재분류합니다.

분개 예시:
(차) 기타포괄손익누계액  XXX원
(대) 파생상품부채         XXX원

경제적 의미:
환율 변동으로 인한 파생상품 평가손익을 OCI에 보류하여,
예상거래 실현 시점에 당기손익으로 인식함으로써
손익 매칭(Matching) 효과를 달성합니다.`,
  },
} as const

type ClauseId = keyof typeof CLAUSES

// ─── Props ────────────────────────────────────────────────────────────────────

interface KifrsReferenceProps {
  clauseId: ClauseId
  /** 표시 스타일: badge(뱃지), text(밑줄 텍스트) */
  variant?: 'badge' | 'text'
}

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

/**
 * K-IFRS 조항 참조 버튼 + 원문 팝업 컴포넌트.
 * 클릭 시 해당 조항의 내용을 모달로 표시합니다.
 */
export function KifrsReference({ clauseId, variant = 'badge' }: KifrsReferenceProps) {
  const [open, setOpen] = useState(false)
  const clause = CLAUSES[clauseId]

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={`${clause.code} 원문 보기`}
        className={
          variant === 'badge'
            ? 'inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-blue-100 text-blue-800 hover:bg-blue-200 transition-colors hover:underline underline-offset-2'
            : 'text-xs text-blue-700 hover:text-blue-900 underline underline-offset-2 transition-colors font-medium'
        }
      >
        📖 {clause.code}
      </button>

      {open && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="kifrs-modal-title"
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
        >
          {/* 백드롭 */}
          <div
            className="absolute inset-0 bg-slate-900/50"
            onClick={() => setOpen(false)}
            aria-hidden="true"
          />

          {/* 모달 본문 */}
          <div className="relative bg-white rounded-xl shadow-2xl max-w-lg w-full z-10">
            {/* 헤더 */}
            <div className="flex items-start justify-between px-6 pt-5 pb-4 border-b border-slate-100">
              <div>
                <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-100 text-blue-800 mb-2">
                  {clause.code}
                </span>
                <h3 id="kifrs-modal-title" className="text-base font-semibold text-slate-900">
                  {clause.title}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="닫기"
                className="ml-4 flex-shrink-0 text-slate-400 hover:text-slate-600 transition-colors rounded-md p-1 hover:bg-slate-100"
              >
                <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                </svg>
              </button>
            </div>

            {/* 내용 */}
            <div className="px-6 py-4">
              <pre className="text-sm text-slate-700 leading-relaxed whitespace-pre-wrap font-sans bg-slate-50 rounded-lg p-4 border border-slate-200">
                {clause.content}
              </pre>
            </div>

            {/* 푸터 */}
            <div className="px-6 pb-5 flex items-center justify-between">
              <p className="text-xs text-slate-400">출처: 한국회계기준원 K-IFRS</p>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="px-4 py-2 text-sm font-medium text-white bg-blue-900 hover:bg-blue-800 rounded-lg transition-colors"
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
