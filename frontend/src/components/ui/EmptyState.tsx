/**
 * EmptyState — 빈 상태 공통 컴포넌트
 *
 * 모든 피처의 빈 상태(데이터 없음, ID 미입력 등)를
 * 일관된 레이아웃으로 표시합니다.
 */

type IconType = 'document' | 'chart' | 'clipboard' | 'search' | 'tag' | 'inbox'

interface EmptyStateProps {
  /** 아이콘 종류 */
  icon?: IconType
  /** 상태 제목 (굵게) */
  title: string
  /** 보조 설명 */
  description?: string
  /** 다음 단계 힌트 — 형광 배경으로 강조 표시 */
  hint?: string
}

// ─── 아이콘 경로 ──────────────────────────────────────────────────────────────

const ICON_PATHS: Record<IconType, string> = {
  document:
    'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z',
  chart:
    'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z',
  clipboard:
    'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01',
  search:
    'M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z',
  tag:
    'M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A2 2 0 013 12V7a4 4 0 014-4z',
  inbox:
    'M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4',
}

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

export function EmptyState({ icon = 'document', title, description, hint }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-12 px-6 text-center">
      {/* 아이콘 */}
      <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center mb-4">
        <svg
          className="w-6 h-6 text-slate-400"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.5}
          aria-hidden="true"
        >
          <path strokeLinecap="round" strokeLinejoin="round" d={ICON_PATHS[icon]} />
        </svg>
      </div>

      {/* 제목 */}
      <p className="text-sm font-semibold text-slate-600 mb-1">{title}</p>

      {/* 보조 설명 */}
      {description && (
        <p className="text-xs text-slate-400 max-w-xs leading-relaxed">{description}</p>
      )}

      {/* 다음 단계 힌트 */}
      {hint && (
        <div className="mt-4 px-4 py-2.5 bg-blue-50 border border-blue-100 rounded-lg max-w-sm">
          <p className="text-xs text-blue-700 leading-relaxed">
            <span className="font-semibold">다음 단계 →</span> {hint}
          </p>
        </div>
      )}
    </div>
  )
}
