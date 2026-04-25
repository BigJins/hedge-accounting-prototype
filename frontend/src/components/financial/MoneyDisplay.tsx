import clsx from 'clsx'
import { formatKrw } from '@/utils/formatters'

interface MoneyDisplayProps {
  amount: number
  abbreviated?: boolean
  showSign?: boolean
  size?: 'sm' | 'md' | 'lg' | 'xl'
  className?: string
}

/**
 * 금액 표시 전용 컴포넌트.
 * - 천단위 콤마 자동 적용
 * - 양수(익)/음수(손) 색상 자동 구분
 * - 등폭 폰트(Roboto Mono) 적용
 */
export function MoneyDisplay({
  amount,
  abbreviated = false,
  showSign = false,
  size = 'md',
  className,
}: MoneyDisplayProps) {
  const isPositive = amount > 0
  const isNegative = amount < 0
  const isZero = amount === 0

  const sizes = {
    sm: 'text-sm',
    md: 'text-base',
    lg: 'text-xl',
    xl: 'text-3xl font-semibold',
  }

  const colorClass = isPositive
    ? 'text-emerald-600'
    : isNegative
      ? 'text-red-600'
      : 'text-slate-700'

  const prefix = showSign && isPositive ? '+' : ''

  return (
    <span
      className={clsx('font-financial tabular-nums', sizes[size], !isZero && colorClass, className)}
      aria-label={`${amount.toLocaleString('ko-KR')}원`}
    >
      {prefix}{formatKrw(amount, abbreviated)}
    </span>
  )
}
