import { Button } from './Button'

interface PaginationProps {
  /** 현재 페이지 (0-based) */
  page: number
  totalPages: number
  totalElements: number
  size: number
  onPageChange: (page: number) => void
}

/**
 * 공통 페이지네이션 컴포넌트.
 *
 * @remarks
 * page는 0-based (백엔드 Spring Page 기준).
 * UI에는 1-based (page + 1)로 표시합니다.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  onPageChange,
}: PaginationProps) {
  if (totalPages <= 1) return null

  const isFirst = page === 0
  const isLast = page >= totalPages - 1

  return (
    <div className="flex items-center justify-between pt-3 mt-3 border-t border-slate-100">
      <span className="text-xs text-slate-500">
        총 <span className="font-semibold text-slate-700">{totalElements.toLocaleString('ko-KR')}</span>건
      </span>

      <div className="flex items-center gap-2">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={isFirst}
          aria-label="이전 페이지"
        >
          <svg className="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clipRule="evenodd" />
          </svg>
          이전
        </Button>

        <span className="text-xs text-slate-600 tabular-nums font-financial min-w-[4rem] text-center">
          {page + 1} / {totalPages}
        </span>

        <Button
          variant="secondary"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={isLast}
          aria-label="다음 페이지"
        >
          다음
          <svg className="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
          </svg>
        </Button>
      </div>
    </div>
  )
}
