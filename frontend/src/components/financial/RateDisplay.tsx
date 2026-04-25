import clsx from 'clsx'
import { formatRate, formatPercent } from '@/utils/formatters'

interface RateDisplayProps {
  value: number
  type: 'exchange' | 'interest'
  decimals?: number
  className?: string
}

/**
 * 환율/금리 표시 전용 컴포넌트.
 * - exchange: KRW/USD 환율 (소수점 2~4자리)
 * - interest: 이자율 % 표시
 */
export function RateDisplay({ value, type, decimals, className }: RateDisplayProps) {
  const formatted =
    type === 'exchange'
      ? formatRate(value, decimals ?? 4)
      : formatPercent(value)

  return (
    <span className={clsx('font-financial tabular-nums text-slate-800', className)}>
      {formatted}
    </span>
  )
}
