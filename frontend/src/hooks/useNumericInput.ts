import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * 천 단위 콤마 포맷 숫자 입력 훅.
 *
 * - 포커스 시 콤마 제거 → 편집 편의성 향상
 * - 편집 중에는 raw digits만 표시 (즉시 포맷 금지 — cursor jump 방지)
 * - blur 시 천 단위 콤마 포맷 적용
 * - 외부 value 변경 시 자동 동기화 (편집 중이 아닐 때 — reset/setValue 대응)
 *
 * @param value - 외부에서 주입되는 숫자 값 (react-hook-form field.value 등)
 * @returns displayValue(표시용 문자열), numericValue(API용 number|undefined),
 *          handleChange, handleBlur, handleFocus, reset
 */
export function useNumericInput(value?: number) {
  const formatWithComma = (raw: string): string => {
    const cleaned = raw.replace(/[^\d.-]/g, '')
    if (cleaned === '' || cleaned === '-') return cleaned
    const parts = cleaned.split('.')
    const intPart = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    return parts.length > 1 ? `${intPart}.${parts[1]}` : intPart
  }

  const toDisplay = (v?: number): string => {
    if (v === undefined || v === null) return ''
    return formatWithComma(String(v))
  }

  const [displayValue, setDisplayValue] = useState<string>(toDisplay(value))
  const isEditing = useRef(false)

  // 외부 value 변경 시 표시값 동기화 (편집 중이 아닐 때만)
  // react-hook-form reset(), setValue(), 서버 프리필 대응
  useEffect(() => {
    if (!isEditing.current) {
      setDisplayValue(toDisplay(value))
    }
  }, [value]) // eslint-disable-line react-hooks/exhaustive-deps

  // 포커스: 콤마 제거 → 편집 중 숫자만 보이도록
  const handleFocus = useCallback(() => {
    isEditing.current = true
    setDisplayValue((prev) => prev.replace(/,/g, ''))
  }, [])

  // 변경: raw digits만 저장, 즉시 포맷 하지 않음 (cursor jump 방지)
  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const cleaned = e.target.value.replace(/[^\d.-]/g, '')
    setDisplayValue(cleaned)
  }, [])

  // blur: 천 단위 콤마 포맷 적용
  const handleBlur = useCallback(() => {
    isEditing.current = false
    setDisplayValue((prev) => formatWithComma(prev))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const numericValue: number | undefined = (() => {
    const raw = displayValue.replace(/,/g, '')
    if (raw === '' || raw === '-') return undefined
    const n = parseFloat(raw)
    return isNaN(n) ? undefined : n
  })()

  const reset = useCallback((v?: number) => {
    isEditing.current = false
    setDisplayValue(toDisplay(v))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return { displayValue, numericValue, handleChange, handleBlur, handleFocus, reset }
}
