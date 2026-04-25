import React, { useState } from 'react'
import clsx from 'clsx'
import { KifrsReference } from '@/components/financial/KifrsReference'
import { Button } from '@/components/ui/Button'
import type { HedgeDesignationResponse, ConditionResultType } from '@/types/hedge'
import { formatDate } from '@/utils/formatters'
import type { FormValues } from './HedgeDesignationForm'

// ─── 타입 ─────────────────────────────────────────────────────────────────────

type PreStatus = 'idle' | 'partial' | 'ready' | 'warn'

interface PreCheck {
  documentation: PreStatus
  economicRel: PreStatus
  economicDetails: string[]
  creditRisk: PreStatus
  creditDetails: string[]
  hedgeRatio: PreStatus
  hedgeRatioDetails: string[]
  anyInput: boolean
  allReady: boolean
  anyWarn: boolean
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface LiveEligibilityPanelProps {
  /** react-hook-form에서 watch로 전달받은 실시간 폼 값 */
  liveValues: Partial<FormValues> | null
  /** 서버 검증 완료 후 결과 */
  serverResult: { response: HedgeDesignationResponse; isSaved: boolean } | null
  systemError: string | null
  isLoading: boolean
  onEditClick: () => void
}

// ─── 클라이언트 사전 검증 ────────────────────────────────────────────────────

const INVESTMENT_GRADES = ['AAA', 'AA', 'A', 'BBB'] as const

function computePreCheck(values: Partial<FormValues>): PreCheck {
  // ── 문서화 의무 ─────────────────────────────────────────────
  const hasObjective = (values.riskManagementObjective?.trim().length ?? 0) >= 5
  const hasStrategy  = (values.hedgeStrategy?.trim().length ?? 0) >= 5
  const documentation: PreStatus =
    !hasObjective && !hasStrategy ? 'idle' :
    hasObjective && hasStrategy   ? 'ready' : 'partial'

  // ── 조건 1: 경제적 관계 ─────────────────────────────────────
  const hasFxId      = !!values.fxForwardContractId?.trim()
  const hasCurrency  = !!values.currency?.trim()
  const hasDesgDate  = !!values.designationDate
  const hasPeriodEnd = !!values.hedgePeriodEnd
  const hasMaturity  = !!values.maturityDate

  const economicDetails: string[] = []
  if (hasFxId)      economicDetails.push(`헤지수단 계약 ID: ${values.fxForwardContractId}`)
  else              economicDetails.push('헤지수단 계약 ID 미입력')
  if (hasCurrency)  economicDetails.push(`통화: ${values.currency}`)
  else              economicDetails.push('통화 미입력')
  if (hasDesgDate && hasPeriodEnd && hasMaturity)
                    economicDetails.push('지정일 · 만기 · 회피기간 입력 완료')
  else              economicDetails.push('날짜 정보 미완료 (지정일/만기/위험회피기간)')

  const economicRel: PreStatus =
    !hasFxId && !hasCurrency ? 'idle' :
    hasFxId && hasCurrency && hasDesgDate && hasPeriodEnd && hasMaturity ? 'ready' : 'partial'

  // ── 조건 2: 신용위험 ────────────────────────────────────────
  const rating = values.counterpartyCreditRating
  const isInvestGrade = rating ? (INVESTMENT_GRADES as readonly string[]).includes(rating) : false
  const creditDetails: string[] = []
  if (rating) {
    creditDetails.push(
      `거래상대방 신용등급: ${rating} — ${isInvestGrade ? '투자등급 (Investment Grade)' : '비투자등급 ⚠️'}`
    )
    creditDetails.push('선택한 신용등급 값이 조건 2(신용위험 지배적 아님) 판단에 직접 반영됩니다.')
    if (isInvestGrade) creditDetails.push('K-IFRS B6.4.7 신용위험 지배적 아님 충족 가능')
    else               creditDetails.push('BB 이하: 신용위험 지배적 가능성 있음 → 재검토 필요')
  } else {
    creditDetails.push('거래상대방 신용등급 미선택')
    creditDetails.push('신용등급을 선택해야 조건 2 사전 판단을 명확히 확인할 수 있습니다.')
  }
  const creditRisk: PreStatus =
    !rating ? 'idle' : isInvestGrade ? 'ready' : 'warn'

  // ── 조건 3: 헤지비율 ────────────────────────────────────────
  const ratio = values.hedgeRatio ?? 0
  const ratioPct = ratio * 100
  const inRange = ratio >= 0.80 && ratio <= 1.25
  const hedgeRatioDetails: string[] = []
  if (ratio > 0) {
    hedgeRatioDetails.push(`헤지비율 = ${ratioPct.toFixed(1)}%`)
    hedgeRatioDetails.push('허용 범위: 80% ~ 125%')
    if (inRange)   hedgeRatioDetails.push('범위 내 — K-IFRS 6.4.1(c)(iii) 충족 가능')
    else           hedgeRatioDetails.push('범위 이탈 — 재조정(Rebalancing) 필요 (K-IFRS 6.5.5)')
  } else {
    hedgeRatioDetails.push('헤지비율 미입력 (권장: 1.00 = 100%)')
  }
  const hedgeRatio: PreStatus =
    ratio === 0 ? 'idle' : inRange ? 'ready' : 'warn'

  // ── 종합 ────────────────────────────────────────────────────
  const allReady = documentation === 'ready' && economicRel === 'ready' && creditRisk === 'ready' && hedgeRatio === 'ready'
  const anyWarn  = creditRisk === 'warn' || hedgeRatio === 'warn'
  const anyInput = documentation !== 'idle' || economicRel !== 'idle' || creditRisk !== 'idle' || hedgeRatio !== 'idle'

  return { documentation, economicRel, economicDetails, creditRisk, creditDetails, hedgeRatio, hedgeRatioDetails, allReady, anyWarn, anyInput }
}

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

/**
 * 헤지 지정 화면 우측 고정 패널 — 입력 즉시 K-IFRS 적격요건 실시간 검토
 *
 * 모드 1 (pre-check): 서버 검증 전 — 폼 입력 기반 클라이언트 사전 검토
 * 모드 2 (server-result): 서버 응답 후 — K-IFRS 3조건 최종 결과 표시
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용조건)
 */
export function LiveEligibilityPanel({
  liveValues,
  serverResult,
  systemError,
  isLoading,
  onEditClick,
}: LiveEligibilityPanelProps) {
  const preCheck = liveValues ? computePreCheck(liveValues) : null
  const hasResult = !!serverResult

  return (
    <div className="bg-white border-2 border-blue-900 rounded-xl overflow-hidden shadow-md">
      {/* ── 패널 헤더 ───────────────────────────────────────────── */}
      <PanelHeader preCheck={preCheck} serverResult={serverResult} isLoading={isLoading} />

      <div className="divide-y divide-slate-100">
        {/* ── 서버 결과 없을 때: 클라이언트 사전 검토 ─────────────── */}
        {!hasResult && (
          <>
            <DocumentationRow preCheck={preCheck} />
            <PreConditionRow
              index={1}
              label="경제적 관계 존재"
              description="헤지수단과 헤지대상의 가치 변동이 반대 방향으로 움직이는가"
              kifrsRef="KIFRS1109-6.4.1-3-ga"
              status={preCheck?.economicRel ?? 'idle'}
              details={preCheck?.economicDetails ?? ['폼을 입력하면 실시간으로 검토됩니다.']}
            />
            <PreConditionRow
              index={2}
              label="신용위험 지배적 아님"
              description="신용위험이 헤지관계의 가치 변동에 지배적 영향을 미치지 않는가"
              kifrsRef="KIFRS1109-6.4.1-3-na"
              status={preCheck?.creditRisk ?? 'idle'}
              details={preCheck?.creditDetails ?? ['거래상대방 신용등급을 선택하세요.']}
            />
            <PreConditionRow
              index={3}
              label="헤지비율 적절"
              description="헤지비율이 실제 위험회피 수량 비율과 동일한가 (권장: 80%~125%)"
              kifrsRef="KIFRS1109-6.4.1-3-da"
              status={preCheck?.hedgeRatio ?? 'idle'}
              details={preCheck?.hedgeRatioDetails ?? ['헤지비율을 입력하세요 (예: 1.00 = 100%).']  }
            />
          </>
        )}

        {/* ── 서버 결과 있을 때: 최종 검증 결과 ───────────────────── */}
        {hasResult && serverResult && (
          <ServerResultSection result={serverResult.response} isSaved={serverResult.isSaved} />
        )}
      </div>

      {/* ── 시스템 에러 ─────────────────────────────────────────── */}
      {systemError && (
        <div className="px-5 py-3.5 bg-red-50 border-t border-red-200">
          <div className="flex items-start gap-2">
            <svg className="w-4 h-4 text-red-500 mt-0.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <div>
              <p className="text-xs font-semibold text-red-800">저장 중 오류가 발생했습니다</p>
              <p className="text-xs text-red-600 mt-0.5">{systemError}</p>
              <p className="text-xs text-red-500 mt-1.5">
                입력 값을 다시 확인하고 재시도해 주세요. 문제가 지속되면 서버(포트 8090) 상태를 확인하세요.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ── 액션 버튼 영역 ──────────────────────────────────────── */}
      <ActionButtons
        isLoading={isLoading}
        isSaved={serverResult?.isSaved ?? false}
        hasResult={hasResult}
        onEditClick={onEditClick}
      />
    </div>
  )
}

// ─── 패널 헤더 ────────────────────────────────────────────────────────────────

function PanelHeader({
  preCheck,
  serverResult,
  isLoading,
}: {
  preCheck: PreCheck | null
  serverResult: LiveEligibilityPanelProps['serverResult']
  isLoading: boolean
}) {
  if (isLoading) {
    return (
      <div className="bg-blue-900 px-5 py-4 flex items-center gap-3">
        <svg className="animate-spin h-5 w-5 text-white flex-shrink-0" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
        </svg>
        <div>
          <p className="text-white font-bold text-sm">K-IFRS 6.4.1 적격요건 검증 중...</p>
          <p className="text-blue-200 text-xs mt-0.5">서버에서 3가지 조건을 검토하고 있습니다</p>
        </div>
      </div>
    )
  }

  if (serverResult) {
    const isPass = serverResult.response.eligibilityCheckResult.overallResult === 'PASS'
    return (
      <div className={clsx('px-5 py-4 flex items-center justify-between', isPass ? 'bg-emerald-800' : 'bg-red-800')}>
        <div className="flex items-center gap-3">
          <StatusDot size="lg" status={isPass ? 'confirmed_pass' : 'confirmed_fail'} />
          <div>
            <p className="text-white font-bold text-sm">
              {isPass ? '적격요건 충족 — 헤지회계 적용 가능' : '적격요건 미충족 — 수정 필요'}
            </p>
            <p className="text-white/70 text-xs mt-0.5">
              K-IFRS 1109호 6.4.1 최종 검증 완료
            </p>
          </div>
        </div>
        <KifrsReference clauseId="KIFRS1109-6.4.1" variant="badge" />
      </div>
    )
  }

  // 입력 전 / 입력 중
  const overallStatus = preCheck?.allReady ? 'ready' :
                        preCheck?.anyWarn  ? 'warn'  :
                        preCheck?.anyInput ? 'partial' : 'idle'

  const headerMeta: Record<string, { bg: string; title: string; sub: string }> = {
    idle:    { bg: 'bg-blue-900',   title: 'K-IFRS 6.4.1 적격요건 실시간 검토',     sub: '항목을 입력하면 즉시 검토 결과가 표시됩니다' },
    partial: { bg: 'bg-blue-900',   title: 'K-IFRS 6.4.1 적격요건 검토 중',        sub: '계속 입력하면 실시간으로 반영됩니다' },
    ready:   { bg: 'bg-blue-800',   title: '입력 완료 — 최종 검증 준비됨',           sub: '아래 [저장 및 검증] 버튼으로 K-IFRS 확정 검증' },
    warn:    { bg: 'bg-amber-700',  title: '일부 조건 주의 — 입력값을 확인하세요',   sub: '빨간 항목을 수정 후 검증하세요' },
  }
  const meta = headerMeta[overallStatus]

  return (
    <div className={clsx('px-5 py-4 flex items-center justify-between', meta.bg)}>
      <div className="flex items-center gap-3">
        <StatusDot size="lg" status={overallStatus} />
        <div>
          <p className="text-white font-bold text-sm">{meta.title}</p>
          <p className="text-white/70 text-xs mt-0.5">{meta.sub}</p>
        </div>
      </div>
      <KifrsReference clauseId="KIFRS1109-6.4.1" variant="badge" />
    </div>
  )
}

// ─── 문서화 의무 행 ────────────────────────────────────────────────────────────

function DocumentationRow({ preCheck }: { preCheck: PreCheck | null }) {
  const status = preCheck?.documentation ?? 'idle'
  const details =
    status === 'idle'    ? ['위험관리 목적 및 헤지 전략을 입력하세요 (각 5자 이상)'] :
    status === 'partial' ? ['위험관리 목적 또는 헤지 전략 중 하나가 미완료입니다'] :
                           ['위험관리 목적 · 헤지 전략 모두 입력 완료']

  return (
    <div className="px-5 py-3.5 bg-slate-50">
      <div className="flex items-start gap-2.5">
        <StatusDot size="sm" status={status} className="mt-0.5 flex-shrink-0" />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-xs font-semibold text-slate-600 uppercase tracking-wide">
              문서화 의무
            </span>
            <span className="text-xs text-slate-400">K-IFRS 6.4.1(나) — 지정 및 문서화</span>
          </div>
          {details.map((d, i) => (
            <p key={i} className={clsx('text-xs mt-1', status === 'ready' ? 'text-emerald-700' : 'text-slate-500')}>
              {status === 'ready' ? '✓' : '→'} {d}
            </p>
          ))}
        </div>
      </div>
    </div>
  )
}

// ─── 사전 검토 조건 행 ────────────────────────────────────────────────────────

interface PreConditionRowProps {
  index: number
  label: string
  description: string
  kifrsRef: 'KIFRS1109-6.4.1-3-ga' | 'KIFRS1109-6.4.1-3-na' | 'KIFRS1109-6.4.1-3-da'
  status: PreStatus
  details: string[]
}

function PreConditionRow({ index, label, description, kifrsRef, status, details }: PreConditionRowProps) {
  const [expanded, setExpanded] = useState(true)

  const statusMeta: Record<PreStatus, { label: string; labelClass: string; textClass: string; icon: string }> = {
    idle:    { label: '입력 대기',  labelClass: 'bg-slate-100 text-slate-500',   textClass: 'text-slate-400', icon: '○' },
    partial: { label: '입력 중',   labelClass: 'bg-blue-100 text-blue-700',    textClass: 'text-slate-500', icon: '◐' },
    ready:   { label: '적격 가능', labelClass: 'bg-emerald-100 text-emerald-700', textClass: 'text-emerald-700', icon: '✓' },
    warn:    { label: '주의',      labelClass: 'bg-amber-100 text-amber-700',  textClass: 'text-amber-700', icon: '⚠' },
  }
  const meta = statusMeta[status]

  return (
    <div className={clsx(
      'px-5 py-3.5 transition-colors duration-200',
      status === 'ready' ? 'bg-emerald-50/40' : status === 'warn' ? 'bg-amber-50/40' : 'bg-white',
    )}>
      {/* 조건 헤더 */}
      <div className="flex items-start gap-2.5">
        <StatusDot size="sm" status={status} className="mt-0.5 flex-shrink-0" />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className={clsx('text-xs font-bold', meta.textClass)}>
              조건 {index}: {label}
            </span>
            <span className={clsx('inline-flex items-center px-1.5 py-0.5 rounded text-xs font-semibold', meta.labelClass)}>
              {meta.icon} {meta.label}
            </span>
            <KifrsReference clauseId={kifrsRef} variant="badge" />
          </div>
          <p className="text-xs text-slate-400 mt-0.5">{description}</p>
        </div>
        <button
          type="button"
          onClick={() => setExpanded(v => !v)}
          aria-label={expanded ? '접기' : '펼치기'}
          className="flex-shrink-0 text-slate-300 hover:text-slate-500 transition-colors p-1"
        >
          <svg className={clsx('w-3.5 h-3.5 transition-transform', expanded ? 'rotate-180' : '')} viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
          </svg>
        </button>
      </div>

      {/* 상세 내용 */}
      {expanded && (
        <div className="mt-2 pl-7 space-y-1">
          {details.map((d, i) => (
            <p key={i} className={clsx('text-xs flex items-start gap-1', meta.textClass)}>
              <span className="flex-shrink-0">→</span>
              <span>{d}</span>
            </p>
          ))}
        </div>
      )}
    </div>
  )
}

// ─── 서버 결과 섹션 ───────────────────────────────────────────────────────────

function ServerResultSection({ result, isSaved }: { result: HedgeDesignationResponse; isSaved: boolean }) {
  const check = result.eligibilityCheckResult
  const isPass = check.overallResult === 'PASS'

  const conditions = [
    {
      index: 1,
      label: '경제적 관계 존재',
      result: check.condition1EconomicRelationship.result,
      details: check.condition1EconomicRelationship.details,
      kifrsRef: 'KIFRS1109-6.4.1-3-ga' as const,
    },
    {
      index: 2,
      label: '신용위험 지배적 아님',
      result: check.condition2CreditRisk.result,
      details: check.condition2CreditRisk.details,
      kifrsRef: 'KIFRS1109-6.4.1-3-na' as const,
    },
    {
      index: 3,
      label: `헤지비율 적절 (${((check.hedgeRatioValue ?? 0) * 100).toFixed(0)}%)`,
      result: check.condition3HedgeRatio.result,
      details: check.condition3HedgeRatio.details,
      kifrsRef: 'KIFRS1109-6.4.1-3-da' as const,
    },
  ]

  return (
    <>
      {conditions.map((c) => (
        <ServerConditionRow key={c.index} {...c} />
      ))}

      {/* 종합 결과 */}
      <div className={clsx('px-5 py-4', isPass ? 'bg-emerald-50' : 'bg-red-50')}>
        {isPass ? (
          <PassSummary result={result} isSaved={isSaved} />
        ) : (
          <FailSummary result={result} />
        )}
      </div>
    </>
  )
}

function ServerConditionRow({
  index,
  label,
  result,
  details,
  kifrsRef,
}: {
  index: number
  label: string
  result: ConditionResultType
  details: string
  kifrsRef: 'KIFRS1109-6.4.1-3-ga' | 'KIFRS1109-6.4.1-3-na' | 'KIFRS1109-6.4.1-3-da'
}) {
  const isPass = result === 'PASS'
  return (
    <div className={clsx('px-5 py-3.5', isPass ? 'bg-emerald-50/50' : 'bg-red-50/50')}>
      <div className="flex items-start gap-2.5">
        <ServerStatusIcon pass={isPass} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className={clsx('text-xs font-bold', isPass ? 'text-emerald-800' : 'text-red-800')}>
              조건 {index}: {label}
            </span>
            <KifrsReference clauseId={kifrsRef} variant="badge" />
          </div>
          <p className={clsx('text-xs mt-1', isPass ? 'text-emerald-700' : 'text-red-600')}>
            → {details}
          </p>
        </div>
      </div>
    </div>
  )
}

function PassSummary({ result, isSaved }: { result: HedgeDesignationResponse; isSaved: boolean }) {
  return (
    <div className="space-y-2">
      <div>
        <p className="text-sm font-bold text-emerald-900">K-IFRS 1109호 6.4.1 3조건 모두 통과</p>
        <p className="text-xs text-emerald-700 mt-0.5">검증일: {formatDate(result.eligibilityCheckResult.checkedAt.substring(0, 10))}</p>
      </div>
      {isSaved && result.documentationGenerated && (
        <div className="flex items-center gap-2 px-3 py-2 bg-emerald-100 rounded-lg border border-emerald-200">
          <svg className="w-4 h-4 text-emerald-700 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clipRule="evenodd" />
          </svg>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-semibold text-emerald-800">헤지 문서화 자동 생성 완료</p>
            {result.hedgeRelationshipId && (
              <p className="text-xs text-emerald-600 font-financial truncate">{result.hedgeRelationshipId}</p>
            )}
          </div>
        </div>
      )}
      {result.documentationSummary && isSaved && (
        <div className="bg-white rounded-lg border border-emerald-200 px-3 py-2.5">
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">문서화 요약</p>
          <dl className="space-y-1">
            {([
              ['헤지대상',   result.documentationSummary.hedgedItem],
              ['헤지수단',   result.documentationSummary.hedgingInstrument],
              ['회피위험',   result.documentationSummary.hedgedRisk],
              ['유효성평가', result.documentationSummary.effectivenessAssessmentMethod],
            ] as [string, string][]).map(([label, value]) => (
              <div key={label} className="flex gap-2 text-xs">
                <dt className="text-slate-400 flex-shrink-0 w-14">{label}</dt>
                <dd className="text-slate-700 font-financial text-xs">{value}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </div>
  )
}

function FailSummary({ result }: { result: HedgeDesignationResponse }) {
  return (
    <div className="space-y-2">
      <div>
        <p className="text-sm font-bold text-red-900">헤지회계 적용 불가</p>
        <p className="text-xs text-red-700 mt-0.5">아래 항목을 수정 후 다시 검증하세요.</p>
      </div>
      {result.errors && result.errors.length > 0 && (
        <div className="space-y-1.5">
          {result.errors.map((err) => (
            <div key={err.errorCode} className="px-3 py-2 bg-red-100 rounded-lg border border-red-200">
              <div className="flex items-start gap-1.5">
                <span className="text-xs font-bold bg-red-200 text-red-800 px-1.5 py-0.5 rounded flex-shrink-0">
                  {err.errorCode}
                </span>
                <p className="text-xs text-red-700">{err.message}</p>
              </div>
              {err.kifrsReference && (
                <p className="text-xs text-red-400 mt-1">{err.kifrsReference}</p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ─── 액션 버튼 영역 ───────────────────────────────────────────────────────────

function ActionButtons({
  isLoading,
  isSaved,
  hasResult,
  onEditClick,
}: {
  isLoading: boolean
  isSaved: boolean
  hasResult: boolean
  onEditClick: () => void
}) {
  return (
    <div className="px-5 py-4 bg-slate-50 border-t border-slate-200 space-y-2">
      {!hasResult ? (
        /* 검증 전: 저장 버튼 */
        <Button
          form="hedge-designation-form"
          type="submit"
          size="lg"
          loading={isLoading}
          className="w-full justify-center"
        >
          <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
          </svg>
          저장 및 적격요건 검증
        </Button>
      ) : (
        /* 검증 후: 수정 버튼 + PDF 안내 */
        <>
          <Button
            variant="secondary"
            size="md"
            type="button"
            onClick={onEditClick}
            className="w-full justify-center"
          >
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
            </svg>
            내용 수정
          </Button>

          {/* 문서화 상태 안내 — 저장 성공 시 */}
          {isSaved && (
            <div className="flex items-start gap-2.5 px-3 py-2.5 bg-white rounded-lg border border-slate-200">
              <svg
                className="w-4 h-4 text-slate-400 flex-shrink-0 mt-0.5"
                viewBox="0 0 20 20"
                fill="currentColor"
                aria-hidden="true"
              >
                <path
                  fillRule="evenodd"
                  d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z"
                  clipRule="evenodd"
                />
              </svg>
              <div>
                <p className="text-xs font-semibold text-slate-700">헤지 문서화 서버 저장 완료</p>
                <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
                  PDF 파일 다운로드는 본개발 단계에서 구현 예정입니다.
                </p>
              </div>
            </div>
          )}
        </>
      )}

      <p className="text-center text-xs text-slate-400 pt-1">
        K-IFRS 1109호 6.4.1 자동 검증 · 헤지 문서화 자동 생성
      </p>
      <p className="text-center text-xs text-slate-300">
        1단계: FX Forward (USD/KRW) 중심 · IRS · CRS는 2단계 예정
      </p>
    </div>
  )
}

// ─── 상태 아이콘 ──────────────────────────────────────────────────────────────

type AnyStatus = PreStatus | 'confirmed_pass' | 'confirmed_fail'

function StatusDot({ status, size = 'sm', className }: { status: AnyStatus; size?: 'sm' | 'lg'; className?: string }) {
  const sizeClass = size === 'lg' ? 'w-7 h-7' : 'w-5 h-5'
  const iconClass = size === 'lg' ? 'w-4 h-4' : 'w-3 h-3'

  const config: Record<AnyStatus, { bg: string; content: React.ReactElement }> = {
    idle: {
      bg: 'bg-slate-200',
      content: <span className="w-2 h-2 rounded-full bg-slate-400" />,
    },
    partial: {
      bg: 'bg-blue-100',
      content: (
        <svg className={clsx(iconClass, 'text-blue-600')} viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" />
        </svg>
      ),
    },
    ready: {
      bg: 'bg-emerald-100',
      content: (
        <svg className={clsx(iconClass, 'text-emerald-600')} viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
        </svg>
      ),
    },
    warn: {
      bg: 'bg-amber-100',
      content: (
        <svg className={clsx(iconClass, 'text-amber-600')} viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
        </svg>
      ),
    },
    confirmed_pass: {
      bg: 'bg-emerald-500',
      content: (
        <svg className={clsx(iconClass, 'text-white')} viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
        </svg>
      ),
    },
    confirmed_fail: {
      bg: 'bg-red-500',
      content: (
        <svg className={clsx(iconClass, 'text-white')} viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
        </svg>
      ),
    },
  }

  const { bg, content } = config[status]
  return (
    <span className={clsx('rounded-full flex items-center justify-center flex-shrink-0', sizeClass, bg, className)}>
      {content}
    </span>
  )
}

function ServerStatusIcon({ pass }: { pass: boolean }) {
  return (
    <span
      aria-label={pass ? '통과' : '실패'}
      className={clsx(
        'w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5',
        pass ? 'bg-emerald-100' : 'bg-red-100',
      )}
    >
      {pass ? (
        <svg className="w-3 h-3 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
        </svg>
      ) : (
        <svg className="w-3 h-3 text-red-500" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
        </svg>
      )}
    </span>
  )
}
