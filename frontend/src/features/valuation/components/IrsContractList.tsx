import { useState } from 'react'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorAlert } from '@/components/ui/ErrorAlert'
import { useIrsContractList, useDeleteIrsContract } from '../api/useIrsValuation'
import { formatDate } from '@/utils/formatters'
import type { IrsContractResponse } from '@/types/valuation'
import clsx from 'clsx'

// ─── Props ────────────────────────────────────────────────────────────────────

interface IrsContractListProps {
  onSelectContract: (contractId: string | null) => void
  selectedContractId?: string | null
}

// ─── 상태 배지 ────────────────────────────────────────────────────────────────

const STATUS_BADGE: Record<IrsContractResponse['status'], { label: string; className: string }> = {
  ACTIVE:     { label: '활성',  className: 'bg-emerald-100 text-emerald-800' },
  MATURED:    { label: '만기',  className: 'bg-slate-100 text-slate-600' },
  TERMINATED: { label: '종료',  className: 'bg-red-100 text-red-700' },
}

const PAGE_SIZE = 10

// ─── 컴포넌트 ─────────────────────────────────────────────────────────────────

/**
 * IRS 계약 목록 테이블.
 * 행 클릭으로 평가 이력 필터링, 삭제 작업을 제공합니다.
 *
 * @see K-IFRS 1109호 6.2.1 (위험회피수단 — IRS 계약 목록)
 */
export function IrsContractList({ onSelectContract, selectedContractId }: IrsContractListProps) {
  const [page, setPage] = useState(0)

  const { data, isLoading, isError, error } = useIrsContractList(page, PAGE_SIZE)
  const deleteMutation = useDeleteIrsContract()

  const contracts      = data?.content ?? []
  const totalPages     = data?.totalPages ?? 0
  const totalElements  = data?.totalElements ?? 0
  const pageNumber     = data?.number ?? page

  const handleRowClick = (contractId: string) => {
    onSelectContract(selectedContractId === contractId ? null : contractId)
  }

  const handleDelete = (contractId: string) => {
    if (!window.confirm(`IRS 계약 ${contractId}를 삭제하시겠습니까?`)) return
    deleteMutation.mutate(contractId)
  }

  // ── 로딩 스켈레톤 ─────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <Card title="IRS 계약 목록" description="등록된 이자율스왑 계약">
        <div className="overflow-hidden rounded-lg border border-slate-200" aria-busy="true">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                {['계약번호', '명목금액(KRW)', '고정금리', '만기일', '방향', '상태', '작업'].map((h) => (
                  <th key={h} className="px-4 py-2.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {Array.from({ length: 3 }).map((_, i) => (
                <tr key={i} className="border-b border-slate-100">
                  {Array.from({ length: 7 }).map((__, j) => (
                    <td key={j} className="px-4 py-3">
                      <div className="h-3 bg-slate-200 rounded animate-pulse" />
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

  if (isError) {
    return (
      <Card title="IRS 계약 목록">
        <ErrorAlert error={error ?? new Error('계약 목록을 불러오지 못했습니다.')} />
      </Card>
    )
  }

  return (
    <Card
      title="IRS 계약 목록"
      description="등록된 이자율스왑 계약 — 행 클릭으로 평가 이력 필터링"
      actions={
        <span className="text-xs text-slate-500">
          총 {data?.totalElements ?? 0}건
        </span>
      }
    >
      {contracts.length === 0 ? (
        <EmptyState
          title="등록된 IRS 계약이 없습니다."
          description="위 폼으로 이자율스왑 계약을 등록하세요."
        />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-sm min-w-[700px]">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  {['계약번호', '명목금액(KRW)', '고정금리', '만기일', '방향', '상태', '작업'].map((h) => (
                    <th
                      key={h}
                      scope="col"
                      className="px-4 py-2.5 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {contracts.map((c) => {
                  const isSelected = c.contractId === selectedContractId
                  const badge = STATUS_BADGE[c.status]

                  return (
                    <tr
                      key={c.contractId}
                      onClick={() => handleRowClick(c.contractId)}
                      className={clsx(
                        'cursor-pointer transition-colors',
                        isSelected
                          ? 'bg-blue-50 hover:bg-blue-100'
                          : 'hover:bg-slate-50',
                      )}
                      aria-selected={isSelected}
                    >
                      <td className="px-4 py-3">
                        <span className={clsx(
                          'font-medium font-financial',
                          isSelected ? 'text-blue-700' : 'text-slate-800',
                        )}>
                          {c.contractId}
                        </span>
                        {c.hedgeRelationshipId && (
                          <span className="ml-2 text-[10px] text-blue-500 bg-blue-50 px-1.5 py-0.5 rounded">
                            헤지연계
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right font-financial tabular-nums text-slate-700">
                        {c.notionalAmount.toLocaleString('ko-KR')}
                      </td>
                      <td className="px-4 py-3 font-financial tabular-nums text-slate-700">
                        {(c.fixedRate * 100).toFixed(3)}%
                      </td>
                      <td className="px-4 py-3 text-slate-600">{formatDate(c.maturityDate)}</td>
                      <td className="px-4 py-3">
                        {c.payFixedReceiveFloating ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-purple-100 text-purple-800">
                            CFH — 고정지급
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800">
                            FVH — 고정수취
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <span className={clsx(
                          'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium',
                          badge.className,
                        )}>
                          {badge.label}
                        </span>
                      </td>
                      <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDelete(c.contractId)}
                          loading={deleteMutation.isPending && deleteMutation.variables === c.contractId}
                          className="text-red-500 hover:text-red-700 hover:bg-red-50"
                        >
                          삭제
                        </Button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="mt-4">
              <Pagination
                page={pageNumber}
                totalPages={totalPages}
                totalElements={totalElements}
                size={PAGE_SIZE}
                onPageChange={setPage}
              />
            </div>
          )}
        </>
      )}
    </Card>
  )
}
