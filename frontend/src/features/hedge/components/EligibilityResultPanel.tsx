import { useState } from 'react'
import clsx from 'clsx'
import { KifrsReference } from '@/components/financial/KifrsReference'
import { Button } from '@/components/ui/Button'
import type {
  HedgeDesignationResponse,
  ConditionResultType,
  EconomicRelationshipResult,
  CreditRiskResult,
  HedgeRatioResult,
} from '@/types/hedge'
import { formatDate } from '@/utils/formatters'

// ─── Props ────────────────────────────────────────────────────────────────────

interface EligibilityResultPanelProps {
  result: HedgeDesignationResponse
  /** 저장에 성공했는지 여부 (false = HTTP 422 검증 실패) */
  isSaved: boolean
  /** [수정] 버튼 클릭 핸들러 */
  onEditClick: () => void
}

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

/**
 * K-IFRS 1109호 6.4.1 적격요건 검증 결과 패널
 *
 * DEMO_SCENARIO 화면 2 WOW 포인트 1:
 * - 3조건 각각 PASS(초록)/FAIL(빨강) 표시
 * - K-IFRS 조항 배지 클릭 시 원문 팝업
 * - 실패 조건 상세 이유 표시
 * - HTTP 422 (저장 안 됨) 안내
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용조건)
 */
export function EligibilityResultPanel({
  result,
  isSaved,
  onEditClick,
}: EligibilityResultPanelProps) {
  const { eligibilityCheckResult: check } = result
  const isOverallPass = check.overallResult === 'PASS'

  return (
    <div
      role="region"
      aria-label="K-IFRS 적격요건 검증 결과"
      className={clsx(
        'rounded-xl border-2 overflow-hidden',
        isOverallPass
          ? 'border-emerald-400 shadow-emerald-100 shadow-lg'
          : 'border-red-400 shadow-red-100 shadow-lg',
      )}
    >
      {/* ── 상단 헤더 ─────────────────────────────────────────────── */}
      <div
        className={clsx(
          'px-6 py-4 flex items-center justify-between',
          isOverallPass ? 'bg-emerald-900' : 'bg-red-900',
        )}
      >
        <div className="flex items-center gap-3">
          <span className="text-2xl" aria-hidden="true">{isOverallPass ? '🔍' : '🔍'}</span>
          <div>
            <h3 className="text-white font-bold text-base">
              K-IFRS 1109호 6.4.1 적격요건 검증
            </h3>
            <p className="text-xs text-white/70 mt-0.5">
              검증 일시: {formatDate(check.checkedAt.substring(0, 10))}
            </p>
          </div>
        </div>
        <KifrsReference clauseId="KIFRS1109-6.4.1" variant="badge" />
      </div>

      {/* ── 3조건 검증 결과 목록 ──────────────────────────────────── */}
      <div className="bg-white divide-y divide-slate-100">
        {/* 조건 1: 경제적 관계 */}
        <ConditionRow
          index={1}
          label="경제적 관계 존재"
          result={check.condition1EconomicRelationship.result}
          kifrsClauseId="KIFRS1109-6.4.1-3-ga"
          subItems={buildEconomicRelationshipSubItems(check.condition1EconomicRelationship)}
        />

        {/* 조건 2: 신용위험 */}
        <ConditionRow
          index={2}
          label="신용위험 지배적 아님"
          result={check.condition2CreditRisk.result}
          kifrsClauseId="KIFRS1109-6.4.1-3-na"
          subItems={buildCreditRiskSubItems(check.condition2CreditRisk)}
        />

        {/* 조건 3: 헤지비율 */}
        <ConditionRow
          index={3}
          label={`헤지 비율 적절 (${((check.hedgeRatioValue ?? 0) * 100).toFixed(0)}%)`}
          result={check.condition3HedgeRatio.result}
          kifrsClauseId="KIFRS1109-6.4.1-3-da"
          subItems={buildHedgeRatioSubItems(check.condition3HedgeRatio, check.hedgeRatioValue)}
        />
      </div>

      {/* ── 종합 결과 ─────────────────────────────────────────────── */}
      <div
        className={clsx(
          'px-6 py-5 border-t-2',
          isOverallPass
            ? 'bg-emerald-50 border-emerald-200'
            : 'bg-red-50 border-red-200',
        )}
      >
        {isOverallPass ? (
          <OverallPassContent
            result={result}
            isSaved={isSaved}
            onEditClick={onEditClick}
          />
        ) : (
          <OverallFailContent
            result={result}
            isSaved={isSaved}
            onEditClick={onEditClick}
          />
        )}
      </div>
    </div>
  )
}

// ─── 조건 행 컴포넌트 ─────────────────────────────────────────────────────────

interface ConditionRowProps {
  index: number
  label: string
  result: ConditionResultType
  kifrsClauseId: 'KIFRS1109-6.4.1-3-ga' | 'KIFRS1109-6.4.1-3-na' | 'KIFRS1109-6.4.1-3-da'
  subItems: SubItem[]
}

interface SubItem {
  icon: string
  text: string
  isNegative?: boolean
}

function ConditionRow({
  index,
  label,
  result,
  kifrsClauseId,
  subItems,
}: ConditionRowProps) {
  const [expanded, setExpanded] = useState(true)
  const isPass = result === 'PASS'

  return (
    <div className="px-6 py-4">
      {/* 조건 헤더 */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 flex-1 min-w-0">
          {/* PASS/FAIL 아이콘 */}
          <span className="flex-shrink-0 mt-0.5">
            {isPass ? (
              <PassIcon />
            ) : (
              <FailIcon />
            )}
          </span>

          {/* 조건 텍스트 */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span
                className={clsx(
                  'text-sm font-semibold',
                  isPass ? 'text-emerald-800' : 'text-red-800',
                )}
              >
                조건 {index}: {label}
              </span>
              <KifrsReference clauseId={kifrsClauseId} variant="badge" />
            </div>

            {/* 상세 설명 */}
            {expanded && (
              <div className="mt-2 space-y-1">
                {subItems.map((item, i) => (
                  <p
                    key={i}
                    className={clsx(
                      'text-xs flex items-start gap-1.5',
                      item.isNegative ? 'text-red-600' : 'text-slate-600',
                    )}
                  >
                    <span className="flex-shrink-0 mt-0.5">{item.icon}</span>
                    <span>{item.text}</span>
                  </p>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* 펼치기/접기 */}
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          aria-label={expanded ? '조건 상세 접기' : '조건 상세 펼치기'}
          className="flex-shrink-0 text-slate-400 hover:text-slate-600 transition-colors p-1"
        >
          <svg
            className={clsx('w-4 h-4 transition-transform', expanded ? 'rotate-180' : '')}
            viewBox="0 0 20 20"
            fill="currentColor"
            aria-hidden="true"
          >
            <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
          </svg>
        </button>
      </div>
    </div>
  )
}

// ─── 종합 PASS 결과 ───────────────────────────────────────────────────────────

function OverallPassContent({
  result,
  isSaved,
  onEditClick,
}: {
  result: HedgeDesignationResponse
  isSaved: boolean
  onEditClick: () => void
}) {
  return (
    <>
      <div className="flex items-center gap-3 mb-4">
        <span className="text-2xl" aria-hidden="true">🎯</span>
        <div>
          <p className="font-bold text-emerald-900 text-base">적격요건 완전 충족!</p>
          <p className="text-xs text-emerald-700 mt-0.5">
            K-IFRS 1109호 6.4.1 3가지 조건 모두 통과
          </p>
        </div>
      </div>

      {isSaved && result.documentationGenerated && (
        <div className="flex items-center gap-2 mb-4 px-3 py-2 bg-emerald-100 rounded-lg border border-emerald-200">
          <span className="text-base" aria-hidden="true">📋</span>
          <p className="text-sm text-emerald-800 font-medium">헤지 문서화 자동 생성됨</p>
          {result.hedgeRelationshipId && (
            <span className="ml-auto text-xs text-emerald-600 font-financial">
              {result.hedgeRelationshipId}
            </span>
          )}
        </div>
      )}

      {result.documentationSummary && isSaved && (
        <DocumentationSummaryBlock summary={result.documentationSummary} />
      )}

      <div className="flex gap-3 mt-4">
        <Button variant="secondary" size="sm" type="button" disabled>
          📄 PDF 보기
        </Button>
        <Button variant="ghost" size="sm" type="button" onClick={onEditClick}>
          ✏️ 수정
        </Button>
      </div>
    </>
  )
}

// ─── 종합 FAIL 결과 ───────────────────────────────────────────────────────────

function OverallFailContent({
  result,
  isSaved,
  onEditClick,
}: {
  result: HedgeDesignationResponse
  isSaved: boolean
  onEditClick: () => void
}) {
  return (
    <>
      <div className="flex items-center gap-3 mb-4">
        <span className="text-2xl" aria-hidden="true">⛔</span>
        <div>
          <p className="font-bold text-red-900 text-base">적격요건 미충족</p>
          <p className="text-xs text-red-700 mt-0.5">
            헤지회계 적용 불가 — 내용을 수정하거나 재지정이 필요합니다
          </p>
        </div>
      </div>

      {!isSaved && (
        <div className="flex items-center gap-2 mb-4 px-3 py-2 bg-amber-50 rounded-lg border border-amber-200">
          <span className="text-base" aria-hidden="true">⚠️</span>
          <p className="text-sm text-amber-800">
            적격요건 미충족으로 헤지관계가 <strong>저장되지 않았습니다.</strong>
          </p>
        </div>
      )}

      {/* 에러 상세 목록 */}
      {result.errors && result.errors.length > 0 && (
        <div className="mb-4 space-y-2">
          {result.errors.map((err) => (
            <div
              key={err.errorCode}
              className="px-3 py-2.5 bg-red-50 rounded-lg border border-red-200"
            >
              <div className="flex items-start gap-2">
                <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-bold bg-red-200 text-red-800 flex-shrink-0">
                  {err.errorCode}
                </span>
                <p className="text-xs text-red-700">{err.message}</p>
              </div>
              <p className="text-xs text-red-400 mt-1 ml-0">{err.kifrsReference}</p>
            </div>
          ))}
        </div>
      )}

      <Button variant="primary" size="sm" type="button" onClick={onEditClick}>
        ✏️ 수정하기
      </Button>
    </>
  )
}

// ─── 문서화 요약 블록 ─────────────────────────────────────────────────────────

function DocumentationSummaryBlock({
  summary,
}: {
  summary: HedgeDesignationResponse['documentationSummary']
}) {
  if (!summary) return null

  return (
    <div className="bg-white rounded-lg border border-emerald-200 px-4 py-3 mb-2">
      <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
        헤지 문서화 요약
      </p>
      <dl className="space-y-1">
        <DocRow label="헤지대상" value={summary.hedgedItem} />
        <DocRow label="헤지수단" value={summary.hedgingInstrument} />
        <DocRow label="회피위험" value={summary.hedgedRisk} />
        <DocRow label="유효성평가" value={summary.effectivenessAssessmentMethod} />
      </dl>
    </div>
  )
}

function DocRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-2 text-xs">
      <dt className="text-slate-400 flex-shrink-0 w-16">{label}</dt>
      <dd className="text-slate-700 font-financial">{value}</dd>
    </div>
  )
}

// ─── 아이콘 ───────────────────────────────────────────────────────────────────

function PassIcon() {
  return (
    <span
      aria-label="통과"
      className="w-6 h-6 rounded-full bg-emerald-100 flex items-center justify-center flex-shrink-0"
    >
      <svg className="w-4 h-4 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
      </svg>
    </span>
  )
}

function FailIcon() {
  return (
    <span
      aria-label="실패"
      className="w-6 h-6 rounded-full bg-red-100 flex items-center justify-center flex-shrink-0"
    >
      <svg className="w-4 h-4 text-red-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
      </svg>
    </span>
  )
}

// ─── SubItem 빌더 헬퍼 ────────────────────────────────────────────────────────

function buildEconomicRelationshipSubItems(c: EconomicRelationshipResult): SubItem[] {
  // Extended fields are optional — fallback to details string when backend sends only base fields
  if (c.underlyingMatch === undefined) {
    return [{ icon: '→', text: c.details, isNegative: c.result === 'FAIL' }]
  }

  const items: SubItem[] = []
  const isFail = c.result === 'FAIL'

  items.push(
    c.underlyingMatch
      ? { icon: '→', text: '기초변수(USD/KRW) 동일 확인' }
      : { icon: '→', text: '기초변수 불일치', isNegative: true },
  )

  const coverPct = ((c.notionalCoverageRatio ?? 0) * 100).toFixed(0)
  items.push(
    (c.notionalCoverageRatio ?? 0) >= 0.5 && (c.notionalCoverageRatio ?? 0) <= 2.0
      ? { icon: '→', text: `명목금액 ${coverPct}% 매칭 확인` }
      : { icon: '→', text: `명목금액 커버율 ${coverPct}% — 범위 이탈 (50%~200%)`, isNegative: true },
  )

  items.push(
    c.maturityMatch
      ? { icon: '→', text: '만기 일치 확인' }
      : { icon: '→', text: '만기 불일치', isNegative: true },
  )

  if (!isFail) {
    items.push({ icon: '→', text: '반대 방향 움직임 확인 (경제적 상쇄 관계)' })
  }

  return items
}

function buildCreditRiskSubItems(c: CreditRiskResult): SubItem[] {
  // Extended fields are optional — fallback to details string when backend sends only base fields
  if (c.hedgedItemCreditRating === undefined) {
    return [{ icon: '→', text: c.details, isNegative: c.result === 'FAIL' }]
  }

  const isItemInvestmentGrade = isInvestmentGrade(c.hedgedItemCreditRating)
  const isInstrumentInvestmentGrade = isInvestmentGrade(c.hedgingInstrumentCreditRating!)

  return [
    {
      icon: '→',
      text: `헤지대상 신용등급: ${c.hedgedItemCreditRating} (${isItemInvestmentGrade ? '투자등급' : '비투자등급'})`,
      isNegative: !isItemInvestmentGrade,
    },
    {
      icon: '→',
      text: `헤지수단 거래상대방: ${c.hedgingInstrumentCreditRating} (${isInstrumentInvestmentGrade ? '투자등급' : '비투자등급'})`,
      isNegative: !isInstrumentInvestmentGrade,
    },
    c.creditRiskDominant
      ? { icon: '→', text: '신용위험 지배적 — 헤지관계 가치 변동에 지배적 영향', isNegative: true }
      : { icon: '→', text: '양측 모두 투자등급 — 신용위험 지배적 아님' },
  ]
}

function buildHedgeRatioSubItems(c: HedgeRatioResult, hedgeRatioValue?: number): SubItem[] {
  const items: SubItem[] = []

  // Show ratio from top-level hedgeRatioValue if available
  if (hedgeRatioValue !== undefined) {
    const pct = (hedgeRatioValue * 100).toFixed(1)
    items.push({ icon: '→', text: `헤지비율 = ${pct}%` })
    items.push({ icon: '→', text: '허용 범위: 80% ~ 125%' })
  }

  // Extended fields are optional — use details as fallback
  if (c.withinAcceptableRange !== undefined) {
    items.push(
      c.withinAcceptableRange
        ? { icon: '→', text: 'K-IFRS 6.4.1(c) 충족 — 비율 적절' }
        : { icon: '→', text: '허용 범위 이탈 — 재조정(Rebalancing) 권고 (K-IFRS 6.5.5)', isNegative: true },
    )
  } else {
    items.push({ icon: '→', text: c.details, isNegative: c.result === 'FAIL' })
  }

  return items
}

// ─── 신용등급 헬퍼 ────────────────────────────────────────────────────────────

/** 투자등급 여부 판단 (BBB 이상) */
function isInvestmentGrade(rating: string): boolean {
  const investmentGrades = ['AAA', 'AA', 'A', 'BBB']
  return investmentGrades.includes(rating)
}
