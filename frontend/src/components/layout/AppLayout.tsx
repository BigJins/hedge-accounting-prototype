import { type ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import clsx from 'clsx'

const NAV_ITEMS = [
  { to: '/dashboard', label: '대시보드', icon: '⌂', hint: '현재 상태와 다음 행동' },
  { to: '/valuation', label: '공정가치 평가', icon: '◈', hint: '1단계 · 계약 등록 및 평가' },
  { to: '/hedge/designation', label: '헤지 지정', icon: '⇄', hint: '2단계 · 위험회피관계 지정' },
  { to: '/effectiveness', label: '유효성 테스트', icon: '◉', hint: '3단계 · 효과성 검증' },
  { to: '/journal', label: '자동 분개', icon: '☰', hint: '4단계 · 결과 확인' },
] as const

interface AppLayoutProps {
  children: ReactNode
}

export default function AppLayout({ children }: AppLayoutProps) {
  return (
    <div className="min-h-screen flex bg-slate-50">
      <aside className="w-60 shrink-0 bg-blue-950 text-white flex flex-col">
        <div className="px-6 py-5 border-b border-blue-900">
          <p className="text-xs font-medium text-blue-300 tracking-widest uppercase">Hedge Accounting</p>
          <h1 className="text-lg font-bold text-white mt-0.5">헤지회계 자동화</h1>
        </div>

        <div className="px-6 py-3 border-b border-blue-900 bg-blue-900/40">
          <p className="text-xs text-blue-300">환경</p>
          <p className="text-sm font-semibold text-white">PoC 데모</p>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-0.5" aria-label="주요 메뉴">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              title={item.hint}
              className={({ isActive }) =>
                clsx(
                  'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-all duration-150',
                  isActive
                    ? 'bg-blue-700 text-white font-medium'
                    : 'text-blue-200 hover:bg-blue-900 hover:text-white',
                )
              }
            >
              <span aria-hidden="true" className="w-4 text-center">
                {item.icon}
              </span>
              <div className="min-w-0">
                <div>{item.label}</div>
                <div className="text-[11px] text-blue-300/80 truncate">{item.hint}</div>
              </div>
            </NavLink>
          ))}
        </nav>

        <div className="px-6 py-4 border-t border-blue-900">
          <p className="text-xs text-blue-400">시작 경로</p>
          <p className="text-xs text-blue-200 mt-1 leading-relaxed">
            공정가치 평가 → 헤지 지정 →<br />
            유효성 테스트 → 자동 분개
          </p>
          <p className="text-xs text-blue-500 mt-2">K-IFRS 1109 · 1113 · 1107</p>
        </div>
      </aside>

      <main className="flex-1 overflow-auto">{children}</main>
    </div>
  )
}
