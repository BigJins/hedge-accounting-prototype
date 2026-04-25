/**
 * ErrorAlert — 사용자 친화적 에러 알림 컴포넌트
 *
 * 기술적 에러 메시지(axios, fetch 등)를 심사관이 이해할 수 있는 문구로 변환합니다.
 * onRetry 제공 시 "다시 시도" 링크가 표시됩니다.
 */

interface ErrorAlertProps {
  /** TanStack Query / axios / Error 객체 */
  error: Error | unknown
  /** 재시도 콜백 (refetch 등) */
  onRetry?: () => void
  className?: string
}

// ─── 에러 메시지 → 사용자 친화적 문구 변환 ────────────────────────────────────

function friendlyMessage(err: Error | unknown): string {
  const raw = err instanceof Error ? err.message : String(err ?? '')

  if (!raw || raw === 'Unknown Error' || raw === 'undefined') {
    return '서버와 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'
  }

  const lower = raw.toLowerCase()

  // 네트워크 연결 불가 (백엔드 미실행)
  if (
    lower.includes('network error') ||
    lower.includes('failed to fetch') ||
    lower.includes('err_connection_refused') ||
    lower.includes('econnrefused')
  ) {
    return '백엔드 서버(포트 8090)에 연결할 수 없습니다. 서버가 실행 중인지 확인해 주세요.'
  }

  // HTTP 500
  if (lower.includes('500') || lower.includes('internal server error')) {
    return '서버 내부 오류가 발생했습니다. 입력 값을 확인하거나 잠시 후 다시 시도해 주세요.'
  }

  // HTTP 404
  if (lower.includes('404') || lower.includes('not found')) {
    return '요청한 데이터를 찾을 수 없습니다. ID가 올바른지 확인해 주세요.'
  }

  // HTTP 403 / 401
  if (lower.includes('403') || lower.includes('401') || lower.includes('unauthorized') || lower.includes('forbidden')) {
    return '접근 권한이 없습니다.'
  }

  // 타임아웃
  if (lower.includes('timeout') || lower.includes('timed out')) {
    return '응답 시간이 초과됐습니다. 다시 시도해 주세요.'
  }

  // 422 (유효성 오류) - 보통 업무 메시지가 담겨 있으므로 원문 노출
  if (lower.includes('422') || lower.includes('unprocessable')) {
    return '입력 값이 유효하지 않습니다. 폼 내용을 다시 확인해 주세요.'
  }

  // 그 외 — 원문 노출 (업무 메시지일 가능성)
  return raw
}

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

export function ErrorAlert({ error, onRetry, className }: ErrorAlertProps) {
  const message = friendlyMessage(error)

  return (
    <div
      role="alert"
      className={`px-4 py-3.5 bg-red-50 border border-red-200 rounded-lg ${className ?? ''}`}
    >
      <div className="flex items-start gap-3">
        {/* 아이콘 */}
        <svg
          className="w-4 h-4 text-red-500 mt-0.5 flex-shrink-0"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>

        {/* 메시지 */}
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-red-800">오류가 발생했습니다</p>
          <p className="text-xs text-red-600 mt-0.5 break-words">{message}</p>
          {onRetry && (
            <button
              type="button"
              onClick={onRetry}
              className="text-xs font-semibold text-red-700 underline underline-offset-2 mt-2 hover:text-red-900 transition-colors"
            >
              다시 시도
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

/**
 * MutationErrorAlert — 뮤테이션(저장/실행) 실패 알림
 *
 * 폼 제출 후 서버 오류 시 폼 하단에 인라인으로 표시합니다.
 * onRetry 없이도 사용 가능합니다.
 */
export function MutationErrorAlert({
  error,
  className,
}: {
  error: Error | unknown
  className?: string
}) {
  const message = friendlyMessage(error)

  return (
    <div
      role="alert"
      className={`px-4 py-3 bg-red-50 border border-red-200 rounded-lg ${className ?? ''}`}
    >
      <div className="flex items-start gap-2.5">
        <svg
          className="w-4 h-4 text-red-500 mt-0.5 flex-shrink-0"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
        <div>
          <p className="text-sm font-semibold text-red-800">실행에 실패했습니다</p>
          <p className="text-xs text-red-600 mt-0.5">{message}</p>
          <p className="text-xs text-red-500 mt-1.5">
            입력 값을 확인하고 다시 시도하거나, 문제가 지속되면 서버 로그를 확인해 주세요.
          </p>
        </div>
      </div>
    </div>
  )
}
