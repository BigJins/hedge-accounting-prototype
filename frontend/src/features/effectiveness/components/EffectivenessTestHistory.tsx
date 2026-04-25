import { useState, useEffect } from 'react'
import clsx from 'clsx'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorAlert } from '@/components/ui/ErrorAlert'
import { useEffectivenessTestList } from '../api/useEffectivenessTest'
import { EffectivenessTestResult } from './EffectivenessTestResult'
import { formatDate } from '@/utils/formatters'
import type { EffectivenessTestResponse } from '@/types/effectiveness'

// ─── Props ────────────────────────────────────────────────────────────────────

interface EffectivenessTestHistoryProps {
  /** 위험회피관계 ID — 없으면 안내 메시지 표시 */
  hedgeRelationshipId?: string | null
  /** "이 값으로 재테스트" 클릭 시 콜백 */
  onRetest?: (item: EffectivenessTestResponse) => void
}

// ─── 상수 ─────────────────────────────────────────────────────────────────────

const PAGE_SIZE = 10

// ─── 배지 매핑 ────────────────────────────────────────────────────────────────

const RESULT_BADGE: Record<string, { label: string; className: string }> = {
  PASS:    { label: 'PASS',    className: 'bg-emerald-100 text-emerald-800' },
  WARNING: { label: 'WARNING', className: 'bg-amber-100 text-amber-800' },
  FAIL:    { label: 'FAIL',    className: 'bg-red-100 text-red-700' },
}

const ACTION_BADGE: Record<string, { label: string; className: string }> = {
  NONE:        { label: '조치 불필요', className: 'bg-slate-100 text-slate-600' },
  REBALANCE:   { label: '재조정',     className: 'bg-amber-100 text-amber-800' },
  DISCONTINUE: { label: '중단 필요',  className: 'bg-red-100 text-red-700' },
}

const TEST_TYPE_LABEL: Record<string, string> = {
  DOLLAR_OFFSET_PERIODIC:   '기간별 (표준)',
  DOLLAR_OFFSET_CUMULATIVE: '누적 (보조)',
}

const HEDGE_TYPE_LABEL: Record<string, string> = {
  FAIR_VALUE: '공정가치',
  CASH_FLOW:  '현금흐름',
}

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

/**
 * 유효성 테스트 이력 테이블.
 *
 * hedgeRelationshipId가 지정되면 해당 위험회피관계의 이력을 페이징으로 표시합니다.
 * 행 클릭 시 상세 결과 패널, "이 값으로 재테스트" 버튼을 제공합니다.
 *
 * @see K-IFRS 1107호 (헤지회계 공시 — 유효성 테스트 이력)
 */
export function EffectivenessTestHistory({ hedgeRelationshipId, onRetest }: EffectivenessTestHistoryProps) {
  const [page, setPage] = useState(0)
  const [selectedItem, setSelectedItem] = useState<EffectivenessTestResponse | null>(null)

  // hedgeRelationshipId 변경 시 페이지 및 선택 항목 리셋
  useEffect(() => {
    setPage(0)
    setSelectedItem(null)
  }, [hedgeRelationshipId])

  const { data, isLoading, isError, error } = useEffectivenessTestList(
    hedgeRelationshipId,
    page,
    PAGE_SIZE,
  )

  // null guard — 데이터 없을 때 안전한 기본값
  const items        = data?.content ?? []
  const totalElements = data?.totalElements ?? 0
  const totalPages   = data?.totalPages ?? 0
  const pageNumber   = data?.number ?? page
  const pageSize     = data?.size ?? PAGE_SIZE

  const handlePageChange = (newPage: number) => {
    setPage(newPage)
    setSelectedItem(null)
  }

  // ── hedgeRelationshipId 미입력 ────────────────────────────────────────────
  if (!hedgeRelationshipId) {
    return (
      <Card title="유효성 테스트 이력" description="위험회피관계별 테스트 기록">
        <EmptyState
          icon="clipboard"
          title="위험회피관계 ID를 입력하면 이력이 표시됩니다"
          description="유효성 테스트 이력은 특정 헤지관계에 연결되어 있습니다."
          hint="위 폼에서 '위험회피관계 ID'를 입력하고 '유효성 테스트 실행'을 클릭하세요. ID는 헤지 지정 화면의 이력 테이블에서 확인할 수 있습니다."
        />
      </Card>
    )
  }

  // ── 로딩 스켈레톤 ─────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <Card title="유효성 테스트 이력" description={`${hedgeRelationshipId} 테스트 기록`}>
        <div className="overflow-hidden rounded-lg border border-slate-200" aria-busy="true" aria-label="이력 불러오는 중">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                {['평가기준일', '방법', '유형', '유효성 비율', '결과', '조치', '작업'].map((h) => (
                  <th key={h} scope="col" className="px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider text-left">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {Array.from({ length: 3 }).map((_, i) => (
                <tr key={i}>
                  {Array.from({ length: 7 }).map((__, j) => (
                    <td key={j} className="px-4 py-3">
                      <div className="h-3.5 bg-slate-200 rounded animate-pulse" style={{ width: j === 6 ? '4rem' : '60%' }} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    )
  }

  // ── 에러 ──────────────────────────────────────────────────────────────────
  if (isError) {
    return (
      <Card title="유효성 테스트 이력" description={`${hedgeRelationshipId} 테스트 기록`}>
        <ErrorAlert error={error} onRetry={() => window.location.reload()} />
      </Card>
    )
  }

  // ── 빈 상태 ───────────────────────────────────────────────────────────────
  if (!data || data.empty) {
    return (
      <Card title="유효성 테스트 이력" description={`${hedgeRelationshipId} 테스트 기록`}>
        <EmptyState
          icon="clipboard"
          title="이 헤지관계의 테스트 기록이 없습니다"
          description={`"${hedgeRelationshipId}"에 대한 유효성 테스트를 아직 실행하지 않았습니다.`}
          hint="위 폼에서 헤지수단 공정가치 변동액과 피헤지항목 현재가치 변동액을 입력하고 '유효성 테스트 실행'을 클릭하세요."
        />
      </Card>
    )
  }

  return (
    <Card
      title="유효성 테스트 이력"
      description={`${hedgeRelationshipId} — ${totalElements}건`}
    >
      <div className="space-y-4">
        {/* ── 이력 테이블 ─────────────────────────────────────────────── */}
        <div className="overflow-hidden rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  평가기준일
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  방법
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  유형
                </th>
                <th scope="col" className="text-right px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  유효성 비율
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  결과
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  조치
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  작업
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((item) => {
                const isSelected   = selectedItem?.effectivenessTestId === item.effectivenessTestId
                const resultBadge  = RESULT_BADGE[item.testResult] ?? { label: item.testResult, className: 'bg-slate-100 text-slate-600' }
                const actionBadge  = ACTION_BADGE[item.actionRequired] ?? { label: item.actionRequired, className: 'bg-slate-100 text-slate-600' }
                const ratioPercent = `${(Math.abs(item.effectivenessRatio) * 100).toFixed(2)}%`

                return (
                  <tr
                    key={item.effectivenessTestId}
                    className={clsx(
                      'transition-colors cursor-pointer',
                      isSelected ? 'bg-blue-50' : 'hover:bg-slate-50',
                    )}
                    onClick={() => setSelectedItem(isSelected ? null : item)}
                    aria-selected={isSelected}
                  >
                    <td className="px-4 py-3 text-slate-700 font-financial tabular-nums">
                      {formatDate(item.testDate)}
                    </td>
                    <td className="px-4 py-3 text-slate-600 text-xs">
                      {TEST_TYPE_LABEL[item.testType] ?? item.testType}
                    </td>
                    <td className="px-4 py-3 text-slate-600 text-xs">
                      {HEDGE_TYPE_LABEL[item.hedgeType] ?? item.hedgeType}
                    </td>
                    <td className="px-4 py-3 text-right font-financial tabular-nums text-slate-700">
                      {ratioPercent}
                    </td>
                    <td className="px-4 py-3">
                      <span className={clsx(
                        'inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold',
                        resultBadge.className,
                      )}>
                        {resultBadge.label}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={clsx(
                        'inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold',
                        actionBadge.className,
                      )}>
                        {actionBadge.label}
                      </span>
                    </td>
                    <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => setSelectedItem(isSelected ? null : item)}
                        aria-pressed={isSelected}
                      >
                        {isSelected ? '닫기' : '상세'}
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        {/* ── 상세 패널 ──────────────────────────────────────────────── */}
        {selectedItem && (
          <EffectivenessDetailPanel
            item={selectedItem}
            onRetest={onRetest}
            onClose={() => setSelectedItem(null)}
          />
        )}

        {/* ── 페이지네이션 ───────────────────────────────────────────── */}
        <Pagination
          page={pageNumber}
          totalPages={totalPages}
          totalElements={totalElements}
          size={pageSize}
          onPageChange={handlePageChange}
        />
      </div>
    </Card>
  )
}

// ─── 상세 패널 ────────────────────────────────────────────────────────────────

interface EffectivenessDetailPanelProps {
  item: EffectivenessTestResponse
  onRetest?: (item: EffectivenessTestResponse) => void
  onClose: () => void
}

/**
 * 유효성 테스트 상세 패널.
 * EffectivenessTestResult를 인라인으로 렌더링하고 재테스트 버튼을 제공합니다.
 */
function EffectivenessDetailPanel({ item, onRetest, onClose }: EffectivenessDetailPanelProps) {
  return (
    <div
      role="region"
      aria-label="유효성 테스트 상세"
      className="rounded-lg border border-blue-200 bg-blue-50 p-5"
    >
      {/* 패널 헤더 */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h4 className="text-sm font-semibold text-blue-900">
            테스트 상세 — {item.hedgeRelationshipId}
          </h4>
          <p className="text-xs text-blue-600 mt-0.5">
            평가기준일: {formatDate(item.testDate)} · ID: {item.effectivenessTestId}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="상세 패널 닫기"
          className="text-blue-400 hover:text-blue-600 transition-colors p-1"
        >
          <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
          </svg>
        </button>
      </div>

      {/* 결과 상세 */}
      <EffectivenessTestResult result={item} />

      {/* 재테스트 버튼 */}
      {onRetest && (
        <div className="flex justify-end pt-3 border-t border-blue-200">
          <button
            type="button"
            onClick={() => onRetest(item)}
            className="px-3 py-1.5 text-xs font-medium text-blue-700 bg-white border border-blue-300 rounded-md hover:bg-blue-50 transition-colors"
          >
            이 값으로 재테스트
          </button>
        </div>
      )}
    </div>
  )
}