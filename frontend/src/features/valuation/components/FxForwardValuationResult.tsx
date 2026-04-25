import { useState } from 'react'
import type { ReactNode } from 'react'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { MoneyDisplay } from '@/components/financial/MoneyDisplay'
import { RateDisplay } from '@/components/financial/RateDisplay'
import { KifrsReference } from '@/components/financial/KifrsReference'
import type { FxForwardValuationResponse } from '@/types/valuation'
import { formatDate, formatPercent, formatRate } from '@/utils/formatters'
import clsx from 'clsx'

interface FxForwardValuationResultProps {
  result: FxForwardValuationResponse
  onConfirm?: (result: FxForwardValuationResponse) => void
}

/**
 * 공정가치 평가 결과 표시 컴포넌트.
 *
 * 표시 내용:
 * 1. 상단 요약 카드 — 공정가치, 변동액, 잔존일수
 * 2. Level 분류 패널 — K-IFRS 1113호 기준 Level 2 선택 근거
 * 3. IRP 계산 근거 — 투입변수 테이블 + 공식
 * 4. 공정가치 세부 내역
 * 5. 평가 확정 버튼
 *
 * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 평가손익 P&L 인식)
 * @see K-IFRS 1113호 (공정가치 Level 1/2/3 분류 및 공시)
 */
export function FxForwardValuationResult({ result, onConfirm }: FxForwardValuationResultProps) {
  const [confirmed, setConfirmed] = useState(false)
  const isLoss   = result.fairValue < 0
  const isProfit = result.fairValue > 0

  const handleConfirm = () => {
    setConfirmed(true)
    onConfirm?.(result)
  }

  return (
    <div className="space-y-4">
      {/* ── 상단 요약 카드 ──────────────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <SummaryCard
          label="공정가치"
          subLabel="K-IFRS 1113호 Level 2"
          highlight
          loss={isLoss}
          profit={isProfit}
        >
          <MoneyDisplay amount={result.fairValue} size="xl" />
        </SummaryCard>

        <SummaryCard
          label="공정가치 변동액"
          subLabel="당기 평가손익 (K-IFRS 1109호 6.5.8)"
          loss={result.fairValueChange < 0}
          profit={result.fairValueChange > 0}
        >
          <MoneyDisplay amount={result.fairValueChange} size="xl" showSign />
        </SummaryCard>

        <SummaryCard label="잔존일수" subLabel="평가기준일 → 만기일">
          <span className="text-3xl font-bold font-financial text-slate-800 tabular-nums">
            {result.remainingDays}
            <span className="text-base font-normal text-slate-500 ml-1">일</span>
          </span>
        </SummaryCard>
      </div>

      {/* ── 평가손익 방향 배너 ─────────────────────────────────────────── */}
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
              ? `평가이익 — 현재 선물환율(${formatRate(result.currentForwardRate, 2)}원)이 계약환율보다 높아 헤지수단 가치 상승`
              : isLoss
              ? `평가손실 — 현재 선물환율(${formatRate(result.currentForwardRate, 2)}원)이 계약환율보다 낮아 헤지수단 가치 하락`
              : '공정가치 변동 없음'}
          </p>
          <p className={clsx(
            'text-xs mt-1',
            isProfit && 'text-emerald-600',
            isLoss   && 'text-red-600',
            !isProfit && !isLoss && 'text-slate-500',
          )}>
            K-IFRS 1109호 6.5.8 — 공정가치위험회피 시 위험회피수단 손익을 즉시 당기손익(P&L)으로 인식합니다.
          </p>
        </div>
        <KifrsReference clauseId="KIFRS1109-6.5.8" variant="badge" />
      </div>

      {/* ── Level 분류 패널 ────────────────────────────────────────────── */}
      <Card
        title="공정가치 수준 분류"
        description="K-IFRS 1113호 — 공정가치 측정 위계"
        actions={<KifrsReference clauseId="KIFRS1113-72-90" variant="badge" />}
      >
        <div className="grid grid-cols-3 gap-3 mb-5">
          <LevelCard
            level="LEVEL_1"
            title="Level 1"
            desc="활성시장 공시가격"
            example="상장주식, 국채(활성시장)"
            active={result.fairValueLevel === 'LEVEL_1'}
          />
          <LevelCard
            level="LEVEL_2"
            title="Level 2"
            desc="관측가능한 투입변수"
            example="통화선도 (현물환율·금리 사용)"
            active={result.fairValueLevel === 'LEVEL_2'}
          />
          <LevelCard
            level="LEVEL_3"
            title="Level 3"
            desc="관측불가능한 투입변수"
            example="비유동 파생상품, 내재가치"
            active={result.fairValueLevel === 'LEVEL_3'}
          />
        </div>

        {/* Level 2 선택 근거 상세 */}
        <div className="bg-blue-50 rounded-lg px-5 py-4 border border-blue-100">
          <p className="text-xs font-semibold text-blue-800 mb-2 uppercase tracking-wider">
            Level 2 선택 근거 — K-IFRS 1113호 72~90항
          </p>
          <ul className="space-y-1.5 text-xs text-blue-700">
            <li className="flex items-start gap-2">
              <span className="text-blue-400 flex-shrink-0 mt-0.5">▶</span>
              <span>
                <strong>현물환율(S₀) {formatRate(result.spotRate, 2)} 원/USD</strong> —
                Bloomberg, 서울외국환중개(SMBS) 등 활성시장에서 직접 관측 가능 (Level 2)
              </span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-400 flex-shrink-0 mt-0.5">▶</span>
              <span>
                <strong>원화이자율 {formatPercent(result.krwInterestRate)}</strong> —
                한국은행 공표 국고채 금리, CD금리 (관측가능 수익률 곡선)
              </span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-400 flex-shrink-0 mt-0.5">▶</span>
              <span>
                <strong>달러이자율 {formatPercent(result.usdInterestRate)}</strong> —
                SOFR (Secured Overnight Financing Rate), 관측가능 벤치마크
              </span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-400 flex-shrink-0 mt-0.5">▶</span>
              <span>
                모든 투입변수가 관측가능하므로 <strong>Level 2</strong> 분류.
                IRP(이자율 평형 이론)은 시장참여자가 실제 사용하는 표준 평가기법.
              </span>
            </li>
          </ul>
        </div>
      </Card>

      {/* ── IRP 계산 근거 ─────────────────────────────────────────────── */}
      <Card
        title="IRP 공정가치 계산 근거"
        description="이자율 평형 이론 (Interest Rate Parity)"
        footer={
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-400">
            <KifrsReference clauseId="KIFRS1113-72-90" variant="text" />
            <span>— Level 2 관측가능 투입변수 기반 평가기법</span>
            <span className="text-slate-300">|</span>
            <KifrsReference clauseId="KIFRS1113-61-66" variant="text" />
            <span>— 시장참여자 가격결정기법</span>
          </div>
        }
      >
        {/* 계산 공식 표시 */}
        <div className="bg-slate-50 rounded-lg px-5 py-4 mb-5 border border-slate-200">
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">IRP 평가 공식</p>
          <p className="text-sm font-financial text-slate-700 leading-relaxed">
            선물환율 = S₀ × (1 + r<sub>KRW</sub> × T/365) / (1 + r<sub>USD</sub> × T/360)
          </p>
          <p className="text-sm font-financial text-slate-700 mt-1 leading-relaxed">
            공정가치 = (현재선물환율 − 계약선물환율) × 명목원금 × 현가계수
          </p>

          {/* Day Count Convention */}
          <div className="mt-3 pt-3 border-t border-slate-200 grid grid-cols-1 sm:grid-cols-2 gap-2">
            <div className="flex items-start gap-2">
              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-bold bg-blue-100 text-blue-800 flex-shrink-0">
                KRW
              </span>
              <span className="text-xs text-slate-600">
                Actual/365 Fixed — 한국 자금시장 표준
                <span className="block text-slate-400">(국고채·CD금리·통안채)</span>
              </span>
            </div>
            <div className="flex items-start gap-2">
              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-bold bg-emerald-100 text-emerald-800 flex-shrink-0">
                USD
              </span>
              <span className="text-xs text-slate-600">
                Actual/360 — USD SOFR 국제 표준
                <span className="block text-slate-400">(구LIBOR·SOFR·Fed Funds)</span>
              </span>
            </div>
          </div>
        </div>

        {/* K-IFRS 근거 배지 */}
        <div className="flex flex-wrap gap-2 mb-4">
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200">
            K-IFRS 1113호 Level 2
            <span className="text-blue-400">— 관측가능 투입변수</span>
          </span>
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-200">
            K-IFRS 1109호 6.5.8
            <span className="text-indigo-400">— 공정가치위험회피</span>
          </span>
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-slate-50 text-slate-700 border border-slate-200">
            K-IFRS 1113호 61~66항
            <span className="text-slate-400">— 시장참여자 가격결정기법</span>
          </span>
        </div>

        {/* 투입변수 테이블 */}
        <div className="overflow-hidden rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  투입변수
                </th>
                <th scope="col" className="text-right px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  값
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  분류
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              <InputRow
                label="평가기준일"
                value={formatDate(result.valuationDate)}
                category="측정일"
              />
              <InputRow
                label="현물환율 (S₀)"
                value={<RateDisplay value={result.spotRate} type="exchange" decimals={4} />}
                category="Level 2 — 관측가능"
              />
              <InputRow
                label="원화이자율 (r_KRW)"
                value={formatPercent(result.krwInterestRate)}
                category="Level 2 — 국고채 · ACT/365"
              />
              <InputRow
                label="달러이자율 (r_USD)"
                value={formatPercent(result.usdInterestRate)}
                category="Level 2 — SOFR · ACT/360"
              />
              <InputRow
                label="현재 선물환율"
                value={<RateDisplay value={result.currentForwardRate} type="exchange" decimals={4} />}
                category="IRP 산출값"
                highlight
              />
            </tbody>
          </table>
        </div>
      </Card>

      {/* ── 공정가치 세부 내역 ────────────────────────────────────────── */}
      <Card title="공정가치 내역" description={`평가기준일: ${formatDate(result.valuationDate)}`}>
        <div className="space-y-3">

          {/* 계산 상세 내역 테이블 */}
          <div className="overflow-hidden rounded-lg border border-slate-200 mb-1">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th scope="col" className="text-left px-4 py-2 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    계산 단계
                  </th>
                  <th scope="col" className="text-right px-4 py-2 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    값
                  </th>
                  <th scope="col" className="text-left px-4 py-2 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    비고
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                <CalcRow
                  label="잔존일수"
                  value={`${result.remainingDays}일`}
                  note="평가기준일 → 만기일"
                />
                <CalcRow
                  label="KRW factor"
                  value={`1 + ${formatPercent(result.krwInterestRate)} × ${result.remainingDays}/365`}
                  note="ACT/365 Fixed"
                />
                <CalcRow
                  label="USD factor"
                  value={`1 + ${formatPercent(result.usdInterestRate)} × ${result.remainingDays}/360`}
                  note="ACT/360"
                />
                <CalcRow
                  label="현재 선물환율"
                  value={<RateDisplay value={result.currentForwardRate} type="exchange" decimals={4} />}
                  note="IRP 산출값 (KRW/USD)"
                />
                <CalcRow
                  label="현가계수"
                  value={`1 / (1 + r_KRW × T/365)`}
                  note="KRW 무위험이자율 할인"
                />
              </tbody>
            </table>
          </div>

          <DetailRow label="전기 공정가치">
            <MoneyDisplay amount={result.previousFairValue} size="md" />
          </DetailRow>
          <DetailRow label="당기 공정가치">
            <MoneyDisplay amount={result.fairValue} size="md" />
          </DetailRow>
          <div className="border-t border-slate-200 pt-3 mt-3">
            <DetailRow label="공정가치 변동액 (P&L 인식)" bold>
              <MoneyDisplay amount={result.fairValueChange} size="md" showSign />
            </DetailRow>
          </div>
          <div className="mt-4 px-4 py-3 bg-blue-50 rounded-lg border border-blue-100">
            <p className="text-xs text-blue-700 flex flex-wrap items-center gap-1">
              <KifrsReference clauseId="KIFRS1109-6.5.8" variant="text" />
              <span>— 공정가치위험회피 시 위험회피수단(통화선도)의 공정가치 변동</span>
              <strong>{result.fairValueChange >= 0 ? '이익' : '손실'}</strong>
              <span>을 당기손익(P&L)으로 인식합니다.</span>
            </p>
          </div>
        </div>
      </Card>

      {/* ── 공시 정보 ────────────────────────────────────────────────── */}
      <Card
        title="공시 정보"
        actions={<KifrsReference clauseId="KIFRS1107" variant="badge" />}
        description="K-IFRS 1107호 — 금융상품 공시"
      >
        <div className="grid grid-cols-3 gap-4 text-sm">
          <div>
            <p className="text-xs text-slate-400 mb-1">평가 ID</p>
            <p className="font-financial text-slate-700">{result.valuationId}</p>
          </div>
          <div>
            <p className="text-xs text-slate-400 mb-1">계약번호</p>
            <p className="font-medium text-slate-700">{result.contractId}</p>
          </div>
          <div>
            <p className="text-xs text-slate-400 mb-1">공정가치 수준</p>
            <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-100 text-blue-800">
              {result.fairValueLevel.replace('_', ' ')}
            </span>
          </div>
        </div>
      </Card>

      {/* ── 평가 확정 섹션 ──────────────────────────────────────────────── */}
      {confirmed ? (
        <div
          role="status"
          className="rounded-lg border border-emerald-200 bg-emerald-50 px-6 py-5 flex items-center gap-4"
        >
          <div className="flex-shrink-0 w-10 h-10 rounded-full bg-emerald-100 flex items-center justify-center">
            <svg className="w-6 h-6 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
            </svg>
          </div>
          <div>
            <p className="text-sm font-semibold text-emerald-800">평가 확정 완료</p>
            <p className="text-xs text-emerald-600 mt-0.5">
              평가 ID {result.valuationId} — {formatDate(result.valuationDate)} 기준
              공정가치 {result.fairValue >= 0 ? '이익' : '손실'} 확정.
              다음 단계로 「유효성 테스트」를 실행하면 PASS 결과 기준으로 분개가 자동 생성됩니다.
            </p>
          </div>
        </div>
      ) : (
        <div className="rounded-lg border border-slate-200 bg-slate-50 px-6 py-5">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm font-semibold text-slate-800">평가 확정</p>
              {/*
                과거 문구는 "확정 → 분개 자동 생성" 으로 한 단계를 건너뛴 설명이라
                심사관에게 "왜 평가 직후 분개가 안 보이지?" 같은 혼란을 주었다.
                실제 체인(평가 → 유효성 테스트 PASS → 분개) 을 명시한다.
              */}
              <p className="text-xs text-slate-500 mt-1">
                이 결과는 평가 이력으로 저장됩니다. 분개는 이후 「유효성 테스트」를 실행해
                PASS 판정을 받은 후에 자동 생성됩니다. FAIL 이면 K-IFRS 1109호 6.5.6에 따라
                분개가 생성되지 않습니다.
              </p>
              <p className="text-xs text-slate-400 mt-1">
                확정 후에도 동일 기간 재평가는 가능합니다 (Append-Only 이력 관리 · K-IFRS 1107호).
              </p>
            </div>
            <div className="flex-shrink-0">
              <Button
                type="button"
                variant="primary"
                size="lg"
                onClick={handleConfirm}
              >
                평가 확정
              </Button>
            </div>
          </div>
        </div>
      )}
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
    <div
      className={clsx(
        'rounded-lg px-5 py-4 border',
        highlight && profit && 'bg-emerald-50 border-emerald-200',
        highlight && loss  && 'bg-red-50 border-red-200',
        highlight && !profit && !loss && 'bg-blue-50 border-blue-200',
        !highlight && 'bg-white border-slate-200',
      )}
    >
      <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">{label}</p>
      {subLabel && <p className="text-xs text-slate-400 mb-3">{subLabel}</p>}
      <div>{children}</div>
    </div>
  )
}

function LevelCard({
  title,
  desc,
  example,
  active,
}: {
  level: string
  title: string
  desc: string
  example: string
  active: boolean
}) {
  return (
    <div
      className={clsx(
        'rounded-lg border px-4 py-3 transition-colors',
        active
          ? 'bg-blue-900 border-blue-900 text-white'
          : 'bg-white border-slate-200 text-slate-600',
      )}
      aria-current={active ? 'true' : undefined}
    >
      <p className={clsx('text-sm font-bold mb-1', active ? 'text-white' : 'text-slate-800')}>
        {title}
        {active && (
          <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-xs font-semibold bg-white/20 text-white">
            선택
          </span>
        )}
      </p>
      <p className={clsx('text-xs mb-1', active ? 'text-blue-200' : 'text-slate-500')}>{desc}</p>
      <p className={clsx('text-xs', active ? 'text-blue-300' : 'text-slate-400')}>{example}</p>
    </div>
  )
}

function InputRow({
  label,
  value,
  category,
  highlight = false,
}: {
  label: string
  value: ReactNode
  category: string
  highlight?: boolean
}) {
  return (
    <tr className={clsx(highlight && 'bg-blue-50 font-semibold')}>
      <td className="px-4 py-2.5 text-slate-700">{label}</td>
      <td className="px-4 py-2.5 text-right font-financial tabular-nums text-slate-800">{value}</td>
      <td className="px-4 py-2.5 text-slate-500 text-xs">{category}</td>
    </tr>
  )
}

function DetailRow({
  label,
  children,
  bold = false,
}: {
  label: string
  children: ReactNode
  bold?: boolean
}) {
  return (
    <div className="flex items-center justify-between">
      <span className={clsx('text-sm text-slate-600', bold && 'font-semibold text-slate-800')}>
        {label}
      </span>
      <span>{children}</span>
    </div>
  )
}

function CalcRow({
  label,
  value,
  note,
}: {
  label: string
  value: ReactNode
  note: string
}) {
  return (
    <tr>
      <td className="px-4 py-2 text-slate-700">{label}</td>
      <td className="px-4 py-2 text-right font-financial tabular-nums text-slate-800">{value}</td>
      <td className="px-4 py-2 text-slate-400 text-xs">{note}</td>
    </tr>
  )
}
