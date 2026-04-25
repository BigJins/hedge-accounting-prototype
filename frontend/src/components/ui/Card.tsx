import { type ReactNode } from 'react'
import clsx from 'clsx'

interface CardProps {
  title?: string
  description?: string
  actions?: ReactNode
  footer?: ReactNode
  children: ReactNode
  className?: string
  variant?: 'default' | 'bordered' | 'elevated'
}

export function Card({
  title,
  description,
  actions,
  footer,
  children,
  className,
  variant = 'default',
}: CardProps) {
  const variants = {
    default:  'bg-white border border-slate-200 rounded-lg',
    bordered: 'bg-white border-2 border-blue-900 rounded-lg',
    elevated: 'bg-white rounded-lg shadow-md',
  }

  return (
    <div className={clsx(variants[variant], className)}>
      {(title || actions) && (
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div>
            {title && <h3 className="text-base font-semibold text-slate-900">{title}</h3>}
            {description && <p className="text-sm text-slate-500 mt-0.5">{description}</p>}
          </div>
          {actions && <div className="flex gap-2">{actions}</div>}
        </div>
      )}
      <div className="p-6">{children}</div>
      {footer && (
        <div className="px-6 py-3 border-t border-slate-100 bg-slate-50 rounded-b-lg">
          {footer}
        </div>
      )}
    </div>
  )
}
