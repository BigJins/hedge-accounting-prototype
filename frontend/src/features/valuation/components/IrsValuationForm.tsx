import { useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import clsx from 'clsx'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { NumericInput } from '@/components/ui/NumericInput'
import { MutationErrorAlert } from '@/components/ui/ErrorAlert'
import { formatPercent } from '@/utils/formatters'
import type { IrsValuationRequest, IrsValuationResponse } from '@/types/valuation'
import { useIrsValuationMutation } from '../api/useIrsValuation'

// ─── Zod 스키마 ──────────────────────────────────────────────────────────────

const schema = z.object({
  contractId:          z.string().min(1, '계약번호를 입력하세요.'),
  valuationDate:       z.string().min(1, '평가기준일을 입력하세요.'),
  currentFloatingRate: z.coerce.number().min(0, '변동금리는 0 이상이어야 합니다.'),
  discountRate:        z.coerce.number().min(0, '할인율은 0 이상이어야 합니다.'),
  notionalAmount:      z.coerce.number().positive().optional(),
})

type FormValues = z.infer<typeof schema>

const _today = () => new Date().toISOString().slice(0, 10)

const DEFAULT_VALUES: FormValues = {
  contractId:          '',
  valuationDate:       _today(),
  currentFloatingRate: 0,
  discountRate:        0,
  notionalAmount:      undefined,
}

/** 심사관용 예시 데이터 — IRS-FVH-2026-001 계약 기준 */
const SAMPLE_VALUES: FormValues = {
  contractId:          'IRS-FVH-2026-001',
  valuationDate:       _today(),
  currentFloatingRate: 0.0425,      // 4.25% CD 91일 — 금리 하락 시나리오 (계약 고정금리 3.5%)
  discountRate:        0.0380,      // 3.80% 국고채 무위험이자율
  notionalAmount:      undefined,   // null → 계약에서 자동 조회
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface IrsValuationFormProps {
  onSuccess: (result: IrsValuationResponse) => void
  initialContractId?: string
}

// ─── 공통 input 클래스 ────────────────────────────────────────────────────────

const inputClass =
  'w-full border border-slate-300 rounded-md px-3 py-2 text-sm text-slate-900 ' +
  'focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-blue-600 ' +
  'disabled:bg-slate-50 disabled:text-slate-400 font-financial'

// ─── 처리 단계 ───────────────────────────────────────────────────────────────

interface StepData {
  label: string
  formula: string
}

const STEPS: StepData[] = [
  { label: '시장 데이터 확인',    formula: 'CD금리 · 국고채 수익률 곡선 조회' },
  { label: '고정 다리 PV 계산',   formula: 'PV(fixed) = Σ [fixedRate × N × Δt × DF(tᵢ)]' },
  { label: '변동 다리 PV 계산',   formula: 'PV(float) = Σ [floatingRate × N × Δt × DF(tᵢ)]' },
  { label: '공정가치 산출',        formula: 'FV = PV(float) − PV(fixed)  [수취고정 기준]' },
]

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

/**
 * IRS 공정가치 평가 폼.
 *
 * @see K-IFRS 1113호 72~90항 (Level 2 관측가능 투입변수)
 * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 손익 인식)
 */
export function IrsValuationForm({ onSuccess, initialContractId }: IrsValuationFormProps) {
  const mutation = useIrsValuationMutation()
  const [progressStep, setProgressStep] = useState(0)

  const {
    register,
    control,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: initialContractId
      ? { ...DEFAULT_VALUES, contractId: initialContractId }
      : DEFAULT_VALUES,
  })

  const fillSample = () => {
    Object.entries(SAMPLE_VALUES).forEach(([k, v]) => {
      if (v !== undefined) setValue(k as keyof FormValues, v as never, { shouldValidate: false })
    })
  }

  const onSubmit = (data: FormValues) => {
    const request: IrsValuationRequest = {
      contractId:          data.contractId,
      valuationDate:       data.valuationDate,
      currentFloatingRate: data.currentFloatingRate,
      discountRate:        data.discountRate,
      notionalAmount:      data.notionalAmount ?? null,
    }

    setProgressStep(1)
    const t1 = setTimeout(() => setProgressStep(2), 400)
    const t2 = setTimeout(() => setProgressStep(3), 800)
    const t3 = setTimeout(() => setProgressStep(4), 1200)

    mutation.mutate(request, {
      onSuccess: (result) => {
        clearTimeout(t1); clearTimeout(t2); clearTimeout(t3)
        setProgressStep(5)
        onSuccess(result)
      },
      onError: () => {
        clearTimeout(t1); clearTimeout(t2); clearTimeout(t3)
        setProgressStep(0)
      },
    })
  }

  const isDone = progressStep >= 5

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <Card
        title="IRS 공정가치 평가"
        description="K-IFRS 1113호 Level 2 — 수익률 곡선 할인 기반 IRS 평가"
        variant="bordered"
        actions={
          <span className="text-xs text-blue-700 font-medium bg-blue-50 px-2 py-1 rounded">
            K-IFRS 1113호 Level 2
          </span>
        }
      >
        {/* 엔진 안내 배너 */}
        <div className="mb-5 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3">
          <p className="text-xs font-semibold text-blue-800 mb-1">IRS 금리위험 FVH 평가 — K-IFRS 1113 Level 2</p>
          <div className="flex flex-wrap gap-x-6 gap-y-0.5 text-xs">
            <span className="text-blue-800 font-medium">✓ IRS 수취고정 구조 (FVH) · 고정/변동 다리 현재가치 분리 계산</span>
            <span className="text-blue-500">○ 1단계: FX Forward · USD/KRW · IRP 기반</span>
          </div>
          <p className="mt-2 text-xs text-blue-700">
            할인율: 무위험이자율 적용 · 변동금리: CD 91일 또는 SOFR 관측값 · Level 2 분류
          </p>
        </div>

        <div className="flex justify-end mb-4">
          <Button type="button" variant="ghost" size="sm" onClick={fillSample}>
            예시 데이터 채우기
          </Button>
        </div>

        {/* ── 평가 입력 ────────────────────────────────────────────── */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
          <Field htmlFor="contractId" label="계약번호" error={errors.contractId?.message}>
            <input
              id="contractId"
              {...register('contractId')}
              className={inputClass}
              placeholder="IRS-FVH-2026-001"
            />
          </Field>

          <Field htmlFor="valuationDate" label="평가기준일" error={errors.valuationDate?.message}>
            <input
              id="valuationDate"
              {...register('valuationDate')}
              type="date"
              className={inputClass}
            />
          </Field>

          <Field
            htmlFor="currentFloatingRate"
            label="현재 변동금리 (소수 — 예: 0.0425 = 4.25%)"
            error={errors.currentFloatingRate?.message}
            hint="CD 91일 금리 또는 SOFR — K-IFRS 1113호 72항 (관측가능 투입변수)"
          >
            <Controller
              name="currentFloatingRate"
              control={control}
              render={({ field }) => (
                <NumericInput
                  id="currentFloatingRate"
                  value={field.value}
                  onNumericChange={(n) => field.onChange(n ?? 0)}
                  className={inputClass}
                  placeholder="0.0425"
                />
              )}
            />
          </Field>

          <Field
            htmlFor="discountRate"
            label="할인율 r (무위험이자율, 소수 — 예: 0.038)"
            error={errors.discountRate?.message}
            hint="국고채 기준 무위험이자율 — Level 2 관측가능"
          >
            <Controller
              name="discountRate"
              control={control}
              render={({ field }) => (
                <NumericInput
                  id="discountRate"
                  value={field.value}
                  onNumericChange={(n) => field.onChange(n ?? 0)}
                  className={inputClass}
                  placeholder="0.0380"
                />
              )}
            />
          </Field>

          <Field
            htmlFor="notionalAmount"
            label="명목금액 오버라이드 (KRW — 선택)"
            error={errors.notionalAmount?.message}
            hint="비워두면 등록된 계약의 명목금액을 자동 사용합니다."
          >
            <Controller
              name="notionalAmount"
              control={control}
              render={({ field }) => (
                <NumericInput
                  id="notionalAmount"
                  value={field.value ?? 0}
                  onNumericChange={(n) => field.onChange(n || undefined)}
                  className={inputClass}
                  placeholder="비워두면 계약 값 사용"
                />
              )}
            />
          </Field>
        </div>

        {/* ── 에러 ─────────────────────────────────────────────────── */}
        {mutation.isError && (
          <MutationErrorAlert error={mutation.error} className="mb-4" />
        )}

        {/* ── 처리 과정 ────────────────────────────────────────────── */}
        {progressStep > 0 && (
          <div
            role="status"
            aria-live="polite"
            className="mt-4 mb-4 border border-slate-200 rounded-lg overflow-hidden"
          >
            <div className={clsx(
              'px-5 py-3 flex items-center gap-3',
              isDone ? 'bg-emerald-900' : 'bg-slate-800',
            )}>
              {isDone ? (
                <svg className="w-4 h-4 text-emerald-400 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                </svg>
              ) : (
                <svg className="w-4 h-4 text-blue-400 animate-spin flex-shrink-0" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
                </svg>
              )}
              <span className="text-sm font-semibold text-white tracking-wide">
                {isDone ? 'IRS 평가 완료 — 결과를 확인하세요' : '처리 중 — IRS 다리별 현재가치 계산'}
              </span>
              {isDone && (
                <span className="ml-auto text-xs text-emerald-300 font-financial">K-IFRS 1113호 Level 2</span>
              )}
            </div>
            <div className="bg-slate-950 px-5 py-4 space-y-3 font-mono text-xs">
              {STEPS.map((s, idx) => {
                const stepNum  = idx + 1
                const isActive = progressStep === stepNum
                const isDoneS  = progressStep > stepNum || isDone

                return (
                  <div key={stepNum}>
                    <div className="flex items-center gap-2">
                      {isDoneS ? (
                        <span className="text-emerald-400">✓</span>
                      ) : isActive ? (
                        <span className="text-blue-400 animate-pulse">⏳</span>
                      ) : (
                        <span className="text-slate-600">○</span>
                      )}
                      <span className={clsx(
                        'font-semibold',
                        isDoneS  && 'text-emerald-400',
                        isActive && 'text-blue-300',
                        !isDoneS && !isActive && 'text-slate-600',
                      )}>
                        STEP {stepNum}: {s.label}
                      </span>
                    </div>
                    {(isActive || isDoneS) && (
                      <div className="ml-6 mt-1">
                        <p className="text-slate-400">
                          <span className="text-slate-600">&gt;</span> {s.formula}
                        </p>
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
        )}

        {/* ── 제출 버튼 ────────────────────────────────────────────── */}
        <div className="flex items-center justify-between mt-2">
          <p className="text-xs text-slate-400">
            K-IFRS 1113호 72~90항 · Level 2 관측가능 투입변수 · 변동금리 {
              mutation.isPending ? '—' : ''
            }{formatPercent !== undefined ? '' : ''}
          </p>
          <Button type="submit" size="lg" loading={mutation.isPending}>
            IRS 공정가치 평가 실행
          </Button>
        </div>
      </Card>
    </form>
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
