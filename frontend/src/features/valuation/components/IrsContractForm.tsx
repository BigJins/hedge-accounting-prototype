import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { NumericInput } from '@/components/ui/NumericInput'
import { MutationErrorAlert } from '@/components/ui/ErrorAlert'
import type { IrsContractRequest, IrsContractResponse } from '@/types/valuation'
import { useIrsContractMutation } from '../api/useIrsValuation'

// ─── Zod 스키마 ──────────────────────────────────────────────────────────────

const schema = z
  .object({
    contractId:              z.string().min(1, '계약번호를 입력하세요.').max(50),
    notionalAmount:          z.coerce.number().positive('명목금액은 0보다 커야 합니다.'),
    fixedRate:               z.coerce.number().min(0, '고정금리는 0 이상이어야 합니다.'),
    floatingRateIndex:       z.string().min(1, '변동금리 기준지수를 입력하세요.').max(30),
    floatingSpread:          z.coerce.number().optional(),
    contractDate:            z.string().min(1, '계약일을 입력하세요.'),
    maturityDate:            z.string().min(1, '만기일을 입력하세요.'),
    payFixedReceiveFloating: z.boolean(),
    settlementFrequency:     z.enum(['QUARTERLY', 'SEMI_ANNUAL', 'ANNUAL'], {
      required_error: '결제 주기를 선택하세요.',
    }),
    dayCountConvention:      z.enum(['ACT_365', 'ACT_360'], {
      required_error: '일수 계산 관행을 선택하세요.',
    }),
    counterpartyName:          z.string().max(100).optional(),
    counterpartyCreditRating:  z.enum(['AAA', 'AA', 'A', 'BBB', 'BB', 'B', 'CCC', 'D']).optional(),
  })
  .refine(
    (d) => !d.contractDate || !d.maturityDate || d.maturityDate > d.contractDate,
    { message: '만기일은 계약일 이후여야 합니다.', path: ['maturityDate'] },
  )

type FormValues = z.infer<typeof schema>

// ─── 예시 데이터 ─────────────────────────────────────────────────────────────

const SAMPLE_VALUES: FormValues = {
  contractId:              'IRS-FVH-2026-001',
  notionalAmount:          10_000_000_000,       // 100억
  fixedRate:               0.035,                // 3.5% 고정
  floatingRateIndex:       'CD_91D',
  floatingSpread:          0,
  contractDate:            '2026-01-02',
  maturityDate:            '2028-12-31',
  payFixedReceiveFloating: false,                 // 변동지급/고정수취 = FVH
  settlementFrequency:     'QUARTERLY',
  dayCountConvention:      'ACT_365',
  counterpartyName:        '한국산업은행',
  counterpartyCreditRating: 'AA',
}

const DEFAULT_VALUES: FormValues = {
  contractId:              '',
  notionalAmount:          0,
  fixedRate:               0,
  floatingRateIndex:       '',
  floatingSpread:          undefined,
  contractDate:            '',
  maturityDate:            '',
  payFixedReceiveFloating: false,
  settlementFrequency:     'QUARTERLY',
  dayCountConvention:      'ACT_365',
  counterpartyName:        '',
  counterpartyCreditRating: undefined,
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface IrsContractFormProps {
  onSuccess: (contract: IrsContractResponse) => void
}

// ─── 공통 input 클래스 ────────────────────────────────────────────────────────

const inputClass =
  'w-full border border-slate-300 rounded-md px-3 py-2 text-sm text-slate-900 ' +
  'focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-blue-600 ' +
  'disabled:bg-slate-50 disabled:text-slate-400 font-financial'

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

/**
 * IRS 계약 등록 폼.
 *
 * @see K-IFRS 1109호 6.2.1 (위험회피수단 — IRS 계약 적격성)
 * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
 */
export function IrsContractForm({ onSuccess }: IrsContractFormProps) {
  const mutation = useIrsContractMutation()

  const {
    register,
    control,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: DEFAULT_VALUES,
  })

  const fillSample = () => {
    Object.entries(SAMPLE_VALUES).forEach(([k, v]) => {
      setValue(k as keyof FormValues, v as never, { shouldValidate: false })
    })
  }

  const onSubmit = (data: FormValues) => {
    const request: IrsContractRequest = {
      ...data,
      floatingSpread:          data.floatingSpread ?? null,
      counterpartyName:        data.counterpartyName || null,
      counterpartyCreditRating: data.counterpartyCreditRating ?? null,
    }
    mutation.mutate(request, {
      onSuccess: (contract) => {
        onSuccess(contract)
        reset()
      },
    })
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <Card
        title="IRS 계약 등록"
        description="이자율스왑 계약을 위험회피수단으로 등록합니다 — K-IFRS 1109호 §6.2.1"
        variant="bordered"
        actions={
          <span className="text-xs text-blue-700 font-medium bg-blue-50 px-2 py-1 rounded">
            K-IFRS 1109호 6.4.1
          </span>
        }
      >
        {/* 2단계 엔진 안내 */}
        <div className="mb-5 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3">
          <p className="text-xs font-semibold text-blue-800 mb-1">IRS 금리위험 FVH — 2단계 확장 엔진</p>
          <p className="text-xs text-blue-700 leading-relaxed">
            payFixedReceiveFloating = <strong>false</strong> (변동지급/고정수취) = 공정가치 위험회피(FVH).
            금리 하락 시 고정채권 공정가치 상승을 IRS 평가손실로 상쇄합니다.
          </p>
        </div>

        {/* 상단 액션 */}
        <div className="flex justify-end mb-4">
          <Button type="button" variant="ghost" size="sm" onClick={fillSample}>
            예시 데이터 채우기
          </Button>
        </div>

        {/* ── 계약 기본 정보 ───────────────────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            계약 기본 정보
          </legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field htmlFor="contractId" label="계약번호" error={errors.contractId?.message}>
              <input
                id="contractId"
                {...register('contractId')}
                className={inputClass}
                placeholder="IRS-FVH-2026-001"
              />
            </Field>

            <Field
              htmlFor="notionalAmount"
              label="명목금액 (KRW)"
              error={errors.notionalAmount?.message}
              hint="이자 계산의 기준금액 — K-IFRS 1109호 B6.4.9~B6.4.11"
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
                    placeholder="10,000,000,000"
                  />
                )}
              />
            </Field>

            <Field
              htmlFor="fixedRate"
              label="고정금리 (소수 — 예: 0.035 = 3.5%)"
              error={errors.fixedRate?.message}
              hint="K-IFRS 1113호 Level 2 관측가능 투입변수"
            >
              <Controller
                name="fixedRate"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="fixedRate"
                    value={field.value}
                    onNumericChange={(n) => field.onChange(n ?? 0)}
                    className={inputClass}
                    placeholder="0.0350"
                  />
                )}
              />
            </Field>

            <Field
              htmlFor="floatingRateIndex"
              label="변동금리 기준지수"
              error={errors.floatingRateIndex?.message}
            >
              <select id="floatingRateIndex" {...register('floatingRateIndex')} className={inputClass}>
                <option value="">선택하세요</option>
                <option value="CD_91D">CD_91D — 91일 CD금리 (원화 표준)</option>
                <option value="SOFR">SOFR — Secured Overnight Financing Rate (USD)</option>
                <option value="EURIBOR_3M">EURIBOR_3M — 3개월 유로금리</option>
                <option value="LIBOR_3M">LIBOR_3M — 3개월 LIBOR (레거시)</option>
              </select>
            </Field>

            <Field
              htmlFor="floatingSpread"
              label="변동금리 스프레드 (소수 — nullable)"
              error={errors.floatingSpread?.message}
              hint="없으면 0 또는 비워두세요"
            >
              <Controller
                name="floatingSpread"
                control={control}
                render={({ field }) => (
                  <NumericInput
                    id="floatingSpread"
                    value={field.value ?? 0}
                    onNumericChange={(n) => field.onChange(n)}
                    className={inputClass}
                    placeholder="0.0000"
                  />
                )}
              />
            </Field>
          </div>
        </fieldset>

        {/* ── 계약 기간 ─────────────────────────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            계약 기간
          </legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field htmlFor="contractDate" label="계약일" error={errors.contractDate?.message}>
              <input id="contractDate" {...register('contractDate')} type="date" className={inputClass} />
            </Field>

            <Field htmlFor="maturityDate" label="만기일" error={errors.maturityDate?.message}>
              <input id="maturityDate" {...register('maturityDate')} type="date" className={inputClass} />
            </Field>
          </div>
        </fieldset>

        {/* ── 스왑 구조 ─────────────────────────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            스왑 구조 및 결제 조건
          </legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field
              htmlFor="payFixedReceiveFloating"
              label="금리 방향"
              error={errors.payFixedReceiveFloating?.message}
              hint="FVH = 변동지급/고정수취 (false)"
            >
              <select
                id="payFixedReceiveFloating"
                {...register('payFixedReceiveFloating', { setValueAs: (v) => v === 'true' })}
                className={inputClass}
              >
                <option value="false">변동지급/고정수취 — FVH (공정가치 위험회피)</option>
                <option value="true">고정지급/변동수취 — CFH (현금흐름 위험회피)</option>
              </select>
            </Field>

            <Field
              htmlFor="settlementFrequency"
              label="결제 주기"
              error={errors.settlementFrequency?.message}
            >
              <select id="settlementFrequency" {...register('settlementFrequency')} className={inputClass}>
                <option value="QUARTERLY">QUARTERLY — 분기별</option>
                <option value="SEMI_ANNUAL">SEMI_ANNUAL — 반기별</option>
                <option value="ANNUAL">ANNUAL — 연간</option>
              </select>
            </Field>

            <Field
              htmlFor="dayCountConvention"
              label="일수 계산 관행 (Day Count)"
              error={errors.dayCountConvention?.message}
              hint="원화 IRS: ACT_365 / USD IRS: ACT_360"
            >
              <select id="dayCountConvention" {...register('dayCountConvention')} className={inputClass}>
                <option value="ACT_365">ACT/365 Fixed — KRW (한국 자금시장 표준)</option>
                <option value="ACT_360">ACT/360 — USD/EUR (SOFR · EURIBOR 표준)</option>
              </select>
            </Field>
          </div>
        </fieldset>

        {/* ── 거래상대방 (optional) ──────────────────────────────────── */}
        <fieldset className="mb-6">
          <legend className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">
            거래상대방 정보 (선택)
          </legend>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field htmlFor="counterpartyName" label="거래상대방명" error={errors.counterpartyName?.message}>
              <input
                id="counterpartyName"
                {...register('counterpartyName')}
                className={inputClass}
                placeholder="한국산업은행"
              />
            </Field>

            <Field
              htmlFor="counterpartyCreditRating"
              label="거래상대방 신용등급"
              error={errors.counterpartyCreditRating?.message}
              hint="K-IFRS 1109호 B6.4.7: 신용위험 지배 여부 판단"
            >
              <select id="counterpartyCreditRating" {...register('counterpartyCreditRating')} className={inputClass}>
                <option value="">선택 안함</option>
                <optgroup label="투자등급">
                  <option value="AAA">AAA — 최우량</option>
                  <option value="AA">AA — 우량</option>
                  <option value="A">A — 양호</option>
                  <option value="BBB">BBB — 적정 (투자등급 최저)</option>
                </optgroup>
                <optgroup label="비투자등급">
                  <option value="BB">BB — 투기</option>
                  <option value="B">B — 투기</option>
                  <option value="CCC">CCC — 부실 우려</option>
                  <option value="D">D — 채무불이행</option>
                </optgroup>
              </select>
            </Field>
          </div>
        </fieldset>

        {mutation.isError && <MutationErrorAlert error={mutation.error} className="mb-4" />}

        <div className="flex items-center justify-between">
          <p className="text-xs text-slate-400">
            K-IFRS 1109호 6.2.1 · 6.4.1(2) — 위험회피수단 등록 및 공식 문서화
          </p>
          <Button type="submit" size="lg" loading={mutation.isPending}>
            IRS 계약 등록
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
