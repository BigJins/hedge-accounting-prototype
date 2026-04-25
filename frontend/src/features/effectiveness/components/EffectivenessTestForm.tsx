import { useState, type ReactNode } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Card } from '@/components/ui/Card'
import { MutationErrorAlert } from '@/components/ui/ErrorAlert'
import { Button } from '@/components/ui/Button'
import { NumericInput } from '@/components/ui/NumericInput'
import { HedgeRelationshipSelector } from '@/features/hedge/components/HedgeRelationshipSelector'
import type { HedgeRelationshipSummary } from '@/types/hedge'
import type { EffectivenessTestRequest, EffectivenessTestResponse } from '@/types/effectiveness'
import { useEffectivenessTestMutation } from '../api/useEffectivenessTest'

// ─── Zod 스키마 ──────────────────────────────────────────────────────────────
//
// 백엔드는 둘 다 0 을 실제로 거부하지는 않지만 (hedgedItemPv ≤ 0.0001 은 분모 0
// 처리로 자동 PASS 반환), 두 변동액이 모두 0 이면 "이번 기간에 아무 일도 없었다"
// 는 뜻이라 유효성 테스트 자체의 의미가 없다.
// 심사관이 기본값(0, 0)에서 바로 제출 버튼을 눌렀을 때 백엔드까지 보내지 않고
// 프런트에서 차단하기 위해 superRefine 으로 양쪽 필드에 동시에 에러를 표시한다.
const BOTH_ZERO_MESSAGE =
  '두 변동액이 모두 0이면 유효성 테스트를 수행할 수 없습니다. 최소 한 쪽은 실제 당기 변동 금액을 입력하세요.'

const schema = z
  .object({
    hedgeRelationshipId: z.string().min(1, '위험회피관계 ID를 입력하세요.').max(100),
    testDate:            z.string().min(1, '평가 기준일을 입력하세요.'),
    testType:            z.enum(['DOLLAR_OFFSET_PERIODIC', 'DOLLAR_OFFSET_CUMULATIVE']),
    hedgeType:           z.enum(['FAIR_VALUE', 'CASH_FLOW']),
    instrumentFvChange:  z.coerce.number({ invalid_type_error: '숫자를 입력하세요.' }),
    hedgedItemPvChange:  z.coerce.number({ invalid_type_error: '숫자를 입력하세요.' }),
    instrumentType:      z.enum(['FX_FORWARD', 'IRS', 'CRS']).optional(),
  })
  .superRefine((data, ctx) => {
    if (data.instrumentFvChange === 0 && data.hedgedItemPvChange === 0) {
      // 두 필드 모두 붉게 표시되도록 각 path 에 동일 이슈 추가
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: BOTH_ZERO_MESSAGE,
        path: ['instrumentFvChange'],
      })
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: BOTH_ZERO_MESSAGE,
        path: ['hedgedItemPvChange'],
      })
    }
  })

type FormValues = z.infer<typeof schema>

const _d = (offset: number): string => {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return d.toISOString().slice(0, 10)
}

const DEFAULT_VALUES: FormValues = {
  hedgeRelationshipId: '',
  testDate:            _d(0),
  testType:            'DOLLAR_OFFSET_PERIODIC',
  hedgeType:           'CASH_FLOW',
  instrumentFvChange:  0,
  hedgedItemPvChange:  0,
  instrumentType:      undefined,
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface EffectivenessTestFormProps {
  onSuccess: (result: EffectivenessTestResponse) => void
  /** 재테스트 시 폼을 초기화할 기본값 */
  initialValues?: Partial<FormValues>
}

// ─── 공통 input 클래스 ────────────────────────────────────────────────────────

const inputClass =
  'w-full border border-slate-300 rounded-md px-3 py-2 text-sm text-slate-900 ' +
  'focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-blue-600 ' +
  'disabled:bg-slate-50 disabled:text-slate-400 font-financial'

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

/**
 * 유효성 테스트 실행 폼.
 *
 * Dollar-offset 방법으로 위험회피 유효성을 평가합니다.
 *
 * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 평가)
 * @see K-IFRS 1109호 B6.4.13 (Dollar-offset 방법 허용)
 */
export function EffectivenessTestForm({ onSuccess, initialValues }: EffectivenessTestFormProps) {
  const mutation = useEffectivenessTestMutation()
  const [showAdvancedOptions, setShowAdvancedOptions] = useState(
    initialValues?.testType === 'DOLLAR_OFFSET_CUMULATIVE',
  )

  const {
    register,
    control,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: initialValues ? { ...DEFAULT_VALUES, ...initialValues } : DEFAULT_VALUES,
  })
  const selectedTestType       = watch('testType')
  const selectedHedgeType      = watch('hedgeType')
  const selectedInstrumentType = watch('instrumentType')

  /**
   * 위험회피관계 선택 시 hedgeType + instrumentType 자동 동기화.
   *
   * 백엔드 `EffectivenessTestService.validateRequestedHedgeType` 가 요청의
   * hedgeType 과 관계의 hedgeType 불일치를 거부하므로, 선택 즉시 요약에서
   * hedgeType 을 읽어 폼에 반영해 불일치 실수를 원천 차단한다.
   *
   * IRS 금리위험 관계(hedgedRisk === 'INTEREST_RATE') 선택 시:
   * instrumentType 을 'IRS' 로 자동 세팅하고 고급 옵션 패널을 열어
   * 사용자에게 IRS FVH 엔진 가이드를 노출한다.
   *
   * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 — IRS 평가손익 P&L 인식)
   */
  const handleRelationshipSelected = (rel: HedgeRelationshipSummary | null) => {
    if (!rel) return
    setValue('hedgeType', rel.hedgeType, { shouldDirty: true, shouldValidate: true })
    if (rel.hedgedRisk === 'INTEREST_RATE') {
      // IRS FVH 관계: instrumentType=IRS 자동 세팅 + 고급 패널 전개
      setValue('instrumentType', 'IRS', { shouldDirty: true })
      setShowAdvancedOptions(true)
    } else {
      // 비 IRS 관계 재선택 시 instrumentType 초기화
      setValue('instrumentType', undefined, { shouldDirty: true })
    }
  }

  const onSubmit = (data: FormValues) => {
    mutation.mutate(data as EffectivenessTestRequest, { onSuccess })
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <Card
        title="유효성 테스트 (Dollar-offset)"
        description="K-IFRS 1109호 B6.4.12 — 매 보고기간 말 위험회피 유효성 평가"
        variant="bordered"
        actions={
          <span className="text-xs text-blue-700 font-medium bg-blue-50 px-2 py-1 rounded">
            유효성 검증 · K-IFRS 1109호 B6.4.12
          </span>
        }
      >
        {/* ── 헤지관계 정보 ───────────────────────────────────────── */}
        <fieldset className="mb-6">
          <input type="hidden" {...register('testType')} />
          <input type="hidden" {...register('hedgeType')} />
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            헤지관계 정보
          </legend>

          {/*
            관계 ID 는 드롭다운 셀렉터로 선택한다.
            이전엔 UUID 를 직접 붙여넣어야 했으나, 이제 「헤지 지정」 이력을 그대로
            목록으로 노출해 외우거나 복사할 필요가 없다. 선택 시 hedgeType 도 자동
            동기화되어 백엔드 불일치(HD_010) 를 원천 차단한다.
          */}
          <div className="mb-4">
            <Field
              htmlFor="hedgeRelationshipId"
              label="위험회피관계"
              hint="「헤지 지정」에서 등록한 관계 중 하나를 선택하세요. 선택 즉시 위험회피 유형도 자동으로 맞춰집니다."
              error={errors.hedgeRelationshipId?.message}
            >
              <Controller
                name="hedgeRelationshipId"
                control={control}
                render={({ field }) => (
                  <HedgeRelationshipSelector
                    inputId="hedgeRelationshipId"
                    value={field.value}
                    onChange={field.onChange}
                    onRelationshipChange={handleRelationshipSelected}
                    error={errors.hedgeRelationshipId?.message}
                  />
                )}
              />
            </Field>
          </div>

          {/* 자동 맞춤 상태 표시 — 관계 선택 후에만 의미 있음 */}
          <div className="mb-4 flex items-center gap-2 px-3 py-2 rounded-md bg-blue-50 border border-blue-100 text-xs text-blue-700">
            <svg className="w-3.5 h-3.5 flex-shrink-0 text-blue-500" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
            </svg>
            <span>
              관계 선택 시 위험회피 유형과 수단 유형이 자동 설정됩니다.
              현재: <strong>{selectedHedgeType === 'FAIR_VALUE' ? '공정가치 위험회피' : '현금흐름 위험회피'}</strong>
              {selectedInstrumentType === 'IRS' && (
                <> · <strong className="text-blue-900">IRS FVH 자동 감지됨</strong></>
              )}
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field htmlFor="testDate" label="평가 기준일" error={errors.testDate?.message}>
              <input
                id="testDate"
                {...register('testDate')}
                type="date"
                className={inputClass}
                aria-label="평가 기준일"
              />
            </Field>

            <Field label="테스트 방법">
              <div className="rounded-md border border-slate-300 bg-slate-50 px-3 py-2.5">
                <p className="text-sm font-medium text-slate-900">
                  기간별 Dollar-offset (Periodic)
                </p>
                <p className="text-xs text-slate-500 mt-1">
                  1단계 엔진의 일반 사용자 흐름에서는 기간별 테스트를 기본값으로 사용합니다.
                </p>
              </div>
            </Field>
          </div>

          <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
            <button
              type="button"
              onClick={() => setShowAdvancedOptions((prev) => !prev)}
              className="flex w-full items-center justify-between text-left"
              aria-expanded={showAdvancedOptions}
              aria-controls="advanced-effectiveness-options"
            >
              <span>
                <span className="block text-xs font-semibold uppercase tracking-wider text-slate-500">
                  고급 옵션
                </span>
                <span className="block text-sm text-slate-700 mt-1">
                  누적 Dollar-offset (Cumulative) — 보조 분석용
                </span>
              </span>
              <svg
                className={`w-4 h-4 text-slate-400 transition-transform ${showAdvancedOptions ? 'rotate-180' : ''}`}
                viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"
              >
                <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
              </svg>
            </button>

            {showAdvancedOptions && (
              <div id="advanced-effectiveness-options" className="mt-4 space-y-4">
                <div className="rounded-md bg-amber-50 border border-amber-200 px-3 py-2.5 text-xs text-amber-700">
                  <p className="font-semibold mb-1">누적 방식 사용 시 유의사항</p>
                  <p>누적 Dollar-offset은 헤지 개시 이후 전체 기간의 변동을 합산합니다. 기간별 PASS/FAIL 변동이 심해 보조 검증이 필요한 경우에만 사용하세요. 일반적인 분기 말 평가는 기간별(Periodic)을 사용합니다.</p>
                </div>
                <Field htmlFor="testTypeAdvanced" label="테스트 방법 선택" error={errors.testType?.message}>
                  <select
                    id="testTypeAdvanced"
                    value={selectedTestType}
                    onChange={(event) => {
                      setValue('testType', event.target.value as FormValues['testType'], {
                        shouldDirty: true,
                        shouldValidate: true,
                      })
                    }}
                    className={inputClass}
                    aria-label="고급 테스트 방법"
                  >
                    <option value="DOLLAR_OFFSET_PERIODIC">기간별 Dollar-offset (Periodic) — 표준</option>
                    <option value="DOLLAR_OFFSET_CUMULATIVE">누적 Dollar-offset (Cumulative) — 보조</option>
                  </select>
                </Field>
                {selectedTestType === 'DOLLAR_OFFSET_CUMULATIVE' && (
                  <p className="text-xs text-amber-600 font-medium">
                    ⚠ 누적 방식 적용 중 — 헤지 개시 이후 전체 누적 변동액을 사용합니다.
                  </p>
                )}

                {/* ── 위험회피수단 유형 (2단계 확장) ─────────────────── */}
                <div className="border-t border-amber-100 pt-4">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                      위험회피수단 유형
                    </span>
                    <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-bold bg-blue-100 text-blue-700 border border-blue-200">
                      2단계
                    </span>
                  </div>
                  <p className="text-xs text-slate-500 mb-2.5">
                    <strong>1단계 시연 범위:</strong> FX Forward (USD/KRW) 중심.{' '}
                    IRS FVH는 2단계 백엔드가 구현되어 선택 가능합니다.
                    CRS는 분석 진행 중으로 비활성화됩니다.
                  </p>
                  <select
                    id="instrumentTypeSelect"
                    value={selectedInstrumentType ?? 'FX_FORWARD'}
                    onChange={(e) => {
                      const v = e.target.value as 'FX_FORWARD' | 'IRS' | 'CRS'
                      setValue(
                        'instrumentType',
                        v === 'FX_FORWARD' ? undefined : v,
                        { shouldDirty: true },
                      )
                    }}
                    className={inputClass}
                    aria-label="위험회피수단 유형"
                  >
                    <option value="FX_FORWARD">FX Forward (USD/KRW) — 1단계 기본</option>
                    <option value="IRS">IRS 금리스왑 FVH — 2단계 ✓ 백엔드 준비됨</option>
                    <option value="CRS" disabled>CRS 통화스왑 — 준비 중 (비활성)</option>
                  </select>
                  {selectedInstrumentType === 'IRS' && (
                    <div className="mt-2 bg-blue-50 border border-blue-200 rounded px-3 py-2.5 space-y-2">
                      <p className="text-xs font-semibold text-blue-800">
                        ✓ IRS FVH 2단계 엔진 활성 — K-IFRS 1109호 6.5.8 / 6.5.9
                      </p>
                      <p className="text-xs text-blue-700 leading-relaxed">
                        <strong>§6.5.8</strong> — 위험회피수단(IRS) 공정가치 변동 전액을 당기손익(P&L)으로 즉시 인식합니다.
                        피헤지항목(원화 고정금리채권)의 금리위험 귀속 공정가치 변동도 같은 금액이 반대 방향으로 P&L에 인식됩니다.
                      </p>
                      <p className="text-xs text-blue-700 leading-relaxed">
                        <strong>§6.5.9</strong> — 피헤지항목 장부금액 조정액은 헤지기간에 걸쳐 상각됩니다.
                        별도 분개 유형 <code className="font-mono bg-blue-100 px-1 rounded">IRS_FVH_AMORTIZATION</code>으로 처리됩니다.
                      </p>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </fieldset>

        {/* ── 당기 변동액 ─────────────────────────────────────────── */}
        <fieldset className="mb-6">
          <div className="flex items-center justify-between mb-4">
            <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              당기 변동액
            </legend>

            {/* IRS FVH 예시값 버튼 — IRS 관계 선택 시 표시 */}
            {selectedInstrumentType === 'IRS' && (
              <button
                type="button"
                onClick={() => {
                  setValue('instrumentFvChange', 390_000_000, { shouldDirty: true, shouldValidate: true })
                  setValue('hedgedItemPvChange', -386_000_000, { shouldDirty: true, shouldValidate: true })
                }}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold
                           bg-blue-600 text-white hover:bg-blue-700 transition-colors"
                title="instrumentFvChange=390,000,000 / hedgedItemPvChange=−386,000,000 — 기대 ratio ≈ −1.0104 (PASS)"
              >
                <svg className="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-11a1 1 0 10-2 0v2H7a1 1 0 100 2h2v2a1 1 0 102 0v-2h2a1 1 0 100-2h-2V7z" clipRule="evenodd" />
                </svg>
                IRS FVH 예시값 채우기
              </button>
            )}
          </div>

          {/* IRS FVH 입력 가이드 — IRS 관계 선택 시 */}
          {selectedInstrumentType === 'IRS' && (
            <div className="mb-4 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 space-y-2">
              <p className="text-xs font-semibold text-blue-800 uppercase tracking-wider">
                IRS FVH 변동액 입력 가이드 — K-IFRS 1109호 6.5.8 / 6.5.9
              </p>
              <p className="text-xs text-blue-700 leading-relaxed">
                <strong>헤지수단 (IRS) 공정가치 변동:</strong> 할인현금흐름(DCF) 평가에 의한
                IRS 공정가치 증감액. 이익(양수) · 손실(음수). P&amp;L에 즉시 인식됩니다.
              </p>
              <p className="text-xs text-blue-700 leading-relaxed">
                <strong>피헤지항목 (원화 고정금리채권) 현재가치 변동:</strong> 헤지 대상
                위험(금리위험)에만 귀속되는 채권 공정가치 변동액. 반대 방향이어야 합니다.
              </p>
              <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-blue-600 mt-1">
                <span>
                  <code className="font-mono bg-blue-100 px-1 rounded">§6.5.8</code> — 수단 평가손익 P&amp;L 즉시 인식
                </span>
                <span>
                  <code className="font-mono bg-blue-100 px-1 rounded">§6.5.9</code> — 피헤지항목 장부금액 조정 · 이후 상각
                </span>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field
              htmlFor="instrumentFvChange"
              label="헤지수단 공정가치 변동액 (KRW)"
              hint="음수 = 손실, 양수 = 이익 · K-IFRS 1109호 6.5.8"
              error={errors.instrumentFvChange?.message}
            >
              <Controller
                name="instrumentFvChange"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="instrumentFvChange"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder="-500,000"
                    aria-label="헤지수단 공정가치 변동액"
                  />
                )}
              />
            </Field>

            <Field
              htmlFor="hedgedItemPvChange"
              label="피헤지항목 현재가치 변동액 (KRW)"
              hint="헤지 위험에 귀속되는 변동만 포함 · K-IFRS 1109호 6.5.8"
              error={errors.hedgedItemPvChange?.message}
            >
              <Controller
                name="hedgedItemPvChange"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="hedgedItemPvChange"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder="510,000"
                    aria-label="피헤지항목 현재가치 변동액"
                  />
                )}
              />
            </Field>
          </div>
        </fieldset>

        {/* ── 누적 변동액 안내 ─────────────────────────────────────── */}
        <div className="rounded-md border border-slate-200 bg-slate-50 px-4 py-3 mb-6">
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
            누적 변동액
          </p>
          <p className="text-xs text-slate-400">
            서버에서 이전 이력 조회 후 자동 계산됩니다.
          </p>
        </div>

        {/* ── IFRS 9 원칙 기반 평가 안내 ──────────────────────────── */}
        <div className="rounded-md border border-slate-200 bg-slate-50 px-4 py-3 mb-6 flex items-start gap-3">
          <svg className="w-4 h-4 text-slate-400 flex-shrink-0 mt-0.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
          <div>
            <p className="text-xs font-semibold text-slate-600 mb-0.5">결과 해석 — IFRS 9는 원칙 기반 평가입니다</p>
            <p className="text-xs text-slate-500">
              K-IFRS 1109호는 수치 기준 대신 <strong>경제적 관계·신용위험·헤지비율</strong> 세 가지를 종합해 유효성을 판단합니다.
              Dollar-offset 80%~125%는 구 IAS 39의 관행에서 비롯된 <strong>참고 지표</strong>이며, 단독으로 PASS·FAIL을 결정하지 않습니다.
            </p>
          </div>
        </div>

        {/* ── 에러 메시지 ───────────────────────────────────────────── */}
        {mutation.isError && (
          <MutationErrorAlert error={mutation.error} className="mt-4" />
        )}

        {/* ── 제출 버튼 ─────────────────────────────────────────────── */}
        <div className="mt-6 flex justify-end">
          <Button type="submit" size="lg" loading={mutation.isPending}>
            유효성 테스트 실행
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