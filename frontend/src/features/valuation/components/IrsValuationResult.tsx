import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'
import { Card } from '@/components/ui/Card'
import { MoneyDisplay } from '@/components/financial/MoneyDisplay'
import { KifrsReference } from '@/components/financial/KifrsReference'
import { formatDate, formatPercent } from '@/utils/formatters'
import type { IrsValuationResponse } from '@/types/valuation'
import clsx from 'clsx'

interface IrsValuationResultProps {
  result: IrsValuationResponse
}

/**
 * IRS 공정가치 평가 결과 카드.
 *
 * 표시 내용:
 * 1. 요약 카드 — 공정가치·변동액·잔존일수
 * 2. 손익 방향 배너
 * 3. 고정/변동 다리 PV 내역 (Level 2 근거)
 * 4. IRS 헤지 지정 CTA
 *
 * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 손익 P&L 인식)
 * @see K-IFRS 1113호 72~90항 (Level 2 관측가능 투입변수)
 */
export function IrsValuationResult({ result }: IrsValuationResultProps) {
  const isProfit = result.fairValue > 0
  const isLoss   = result.fairValue < 0

  return (
    <div className="space-y-4">
      {/* ── 요약 카드 ────────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <SummaryCard
          label="공정가치 (IRS)"
          subLabel="K-IFRS 1113호 Level 2"
          highlight
          profit={isProfit}
          loss={isLoss}
        >
          <MoneyDisplay amount={result.fairValue} size="xl" />
        </SummaryCard>

        <SummaryCard
          label="공정가치 변동액"
          subLabel="당기 평가손익 (K-IFRS 1109호 6.5.8)"
          profit={result.fairValueChange > 0}
          loss={result.fairValueChange < 0}
        >
          <MoneyDisplay amount={result.fairValueChange} size="xl" showSign />
        </SummaryCard>

        <SummaryCard label="잔존일수" subLabel="평가기준일 → 만기일">
          <span className="text-3xl font-bold font-financial text-slate-800 tabular-nums">
            {result.remainingTermDays}
            <span className="text-base font-normal text-slate-500 ml-1">일</span>
          </span>
        </SummaryCard>
      </div>

      {/* ── 손익 방향 배너 ──────────────────────────────────────────── */}
      <div className={clsx(
        'rounded-lg px-5 py-4 border flex items-start gap-4',
        isProfit && 'bg-emerald-50 border-emerald-200',
        isLoss   && 'bg-red-50 border-red-200',
        !isProfit && !isLoss && 'bg-slate-50 border-slate-200',
      )}>
        <div className={clsx(
          'flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center text-lg',
          isProfit && 'bg-emerald-100',
          isLoss   && 'bg-red-100',
          !isProfit && !isLoss && 'bg-slate-100',
        )}>
          {isProfit ? '▲' : isLoss ? '▼' : '—'}
        </div>
        <div className="flex-1">
          <p className={clsx(
            'font-semibold text-sm',
            isProfit && 'text-emerald-800',
            isLoss   && 'text-red-800',
            !isProfit && !isLoss && 'text-slate-700',
          )}>
            {isProfit
              ? '평가이익 — 현재 변동금리가 고정금리보다 높아 수취고정 IRS 가치 상승'
              : isLoss
              ? '평가손실 — 현재 변동금리가 고정금리보다 낮아 수취고정 IRS 가치 하락'
              : 'IRS 공정가치 변동 없음'}
          </p>
          <p className={clsx(
            'text-xs mt-1',
            isProfit && 'text-emerald-600',
            isLoss   && 'text-red-600',
            !isProfit && !isLoss && 'text-slate-500',
          )}>
            K-IFRS 1109호 6.5.8 — 공정가치위험회피 시 IRS(위험회피수단) 평가손익을 즉시 당기손익(P&L)으로 인식합니다.
            피헤지항목(채권) 공정가치 변동분도 같은 금액이 반대 방향으로 P&L에 인식됩니다.
          </p>
        </div>
        <KifrsReference clauseId="KIFRS1109-6.5.8" variant="badge" />
      </div>

      {/* ── 고정/변동 다리 PV 내역 ─────────────────────────────────── */}
      <Card
        title="IRS 다리별 현재가치"
        description="고정 다리 PV · 변동 다리 PV · 공정가치 산출 — K-IFRS 1113호 Level 2"
        actions={<KifrsReference clauseId="KIFRS1113-72-90" variant="badge" />}
      >
        {/* Level 2 근거 패널 */}
        <div className="bg-blue-50 rounded-lg px-4 py-3 border border-blue-100 mb-5">
          <p className="text-xs font-semibold text-blue-800 mb-2 uppercase tracking-wider">
            Level 2 분류 근거 — K-IFRS 1113호 72~90항
          </p>
          <div className="space-y-1.5 text-xs text-blue-700">
            <p className="flex items-start gap-2">
              <span className="text-blue-400 flex-shrink-0 mt-0.5">▶</span>
              <span>
                <strong>변동금리 {formatPercent(result.discountRate)}</strong> —
                CD 91일 금리·SOFR 등 활성시장 관측가능 기준금리 사용 (Level 2)
              </span>
            </p>
            <p className="flex items-start gap-2">
              <span className="text-blue-400 flex-shrink-0 mt-0.5">▶</span>
              <span>
                <strong>할인율 {formatPercent(result.discountRate)}</strong> —
                국고채 기준 무위험이자율 (한국은행 공표, 관측가능 수익률 곡선)
              </span>
            </p>
            <p className="flex items-start gap-2">
              <span className="text-blue-400 flex-shrink-0 mt-0.5">▶</span>
              <span>
                모든 투입변수가 관측가능하므로 <strong>Level 2</strong> 분류.
                수익률 곡선 할인법은 IRS 시장 표준 평가기법입니다.
              </span>
            </p>
          </div>
        </div>

        {/* PV 테이블 */}
        <div className="overflow-hidden rounded-lg border border-slate-200 mb-4">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">구분</th>
                <th scope="col" className="text-right px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">현재가치 (KRW)</th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">설명</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              <tr>
                <td className="px-4 py-3 text-slate-700 font-medium">고정 다리 PV</td>
                <td className="px-4 py-3 text-right font-financial tabular-nums text-slate-800">
                  <MoneyDisplay amount={result.fixedLegPv} size="sm" />
                </td>
                <td className="px-4 py-3 text-xs text-slate-500">Σ [fixedRate × N × Δt × DF(tᵢ)]</td>
              </tr>
              <tr>
                <td className="px-4 py-3 text-slate-700 font-medium">변동 다리 PV</td>
                <td className="px-4 py-3 text-right font-financial tabular-nums text-slate-800">
                  <MoneyDisplay amount={result.floatingLegPv} size="sm" />
                </td>
                <td className="px-4 py-3 text-xs text-slate-500">Σ [floatRate × N × Δt × DF(tᵢ)]</td>
              </tr>
              <tr className="bg-blue-50 font-semibold">
                <td className="px-4 py-3 text-blue-800">공정가치 = float − fixed</td>
                <td className="px-4 py-3 text-right font-financial tabular-nums">
                  <MoneyDisplay amount={result.fairValue} size="sm" showSign />
                </td>
                <td className="px-4 py-3 text-xs text-blue-600">수취고정(FVH) 기준 · K-IFRS 1109호 6.5.8</td>
              </tr>
            </tbody>
          </table>
        </div>

        {/* 평가 메타 */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
          <MetaItem label="평가 ID" value={String(result.valuationId)} />
          <MetaItem label="계약번호" value={result.contractId} />
          <MetaItem label="평가기준일" value={formatDate(result.valuationDate)} />
          <MetaItem
            label="공정가치 수준"
            value={
              <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-100 text-blue-800">
                {result.fairValueLevel.replace('_', ' ')}
              </span>
            }
          />
        </div>

        {/* 공정가치 변동 K-IFRS 근거 */}
        <div className="mt-4 px-4 py-3 bg-blue-50 rounded-lg border border-blue-100">
          <p className="text-xs text-blue-700 flex flex-wrap items-center gap-1">
            <KifrsReference clauseId="KIFRS1109-6.5.8" variant="text" />
            <span>— 공정가치위험회피 시 IRS(위험회피수단) 공정가치 변동</span>
            <strong>{result.fairValueChange >= 0 ? '이익' : '손실'}</strong>
            <span>을 즉시 당기손익(P&L)으로 인식합니다.</span>
          </p>
          <p className="text-xs text-blue-600 mt-1">
            전기 공정가치 대비 당기 변동액: <strong className="font-financial">{result.fairValueChange >= 0 ? '+' : ''}{result.fairValueChange.toLocaleString('ko-KR')} 원</strong>
          </p>
        </div>
      </Card>

      {/* ── IRS 헤지 지정 CTA ────────────────────────────────────────── */}
      <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-5 py-4 flex items-center justify-between gap-4 flex-wrap">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-emerald-700">다음 단계</p>
          <p className="mt-1 text-sm font-semibold text-emerald-900">
            이 IRS 계약을 헤지 지정하여 FVH 위험회피 관계를 수립하세요.
          </p>
          <p className="mt-0.5 text-xs text-emerald-700">
            헤지 지정 → 유효성 테스트(Dollar-offset PASS) → 분개 자동 생성 순으로 진행됩니다.
            K-IFRS 1109호 6.4.1 지정 요건을 충족해야 합니다.
          </p>
        </div>
        <Link
          to="/hedge"
          className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 transition-colors whitespace-nowrap"
        >
          IRS 헤지 지정으로 이동 →
        </Link>
      </div>
    </div>
  )
}

// ─── 하위 컴포넌트 ────────────────────────────────────────────────────────────

function SummaryCard({
  label,
  subLabel,
  children,
  highlight = false,
  profit = false,
  loss = false,
}: {
  label: string
  subLabel?: string
  children: ReactNode
  highlight?: boolean
  profit?: boolean
  loss?: boolean
}) {
  return (
    <div className={clsx(
      'rounded-lg px-5 py-4 border',
      highlight && profit && 'bg-emerald-50 border-emerald-200',
      highlight && loss   && 'bg-red-50 border-red-200',
      highlight && !profit && !loss && 'bg-blue-50 border-blue-200',
      !highlight && 'bg-white border-slate-200',
    )}>
      <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">{label}</p>
      {subLabel && <p className="text-xs text-slate-400 mb-3">{subLabel}</p>}
      <div>{children}</div>
    </div>
  )
}

function MetaItem({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <p className="text-slate-400 mb-1">{label}</p>
      <p className="font-medium text-slate-700 font-financial">{value}</p>
    </div>
  )
}
