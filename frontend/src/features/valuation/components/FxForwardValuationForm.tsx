import { useState, useEffect } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { MutationErrorAlert } from '@/components/ui/ErrorAlert'
import clsx from 'clsx'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { NumericInput } from '@/components/ui/NumericInput'
import { formatRate, formatPercent, formatKrw } from '@/utils/formatters'
import type { FxForwardValuationRequest, FxForwardValuationResponse } from '@/types/valuation'
import { useFxForwardValuationMutation } from '../api/useFxForwardValuation'

// ─── Zod 스키마 ──────────────────────────────────────────────────────────────

const schema = z.object({
  contractId:           z.string().min(1, '계약번호를 입력하세요.').max(50),
  notionalAmountUsd:    z.coerce.number().positive('명목원금은 0보다 커야 합니다.'),
  contractForwardRate:  z.coerce.number().positive('계약 선물환율은 0보다 커야 합니다.'),
  contractDate:         z.string().min(1, '계약일을 입력하세요.'),
  maturityDate:         z.string().min(1, '만기일을 입력하세요.'),
  hedgeDesignationDate: z.string().min(1, '헤지 지정일을 입력하세요.'),
  counterpartyCreditRating: z.enum(['AAA', 'AA', 'A', 'BBB', 'BB', 'B', 'CCC', 'D'], {
    required_error: '거래상대방 신용등급을 선택하세요.',
  }),
  valuationDate:        z.string().min(1, '평가기준일을 입력하세요.'),
  spotRate:             z.coerce.number().positive('현물환율은 0보다 커야 합니다.'),
  krwInterestRate:      z.coerce.number().min(0, '원화이자율은 0 이상이어야 합니다.'),
  usdInterestRate:      z.coerce.number().min(0, '달러이자율은 0 이상이어야 합니다.'),
})

type FormValues = z.infer<typeof schema>

const _d = (offset: number): string => {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return d.toISOString().slice(0, 10)
}

const DEFAULT_VALUES: FormValues = {
  contractId:           '',
  notionalAmountUsd:    0,
  contractForwardRate:  0,
  contractDate:         '',
  maturityDate:         '',
  hedgeDesignationDate: '',
  counterpartyCreditRating: 'A' as const,
  valuationDate:        _d(0),
  spotRate:             0,
  krwInterestRate:      0,
  usdInterestRate:      0,
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface FxForwardValuationFormProps {
  onSuccess: (result: FxForwardValuationResponse) => void
  initialValues?: Partial<FormValues>
}

// ─── 공통 input 클래스 ────────────────────────────────────────────────────────

const inputClass =
  'w-full border border-slate-300 rounded-md px-3 py-2 text-sm text-slate-900 ' +
  'focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-blue-600 ' +
  'disabled:bg-slate-50 disabled:text-slate-400 font-financial'

// ─── 처리 과정 단계 데이터 ──────────────────────────────────────────────────

interface ProgressStepData {
  label: string
  formula: string
  getDetail: (values: FormValues) => string
}

const PROGRESS_STEPS: ProgressStepData[] = [
  {
    label: '시장데이터 확인',
    formula: 'Bloomberg / 한국은행 기준금리 조회',
    getDetail: (v) => {
      const lines = [
        `현물환율 S₀ = ${formatRate(v.spotRate, 2)} 원/USD`,
        `원화이자율 r_KRW = ${formatPercent(v.krwInterestRate)} (ACT/365 Fixed)`,
        `달러이자율 r_USD = ${formatPercent(v.usdInterestRate)} (ACT/360)`,
        `명목원금 = $${v.notionalAmountUsd.toLocaleString('en-US')} USD`,
      ]
      return lines.join('\n')
    },
  },
  {
    label: 'IRP 선물환율 계산',
    formula: 'F = S₀ × (1 + r_KRW × T/365) / (1 + r_USD × T/360)',
    getDetail: (v) => {
      // 잔존일수 계산 (valuationDate → maturityDate)
      const valDate = new Date(v.valuationDate)
      const matDate = new Date(v.maturityDate)
      const T = Math.max(0, Math.round((matDate.getTime() - valDate.getTime()) / 86_400_000))
      const krwFactor = 1 + v.krwInterestRate * T / 365
      const usdFactor = 1 + v.usdInterestRate * T / 360
      const fwdRate = v.spotRate * krwFactor / usdFactor
      return [
        `잔존일수 T = ${T}일`,
        `KRW factor = 1 + ${formatPercent(v.krwInterestRate)} × ${T}/365 = ${krwFactor.toFixed(6)}`,
        `USD factor = 1 + ${formatPercent(v.usdInterestRate)} × ${T}/360 = ${usdFactor.toFixed(6)}`,
        `현재 선물환율 F = ${formatRate(v.spotRate, 2)} × ${krwFactor.toFixed(6)} / ${usdFactor.toFixed(6)}`,
        `                = ${formatRate(fwdRate, 4)} 원/USD`,
      ].join('\n')
    },
  },
  {
    label: '공정가치 산출',
    formula: 'FV = (F − K) × N × PV Factor',
    getDetail: (v) => {
      const valDate = new Date(v.valuationDate)
      const matDate = new Date(v.maturityDate)
      const T = Math.max(0, Math.round((matDate.getTime() - valDate.getTime()) / 86_400_000))
      const krwFactor = 1 + v.krwInterestRate * T / 365
      const usdFactor = 1 + v.usdInterestRate * T / 360
      const fwdRate = v.spotRate * krwFactor / usdFactor
      const pvFactor = 1 / krwFactor
      const fairValue = (fwdRate - v.contractForwardRate) * v.notionalAmountUsd * pvFactor
      const isProfit = fairValue >= 0
      return [
        `계약환율 K = ${formatRate(v.contractForwardRate, 2)} 원/USD`,
        `현재선물환율 F = ${formatRate(fwdRate, 4)} 원/USD`,
        `현가계수 = 1 / (1 + r_KRW × T/365) = ${pvFactor.toFixed(6)}`,
        `공정가치 = (${formatRate(fwdRate, 2)} − ${formatRate(v.contractForwardRate, 2)}) × ${v.notionalAmountUsd.toLocaleString('en-US')} × ${pvFactor.toFixed(4)}`,
        `         = ${formatKrw(Math.round(fairValue))} (평가${isProfit ? '익' : '손'})`,
      ].join('\n')
    },
  },
]

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

export function FxForwardValuationForm({ onSuccess, initialValues }: FxForwardValuationFormProps) {
  const mutation = useFxForwardValuationMutation()

  const {
    register,
    control,
    handleSubmit,
    getValues,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: initialValues ? { ...DEFAULT_VALUES, ...initialValues } : DEFAULT_VALUES,
  })

  // 처리 과정 단계: 0=대기, 1=시장데이터, 2=IRP계산, 3=공정가치산출, 4=완료
  const [progressStep, setProgressStep] = useState(0)
  // 진행 시 폼 값 스냅샷 (getDetail에서 계산에 사용)
  const [snapshotValues, setSnapshotValues] = useState<FormValues | null>(null)

  useEffect(() => {
    if (!mutation.isPending) {
      if (mutation.isSuccess) setProgressStep(4)
      else if (!mutation.isError) setProgressStep(0)
      return
    }
    setProgressStep(1)
    const t1 = setTimeout(() => setProgressStep(2), 400)
    const t2 = setTimeout(() => setProgressStep(3), 850)
    return () => { clearTimeout(t1); clearTimeout(t2) }
  }, [mutation.isPending, mutation.isSuccess, mutation.isError])

  const onSubmit = (data: FormValues) => {
    setSnapshotValues(data)
    mutation.mutate(data as FxForwardValuationRequest, { onSuccess })
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <Card
        title="통화선도 공정가치 평가"
        description="K-IFRS 1113호 Level 2 중심 — IRP(이자율 평형 이론) 기반 평가"
        variant="bordered"
        actions={
          <span className="text-xs text-blue-700 font-medium bg-blue-50 px-2 py-1 rounded">
            K-IFRS 1109호 6.5.8
          </span>
        }
      >
        <div className="mb-6 rounded-lg border border-sky-200 bg-sky-50 px-4 py-3">
          <p className="text-xs font-semibold uppercase tracking-wider text-sky-700 mb-1.5">
            1단계 엔진 범위
          </p>
          <div className="flex flex-wrap gap-x-6 gap-y-0.5 text-xs">
            <span className="text-sky-900 font-medium">✓ FX Forward · USD/KRW · Level 2 공정가치 (IRP)</span>
            <span className="text-sky-500">○ 2단계: IRS · CRS · 다른 통화쌍</span>
          </div>
          <p className="mt-2 text-xs text-sky-700">
            KRW ACT/365 · USD ACT/360 · 신용등급 → 헤지 적격성 조건 2에 반영
          </p>
        </div>

        {/* ── 계약 정보 ──────────────────────────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            계약 정보
          </legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field htmlFor="contractId" label="계약번호" error={errors.contractId?.message}>
              <input
                id="contractId"
                {...register('contractId')}
                className={inputClass}
                placeholder="FX-2026-001"
                aria-label="계약번호"
              />
            </Field>

            <Field htmlFor="notionalAmountUsd" label="명목원금 (USD)" error={errors.notionalAmountUsd?.message}>
              <Controller
                name="notionalAmountUsd"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="notionalAmountUsd"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder="10,000,000"
                    aria-label="명목원금(USD)"
                  />
                )}
              />
            </Field>

            <Field htmlFor="contractForwardRate" label="계약 선물환율 (KRW/USD)" error={errors.contractForwardRate?.message}>
              <Controller
                name="contractForwardRate"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="contractForwardRate"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder="1,300.00"
                    aria-label="계약 선물환율"
                  />
                )}
              />
            </Field>

            <Field htmlFor="contractDate" label="계약일" error={errors.contractDate?.message}>
              <input
                id="contractDate"
                {...register('contractDate')}
                type="date"
                className={inputClass}
                aria-label="계약일"
              />
            </Field>

            <Field htmlFor="maturityDate" label="만기일" error={errors.maturityDate?.message}>
              <input
                id="maturityDate"
                {...register('maturityDate')}
                type="date"
                className={inputClass}
                aria-label="만기일"
              />
            </Field>

            <Field htmlFor="hedgeDesignationDate" label="헤지 지정일" error={errors.hedgeDesignationDate?.message}>
              <input
                id="hedgeDesignationDate"
                {...register('hedgeDesignationDate')}
                type="date"
                className={inputClass}
                aria-label="헤지 지정일"
              />
            </Field>

            <Field
              htmlFor="counterpartyCreditRating"
              label="거래상대방 신용등급"
              error={errors.counterpartyCreditRating?.message}
              hint="K-IFRS B6.4.7: BBB 이상 = 투자등급. 이 값은 헤지 적격성 조건 2(신용위험 지배적 아님) 판단에 직접 반영됩니다."
            >
              <select
                id="counterpartyCreditRating"
                {...register('counterpartyCreditRating')}
                className={inputClass}
                aria-label="거래상대방 신용등급"
              >
                <optgroup label="투자등급 (Investment Grade)">
                  <option value="AAA">AAA — 최우량</option>
                  <option value="AA">AA — 우량</option>
                  <option value="A">A — 양호</option>
                  <option value="BBB">BBB — 적정 (투자등급 최저)</option>
                </optgroup>
                <optgroup label="비투자등급 (Non-Investment Grade)">
                  <option value="BB">BB — 투기</option>
                  <option value="B">B — 투기</option>
                  <option value="CCC">CCC — 부실 우려</option>
                  <option value="D">D — 채무불이행</option>
                </optgroup>
              </select>
            </Field>
          </div>
        </fieldset>

        {/* ── 평가 시장 데이터 ─────────────────────────────────────── */}
        <fieldset>
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            평가 시장 데이터
          </legend>
          <div className="mb-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
            <p className="text-xs text-slate-700">
              평가 입력 금리는 현재 엔진 기준으로 원화는 <strong>ACT/365</strong>, 달러는
              <strong> ACT/360</strong> 규칙을 적용합니다.
            </p>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field htmlFor="valuationDate" label="평가기준일" error={errors.valuationDate?.message}>
              <input
                id="valuationDate"
                {...register('valuationDate')}
                type="date"
                className={inputClass}
                aria-label="평가기준일"
              />
            </Field>

            <Field htmlFor="spotRate" label="현물환율 S₀ (KRW/USD)" error={errors.spotRate?.message}>
              <Controller
                name="spotRate"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="spotRate"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder="1,350.00"
                    aria-label="현물환율"
                  />
                )}
              />
            </Field>

            <Field
              htmlFor="krwInterestRate"
              label="원화이자율 r_KRW (연이율, 소수 — 예: 0.035)"
              error={errors.krwInterestRate?.message}
              hint="국고채 기준 무위험이자율 · ACT/365 Fixed (한국 자금시장 표준)"
            >
              <Controller
                name="krwInterestRate"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="krwInterestRate"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder="0.0350"
                    aria-label="원화이자율"
                  />
                )}
              />
            </Field>

            <Field
              htmlFor="usdInterestRate"
              label="달러이자율 r_USD (연이율, 소수, ACT/360 기준 — 예: 0.052)"
              error={errors.usdInterestRate?.message}
              hint="SOFR 기준 무위험이자율 · ACT/360 (USD 자금시장 국제 표준)"
            >
              <Controller
                name="usdInterestRate"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="usdInterestRate"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder="0.0520"
                    aria-label="달러이자율"
                  />
                )}
              />
            </Field>
          </div>
        </fieldset>

        {/* ── 에러 메시지 ───────────────────────────────────────────── */}
        {mutation.isError && (
          <MutationErrorAlert error={mutation.error} className="mt-4" />
        )}

        {/* ── 처리 과정 표시 ────────────────────────────────────────── */}
        {progressStep > 0 && (
          <ValuationProgress
            step={progressStep}
            formValues={snapshotValues ?? getValues()}
          />
        )}

        {/* ── 제출 버튼 ─────────────────────────────────────────────── */}
        <div className="mt-6 flex items-center justify-between">
          <p className="text-xs text-slate-400">
            K-IFRS 1113호 72~90항 · Level 2 관측가능 투입변수 기반 평가 · KRW ACT/365 · USD ACT/360
          </p>
          <Button type="submit" size="lg" loading={mutation.isPending}>
            공정가치 평가 실행
          </Button>
        </div>
      </Card>
    </form>
  )
}

// ─── ValuationProgress ───────────────────────────────────────────────────────

function ValuationProgress({
  step,
  formValues,
}: {
  step: number
  formValues: FormValues
}) {
  const isDone = step >= 4

  return (
    <div
      role="status"
      aria-live="polite"
      className="mt-5 border border-slate-200 rounded-lg overflow-hidden"
    >
      {/* 헤더 바 */}
      <div className={clsx(
        'px-5 py-3 flex items-center gap-3',
        isDone ? 'bg-emerald-900' : 'bg-slate-800',
      )}>
        {isDone ? (
          <svg className="w-4 h-4 text-emerald-400 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
          </svg>
        ) : (
          <svg className="w-4 h-4 text-blue-400 animate-spin flex-shrink-0" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
          </svg>
        )}
        <span className="text-sm font-semibold text-white tracking-wide">
          {isDone ? '평가 완료 — 결과를 확인하세요' : '처리 중 — IRP 기반 공정가치 계산'}
        </span>
        {isDone && (
          <span className="ml-auto text-xs text-emerald-300 font-financial">
            K-IFRS 1113호 Level 2
          </span>
        )}
      </div>

      {/* 단계별 로그 */}
      <div className="bg-slate-950 px-5 py-4 space-y-4 font-mono text-xs">
        {PROGRESS_STEPS.map((s, idx) => {
          const stepNum = idx + 1
          const isActive   = step === stepNum
          const isDoneStep = step > stepNum || isDone

          return (
            <div key={stepNum}>
              {/* 단계 헤더 */}
              <div className="flex items-center gap-2 mb-1.5">
                {isDoneStep ? (
                  <span className="text-emerald-400">✓</span>
                ) : isActive ? (
                  <span className="text-blue-400 animate-pulse">⏳</span>
                ) : (
                  <span className="text-slate-600">○</span>
                )}
                <span className={clsx(
                  'font-semibold',
                  isDoneStep && 'text-emerald-400',
                  isActive   && 'text-blue-300',
                  !isDoneStep && !isActive && 'text-slate-600',
                )}>
                  STEP {stepNum}: {s.label}
                </span>
              </div>

              {/* 공식 */}
              {(isActive || isDoneStep) && (
                <div className="ml-6">
                  <p className="text-slate-400 mb-1">
                    <span className="text-slate-600">&gt;</span> {s.formula}
                  </p>
                  {/* 실제 계산값 (완료 단계만 표시) */}
                  {isDoneStep && (
                    <pre className={clsx(
                      'mt-1 pl-3 border-l-2 text-emerald-300 leading-relaxed whitespace-pre-wrap',
                      'border-emerald-800',
                    )}>
                      {s.getDetail(formValues)}
                    </pre>
                  )}
                  {/* 진행 중 애니메이션 */}
                  {isActive && (
                    <div className="flex items-center gap-1.5 mt-1 pl-3">
                      <span className="inline-block w-1.5 h-1.5 rounded-full bg-blue-400 animate-bounce" style={{ animationDelay: '0ms' }} />
                      <span className="inline-block w-1.5 h-1.5 rounded-full bg-blue-400 animate-bounce" style={{ animationDelay: '150ms' }} />
                      <span className="inline-block w-1.5 h-1.5 rounded-full bg-blue-400 animate-bounce" style={{ animationDelay: '300ms' }} />
                    </div>
                  )}
                </div>
              )}
            </div>
            )
          })}
        </div>
      </div>
  )
}

// ─── Field 헬퍼 ──────────────────────────────────────────────────────────────

function Field({
  label,
  htmlFor,
  hint,
  error,
  children,
}: {
  label: string
  htmlFor?: string
  hint?: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div>
      <label htmlFor={htmlFor} className="block text-xs font-medium text-slate-600 mb-1.5">
        {label}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-slate-400 mt-1">{hint}</p>}
      {error && (
        <p role="alert" className="text-xs text-red-600 mt-1">
          {error}
        </p>
      )}
    </div>
  )
}
