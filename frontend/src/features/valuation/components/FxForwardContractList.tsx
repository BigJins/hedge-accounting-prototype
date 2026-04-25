import { useState } from 'react'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorAlert } from '@/components/ui/ErrorAlert'
import { useFxForwardContractList, useDeleteFxForwardContract } from '../api/useFxForwardValuation'
import { formatDate, formatUsd, formatRate } from '@/utils/formatters'
import type { FxForwardContractResponse } from '@/types/valuation'
import clsx from 'clsx'

// ─── Props ────────────────────────────────────────────────────────────────────

interface FxForwardContractListProps {
  onEdit: (contract: FxForwardContractResponse) => void
  selectedContractId?: string | null
  onSelectContract: (contractId: string | null) => void
}

// ─── 상태 배지 ────────────────────────────────────────────────────────────────

const STATUS_BADGE: Record<FxForwardContractResponse['status'], { label: string; className: string }> = {
  ACTIVE:     { label: '활성',   className: 'bg-emerald-100 text-emerald-800' },
  MATURED:    { label: '만기',   className: 'bg-slate-100 text-slate-600' },
  TERMINATED: { label: '종료',   className: 'bg-red-100 text-red-700' },
}

const PAGE_SIZE = 10

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

/**
 * 통화선도 계약 목록 테이블.
 * 전체 계약을 페이징으로 표시하고 편집/삭제 작업 및 계약 선택을 제공합니다.
 */
export function FxForwardContractList({
  onEdit,
  selectedContractId,
  onSelectContract,
}: FxForwardContractListProps) {
  const [page, setPage] = useState(0)

  const { data, isLoading, isError, error } = useFxForwardContractList(page, PAGE_SIZE)
  const deleteMutation = useDeleteFxForwardContract()

  // null guard — 데이터 없을 때 안전한 기본값
  const contracts = data?.content ?? []
  const totalElements = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 0
  const pageNumber = data?.number ?? page
  const pageSize = data?.size ?? PAGE_SIZE

  const handleDelete = (contractId: string) => {
    deleteMutation.mutate(contractId, {
      onError: (err) => {
        console.error(`계약 삭제 실패 [${contractId}]:`, err)
      },
    })
  }

  const handleRowClick = (contractId: string) => {
    // 이미 선택된 계약 재클릭 시 토글(선택 해제)
    onSelectContract(selectedContractId === contractId ? null : contractId)
  }

  // ── 로딩 스켈레톤 ─────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <Card title="계약 목록" description="등록된 통화선도 계약">
        <div
          className="overflow-hidden rounded-lg border border-slate-200"
          aria-busy="true"
          aria-label="계약 목록 불러오는 중"
        >
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                {['계약번호', '명목원금(USD)', '계약 선물환율', '계약일', '만기일', '상태', '작업'].map((h) => (
                  <th
                    key={h}
                    scope="col"
                    className="px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider text-left"
                  >
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
                      <div
                        className="h-3.5 bg-slate-200 rounded animate-pulse"
                        style={{ width: j === 6 ? '5rem' : j === 0 ? '70%' : '60%' }}
                      />
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
      <Card title="계약 목록" description="등록된 통화선도 계약">
        <ErrorAlert error={error} onRetry={() => window.location.reload()} />
      </Card>
    )
  }

  // ── 빈 상태 ───────────────────────────────────────────────────────────────
  if (!data || data.empty) {
    return (
      <Card title="계약 목록" description="등록된 통화선도 계약">
        <EmptyState
          icon="chart"
          title="등록된 통화선도 계약이 없습니다"
          description="공정가치 평가를 실행하면 해당 계약이 이 목록에 자동으로 등록됩니다."
          hint="위 '통화선도 공정가치 평가' 폼에서 계약 정보와 시장 데이터를 입력하고 '공정가치 평가 실행'을 클릭하세요."
        />
      </Card>
    )
  }

  // ── 테이블 + 페이지네이션 ─────────────────────────────────────────────────
  return (
    <Card
      title="계약 목록"
      description={`등록된 통화선도 계약 — ${totalElements}건`}
    >
      <div className="space-y-0">
        <div className="overflow-hidden rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  계약번호
                </th>
                <th scope="col" className="text-right px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  명목원금(USD)
                </th>
                <th scope="col" className="text-right px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  계약 선물환율
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  계약일
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  만기일
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  상태
                </th>
                <th scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  작업
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {contracts.map((contract) => {
                const badge = STATUS_BADGE[contract.status] ?? STATUS_BADGE.TERMINATED
                const isSelected = selectedContractId === contract.contractId
                const isDeleting =
                  deleteMutation.isPending &&
                  deleteMutation.variables === contract.contractId

                return (
                  <tr
                    key={contract.contractId}
                    className={clsx(
                      'transition-colors cursor-pointer',
                      isSelected
                        ? 'bg-blue-50 border-l-2 border-l-blue-500'
                        : 'hover:bg-slate-50',
                    )}
                    onClick={() => handleRowClick(contract.contractId)}
                    aria-selected={isSelected}
                  >
                    <td className="px-4 py-3 text-sm text-slate-900 font-medium font-financial">
                      {contract.contractId}
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-900 text-right tabular-nums font-financial">
                      {formatUsd(contract.notionalAmountUsd)}
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-900 text-right tabular-nums font-financial">
                      {formatRate(contract.contractForwardRate)}
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-700 font-financial tabular-nums">
                      {formatDate(contract.contractDate)}
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-700 font-financial tabular-nums">
                      {formatDate(contract.maturityDate)}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold ${badge.className}`}
                      >
                        {badge.label}
                      </span>
                    </td>
                    <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center gap-2">
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => onEdit(contract)}
                          disabled={isDeleting}
                        >
                          편집
                        </Button>
                        <Button
                          variant="danger"
                          size="sm"
                          onClick={() => handleDelete(contract.contractId)}
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

        <Pagination
          page={pageNumber}
          totalPages={totalPages}
          totalElements={totalElements}
          size={pageSize}
          onPageChange={(newPage) => {
            setPage(newPage)
            onSelectContract(null)
          }}
        />
      </div>
    </Card>
  )
}