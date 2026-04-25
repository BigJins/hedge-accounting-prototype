import { Component } from 'react'
import type { ReactNode, ErrorInfo } from 'react'

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
  error?: Error
}

// ─── 에러 메시지 변환 (기술적 문구 → 사용자 친화적) ──────────────────────────

function friendlyBoundaryMessage(err?: Error): { title: string; detail: string } {
  const raw = err?.message ?? ''
  const lower = raw.toLowerCase()

  if (
    lower.includes('network error') ||
    lower.includes('failed to fetch') ||
    lower.includes('econnrefused')
  ) {
    return {
      title: '서버에 연결할 수 없습니다',
      detail:
        '백엔드 서버(포트 8090)가 실행 중인지 확인해 주세요. ' +
        '서버를 시작한 후 페이지를 새로고침하면 정상 동작합니다.',
    }
  }

  if (lower.includes('chunkloaderror') || lower.includes('loading chunk')) {
    return {
      title: '페이지 리소스를 불러오지 못했습니다',
      detail: '네트워크 연결을 확인하고 페이지를 새로고침해 주세요.',
    }
  }

  if (raw) {
    return {
      title: '화면을 표시하는 중 오류가 발생했습니다',
      detail: raw,
    }
  }

  return {
    title: '화면을 표시하는 중 오류가 발생했습니다',
    detail: '알 수 없는 오류입니다. 페이지를 새로고침해 주세요.',
  }
}

// ─── ErrorBoundary 클래스 컴포넌트 ──────────────────────────────────────────────

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ErrorBoundary]', error, info)
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback
      const { title, detail } = friendlyBoundaryMessage(this.state.error)
      return (
        <FallbackUI
          title={title}
          detail={detail}
          onRetry={() => this.setState({ hasError: false })}
        />
      )
    }
    return this.props.children
  }
}

// ─── FallbackUI ──────────────────────────────────────────────────────────────

function FallbackUI({
  title,
  detail,
  onRetry,
}: {
  title: string
  detail: string
  onRetry: () => void
}) {
  return (
    <div className="flex flex-col items-center justify-center min-h-[200px] p-8 text-center">
      <div className="mb-4 text-red-400">
        <svg
          className="w-12 h-12 mx-auto"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
          />
        </svg>
      </div>
      <h2 className="text-base font-semibold text-slate-800 mb-2">{title}</h2>
      <p className="text-sm text-slate-500 max-w-md mb-4">{detail}</p>
      <button
        type="button"
        onClick={onRetry}
        className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 transition-colors"
      >
        다시 시도
      </button>
    </div>
  )
}