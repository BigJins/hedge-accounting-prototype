import { type ReactNode } from 'react'

interface PageLayoutProps {
  title: string
  subtitle?: string
  badge?: string
  children: ReactNode
}

/** 페이지 공통 레이아웃 — 헤더 + 콘텐츠 영역 */
export default function PageLayout({ title, subtitle, badge, children }: PageLayoutProps) {
  return (
    <div className="p-8">
      {/* 페이지 헤더 */}
      <div className="mb-8">
        <div className="flex items-center gap-3 mb-1">
          <h2 className="text-2xl font-bold text-slate-900">{title}</h2>
          {badge && (
            <span className="px-2 py-0.5 text-xs font-medium bg-blue-100 text-blue-800 rounded-full">
              {badge}
            </span>
          )}
        </div>
        {subtitle && <p className="text-sm text-slate-500">{subtitle}</p>}
      </div>

      {/* 페이지 콘텐츠 */}
      <div className="space-y-6">{children}</div>
    </div>
  )
}
