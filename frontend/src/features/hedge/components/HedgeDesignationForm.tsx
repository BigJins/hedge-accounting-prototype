import { type ReactNode, useEffect, useCallback, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import clsx from 'clsx'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { NumericInput } from '@/components/ui/NumericInput'
import { FxContractSelector } from './FxContractSelector'
import { useIrsContractListAll } from '@/features/valuation/api/useIrsValuation'
import type { HedgeDesignationRequest, InstrumentType } from '@/types/hedge'
import type { FxForwardContractResponse } from '@/types/valuation'

// ─── 모드 타입 ────────────────────────────────────────────────────────────────

type DesignationMode = 'fx-forward' | 'irs'

// ─── Zod 스키마 ──────────────────────────────────────────────────────────────

const schema = z.object({
  hedgeType: z.enum(['FAIR_VALUE', 'CASH_FLOW'], {
    required_error: '헤지 유형을 선택하세요.',
  }),
  hedgedRisk: z.enum(['FOREIGN_CURRENCY', 'INTEREST_RATE'], {
    required_error: '헤지 위험 유형을 선택하세요.',
  }),
  designationDate: z.string().min(1, '지정일을 입력하세요.'),
  hedgePeriodEnd: z.string().min(1, '위험회피기간 종료일을 입력하세요.'),
  hedgeRatio: z.coerce
    .number({ invalid_type_error: '헤지비율은 숫자로 입력하세요.' })
    .min(0.1, '헤지비율은 0.10(=10%) 이상이어야 합니다. 통상 1.00(=100%)으로 설정합니다.')
    .max(2.0, '헤지비율은 2.00(=200%) 이하로 입력하세요.'),
  riskManagementObjective: z.string().min(5, '위험관리 목적을 5자 이상 입력하세요.').max(500),
  hedgeStrategy: z.string().min(5, '헤지 전략을 5자 이상 입력하세요.').max(500),
  itemType: z.enum(['FX_DEPOSIT', 'FORECAST_TRANSACTION', 'KRW_FIXED_BOND'], {
    required_error: '항목 유형을 선택하세요.',
  }),
  currency: z.string().min(1, '통화를 입력하세요.').max(10),
  notionalAmount: z.coerce.number().positive('명목금액은 0보다 커야 합니다.'),
  maturityDate: z.string().min(1, '만기일을 입력하세요.'),
  counterpartyName: z.string().optional(),
  counterpartyCreditRating: z.enum(['AAA', 'AA', 'A', 'BBB', 'BB', 'B', 'CCC', 'D'], {
    required_error: '거래상대방 신용등급을 선택하세요.',
  }),
  // FX 모드 — 기존 통화선도 계약 (선택적: FX모드에서만 필수, superRefine 불가로 onSubmit에서 검증)
  fxForwardContractId: z.string().optional(),
  // IRS 모드 — IRS 계약
  instrumentContractId: z.string().optional(),
})

export type FormValues = z.infer<typeof schema>

const DEFAULT_VALUES_FX: FormValues = {
  hedgeType: 'CASH_FLOW',
  hedgedRisk: 'FOREIGN_CURRENCY',
  designationDate: '',
  hedgePeriodEnd:  '',
  hedgeRatio: 1.0,
  riskManagementObjective: '',
  hedgeStrategy: '',
  itemType: 'FX_DEPOSIT',
  currency: '',
  notionalAmount: 0,
  maturityDate: '',
  counterpartyName: '',
  counterpartyCreditRating: 'AAA',
  fxForwardContractId: '',
  instrumentContractId: '',
}

const DEFAULT_VALUES_IRS: FormValues = {
  hedgeType: 'FAIR_VALUE',
  hedgedRisk: 'INTEREST_RATE',
  designationDate: '',
  hedgePeriodEnd:  '',
  hedgeRatio: 1.0,
  riskManagementObjective: '원화 고정금리채권의 공정가치 변동 위험(금리위험)을 이자율스왑(IRS)으로 회피합니다.',
  hedgeStrategy: 'IRS 변동지급/고정수취(수취고정) 구조를 활용하여 KRW 고정금리채권의 금리위험(공정가치)을 헤지합니다.',
  itemType: 'KRW_FIXED_BOND',
  currency: 'KRW',
  notionalAmount: 0,
  maturityDate: '',
  counterpartyName: '',
  counterpartyCreditRating: 'AA',
  fxForwardContractId: '',
  instrumentContractId: '',
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface HedgeDesignationFormProps {
  onSubmit: (request: HedgeDesignationRequest) => void
  isLoading: boolean
  initialValues?: Partial<FormValues>
  onValuesChange?: (values: FormValues) => void
}

// ─── 공통 클래스 ──────────────────────────────────────────────────────────────

const inputClass =
  'w-full border border-slate-300 rounded-md px-3 py-2 text-sm text-slate-900 ' +
  'focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-blue-600 ' +
  'disabled:bg-slate-50 disabled:text-slate-400 font-financial'

const selectClass =
  'w-full border border-slate-300 rounded-md px-3 py-2 text-sm text-slate-900 ' +
  'focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-blue-600 ' +
  'disabled:bg-slate-50 disabled:text-slate-400 bg-white'

const textareaClass =
  'w-full border border-slate-300 rounded-md px-3 py-2 text-sm text-slate-900 ' +
  'focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-blue-600 ' +
  'resize-none'

const readonlyClass =
  'w-full border border-slate-200 rounded-md px-3 py-2 text-sm text-slate-600 ' +
  'bg-slate-50 font-financial cursor-default'

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

/**
 * 헤지 지정 입력 폼 — FX Forward(1단계) + IRS FVH(2단계) 통합
 *
 * @see K-IFRS 1109호 6.4.1 (헤지 지정 및 문서화 의무)
 * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성)
 * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — IRS)
 */
export function HedgeDesignationForm({
  onSubmit,
  isLoading,
  initialValues,
  onValuesChange,
}: HedgeDesignationFormProps) {
  // 초기 모드: initialValues에 IRS 위험 유형이 있으면 IRS 모드로 시작
  const [mode, setMode] = useState<DesignationMode>(
    initialValues?.hedgedRisk === 'INTEREST_RATE' ? 'irs' : 'fx-forward',
  )

  const defaultVals = initialValues
    ? { ...(mode === 'irs' ? DEFAULT_VALUES_IRS : DEFAULT_VALUES_FX), ...initialValues }
    : (mode === 'irs' ? DEFAULT_VALUES_IRS : DEFAULT_VALUES_FX)

  const {
    register,
    handleSubmit,
    control,
    watch,
    getValues,
    setValue,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaultVals,
  })

  // IRS 계약 목록
  const { data: irsContracts = [] } = useIrsContractListAll()

  // ── 모드 전환 ────────────────────────────────────────────────────────────────
  const handleModeChange = (newMode: DesignationMode) => {
    setMode(newMode)
    clearErrors()
    if (newMode === 'irs') {
      setValue('hedgeType',   'FAIR_VALUE',    { shouldValidate: false })
      setValue('hedgedRisk',  'INTEREST_RATE', { shouldValidate: false })
      setValue('itemType',    'KRW_FIXED_BOND',{ shouldValidate: false })
      setValue('currency',    'KRW',           { shouldValidate: false })
      setValue('riskManagementObjective', DEFAULT_VALUES_IRS.riskManagementObjective, { shouldValidate: false })
      setValue('hedgeStrategy', DEFAULT_VALUES_IRS.hedgeStrategy, { shouldValidate: false })
      setValue('counterpartyCreditRating', 'AA', { shouldValidate: false })
      setValue('fxForwardContractId', '', { shouldValidate: false })
    } else {
      setValue('hedgeType',   'CASH_FLOW',        { shouldValidate: false })
      setValue('hedgedRisk',  'FOREIGN_CURRENCY',  { shouldValidate: false })
      setValue('itemType',    'FX_DEPOSIT',         { shouldValidate: false })
      setValue('currency',    '',                   { shouldValidate: false })
      setValue('riskManagementObjective', '', { shouldValidate: false })
      setValue('hedgeStrategy', '', { shouldValidate: false })
      setValue('instrumentContractId', '', { shouldValidate: false })
    }
  }

  // ── IRS 계약 선택 시 자동 채움 ──────────────────────────────────────────────
  const handleIrsContractSelected = (contractId: string) => {
    setValue('instrumentContractId', contractId, { shouldDirty: true, shouldValidate: true })
    const contract = irsContracts.find((c) => c.contractId === contractId)
    if (!contract) return
    setValue('notionalAmount', contract.notionalAmount, { shouldDirty: true, shouldValidate: true })
    setValue('maturityDate',   contract.maturityDate,   { shouldDirty: true, shouldValidate: true })
    if (!getValues('hedgePeriodEnd')) {
      setValue('hedgePeriodEnd', contract.maturityDate, { shouldDirty: true, shouldValidate: true })
    }
  }

  // ── FX 계약 선택 시 자동 채움 ──────────────────────────────────────────────
  const handleFxContractSelected = useCallback(
    (contract: FxForwardContractResponse | null) => {
      if (!contract) return
      setValue('notionalAmount', contract.notionalAmountUsd, { shouldDirty: true, shouldValidate: true })
      setValue('maturityDate',   contract.maturityDate,      { shouldDirty: true, shouldValidate: true })
      setValue('currency',       'USD',                      { shouldDirty: true, shouldValidate: true })
      if (!getValues('hedgePeriodEnd')) {
        setValue('hedgePeriodEnd', contract.maturityDate, { shouldDirty: true, shouldValidate: true })
      }
      if (!getValues('designationDate')) {
        setValue('designationDate', contract.hedgeDesignationDate, { shouldDirty: true, shouldValidate: true })
      }
    },
    [getValues, setValue],
  )

  // ── 실시간 값 변경 구독 ──────────────────────────────────────────────────────
  useEffect(() => {
    if (!onValuesChange) return
    const subscription = watch((values) => {
      onValuesChange(values as FormValues)
    })
    return () => subscription.unsubscribe()
  }, [watch, onValuesChange])

  // ── 제출 핸들러 ─────────────────────────────────────────────────────────────
  const onFormSubmit = (data: FormValues) => {
    // 모드별 필수 계약 검증
    if (mode === 'fx-forward') {
      if (!data.fxForwardContractId) {
        setError('fxForwardContractId', { message: '통화선도 계약을 선택하세요 (드롭다운에서 선택).' })
        return
      }
    } else {
      if (!data.instrumentContractId) {
        setError('instrumentContractId', { message: 'IRS 계약을 선택하세요.' })
        return
      }
    }

    const instrumentType: InstrumentType | undefined = mode === 'irs' ? 'IRS' : undefined

    const request: HedgeDesignationRequest = {
      hedgeType: data.hedgeType,
      hedgedRisk: data.hedgedRisk,
      designationDate: data.designationDate,
      hedgePeriodEnd: data.hedgePeriodEnd,
      hedgeRatio: data.hedgeRatio,
      riskManagementObjective: data.riskManagementObjective,
      hedgeStrategy: data.hedgeStrategy,
      fxForwardContractId: mode === 'fx-forward' ? data.fxForwardContractId : null,
      instrumentType:      instrumentType ?? null,
      instrumentContractId: mode === 'irs' ? data.instrumentContractId : null,
      hedgedItem: {
        itemType: data.itemType,
        currency: data.currency,
        notionalAmount: data.notionalAmount,
        maturityDate: data.maturityDate,
        counterpartyName: data.counterpartyName,
        counterpartyCreditRating: data.counterpartyCreditRating,
      },
    }
    onSubmit(request)
  }

  const selectedInstrumentContractId = watch('instrumentContractId')
  const selectedIrsContract = irsContracts.find((c) => c.contractId === selectedInstrumentContractId)

  return (
    <form id="hedge-designation-form" onSubmit={handleSubmit(onFormSubmit)} noValidate>
      <Card
        title="헤지 지정"
        description="K-IFRS 1109호 6.4.1 — 위험회피관계 지정 및 문서화"
        variant="bordered"
        actions={
          <span className="text-xs text-blue-700 font-medium bg-blue-50 px-2 py-1 rounded">
            K-IFRS 1109호 6.4.1
          </span>
        }
      >
        {/* ── 위험회피수단 모드 탭 ─────────────────────────────────── */}
        <div className="mb-6 flex items-center gap-0 border-b border-slate-200">
          <button
            type="button"
            onClick={() => handleModeChange('fx-forward')}
            className={clsx(
              'px-4 py-2.5 text-sm font-semibold border-b-2 -mb-px transition-colors',
              mode === 'fx-forward'
                ? 'border-blue-600 text-blue-700'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300',
            )}
          >
            FX Forward
            <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-sky-100 text-sky-700">
              1단계
            </span>
          </button>
          <button
            type="button"
            onClick={() => handleModeChange('irs')}
            className={clsx(
              'px-4 py-2.5 text-sm font-semibold border-b-2 -mb-px transition-colors',
              mode === 'irs'
                ? 'border-blue-600 text-blue-700'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300',
            )}
          >
            IRS 금리위험 FVH
            <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-blue-100 text-blue-700">
              2단계
            </span>
          </button>
          <span className="px-4 py-2.5 text-sm text-slate-300 cursor-not-allowed select-none border-b-2 border-transparent -mb-px">
            CRS
            <span className="ml-1 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-400">
              준비 중
            </span>
          </span>
        </div>

        {/* ── 모드 범위 안내 배너 ────────────────────────────────────── */}
        {mode === 'fx-forward' ? (
          <div className="mb-6 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
            <p className="text-xs font-semibold uppercase tracking-wider text-amber-800 mb-1.5">
              1단계 엔진 범위
            </p>
            <div className="flex flex-wrap gap-x-6 gap-y-0.5 text-xs">
              <span className="text-green-700 font-medium">✓ FX Forward · USD/KRW · 외화위험 · Level 2 평가</span>
              <span className="text-amber-600">○ 2단계: IRS · CRS · EUR·JPY · 이자율·상품위험</span>
            </div>
            <p className="mt-2 text-xs text-amber-600 border-t border-amber-200 pt-1.5">
              신용등급 선택 → 헤지 적격성 조건 2(신용위험 지배적 아님) 판단에 자동 반영
            </p>
          </div>
        ) : (
          <div className="mb-6 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3">
            <p className="text-xs font-semibold uppercase tracking-wider text-blue-800 mb-1.5">
              2단계 IRS FVH 엔진 범위
            </p>
            <div className="flex flex-wrap gap-x-6 gap-y-0.5 text-xs">
              <span className="text-blue-900 font-medium">
                ✓ IRS 변동지급/고정수취 (payFixed=false) · KRW 고정금리채권 FVH · K-IFRS 1109호 6.5.8
              </span>
              <span className="text-blue-500">○ 1단계: FX Forward · USD/KRW 환위험</span>
            </div>
            <p className="mt-2 text-xs text-blue-700 border-t border-blue-200 pt-1.5">
              헤지유형 = 공정가치(FVH) · 위험유형 = 이자율위험 · 피헤지항목 = KRW 고정금리채권 — 자동 고정됨
            </p>
          </div>
        )}

        {/* hidden fields for IRS locked values */}
        <input type="hidden" {...register('hedgeType')} />
        <input type="hidden" {...register('hedgedRisk')} />
        <input type="hidden" {...register('itemType')} />

        {/* ── 섹션 1: 위험회피관계 기본 정보 ────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            위험회피관계 기본 정보
          </legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* 헤지 유형 */}
            <Field
              htmlFor="hedgeType_display"
              label="헤지 유형"
              error={errors.hedgeType?.message}
              required
            >
              {mode === 'irs' ? (
                <div className={readonlyClass} aria-label="헤지 유형 고정값">
                  공정가치 위험회피 (Fair Value Hedge)
                  <span className="ml-2 text-[10px] text-blue-600 bg-blue-100 px-1.5 py-0.5 rounded">고정</span>
                </div>
              ) : (
                <select id="hedgeType_display" className={selectClass}
                  value={watch('hedgeType')}
                  onChange={(e) => setValue('hedgeType', e.target.value as 'FAIR_VALUE' | 'CASH_FLOW', { shouldDirty: true })}
                >
                  <option value="CASH_FLOW">현금흐름 위험회피 (Cash Flow Hedge)</option>
                  <option value="FAIR_VALUE">공정가치 위험회피 (Fair Value Hedge)</option>
                </select>
              )}
            </Field>

            {/* 헤지 위험 유형 */}
            <Field
              htmlFor="hedgedRisk_display"
              label="헤지 위험 유형"
              error={errors.hedgedRisk?.message}
              hint={mode === 'fx-forward'
                ? '1단계 엔진은 외화위험(Foreign Currency) 중심입니다.'
                : undefined}
              required
            >
              {mode === 'irs' ? (
                <div className={readonlyClass} aria-label="헤지 위험 유형 고정값">
                  이자율 위험 (Interest Rate)
                  <span className="ml-2 text-[10px] text-blue-600 bg-blue-100 px-1.5 py-0.5 rounded">고정</span>
                </div>
              ) : (
                <select id="hedgedRisk_display" className={selectClass} disabled>
                  <option value="FOREIGN_CURRENCY">외화 위험 (Foreign Currency)</option>
                </select>
              )}
            </Field>

            <Field
              htmlFor="designationDate"
              label="헤지 지정일"
              error={errors.designationDate?.message}
              hint="계약 선택 시 지정일로 자동 제안됩니다 (수정 가능)"
              required
            >
              <input
                id="designationDate"
                {...register('designationDate')}
                type="date"
                className={inputClass}
              />
            </Field>

            <Field
              htmlFor="hedgePeriodEnd"
              label="위험회피기간 종료일"
              error={errors.hedgePeriodEnd?.message}
              hint="계약 선택 시 만기일로 자동 제안됩니다 (수정 가능)"
              required
            >
              <input
                id="hedgePeriodEnd"
                {...register('hedgePeriodEnd')}
                type="date"
                className={inputClass}
              />
            </Field>

            <Field
              htmlFor="hedgeRatio"
              label="헤지 비율 (일반적으로 1.00 = 100%)"
              error={errors.hedgeRatio?.message}
              hint="기본값 1.00(=완전 헤지). 통상 0.80~1.25(K-IFRS B6.4.11 실무 범위). 입력 허용 0.10~2.00."
              required
            >
              <Controller
                name="hedgeRatio"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="hedgeRatio"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder="1.00"
                  />
                )}
              />
            </Field>
          </div>
        </fieldset>

        {/* ── 섹션 2: 위험관리 목적 및 전략 ──────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            위험관리 목적 및 전략
            <span className="ml-2 text-blue-500 normal-case font-normal">
              K-IFRS 6.4.1(2) 문서화 의무
            </span>
          </legend>
          <div className="space-y-4">
            <Field
              htmlFor="riskManagementObjective"
              label="위험관리 목적"
              error={errors.riskManagementObjective?.message}
              hint="K-IFRS 6.4.1(2) 문서화 의무 — 헤지 지정 시점의 목적을 구체적으로 기술하세요."
              required
              action={
                <button
                  type="button"
                  tabIndex={-1}
                  onClick={() =>
                    setValue(
                      'riskManagementObjective',
                      mode === 'irs'
                        ? '원화 고정금리채권의 공정가치 변동 위험(금리위험)을 이자율스왑(IRS)으로 회피합니다.'
                        : 'USD/KRW 환율 변동으로 인한 현금흐름 변동 위험을 회피합니다.',
                      { shouldDirty: true, shouldValidate: true },
                    )
                  }
                  className="text-blue-600 hover:text-blue-800 hover:underline font-normal"
                >
                  예시 문구 사용
                </button>
              }
            >
              <textarea
                id="riskManagementObjective"
                {...register('riskManagementObjective')}
                rows={3}
                className={textareaClass}
                placeholder={
                  mode === 'irs'
                    ? '예: 원화 고정금리채권의 공정가치 변동 위험(금리위험)을 이자율스왑(IRS)으로 회피합니다.'
                    : '예: USD/KRW 환율 변동으로 인한 현금흐름 변동 위험을 회피합니다.'
                }
              />
            </Field>

            <Field
              htmlFor="hedgeStrategy"
              label="헤지 전략"
              error={errors.hedgeStrategy?.message}
              hint="사용 수단, 포지션 방향, 위험 대응 방식을 기술하세요."
              required
              action={
                <button
                  type="button"
                  tabIndex={-1}
                  onClick={() =>
                    setValue(
                      'hedgeStrategy',
                      mode === 'irs'
                        ? 'IRS 변동지급/고정수취(수취고정) 구조를 활용하여 KRW 고정금리채권의 금리위험(공정가치)을 헤지합니다.'
                        : 'USD/KRW 통화선도 매도를 활용하여 달러 자산의 환위험을 헤지합니다.',
                      { shouldDirty: true, shouldValidate: true },
                    )
                  }
                  className="text-blue-600 hover:text-blue-800 hover:underline font-normal"
                >
                  예시 문구 사용
                </button>
              }
            >
              <textarea
                id="hedgeStrategy"
                {...register('hedgeStrategy')}
                rows={3}
                className={textareaClass}
                placeholder={
                  mode === 'irs'
                    ? '예: IRS 변동지급/고정수취 구조로 KRW 고정금리채권의 금리위험을 헤지합니다.'
                    : '예: USD/KRW 통화선도 매도를 활용하여 달러 자산의 환위험을 헤지합니다.'
                }
              />
            </Field>
          </div>
        </fieldset>

        {/* ── 섹션 3: 헤지대상 항목 ─────────────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            헤지대상 항목 (Hedged Item)
            <span className="ml-2 text-blue-500 normal-case font-normal">K-IFRS 6.5.4</span>
          </legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* 항목 유형 */}
            <Field htmlFor="itemType_display" label="항목 유형" error={errors.itemType?.message} required>
              {mode === 'irs' ? (
                <div className={readonlyClass} aria-label="항목 유형 고정값">
                  KRW 고정금리채권 (Fixed Rate Bond)
                  <span className="ml-2 text-[10px] text-blue-600 bg-blue-100 px-1.5 py-0.5 rounded">고정</span>
                </div>
              ) : (
                <select id="itemType_display" className={selectClass}
                  value={watch('itemType')}
                  onChange={(e) => setValue('itemType', e.target.value as FormValues['itemType'], { shouldDirty: true })}
                >
                  <option value="FX_DEPOSIT">외화 정기예금 (FX Deposit)</option>
                  <option value="FORECAST_TRANSACTION">예상거래 (Forecast Transaction)</option>
                </select>
              )}
            </Field>

            {/* 통화 */}
            <Field
              htmlFor="currency_display"
              label="통화 코드"
              error={errors.currency?.message}
              hint={mode === 'irs'
                ? 'IRS FVH는 KRW 원화 기준입니다 (자동 설정).'
                : '1단계 엔진은 USD/KRW 통화쌍만 지원합니다.'}
              required
            >
              {mode === 'irs' ? (
                <div className={readonlyClass} aria-label="통화 고정값">
                  KRW
                  <span className="ml-2 text-[10px] text-blue-600 bg-blue-100 px-1.5 py-0.5 rounded">고정</span>
                </div>
              ) : (
                <input
                  id="currency_display"
                  {...register('currency')}
                  className={inputClass}
                  placeholder="USD"
                  maxLength={10}
                  readOnly
                />
              )}
            </Field>

            <Field
              htmlFor="notionalAmount"
              label={mode === 'irs' ? '명목금액 (KRW)' : '명목금액 (통화 단위)'}
              error={errors.notionalAmount?.message}
              hint="계약 선택 시 자동 입력됩니다."
              required
            >
              <Controller
                name="notionalAmount"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="notionalAmount"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder={mode === 'irs' ? '10,000,000,000' : '10,000,000'}
                  />
                )}
              />
            </Field>

            <Field
              htmlFor="maturityDate"
              label="만기일"
              error={errors.maturityDate?.message}
              hint="계약 선택 시 자동 입력됩니다."
              required
            >
              <input id="maturityDate" {...register('maturityDate')} type="date" className={inputClass} />
            </Field>

            <Field htmlFor="counterpartyName" label="거래상대방명 (선택)" error={errors.counterpartyName?.message}>
              <input
                id="counterpartyName"
                {...register('counterpartyName')}
                className={inputClass}
                placeholder={mode === 'irs' ? '예: 한국산업은행' : '예: 미국씨티은행'}
              />
            </Field>

            <Field
              htmlFor="counterpartyCreditRating"
              label="거래상대방 신용등급"
              error={errors.counterpartyCreditRating?.message}
              hint="K-IFRS B6.4.7 기준 — 적격성 조건 2(신용위험)에 반영됩니다."
              required
            >
              <select id="counterpartyCreditRating" {...register('counterpartyCreditRating')} className={selectClass}>
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

        {/* ── 섹션 4: 헤지수단 선택 ──────────────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            헤지수단 (Hedging Instrument)
            <span className="ml-2 text-blue-500 normal-case font-normal">K-IFRS 1109호 6.5.4</span>
          </legend>

          {mode === 'fx-forward' ? (
            /* ── FX Forward 계약 선택 (기존) ─────────────────────────── */
            <Field
              htmlFor="fxForwardContractId"
              label="통화선도 계약"
              error={errors.fxForwardContractId?.message}
              hint="계약 선택 시 명목금액 · 만기일 · 통화 · 위험회피기간이 자동으로 채워집니다."
              required
            >
              <Controller
                name="fxForwardContractId"
                control={control}
                render={({ field }) => (
                  <FxContractSelector
                    inputId="fxForwardContractId"
                    value={field.value ?? ''}
                    onChange={field.onChange}
                    onContractChange={handleFxContractSelected}
                    error={errors.fxForwardContractId?.message}
                    disabled={isLoading}
                  />
                )}
              />
            </Field>
          ) : (
            /* ── IRS 계약 선택 (2단계) ──────────────────────────────── */
            <>
              {/* IRS FVH 구조 안내 */}
              <div className="mb-4 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3">
                <p className="text-xs font-semibold text-blue-800 mb-1">
                  IRS 수취고정 (Pay Floating / Receive Fixed) = FVH 구조
                </p>
                <p className="text-xs text-blue-700 leading-relaxed">
                  <strong>payFixedReceiveFloating = false</strong> 계약만 FVH(공정가치 위험회피)에 사용합니다.
                  금리 상승 시 IRS 평가이익이 채권 공정가치 하락(피헤지항목)을 상쇄합니다.
                  K-IFRS 1109호 §6.5.8 기준.
                </p>
              </div>

              <Field
                htmlFor="instrumentContractId"
                label="IRS 계약 선택"
                error={errors.instrumentContractId?.message}
                hint="등록된 IRS 계약 목록입니다. payFixed=false(수취고정) 계약이 FVH용입니다."
                required
              >
                <select
                  id="instrumentContractId"
                  value={selectedInstrumentContractId ?? ''}
                  onChange={(e) => handleIrsContractSelected(e.target.value)}
                  className={selectClass}
                >
                  <option value="">IRS 계약을 선택하세요</option>
                  {irsContracts.length === 0 && (
                    <option value="" disabled>
                      등록된 IRS 계약 없음 — 「공정가치 평가」 탭에서 먼저 등록하세요
                    </option>
                  )}
                  {irsContracts.map((c) => (
                    <option key={c.contractId} value={c.contractId}>
                      {c.contractId} — 고정{(c.fixedRate * 100).toFixed(3)}% · {c.payFixedReceiveFloating ? 'CFH(고정지급)' : 'FVH(수취고정)'} · 만기 {c.maturityDate}
                    </option>
                  ))}
                </select>
              </Field>

              {/* 선택된 IRS 계약 상세 */}
              {selectedIrsContract && (
                <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3">
                  <p className="text-xs font-semibold text-emerald-800 mb-2">선택된 IRS 계약</p>
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
                    <div>
                      <p className="text-slate-500">계약번호</p>
                      <p className="font-financial font-medium text-slate-800">{selectedIrsContract.contractId}</p>
                    </div>
                    <div>
                      <p className="text-slate-500">명목금액</p>
                      <p className="font-financial text-slate-800">{selectedIrsContract.notionalAmount.toLocaleString('ko-KR')} KRW</p>
                    </div>
                    <div>
                      <p className="text-slate-500">고정금리</p>
                      <p className="font-financial text-slate-800">{(selectedIrsContract.fixedRate * 100).toFixed(3)}%</p>
                    </div>
                    <div>
                      <p className="text-slate-500">만기일</p>
                      <p className="font-financial text-slate-800">{selectedIrsContract.maturityDate}</p>
                    </div>
                    <div>
                      <p className="text-slate-500">금리 방향</p>
                      <span className={clsx(
                        'inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold',
                        selectedIrsContract.payFixedReceiveFloating
                          ? 'bg-purple-100 text-purple-800'
                          : 'bg-blue-100 text-blue-800',
                      )}>
                        {selectedIrsContract.payFixedReceiveFloating ? 'CFH — 고정지급' : '✓ FVH — 수취고정'}
                      </span>
                    </div>
                    <div>
                      <p className="text-slate-500">변동금리지수</p>
                      <p className="font-financial text-slate-800">{selectedIrsContract.floatingRateIndex}</p>
                    </div>
                  </div>
                  {selectedIrsContract.payFixedReceiveFloating && (
                    <p className="mt-2 text-xs text-amber-700 font-medium">
                      ⚠ 이 계약은 CFH(고정지급) 구조입니다. FVH 지정에는 payFixed=false(수취고정) 계약을 사용하세요.
                    </p>
                  )}
                </div>
              )}
            </>
          )}
        </fieldset>

        {/* ── 제출 버튼 ────────────────────────────────────────────── */}
        <div className="mt-2 flex justify-end lg:hidden">
          <Button type="submit" size="lg" loading={isLoading}>
            헤지 지정 등록
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
  required,
  children,
  action,
}: {
  label: string
  htmlFor?: string
  hint?: string
  error?: string
  required?: boolean
  children: ReactNode
  action?: ReactNode
}) {
  return (
    <div>
      <div className="flex items-center justify-between mb-1.5">
        <label htmlFor={htmlFor} className="block text-xs font-medium text-slate-600">
          {label}
          {required && <span className="text-red-500 ml-0.5">*</span>}
        </label>
        {action && <span>{action}</span>}
      </div>
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
