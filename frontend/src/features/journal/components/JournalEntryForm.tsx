import { type ReactNode, useState } from 'react'
import { useForm, Controller, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { NumericInput } from '@/components/ui/NumericInput'
import { MutationErrorAlert } from '@/components/ui/ErrorAlert'
import type { JournalEntryRequest, JournalEntryResponse } from '@/types/journal'
import { useJournalEntryMutation } from '../api/useJournalEntry'

// ─── Zod 스키마 ──────────────────────────────────────────────────────────────
// 음수 허용 — z.coerce.number() (min 없음)

const schema = z.object({
  hedgeRelationshipId: z.string().min(1, '위험회피관계 ID를 입력하세요.').max(100),
  entryDate:           z.string().min(1, '분개 기준일을 입력하세요.'),
  hedgeType:           z.enum(['FAIR_VALUE', 'CASH_FLOW']),
  instrumentFvChange:  z.coerce.number({ invalid_type_error: '숫자를 입력하세요.' }),
  hedgedItemFvChange:  z.coerce.number({ invalid_type_error: '숫자를 입력하세요.' }),
  effectiveAmount:     z.coerce.number().optional(),
  ineffectiveAmount:   z.coerce.number().optional(),
  isReclassification:  z.boolean().optional(),
  reclassificationAmount:  z.coerce.number().optional(),
  reclassificationReason:  z.enum([
    'TRANSACTION_REALIZED',
    'HEDGE_DISCONTINUED',
    'TRANSACTION_NO_LONGER_EXPECTED',
  ]).optional(),
  originalOciEntryDate: z.string().optional(),
  plAccountCode:        z.string().optional(),
})

type FormValues = z.infer<typeof schema>

const _d = (offset: number): string => {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return d.toISOString().slice(0, 10)
}

const DEFAULT_VALUES: FormValues = {
  hedgeRelationshipId: '',
  entryDate:           _d(0),     // 분개 기준일은 오늘 유지
  hedgeType:           'CASH_FLOW',
  instrumentFvChange:  0,
  hedgedItemFvChange:  0,
  effectiveAmount:     0,
  ineffectiveAmount:   0,
  isReclassification:  false,
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface JournalEntryFormProps {
  onSuccess: (results: JournalEntryResponse[]) => void
  /** 재분개 시 폼을 초기화할 기본값 */
  initialValues?: Partial<FormValues>
}

// ─── 공통 input 클래스 ────────────────────────────────────────────────────────

const inputClass =
  'w-full border border-slate-300 rounded-md px-3 py-2 text-sm text-slate-900 ' +
  'focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-blue-600 ' +
  'disabled:bg-slate-50 disabled:text-slate-400 font-financial'

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

/**
 * 헤지회계 자동 분개 생성 폼.
 *
 * 공정가치 위험회피(FVH) 및 현금흐름 위험회피(CFH) 분개를 자동 생성합니다.
 * OCI 재분류 분개도 함께 생성할 수 있습니다.
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 회계처리)
 * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 OCI/P&L 분리)
 * @see K-IFRS 1109호 6.5.11(다) (OCI 재분류 조정)
 */
export function JournalEntryForm({ onSuccess, initialValues }: JournalEntryFormProps) {
  const mutation = useJournalEntryMutation()
  const [showAdvanced, setShowAdvanced] = useState(false)

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: initialValues ? { ...DEFAULT_VALUES, ...initialValues } : DEFAULT_VALUES,
  })

  // 조건부 섹션 제어용 watch
  const hedgeType        = useWatch({ control, name: 'hedgeType' })
  const isReclassification = useWatch({ control, name: 'isReclassification' })

  const onSubmit = (data: FormValues) => {
    const request: JournalEntryRequest = {
      hedgeRelationshipId: data.hedgeRelationshipId,
      entryDate:           data.entryDate,
      hedgeType:           data.hedgeType,
      instrumentFvChange:  data.instrumentFvChange,
      hedgedItemFvChange:  data.hedgedItemFvChange,
      ...(data.hedgeType === 'CASH_FLOW' && {
        effectiveAmount:   data.effectiveAmount,
        ineffectiveAmount: data.ineffectiveAmount,
      }),
      isReclassification: data.isReclassification ?? false,
      ...(data.isReclassification && {
        reclassificationAmount: data.reclassificationAmount,
        reclassificationReason: data.reclassificationReason,
        originalOciEntryDate:   data.originalOciEntryDate || undefined,
        plAccountCode:          data.plAccountCode || undefined,
      }),
    }
    mutation.mutate(request, { onSuccess })
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <Card
        title="자동 분개 생성"
        description="K-IFRS 1109호 6.5.8 / 6.5.11 — 위험회피 유형에 따른 분개 자동 생성"
        variant="bordered"
        actions={
          <span className="text-xs text-blue-700 font-medium bg-blue-50 px-2 py-1 rounded">
            분개 생성 · K-IFRS 1109호 6.5.8
          </span>
        }
      >
        {/* ── 헤지관계 정보 ───────────────────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            헤지관계 정보
          </legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field
              htmlFor="hedgeRelationshipId"
              label="위험회피관계 ID"
              hint="헤지 지정 화면 이력 테이블에서 '관계ID' 값을 복사해서 붙여넣으세요."
              error={errors.hedgeRelationshipId?.message}
            >
              <input
                id="hedgeRelationshipId"
                {...register('hedgeRelationshipId')}
                className={inputClass}
                placeholder="예: a1b2c3d4-e5f6-..."
                aria-label="위험회피관계 ID"
              />
            </Field>

            <Field htmlFor="entryDate" label="분개 기준일" error={errors.entryDate?.message}>
              <input
                id="entryDate"
                {...register('entryDate')}
                type="date"
                className={inputClass}
                aria-label="분개 기준일"
              />
            </Field>

            <Field htmlFor="hedgeType" label="위험회피 유형" error={errors.hedgeType?.message}>
              <select
                id="hedgeType"
                {...register('hedgeType')}
                className={inputClass}
                aria-label="위험회피 유형"
              >
                <option value="CASH_FLOW">현금흐름 위험회피</option>
                <option value="FAIR_VALUE">공정가치 위험회피</option>
              </select>
            </Field>
          </div>
        </fieldset>

        {/* ── 공정가치 변동액 ──────────────────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            공정가치 변동액
          </legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field
              htmlFor="instrumentFvChange"
              label="헤지수단 공정가치 변동 (KRW)"
              hint="음수 = 손실, 양수 = 이익 · K-IFRS 1109호 6.5.8(가)"
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
                    aria-label="헤지수단 공정가치 변동"
                  />
                )}
              />
            </Field>

            <Field
              htmlFor="hedgedItemFvChange"
              label="피헤지항목 공정가치 변동 (KRW)"
              hint="헤지 위험에 귀속되는 변동만 포함 · K-IFRS 1109호 6.5.8(나)"
              error={errors.hedgedItemFvChange?.message}
            >
              <Controller
                name="hedgedItemFvChange"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="hedgedItemFvChange"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder="510,000"
                    aria-label="피헤지항목 공정가치 변동"
                  />
                )}
              />
            </Field>
          </div>
        </fieldset>

        {/* ── 상세 입력 (고급) — 기본 접힌 상태 ─────────────────── */}
        <div className="mb-6">
          {/* 토글 버튼 */}
          <button
            type="button"
            onClick={() => setShowAdvanced((v) => !v)}
            className="flex items-center gap-2 text-xs text-slate-500 hover:text-blue-700 transition-colors group"
          >
            <svg
              className={`w-3.5 h-3.5 transition-transform ${showAdvanced ? 'rotate-90' : ''}`}
              viewBox="0 0 20 20"
              fill="currentColor"
              aria-hidden="true"
            >
              <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
            </svg>
            <span className="font-semibold group-hover:underline underline-offset-2">
              {showAdvanced ? '상세 입력 닫기' : '상세 입력 펼치기'}
            </span>
            <span className="text-slate-400">
              — 유효/비유효 금액, OCI 재분류 (K-IFRS 1109호 6.5.11)
            </span>
          </button>
          <p className="text-xs text-slate-400 mt-1 ml-5">
            유효/비유효 금액은 유효성 테스트 결과를 참조하세요. 직접 입력이 필요한 경우에만 펼치세요.
          </p>

          {showAdvanced && (
            <div className="mt-4 space-y-6 pl-1 border-l-2 border-slate-200">

              {/* CFH 전용 필드 (현금흐름 위험회피) */}
              {hedgeType === 'CASH_FLOW' && (
                <fieldset>
                  <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
                    현금흐름 위험회피 — 유효/비유효 금액{' '}
                    <span className="font-normal text-slate-400 normal-case">
                      (K-IFRS 1109호 6.5.11)
                    </span>
                  </legend>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pl-4">
                    <Field
                      htmlFor="effectiveAmount"
                      label="유효 부분 금액 (KRW)"
                      hint="OCI로 인식 · K-IFRS 1109호 6.5.11(가)"
                      error={errors.effectiveAmount?.message}
                    >
                      <Controller
                        name="effectiveAmount"
                        control={control}
                        render={({ field }) => (
                          <NumericInput
                            id="effectiveAmount"
                            value={field.value}
                            onNumericChange={(n) => field.onChange(n)}
                            className={inputClass}
                            placeholder="-500,000"
                            aria-label="유효 부분 금액"
                          />
                        )}
                      />
                    </Field>

                    <Field
                      htmlFor="ineffectiveAmount"
                      label="비유효 부분 금액 (KRW)"
                      hint="P&L 즉시 인식 · K-IFRS 1109호 6.5.11(나)"
                      error={errors.ineffectiveAmount?.message}
                    >
                      <Controller
                        name="ineffectiveAmount"
                        control={control}
                        render={({ field }) => (
                          <NumericInput
                            id="ineffectiveAmount"
                            value={field.value}
                            onNumericChange={(n) => field.onChange(n)}
                            className={inputClass}
                            placeholder="10,000"
                            aria-label="비유효 부분 금액"
                          />
                        )}
                      />
                    </Field>
                  </div>
                </fieldset>
              )}

              {/* OCI 재분류 토글 */}
              <fieldset>
                <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
                  OCI 재분류{' '}
                  <span className="font-normal text-slate-400 normal-case">
                    (K-IFRS 1109호 6.5.11(다))
                  </span>
                </legend>

                <label className="flex items-center gap-2 cursor-pointer mb-4 pl-4">
                  <input
                    type="checkbox"
                    {...register('isReclassification')}
                    className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm text-slate-700">OCI 재분류 포함</span>
                  <span className="text-xs text-slate-400">
                    (현금흐름위험회피적립금 → P&amp;L 재분류)
                  </span>
                </label>

                {isReclassification && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 ml-4 p-4 bg-purple-50 border border-purple-200 rounded-lg">
                    <Field
                      htmlFor="reclassificationAmount"
                      label="재분류 금액 (KRW)"
                      hint="양수 = OCI 이익→P&L, 음수 = OCI 손실→P&L"
                      error={errors.reclassificationAmount?.message}
                    >
                      <Controller
                        name="reclassificationAmount"
                        control={control}
                        render={({ field }) => (
                          <NumericInput
                            id="reclassificationAmount"
                            value={field.value}
                            onNumericChange={(n) => field.onChange(n)}
                            className={inputClass}
                            placeholder="500,000"
                            aria-label="재분류 금액"
                          />
                        )}
                      />
                    </Field>

                    <Field
                      htmlFor="reclassificationReason"
                      label="재분류 사유"
                      error={errors.reclassificationReason?.message}
                    >
                      <select
                        id="reclassificationReason"
                        {...register('reclassificationReason')}
                        className={inputClass}
                        aria-label="재분류 사유"
                      >
                        <option value="">선택하세요</option>
                        <option value="TRANSACTION_REALIZED">예상거래 실현</option>
                        <option value="HEDGE_DISCONTINUED">헤지 중단</option>
                        <option value="TRANSACTION_NO_LONGER_EXPECTED">예상거래 발생 불가</option>
                      </select>
                    </Field>

                    <Field
                      htmlFor="originalOciEntryDate"
                      label="최초 OCI 인식일"
                      hint="재분류 분개 추적용 (선택)"
                      error={errors.originalOciEntryDate?.message}
                    >
                      <input
                        id="originalOciEntryDate"
                        {...register('originalOciEntryDate')}
                        type="date"
                        className={inputClass}
                        aria-label="최초 OCI 인식일"
                      />
                    </Field>

                    <Field
                      htmlFor="plAccountCode"
                      label="대응 P&L 계정"
                      hint="재분류 후 인식할 손익 계정"
                      error={errors.plAccountCode?.message}
                    >
                      <select
                        id="plAccountCode"
                        {...register('plAccountCode')}
                        className={inputClass}
                        aria-label="대응 P&L 계정"
                      >
                        <option value="">선택하세요 (자동 결정)</option>
                        <option value="FX_GAIN_PL">외환이익</option>
                        <option value="FX_LOSS_PL">외환손실</option>
                        <option value="INTEREST_INCOME">이자수익</option>
                        <option value="INTEREST_EXPENSE">이자비용</option>
                        <option value="RECLASSIFY_PL">OCI재분류손익</option>
                      </select>
                    </Field>
                  </div>
                )}
              </fieldset>

            </div>
          )}
        </div>

        {/* ── 에러 메시지 ───────────────────────────────────────────── */}
        {mutation.isError && (
          <MutationErrorAlert error={mutation.error} className="mt-4" />
        )}

        {/* ── 제출 버튼 ─────────────────────────────────────────────── */}
        <div className="mt-6 flex justify-end">
          <Button type="submit" size="lg" loading={mutation.isPending}>
            분개 생성
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