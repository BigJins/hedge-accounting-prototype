import type { ReactNode } from 'react'
import clsx from 'clsx'
import { Card } from '@/components/ui/Card'
import type { EffectivenessTestResponse } from '@/types/effectiveness'
import { formatDate, formatKrw } from '@/utils/formatters'

// ─── Props ────────────────────────────────────────────────────────────────────

interface EffectivenessTestResultProps {
  result: EffectivenessTestResponse
}

// ─── 배지 매핑 ────────────────────────────────────────────────────────────────

const RESULT_BADGE: Record<string, { label: string; className: string; dotClass: string }> = {
  PASS:    {
    label: 'PASS',
    className: 'bg-emerald-100 text-emerald-800 border border-emerald-300',
    dotClass: 'bg-emerald-500',
  },
  WARNING: {
    label: 'WARNING',
    className: 'bg-amber-100 text-amber-800 border border-amber-300',
    dotClass: 'bg-amber-500',
  },
  FAIL:    {
    label: 'FAIL',
    className: 'bg-red-100 text-red-700 border border-red-300',
    dotClass: 'bg-red-500',
  },
}

const ACTION_BADGE: Record<string, { label: string; className: string }> = {
  NONE:        { label: '조치 불필요',       className: 'bg-slate-100 text-slate-600' },
  REBALANCE:   { label: '헤지비율 재조정',   className: 'bg-amber-100 text-amber-800' },
  DISCONTINUE: { label: '위험회피관계 중단', className: 'bg-red-100 text-red-700' },
}

const TEST_TYPE_LABEL: Record<string, string> = {
  DOLLAR_OFFSET_PERIODIC:   '기간별 Dollar-offset',
  DOLLAR_OFFSET_CUMULATIVE: '누적 Dollar-offset',
}

// ─── 조치 권고 메시지 ─────────────────────────────────────────────────────────

interface ActionGuide {
  title: string
  message: string
  detail: string
  reference: string
  containerClass: string
  titleClass: string
  messageClass: string
  referenceClass: string
  iconPath: string
}

const ACTION_GUIDE: Record<string, ActionGuide> = {
  NONE: {
    title: '헤지관계 계속 유지',
    message:
      '유효성 테스트를 통과하였습니다. 위험회피관계를 계속 유지하고 다음 보고기간 말 재테스트를 수행하세요.',
    detail:
      '비효과적 부분은 당기손익(P&L)으로 즉시 인식하고, 유효 부분은 현금흐름위험회피적립금(OCI)에 계상합니다.',
    reference: 'K-IFRS 1109호 B6.4.12 — 매 보고기간 말 유효성 평가 의무',
    containerClass: 'bg-emerald-50 border-emerald-200',
    titleClass: 'text-emerald-800',
    messageClass: 'text-emerald-700',
    referenceClass: 'text-emerald-500',
    iconPath:
      'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
  },
  REBALANCE: {
    title: '헤지비율 재조정 (Rebalancing) 검토 필요',
    message:
      '위험관리 목적은 유지되지만 Dollar-offset 비율이 참고 범위를 벗어났습니다. 헤지비율 재조정으로 위험회피관계를 계속 유지할 수 있습니다.',
    detail:
      '재조정은 헤지 중단이 아닌 위험회피관계의 지속입니다. IFRS 9는 원칙 기반 평가이므로 경제적 관계가 유지되는 한 재조정을 통해 헤지를 계속하세요.',
    reference: 'K-IFRS 1109호 6.5.5 (재조정) · BC6.234 (정량기준 폐지 — 원칙 기반 전환)',
    containerClass: 'bg-amber-50 border-amber-200',
    titleClass: 'text-amber-800',
    messageClass: 'text-amber-700',
    referenceClass: 'text-amber-500',
    iconPath:
      'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z',
  },
  DISCONTINUE: {
    title: '위험회피관계 중단 (Discontinuation) 필요',
    message:
      '헤지수단과 피헤지항목이 동방향으로 움직이거나 경제적 관계가 성립하지 않습니다. 위험회피관계를 전진적으로 중단하세요.',
    detail:
      '중단 시점까지 인식된 OCI 잔액(현금흐름위험회피적립금)은 피헤지항목이 P&L에 영향을 미칠 때까지 OCI에 유지하거나, 즉시 P&L로 재분류합니다.',
    reference: 'K-IFRS 1109호 6.5.6 (위험회피관계 중단) · 6.5.12 (OCI 재분류)',
    containerClass: 'bg-red-50 border-red-200',
    titleClass: 'text-red-800',
    messageClass: 'text-red-700',
    referenceClass: 'text-red-500',
    iconPath:
      'M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z',
  },
}

// ─── Dollar-offset 게이지 컴포넌트 ────────────────────────────────────────────

/**
 * Dollar-offset 유효성 비율 게이지.
 *
 * 백엔드에서 음수(-0.982 등) 또는 양수로 오는 effectivenessRatio를
 * 절대값으로 변환하여 0~150% 범위의 게이지로 표시합니다.
 * 음수 = 반대방향(정상 헤지), 양수 = 동방향(비정상).
 *
 * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 참고 지표)
 * @see K-IFRS 1109호 BC6.234 (80~125% 정량기준은 참고 범위, 자동 FAIL 기준 아님)
 */
function DollarOffsetGauge({ ratio, testResult }: { ratio: number; testResult: string }) {
  // 백엔드 비율: 음수 = 반대방향(정상). 절대값으로 시각화.
  const absRatio = Math.abs(ratio)
  const isOpposite = ratio < 0  // 음수 = 반대방향 = 정상

  // 표시할 퍼센트 (소수→퍼센트 변환)
  const displayPct = (absRatio * 100).toFixed(1)

  // 게이지 최대 기준: 150%로 설정 (범위 벗어난 케이스도 표시)
  const GAUGE_MAX = 150
  const clampedPct = Math.min(absRatio * 100, GAUGE_MAX)
  const fillPercent = (clampedPct / GAUGE_MAX) * 100

  // 범위 경계선 위치 (80%, 125%)
  const line80  = (80 / GAUGE_MAX) * 100
  const line125 = (125 / GAUGE_MAX) * 100

  // 게이지 색상
  const fillColor =
    testResult === 'PASS'    ? '#059669' :  // emerald-600
    testResult === 'WARNING' ? '#d97706' :  // amber-600
                               '#dc2626'    // red-600

  return (
    <div>
      {/* 비율 수치 */}
      <div className="flex items-baseline justify-between mb-2">
        <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
          Dollar-offset 비율
        </span>
        <div className="text-right">
          <span
            className={clsx(
              'font-financial text-2xl font-bold tabular-nums',
              testResult === 'PASS'    && 'text-emerald-700',
              testResult === 'WARNING' && 'text-amber-700',
              testResult === 'FAIL'    && 'text-red-600',
            )}
          >
            {displayPct}%
          </span>
          <span className={clsx(
            'ml-2 text-xs font-medium',
            isOpposite ? 'text-emerald-600' : 'text-red-600',
          )}>
            {isOpposite ? '반대방향 (정상)' : '동방향 (비정상)'}
          </span>
        </div>
      </div>

      {/* 게이지 바 */}
      <div className="relative h-6 bg-slate-100 rounded-full overflow-visible">
        {/* 참고 범위 표시 (80%~125%) */}
        <div
          className="absolute top-0 bottom-0 bg-emerald-100 rounded-sm opacity-60"
          style={{
            left:  `${line80}%`,
            width: `${line125 - line80}%`,
          }}
          aria-hidden="true"
        />

        {/* 채워진 바 */}
        <div
          className="absolute top-0 left-0 h-full rounded-full transition-all duration-500"
          style={{ width: `${fillPercent}%`, backgroundColor: fillColor }}
          role="progressbar"
          aria-valuenow={parseFloat(displayPct)}
          aria-valuemin={0}
          aria-valuemax={GAUGE_MAX}
          aria-label={`Dollar-offset 비율 ${displayPct}%`}
        />

        {/* 80% 경계선 */}
        <div
          className="absolute top-0 bottom-0 w-0.5 bg-slate-400"
          style={{ left: `${line80}%` }}
          aria-hidden="true"
        />
        {/* 125% 경계선 */}
        <div
          className="absolute top-0 bottom-0 w-0.5 bg-slate-400"
          style={{ left: `${line125}%` }}
          aria-hidden="true"
        />
      </div>

      {/* 범위 레이블 */}
      <div className="flex justify-between items-center mt-1.5 text-xs text-slate-400">
        <span>0%</span>
        <span
          className="flex flex-col items-center gap-0.5"
          style={{ marginLeft: `${line80 - 3}%` }}
        >
          <span className="font-semibold text-slate-500">80%</span>
        </span>
        <span className="flex-1 text-center text-emerald-600 font-medium">
          IFRS 9 참고 범위 (80%~125%)
        </span>
        <span
          className="flex flex-col items-center gap-0.5"
          style={{ marginRight: `${100 - line125 - 3}%` }}
        >
          <span className="font-semibold text-slate-500">125%</span>
        </span>
        <span>{GAUGE_MAX}%</span>
      </div>

      {/* 참고 안내 */}
      <p className="text-xs text-slate-400 mt-2">
        * BC6.234 — IFRS 9는 정량 기준(80%~125%)을 폐지하고 원칙 기반으로 전환했습니다. 이 수치는 참고 지표로만 활용하세요.
      </p>
    </div>
  )
}

// ─── 조치 권고 패널 ───────────────────────────────────────────────────────────

function ActionGuidePanel({ actionRequired }: { actionRequired: string }) {
  const guide = ACTION_GUIDE[actionRequired]
  if (!guide) return null

  return (
    <div
      className={clsx('rounded-lg border px-5 py-4', guide.containerClass)}
      role="region"
      aria-label="조치 권고"
    >
      <div className="flex items-start gap-3">
        <div className="flex-shrink-0 mt-0.5">
          <svg
            className={clsx('w-5 h-5', guide.titleClass)}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
            aria-hidden="true"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d={guide.iconPath} />
          </svg>
        </div>
        <div className="flex-1 min-w-0">
          <p className={clsx('text-sm font-semibold mb-1', guide.titleClass)}>
            {guide.title}
          </p>
          <p className={clsx('text-sm', guide.messageClass)}>
            {guide.message}
          </p>
          <p className={clsx('text-xs mt-2', guide.messageClass, 'opacity-80')}>
            {guide.detail}
          </p>
          <p className={clsx('text-xs mt-2 font-medium', guide.referenceClass)}>
            {guide.reference}
          </p>
        </div>
      </div>
    </div>
  )
}

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

/**
 * 유효성 테스트 결과 표시 패널.
 *
 * 구성:
 * 1. 상단 판정 배지 (PASS/WARNING/FAIL) — 결론 우선
 * 2. Dollar-offset 게이지 — 참고 범위 시각화
 * 3. 조치 권고 메시지 — 다음 단계 안내
 * 4. 금액 상세 테이블 — 유효/비유효 부분
 * 5. OCI 인식액 (현금흐름 헤지만)
 *
 * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 참고 범위 표시)
 * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 — P&L 인식)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 헤지 OCI 인식)
 */
export function EffectivenessTestResult({ result }: EffectivenessTestResultProps) {
  const resultBadge = RESULT_BADGE[result.testResult] ?? {
    label: result.testResult,
    className: 'bg-slate-100 text-slate-600 border border-slate-200',
    dotClass: 'bg-slate-400',
  }
  const actionBadge = ACTION_BADGE[result.actionRequired] ?? {
    label: result.actionRequired,
    className: 'bg-slate-100 text-slate-600',
  }

  const isCashFlow = result.hedgeType === 'CASH_FLOW'
  const isPass     = result.testResult === 'PASS'
  const isIrsFvh   = result.instrumentType === 'IRS' && result.hedgeType === 'FAIR_VALUE'

  // 유효/비유효 금액 레이블
  const effectiveLabel   = isCashFlow
    ? '유효 부분 (OCI 인식)'
    : '유효 부분 (분석용 — P&L 인식)'
  const ineffectiveLabel = isCashFlow
    ? '비유효 부분 (P&L 즉시 인식)'
    : '비유효 부분 (P&L, 부호 있음)'

  return (
    <Card
      title="유효성 테스트 결과"
      description={`${TEST_TYPE_LABEL[result.testType] ?? result.testType} · ${formatDate(result.testDate)}`}
      variant="bordered"
      actions={
        <div className="flex items-center gap-2">
          <span
            className={clsx(
              'inline-flex items-center gap-2 px-4 py-1.5 rounded-lg text-sm font-bold tracking-wide',
              resultBadge.className,
            )}
            aria-label={`유효성 테스트 ${resultBadge.label}`}
          >
            <span
              className={clsx('w-2 h-2 rounded-full flex-shrink-0', resultBadge.dotClass)}
              aria-hidden="true"
            />
            {resultBadge.label}
          </span>
        </div>
      }
    >
      <div className="space-y-5">

        {/* ── 1. Dollar-offset 게이지 ──────────────────────────────────── */}
        <div className="rounded-lg border border-slate-200 bg-white px-5 py-4">
          <DollarOffsetGauge
            ratio={result.effectivenessRatio}
            testResult={result.testResult}
          />
        </div>

        {/* ── 2. 조치 권고 ─────────────────────────────────────────────── */}
        <ActionGuidePanel actionRequired={result.actionRequired} />

        {/* ── 3. 핵심 지표 요약 ────────────────────────────────────────── */}
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
          <ResultField label="판정 결과">
            <span
              className={clsx(
                'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-sm font-bold',
                resultBadge.className,
              )}
            >
              <span className={clsx('w-1.5 h-1.5 rounded-full', resultBadge.dotClass)} aria-hidden="true" />
              {resultBadge.label}
            </span>
          </ResultField>

          <ResultField label="필요 조치">
            <span
              className={clsx(
                'inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold',
                actionBadge.className,
              )}
            >
              {actionBadge.label}
            </span>
          </ResultField>

          <ResultField label="위험회피 유형">
            <span className="text-sm text-slate-800">
              {isCashFlow ? '현금흐름 위험회피' : '공정가치 위험회피'}
            </span>
          </ResultField>
        </div>

        {/* ── 4. 금액 상세 테이블 ──────────────────────────────────────── */}
        <div className="rounded-lg border border-slate-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  항목
                </th>
                <th scope="col" className="text-right px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  당기 변동
                </th>
                <th scope="col" className="text-right px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  누적 변동
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              <tr>
                <td className="px-4 py-3 text-slate-600 text-xs">
                  헤지수단 공정가치
                  <span className="block text-slate-400 mt-0.5">K-IFRS 1109호 6.5.8</span>
                </td>
                <td className="px-4 py-3 text-right font-financial tabular-nums text-sm">
                  <AmountCell amount={result.instrumentFvChange} />
                </td>
                <td className="px-4 py-3 text-right font-financial tabular-nums text-sm">
                  <AmountCell amount={result.instrumentFvCumulative} />
                </td>
              </tr>
              <tr>
                <td className="px-4 py-3 text-slate-600 text-xs">
                  피헤지항목 현재가치
                  <span className="block text-slate-400 mt-0.5">헤지 위험 귀속분만 포함</span>
                </td>
                <td className="px-4 py-3 text-right font-financial tabular-nums text-sm">
                  <AmountCell amount={result.hedgedItemPvChange} />
                </td>
                <td className="px-4 py-3 text-right font-financial tabular-nums text-sm">
                  <AmountCell amount={result.hedgedItemPvCumulative} />
                </td>
              </tr>
              <tr className="bg-slate-50">
                <td className="px-4 py-3 text-slate-700 text-xs font-medium">
                  {effectiveLabel}
                  {isCashFlow && (
                    <span className="block text-slate-400 font-normal mt-0.5">K-IFRS 1109호 6.5.11⑴</span>
                  )}
                </td>
                <td className="px-4 py-3 text-right font-financial tabular-nums text-sm" colSpan={2}>
                  <AmountCell amount={result.effectiveAmount} />
                </td>
              </tr>
              <tr className={clsx(result.ineffectiveAmount !== 0 && 'bg-amber-50')}>
                <td className="px-4 py-3 text-slate-700 text-xs font-medium">
                  {ineffectiveLabel}
                  <span className="block text-slate-400 font-normal mt-0.5">
                    {isCashFlow ? 'K-IFRS 1109호 6.5.11⑵ — 과대헤지 초과분' : 'K-IFRS 1109호 6.5.8 — 순효과'}
                  </span>
                </td>
                <td className="px-4 py-3 text-right font-financial tabular-nums text-sm" colSpan={2}>
                  <AmountCell amount={result.ineffectiveAmount} showSign />
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        {/* ── 5. OCI 인식액 (현금흐름 헤지만) ─────────────────────────── */}
        {isCashFlow && result.ociReserveBalance != null && (
          <div className="rounded-lg bg-blue-50 border border-blue-200 px-4 py-3">
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="text-xs text-blue-600 font-semibold mb-1">
                  당기 OCI 인식액 — 현금흐름위험회피적립금 당기분
                </p>
                <p className="font-financial text-xl font-bold text-blue-800 tabular-nums">
                  {formatKrw(result.ociReserveBalance)}
                </p>
                <p className="text-xs text-blue-400 mt-1">
                  * 누계 잔액이 아닌 당기 보고기간 인식액 · K-IFRS 1109호 6.5.11
                </p>
              </div>
              <div className="flex-shrink-0">
                <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-100 text-blue-700 border border-blue-200">
                  OCI
                </span>
              </div>
            </div>
          </div>
        )}

        {/* ── 6. IRS FVH 2단계 안내 패널 ─────────────────────────────── */}
        {isIrsFvh && (
          <div className="rounded-lg bg-blue-50 border border-blue-200 px-4 py-3">
            <div className="flex items-start gap-3">
              <span className="text-lg flex-shrink-0">🔵</span>
              <div>
                <p className="text-xs font-bold text-blue-800 mb-1">
                  IRS FVH · 2단계 확장 엔진
                </p>
                <div className="flex flex-wrap gap-2 mb-2">
                  <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-mono font-semibold bg-white border border-blue-200 text-blue-700">
                    K-IFRS 1109호 6.5.8
                  </span>
                  <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-mono font-semibold bg-white border border-blue-200 text-blue-700">
                    장부조정 상각: K-IFRS 1109호 6.5.9
                  </span>
                </div>
                <p className="text-xs text-blue-700 leading-relaxed">
                  IRS FVH 분개는 수단 평가손익(6.5.8)과 피헤지항목 장부조정(6.5.8)을 동시 생성합니다.
                  헤지 중단 또는 만기 후 잔여 장부조정액은 <strong>IRS_FVH_AMORTIZATION</strong> 분개 유형(6.5.9)으로 별도 상각됩니다.
                  Dollar-offset 80%~125%는 합격선이 아니라 참고 범위임에 유의하세요.
                </p>
              </div>
            </div>
          </div>
        )}

        {/* ── 7. 실패 사유 (FAIL/WARNING일 때만) ──────────────────────── */}
        {!isPass && result.failureReason && (
          <div
            role="alert"
            className={clsx(
              'rounded-lg border px-4 py-3',
              result.testResult === 'WARNING'
                ? 'bg-amber-50 border-amber-200'
                : 'bg-red-50 border-red-200',
            )}
          >
            <p className={clsx(
              'text-xs font-semibold mb-1',
              result.testResult === 'WARNING' ? 'text-amber-700' : 'text-red-700',
            )}>
              {result.testResult === 'WARNING' ? '주의 사유' : '실패 사유'}
            </p>
            <p className={clsx(
              'text-sm',
              result.testResult === 'WARNING' ? 'text-amber-700' : 'text-red-700',
            )}>
              {result.failureReason}
            </p>
          </div>
        )}

        {/* ── 7. 레코드 메타 ─────────────────────────────────────────── */}
        <p className="text-xs text-slate-400 text-right">
          테스트 ID: {result.effectivenessTestId} · 관계 ID: {result.hedgeRelationshipId}
        </p>
      </div>
    </Card>
  )
}

// ─── 보조 컴포넌트 ────────────────────────────────────────────────────────────

function ResultField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <p className="text-xs text-slate-500 mb-1">{label}</p>
      <div>{children}</div>
    </div>
  )
}

function AmountCell({ amount, showSign = false }: { amount: number; showSign?: boolean }) {
  const isNeg = amount < 0
  const isPos = amount > 0
  const colorClass = showSign
    ? isNeg ? 'text-red-600' : isPos ? 'text-emerald-700' : 'text-slate-700'
    : 'text-slate-700'

  return (
    <span className={colorClass}>
      {showSign && isPos ? '+' : ''}
      {formatKrw(amount)}
    </span>
  )
}
