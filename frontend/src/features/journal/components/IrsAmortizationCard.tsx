import { useState, type ReactNode } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Button } from '@/components/ui/Button'
import { NumericInput } from '@/components/ui/NumericInput'
import { MutationErrorAlert } from '@/components/ui/ErrorAlert'
import { JournalEntryResult } from './JournalEntryResult'
import { useIrsAmortizationMutation } from '../api/useJournalEntry'
import type { JournalEntryResponse } from '@/types/journal'

// ─── Zod 스키마 ──────────────────────────────────────────────────────────────

const schema = z.object({
  hedgeRelationshipId: z.string().min(1, '위험회피관계 ID를 입력하세요.').max(100),
  amortizationDate:    z.string().min(1, '상각 기준일을 입력하세요.'),
  cumulativeAdjBalance: z.coerce.number({ invalid_type_error: '숫자를 입력하세요.' }),
  remainingPeriods: z.coerce
    .number({ invalid_type_error: '숫자를 입력하세요.' })
    .int('정수를 입력하세요.')
    .min(1, '잔여 기간은 1 이상이어야 합니다.'),
})

type FormValues = z.infer<typeof schema>

const _d = (): string => new Date().toISOString().slice(0, 10)

const DEFAULT_VALUES: FormValues = {
  hedgeRelationshipId:  '',
  amortizationDate:     _d(),
  cumulativeAdjBalance: 0,
  remainingPeriods:     4,   // 분기별 상각 기본 4회 (1년)
}

// ─── 공통 input 클래스 ────────────────────────────────────────────────────────

const inputClass =
  'w-full border border-slate-300 rounded-md px-3 py-2 text-sm text-slate-900 ' +
  'focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-blue-600 ' +
  'disabled:bg-slate-50 disabled:text-slate-400 font-financial'

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

/**
 * IRS FVH 장부조정상각 분개 생성 — 접이식 카드.
 *
 * 기본 화면은 자동분개 이력 조회가 주심사이므로, 이 카드는 접힌 상태로 시작한다.
 * 심사관이 상각 분개를 직접 생성해야 할 때만 펼쳐서 입력한다.
 *
 * API: POST /api/v1/journal-entries/irs-fvh-amortization
 *
 * @see K-IFRS 1109호 6.5.9 (공정가치위험회피 피헤지항목 장부금액 조정 상각)
 * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 회계처리 — 수단 손익 P&L 인식)
 */
export function IrsAmortizationCard() {
  const [isOpen, setIsOpen] = useState(false)
  const [result, setResult] = useState<JournalEntryResponse[] | null>(null)
  const mutation = useIrsAmortizationMutation()

  const {
    register,
    control,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: DEFAULT_VALUES,
  })

  const cumulativeBalance = watch('cumulativeAdjBalance')
  const remainingPeriods  = watch('remainingPeriods')
  const periodicAmt = remainingPeriods > 0
    ? Math.abs(cumulativeBalance / remainingPeriods)
    : 0

  const handleSampleData = () => {
    setValue('hedgeRelationshipId',  'HR-IRS-2026-001')
    setValue('amortizationDate',     _d())
    setValue('cumulativeAdjBalance', 3_900_000)
    setValue('remainingPeriods',     4)
  }

  const onSubmit = (data: FormValues) => {
    mutation.mutate(data, {
      onSuccess: (entries) => {
        setResult(entries)
      },
    })
  }

  return (
    <section
      aria-label="IRS FVH 장부조정상각 분개 생성"
      className="rounded-xl border border-blue-200 bg-white overflow-hidden"
    >
      {/* ── 헤더 (토글) ──────────────────────────────────────────────── */}
      <button
        type="button"
        onClick={() => { setIsOpen((v) => !v); setResult(null) }}
        className="w-full flex items-center justify-between px-6 py-4 text-left
                   hover:bg-blue-50 transition-colors focus:outline-none focus-visible:ring-2
                   focus-visible:ring-blue-600 focus-visible:ring-inset"
        aria-expanded={isOpen}
        aria-controls="irs-amortization-body"
      >
        <div className="flex items-center gap-3 flex-wrap">
          {/* 아이콘 */}
          <span className="w-8 h-8 rounded-lg bg-blue-100 flex items-center justify-center text-blue-700 shrink-0">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
            </svg>
          </span>

          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-sm font-bold text-slate-800">
                IRS 상각 분개 생성
              </span>
              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold
                               bg-blue-100 text-blue-800 border border-blue-200">
                IRS-FVH 장부조정상각
              </span>
              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-mono font-semibold
                               bg-white text-blue-600 border border-blue-300">
                K-IFRS 1109호 §6.5.9
              </span>
              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold
                               bg-blue-700 text-white">
                2단계
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              {isOpen ? '접기' : '피헤지항목 장부금액 조정액을 잔여기간에 걸쳐 균등 상각하는 분개를 직접 생성합니다.'}
            </p>
          </div>
        </div>

        {/* 캐럿 */}
        <svg
          className={`w-5 h-5 text-slate-400 transition-transform duration-200 shrink-0 ml-2 ${isOpen ? 'rotate-180' : ''}`}
          viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"
        >
          <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
        </svg>
      </button>

      {/* ── 바디 ──────────────────────────────────────────────────────── */}
      {isOpen && (
        <div id="irs-amortization-body" className="border-t border-blue-100">

          {/* K-IFRS §6.5.9 원칙 안내 */}
          <div className="px-6 py-4 bg-blue-50 border-b border-blue-100">
            <div className="flex items-start gap-3">
              <svg className="w-4 h-4 text-blue-500 shrink-0 mt-0.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
              </svg>
              <div className="space-y-1.5">
                <p className="text-xs font-semibold text-blue-800 uppercase tracking-wide">
                  K-IFRS 1109호 §6.5.9 — 피헤지항목 장부금액 조정 상각
                </p>
                <p className="text-xs text-blue-700 leading-relaxed">
                  공정가치위험회피(FVH)에서 피헤지항목의 장부금액을 조정한 금액은,
                  위험회피가 <strong>만기까지 계속되거나 중단</strong>된 경우에 관계없이
                  남은 만기에 걸쳐 <strong>유효이자율법 또는 균등법으로 상각</strong>하여
                  당기손익으로 인식합니다.
                </p>
                <div className="flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-blue-600">
                  <span>
                    <code className="font-mono bg-blue-100 px-1 rounded">§6.5.9</code>
                    {' '}— 장부조정상각 의무 (위험회피 계속 또는 중단 후 모두 적용)
                  </span>
                  <span>
                    <code className="font-mono bg-blue-100 px-1 rounded">§6.5.8</code>
                    {' '}— 수단·항목 공정가치 변동 P&amp;L 즉시 인식 (당기)
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* 폼 */}
          {result ? (
            // 성공 결과 표시
            <div className="p-6 space-y-4">
              <div className="flex items-center gap-3 px-4 py-3 rounded-lg bg-emerald-50 border border-emerald-200">
                <span className="text-lg">✅</span>
                <div>
                  <p className="text-sm font-bold text-emerald-800">상각 분개 생성 완료</p>
                  <p className="text-xs text-emerald-700 mt-0.5">{result.length}건 생성됨 · 분개 이력에 자동 반영됩니다.</p>
                </div>
                <button
                  type="button"
                  onClick={() => setResult(null)}
                  className="ml-auto text-xs font-medium text-emerald-700 hover:text-emerald-900 underline"
                >
                  다시 생성
                </button>
              </div>
              <JournalEntryResult entries={result} />
            </div>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="p-6 space-y-5">

              {/* 예시값 버튼 */}
              <div className="flex items-center justify-between">
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
                  상각 분개 입력
                </p>
                <button
                  type="button"
                  onClick={handleSampleData}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold
                             bg-slate-700 text-white hover:bg-slate-900 transition-colors"
                >
                  <svg className="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-11a1 1 0 10-2 0v2H7a1 1 0 100 2h2v2a1 1 0 102 0v-2h2a1 1 0 100-2h-2V7z" clipRule="evenodd" />
                  </svg>
                  예시값 채우기
                </button>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* 헤지관계 ID */}
                <Field
                  htmlFor="amort-hedgeRelationshipId"
                  label="위험회피관계 ID"
                  hint="IRS FVH 헤지 지정 시 생성된 관계 ID"
                  error={errors.hedgeRelationshipId?.message}
                >
                  <input
                    id="amort-hedgeRelationshipId"
                    {...register('hedgeRelationshipId')}
                    type="text"
                    placeholder="예: HR-IRS-2026-001"
                    className={inputClass}
                  />
                </Field>

                {/* 상각 기준일 */}
                <Field
                  htmlFor="amort-amortizationDate"
                  label="상각 기준일"
                  hint="보고기간 말 또는 상각 처리일"
                  error={errors.amortizationDate?.message}
                >
                  <input
                    id="amort-amortizationDate"
                    {...register('amortizationDate')}
                    type="date"
                    className={inputClass}
                  />
                </Field>

                {/* 누적 장부조정잔액 */}
                <Field
                  htmlFor="amort-cumulativeAdjBalance"
                  label="누적 장부조정잔액 (KRW)"
                  hint="양수 = 조정 증가(장부 상승), 음수 = 조정 감소. K-IFRS 6.5.9"
                  error={errors.cumulativeAdjBalance?.message}
                >
                  <Controller
                    name="cumulativeAdjBalance"
                    control={control}
                    render={({ field }) => (
                      <NumericInput
                        id="amort-cumulativeAdjBalance"
                        value={field.value}
                        onNumericChange={(n) => field.onChange(n ?? 0)}
                        className={inputClass}
                        placeholder="3,900,000"
                        aria-label="누적 장부조정잔액"
                      />
                    )}
                  />
                </Field>

                {/* 잔여 기간 수 */}
                <Field
                  htmlFor="amort-remainingPeriods"
                  label="잔여 기간 수 (회차)"
                  hint="남은 분기 수 · 상각액 = 잔액 ÷ 잔여기간"
                  error={errors.remainingPeriods?.message}
                >
                  <input
                    id="amort-remainingPeriods"
                    {...register('remainingPeriods')}
                    type="number"
                    min={1}
                    step={1}
                    className={inputClass}
                  />
                </Field>
              </div>

              {/* 당기 상각액 미리보기 */}
              {cumulativeBalance !== 0 && remainingPeriods > 0 && (
                <div className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 flex items-center justify-between gap-4">
                  <div>
                    <p className="text-xs font-semibold text-blue-800 mb-0.5">당기 상각액 (미리보기)</p>
                    <p className="text-xs text-blue-600">
                      {cumulativeBalance.toLocaleString('ko-KR')} ÷ {remainingPeriods} =&nbsp;
                      <strong className="text-blue-900 font-financial tabular-nums">
                        {periodicAmt.toLocaleString('ko-KR', { maximumFractionDigits: 0 })} KRW
                      </strong>
                    </p>
                  </div>
                  <span className="text-xs font-mono font-semibold text-blue-700 bg-white border border-blue-200 px-2 py-0.5 rounded">
                    §6.5.9 균등법
                  </span>
                </div>
              )}

              {mutation.isError && (
                <MutationErrorAlert error={mutation.error} />
              )}

              <div className="flex justify-end">
                <Button type="submit" size="lg" loading={mutation.isPending}>
                  IRS 상각 분개 생성
                </Button>
              </div>
            </form>
          )}
        </div>
      )}
    </section>
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
  children: ReactNode
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
