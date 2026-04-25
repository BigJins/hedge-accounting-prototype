import { useState, useEffect, type ReactNode } from 'react'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorAlert } from '@/components/ui/ErrorAlert'
import { MoneyDisplay } from '@/components/financial/MoneyDisplay'
import { RateDisplay } from '@/components/financial/RateDisplay'
import {
  useFxForwardValuationList,
  useFxForwardValuationByContract,
  useDeleteFxForwardValuation,
} from '../api/useFxForwardValuation'
import { formatDate, formatPercent } from '@/utils/formatters'
import type { FxForwardValuationResponse } from '@/types/valuation'
import clsx from 'clsx'

// ─── Props ────────────────────────────────────────────────────────────────────

interface FxForwardValuationHistoryProps {
  /** 지정 시 해당 계약의 평가 이력만 표시 */
  contractId?: string | null
  /** 이 값으로 재평가 버튼 클릭 시 콜백 */
  onEdit?: (item: FxForwardValuationResponse) => void
  /**
   * 평가 성공 시 부모가 올려주는 카운터.
   * 값이 바뀔 때마다 페이지를 0으로 리셋해 새 결과를 첫 페이지에서 바로 보여줍니다.
   */
  refreshKey?: number
}

// ─── 상수 ─────────────────────────────────────────────────────────────────────

export const PAGE_SIZE = 10

const LEVEL_BADGE: Record<FxForwardValuationResponse['fairValueLevel'], string> = {
  LEVEL_1: 'bg-emerald-100 text-emerald-800',
  LEVEL_2: 'bg-blue-100 text-blue-800',
  LEVEL_3: 'bg-amber-100 text-amber-800',
}

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

/**
 * 공정가치 평가 이력 테이블.
 * contractId가 지정되면 해당 계약의 이력만, 없으면 전체 이력을 페이징으로 표시합니다.
 * 상세보기 및 재평가 기능을 제공합니다.
 */
export function FxForwardValuationHistory({ contractId, onEdit, refreshKey }: FxForwardValuationHistoryProps) {
  const [page, setPage] = useState(0)
  const [selectedItem, setSelectedItem] = useState<FxForwardValuationResponse | null>(null)

  // contractId 변경 또는 새 평가 완료(refreshKey 증가) 시 페이지 및 선택 항목 리셋.
  // refreshKey는 같은 계약을 재평가할 때도 page 0으로 돌아오게 보장합니다.
  useEffect(() => {
    setPage(0)
    setSelectedItem(null)
  }, [contractId, refreshKey])

  // 훅은 조건부 호출 불가 — 둘 다 호출하고 enabled로 제어
  const listQuery = useFxForwardValuationList(page, PAGE_SIZE, !contractId)
  const contractQuery = useFxForwardValuationByContract(contractId ?? '', page, PAGE_SIZE, !!contractId)

  const activeQuery = contractId ? contractQuery : listQuery
  const { data, isLoading, isFetching, isError, error } = activeQuery

  // null guard — 데이터 없을 때 안전한 기본값
  const items = data?.content ?? []
  const totalElements = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 0
  const pageNumber = data?.number ?? page
  const pageSize = data?.size ?? PAGE_SIZE

  const deleteMutation = useDeleteFxForwardValuation()

  const handleDelete = (valuationId: number) => {
    deleteMutation.mutate(valuationId, {
      onError: (err) => {
        console.error(`평가 삭제 실패 [id=${valuationId}]:`, err)
      },
      onSuccess: () => {
        // 삭제된 항목이 상세보기 중이면 패널 닫기
        if (selectedItem?.valuationId === valuationId) {
          setSelectedItem(null)
        }
      },
    })
  }

  const handlePageChange = (newPage: number) => {
    setPage(newPage)
    setSelectedItem(null)
  }

  // 카드 제목/설명: 계약 필터 여부에 따라 다르게 표시
  const cardTitle = '평가 이력'
  const cardDescription = contractId
    ? `계약 ${contractId} 평가 기록`
    : '전체 공정가치 평가 기록'

  // ── 로딩 스켈레톤 ─────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <Card title={cardTitle} description={cardDescription}>
        <div className="overflow-hidden rounded-lg border border-slate-200" aria-busy="true" aria-label="이력 불러오는 중">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                {['평가기준일', '계약번호', '공정가치', '공정가치 변동액', '잔존일수', '수준', '작업'].map((h) => (
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
                      <div className="h-3.5 bg-slate-200 rounded animate-pulse" style={{ width: j === 2 || j === 3 ? '80%' : j === 6 ? '4rem' : '60%' }} />
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
      <Card title={cardTitle} description={cardDescription}>
        <ErrorAlert error={error} onRetry={() => window.location.reload()} />
      </Card>
    )
  }

  // ── 빈 상태 ───────────────────────────────────────────────────────────────
  if (!data || data.empty) {
    return (
      <Card title={cardTitle} description={cardDescription}>
        <EmptyState
          icon="chart"
          title="평가 기록이 없습니다"
          description={
            contractId
              ? '이 계약에 대한 공정가치 평가를 아직 실행하지 않았습니다.'
              : '공정가치 평가를 실행하면 이력이 여기에 표시됩니다.'
          }
          hint={
            contractId
              ? `위 폼에서 계약번호 "${contractId}"를 선택하고 시장 데이터를 입력한 후 평가를 실행하세요.`
              : '위 폼에서 계약 정보·시장 데이터를 입력하고 "공정가치 평가 실행"을 클릭하세요.'
          }
        />
      </Card>
    )
  }

  return (
    <Card
      title={cardTitle}
      description={`${cardDescription} — ${totalElements}건`}
      actions={
        /* 백그라운드 재조회 중일 때만 표시 — isLoading(첫 로딩)은 제외 */
        isFetching && !isLoading ? (
          <span className="flex items-center gap-1.5 text-xs text-blue-600 font-medium">
            <svg
              className="w-3 h-3 animate-spin"
              viewBox="0 0 24 24"
              fill="none"
              aria-hidden="true"
            >
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
            </svg>
            갱신 중
          </span>
        ) : null
      }
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
                  계약번호
                </th>
                <th scope="col" className="text-right px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  공정가치
                </th>
                <th scope="col" className="text-right px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  공정가치 변동액
                </th>
                <th scope="col" className="text-right px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  잔존일수
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  수준
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  작업
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((item) => {
                const isSelected = selectedItem?.valuationId === item.valuationId
                const isDeleting =
                  deleteMutation.isPending && deleteMutation.variables === item.valuationId

                return (
                  <tr
                    key={item.valuationId}
                    className={clsx(
                      'transition-colors cursor-pointer',
                      isSelected ? 'bg-blue-50' : 'hover:bg-slate-50',
                    )}
                    onClick={() => setSelectedItem(isSelected ? null : item)}
                    aria-selected={isSelected}
                  >
                    <td className="px-4 py-3 text-slate-700 font-financial tabular-nums">
                      {formatDate(item.valuationDate)}
                    </td>
                    <td className="px-4 py-3 text-slate-700 font-medium">
                      {item.contractId}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <MoneyDisplay amount={item.fairValue} size="sm" />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <MoneyDisplay amount={item.fairValueChange} size="sm" showSign />
                    </td>
                    <td className="px-4 py-3 text-right font-financial tabular-nums text-slate-700">
                      {item.remainingDays}
                      <span className="text-xs text-slate-400 ml-0.5">일</span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={clsx(
                        'inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold',
                        LEVEL_BADGE[item.fairValueLevel] ?? 'bg-slate-100 text-slate-600',
                      )}>
                        {item.fairValueLevel.replace('_', ' ')}
                      </span>
                    </td>
                    <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center gap-1.5">
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => setSelectedItem(isSelected ? null : item)}
                          aria-pressed={isSelected}
                        >
                          {isSelected ? '닫기' : '상세'}
                        </Button>
                        <Button
                          variant="danger"
                          size="sm"
                          onClick={() => handleDelete(item.valuationId)}
                          disabled={isDeleting}
                          loading={isDeleting}
                        >
                          삭제
                        </Button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        {/* ── 상세 패널 ──────────────────────────────────────────────── */}
        {selectedItem && (
          <ValuationDetailPanel
            item={selectedItem}
            onEdit={onEdit}
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

interface ValuationDetailPanelProps {
  item: FxForwardValuationResponse
  onEdit?: (item: FxForwardValuationResponse) => void
  onClose: () => void
}

/**
 * 평가 이력 상세 패널.
 * 선택된 평가 레코드의 모든 데이터를 표시하고 재평가 진입점을 제공합니다.
 */
function ValuationDetailPanel({ item, onEdit, onClose }: ValuationDetailPanelProps) {
  return (
    <div
      role="region"
      aria-label="평가 이력 상세"
      className="rounded-lg border border-blue-200 bg-blue-50 p-5"
    >
      {/* 패널 헤더 */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h4 className="text-sm font-semibold text-blue-900">
            평가 상세 — {item.contractId}
          </h4>
          <p className="text-xs text-blue-600 mt-0.5">
            평가기준일: {formatDate(item.valuationDate)} · ID: {item.valuationId}
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

      {/* 상세 데이터 그리드 */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 mb-4">
        <DetailField label="공정가치">
          <MoneyDisplay amount={item.fairValue} size="md" />
        </DetailField>

        <DetailField label="공정가치 변동액">
          <MoneyDisplay amount={item.fairValueChange} size="md" showSign />
        </DetailField>

        <DetailField label="공정가치 수준">
          <span className={clsx(
            'inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold',
            LEVEL_BADGE[item.fairValueLevel] ?? 'bg-slate-100 text-slate-600',
          )}>
            {item.fairValueLevel.replace('_', ' ')}
          </span>
        </DetailField>

        <DetailField label="현물환율 (S₀)">
          <RateDisplay value={item.spotRate} type="exchange" decimals={4} />
        </DetailField>

        <DetailField label="원화이자율">
          <span className="font-financial text-sm text-slate-800">{formatPercent(item.krwInterestRate)}</span>
        </DetailField>

        <DetailField label="달러이자율">
          <span className="font-financial text-sm text-slate-800">{formatPercent(item.usdInterestRate)}</span>
        </DetailField>

        <DetailField label="현재 선물환율">
          <RateDisplay value={item.currentForwardRate} type="exchange" decimals={4} />
        </DetailField>

        <DetailField label="잔존일수">
          <span className="font-financial text-sm text-slate-800 tabular-nums">
            {item.remainingDays}일
          </span>
        </DetailField>

        <DetailField label="전기 공정가치">
          <MoneyDisplay amount={item.previousFairValue} size="sm" />
        </DetailField>
      </div>

      {/* 재평가 버튼 */}
      {onEdit && (
        <div className="flex justify-end pt-3 border-t border-blue-200">
          <Button
            variant="primary"
            size="sm"
            type="button"
            onClick={() => onEdit(item)}
          >
            이 값으로 재평가
          </Button>
        </div>
      )}
    </div>
  )
}

// ─── 상세 필드 헬퍼 ─────────────────────────────────────

function DetailField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-slate-500 mb-0.5">{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}
