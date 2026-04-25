import { forwardRef, type InputHTMLAttributes } from 'react'
import { useNumericInput } from '@/hooks/useNumericInput'

/**
 * 천 단위 콤마 자동 포맷 숫자 입력 컴포넌트.
 *
 * - 포커스 시 콤마 제거, blur 시 천 단위 콤마 포맷 적용
 * - 편집 중 cursor jump 없음 (즉시 포맷 금지)
 * - 외부 value 변경(reset/setValue) 자동 반영
 * - `onNumericChange` 콜백으로 순수 number 값 전달 (API 전송용)
 *
 * @example
 * <Controller name="notionalAmount" control={control}
 *   render={({ field }) => (
 *     <NumericInput
 *       id="notionalAmount"
 *       value={field.value}
 *       onNumericChange={(n) => field.onChange(n ?? 0)}
 *       className={inputClass}
 *       placeholder="10,000,000"
 *     />
 *   )}
 * />
 */
export interface NumericInputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'> {
  /** 외부 숫자 값 (react-hook-form field.value 등) */
  value?: number
  /** 콤마 제거된 순수 number 값 콜백 — undefined는 빈 입력 */
  onNumericChange?: (value: number | undefined) => void
}

export const NumericInput = forwardRef<HTMLInputElement, NumericInputProps>(
  ({ value, onNumericChange, ...rest }, ref) => {
    const { displayValue, handleChange, handleBlur, handleFocus } = useNumericInput(value)

    return (
      <input
        {...rest}
        ref={ref}
        type="text"
        inputMode="decimal"
        value={displayValue}
        onChange={(e) => {
          handleChange(e)
          // state 업데이트는 비동기이므로 e.target.value에서 직접 파싱해서 콜백 호출
          const raw = e.target.value.replace(/[^\d.-]/g, '')
          const n = raw === '' || raw === '-' ? undefined : parseFloat(raw)
          onNumericChange?.(isNaN(n as number) ? undefined : n)
        }}
        onBlur={(e) => {
          handleBlur()
          rest.onBlur?.(e)
        }}
        onFocus={(e) => {
          handleFocus()
          rest.onFocus?.(e)
        }}
      />
    )
  },
)

NumericInput.displayName = 'NumericInput'
