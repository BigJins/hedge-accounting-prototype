import { useEffect, useState } from 'react'
import clsx from 'clsx'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorAlert } from '@/components/ui/ErrorAlert'
import { useJournalEntryListAll } from '../api/useJournalEntry'
import { formatDate, formatKrw } from '@/utils/formatters'
import type { JournalEntryResponse, JournalEntryType } from '@/types/journal'

const PAGE_SIZE = 10
const API_BASE = '/api/v1/journal-entries'
const FILTER_OPTIONS = [
  { value: 'ALL',   label: '전체' },
  { value: 'YEAR',  label: '연도' },
  { value: 'MONTH', label: '월' },
  { value: 'DAY',   label: '일' },
] as const

type FilterScope = (typeof FILTER_OPTIONS)[number]['value']

const ENTRY_TYPE_BADGE: Record<JournalEntryType, { label: string; className: string }> = {
  FAIR_VALUE_HEDGE_INSTRUMENT: {
    label: 'FVH-수단',
    className: 'bg-blue-100 text-blue-800',
  },
  FAIR_VALUE_HEDGE_ITEM: {
    label: 'FVH-항목',
    className: 'bg-blue-100 text-blue-800',
  },
  IRS_FVH_AMORTIZATION: {
    label: 'IRS-FVH 장부조정상각',
    className: 'bg-blue-200 text-blue-900 ring-1 ring-blue-300',
  },
  CASH_FLOW_HEDGE_EFFECTIVE: {
    label: '현금흐름헤지 유효부분',
    className: 'bg-emerald-100 text-emerald-800',
  },
  CASH_FLOW_HEDGE_INEFFECTIVE: {
    label: '현금흐름헤지 비유효부분',
    className: 'bg-amber-100 text-amber-800',
  },
  OCI_RECLASSIFICATION: {
    label: 'OCI 재분류',
    className: 'bg-purple-100 text-purple-800',
  },
  HEDGE_DISCONTINUATION: {
    label: '헤지 중단',
    className: 'bg-slate-100 text-slate-600',
  },
  REVERSING_ENTRY: {
    label: '역분개',
    className: 'bg-red-100 text-red-700',
  },
}

interface JournalEntryHistoryProps {
  hedgeRelationshipId?: string | null
}

function getAccountTone(accountCode: string) {
  if (accountCode.includes('OCI')) {
    return {
      label: 'OCI',
      className: 'bg-blue-50 text-blue-700 border border-blue-200',
    }
  }

  if (
    accountCode.includes('_PL')
    || accountCode.includes('RECLASSIFY')
    || accountCode.includes('INEFF_')
  ) {
    return {
      label: 'P/L',
      className: 'bg-red-50 text-red-700 border border-red-200',
    }
  }

  return {
    label: 'BS',
    className: 'bg-slate-50 text-slate-600 border border-slate-200',
  }
}

function downloadFile(url: string, filename: string) {
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.rel = 'noopener noreferrer'
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
}

function getDateParts(entryDate: string) {
  const [year = '', month = '', day = ''] = entryDate.split('-')

  return {
    year,
    month,
    day,
    monthKey: year && month ? `${year}-${month}` : '',
  }
}

function getFilterOptions(items: JournalEntryResponse[], filterScope: FilterScope) {
  if (filterScope === 'ALL') {
    return []
  }

  const uniqueOptions = new Map<string, string>()

  items.forEach((item) => {
    const { year, monthKey } = getDateParts(item.entryDate)

    if (filterScope === 'YEAR' && year) {
      uniqueOptions.set(year, year)
    }

    if (filterScope === 'MONTH' && monthKey) {
      uniqueOptions.set(monthKey, monthKey.replace('-', '.'))
    }

    if (filterScope === 'DAY') {
      uniqueOptions.set(item.entryDate, formatDate(item.entryDate))
    }
  })

  return [...uniqueOptions.entries()]
    .sort(([left], [right]) => right.localeCompare(left))
    .map(([value, label]) => ({ value, label }))
}

function matchesFilter(item: JournalEntryResponse, filterScope: FilterScope, selectedValue: string) {
  if (filterScope === 'ALL' || !selectedValue) {
    return true
  }

  const { year, monthKey } = getDateParts(item.entryDate)

  if (filterScope === 'YEAR') {
    return year === selectedValue
  }

  if (filterScope === 'MONTH') {
    return monthKey === selectedValue
  }

  if (filterScope === 'DAY') {
    return item.entryDate === selectedValue
  }

  return true
}

function formatActiveFilter(filterScope: FilterScope, selectedValue: string) {
  if (filterScope === 'ALL') {
    return '전체 기간'
  }

  if (!selectedValue) {
    return '날짜 선택'
  }

  if (filterScope === 'YEAR') {
    return selectedValue
  }

  if (filterScope === 'MONTH') {
    return selectedValue.replace('-', '.')
  }

  return formatDate(selectedValue)
}

// ── FilterPanel ────────────────────────────────────────────────────────────────

interface FilterPanelProps {
  filterScope: FilterScope
  selectedFilterValue: string
  filterOptions: { value: string; label: string }[]
  totalElements: number
  onFilterScopeChange: (scope: FilterScope) => void
  onFilterValueChange: (value: string) => void
}

function FilterPanel({
  filterScope,
  selectedFilterValue,
  filterOptions,
  totalElements,
  onFilterScopeChange,
  onFilterValueChange,
}: FilterPanelProps) {
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-2.5">
      {/* 기간 범위 버튼 */}
      <div className="flex items-center gap-1">
        {FILTER_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => onFilterScopeChange(opt.value)}
            className={clsx(
              'rounded px-2.5 py-1 text-xs font-semibold transition-colors',
              filterScope === opt.value
                ? 'bg-blue-700 text-white'
                : 'text-slate-600 hover:bg-slate-200',
            )}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {/* 값 선택 드롭다운 — ALL이면 숨김 */}
      {filterScope !== 'ALL' && filterOptions.length > 0 && (
        <select
          value={selectedFilterValue}
          onChange={(e) => onFilterValueChange(e.target.value)}
          className="rounded border border-slate-300 bg-white px-2 py-1 text-xs text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
          aria-label="기간 값 선택"
        >
          {filterOptions.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      )}

      {/* 현재 필터 상태 요약 */}
      <span className="text-xs text-slate-500">
        {formatActiveFilter(filterScope, selectedFilterValue)}
      </span>

      {/* 결과 건수 */}
      <span className="ml-auto text-xs font-semibold text-slate-600">
        {totalElements.toLocaleString('ko-KR')}건
      </span>
    </div>
  )
}

// ── Table headers (Korean) ─────────────────────────────────────────────────────

const TABLE_HEADERS = [
  { label: '분개일',     align: 'left'  },
  { label: '분개유형',   align: 'left'  },
  { label: '차변 계정',  align: 'left'  },
  { label: '차변 금액',  align: 'right' },
  { label: '대변 계정',  align: 'left'  },
  { label: '대변 금액',  align: 'right' },
  { label: 'K-IFRS 근거', align: 'left' },
  { label: '생성일',     align: 'left'  },
] as const

// ── Main component ─────────────────────────────────────────────────────────────

export function JournalEntryHistory({ hedgeRelationshipId }: JournalEntryHistoryProps) {
  const [page, setPage] = useState(0)
  const [filterScope, setFilterScope] = useState<FilterScope>('ALL')
  const [selectedFilterValue, setSelectedFilterValue] = useState('')

  useEffect(() => {
    setPage(0)
    setFilterScope('ALL')
    setSelectedFilterValue('')
  }, [hedgeRelationshipId])

  const { data, isLoading, isError, error } = useJournalEntryListAll(hedgeRelationshipId)
  const items = data ?? []
  const filterOptions = getFilterOptions(items, filterScope)

  useEffect(() => {
    if (filterScope === 'ALL') {
      if (selectedFilterValue) {
        setSelectedFilterValue('')
      }
      return
    }

    if (!filterOptions.some((option) => option.value === selectedFilterValue)) {
      setSelectedFilterValue(filterOptions[0]?.value ?? '')
    }
  }, [filterOptions, filterScope, selectedFilterValue])

  const filteredItems = items.filter((item) =>
    matchesFilter(item, filterScope, selectedFilterValue),
  )
  const totalElements = filteredItems.length
  const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE))
  const safePage = Math.min(page, totalPages - 1)
  const pagedItems = filteredItems.slice(
    safePage * PAGE_SIZE,
    safePage * PAGE_SIZE + PAGE_SIZE,
  )

  useEffect(() => {
    if (page !== safePage) {
      setPage(safePage)
    }
  }, [page, safePage])

  const handlePageChange = (newPage: number) => {
    setPage(newPage)
  }

  const handleFilterScopeChange = (scope: FilterScope) => {
    setFilterScope(scope)
    setPage(0)
    if (scope === 'ALL') {
      setSelectedFilterValue('')
    }
  }

  const handleFilterValueChange = (value: string) => {
    setSelectedFilterValue(value)
    setPage(0)
  }

  const handleExcelDownload = () => {
    if (!hedgeRelationshipId) return
    const url = `${API_BASE}/export/excel?hedgeRelationshipId=${encodeURIComponent(hedgeRelationshipId)}`
    downloadFile(url, `journal_${hedgeRelationshipId}.xlsx`)
  }

  const handlePdfDownload = () => {
    if (!hedgeRelationshipId) return
    const url = `${API_BASE}/export/pdf?hedgeRelationshipId=${encodeURIComponent(hedgeRelationshipId)}`
    downloadFile(url, `journal_${hedgeRelationshipId}.pdf`)
  }

  const downloadButtons = (
    <div className="flex items-center gap-2">
      <Button
        variant="secondary"
        size="sm"
        type="button"
        onClick={handleExcelDownload}
        disabled={!hedgeRelationshipId}
        aria-label="Excel 다운로드"
        title={hedgeRelationshipId ? 'Excel 다운로드' : '헤지관계 ID가 필요합니다.'}
      >
        <svg className="mr-1 h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path
            fillRule="evenodd"
            d="M3 17a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm3.293-7.707a1 1 0 011.414 0L9 10.586V3a1 1 0 112 0v7.586l1.293-1.293a1 1 0 111.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z"
            clipRule="evenodd"
          />
        </svg>
        Excel
      </Button>

      <Button
        variant="secondary"
        size="sm"
        type="button"
        onClick={handlePdfDownload}
        disabled={!hedgeRelationshipId}
        aria-label="PDF 다운로드"
        title={hedgeRelationshipId ? 'PDF 다운로드' : '헤지관계 ID가 필요합니다.'}
      >
        <svg className="mr-1 h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path
            fillRule="evenodd"
            d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z"
            clipRule="evenodd"
          />
        </svg>
        PDF
      </Button>
    </div>
  )

  // ── 빈 상태 (분개 없음, 최초 접속) ──────────────────────────────────────────
  if (!hedgeRelationshipId && items.length === 0 && !isLoading) {
    return (
      <Card
        title="분개 이력"
        description="자동 생성된 분개 확인"
        actions={downloadButtons}
      >
        <EmptyState
          icon="clipboard"
          title="아직 분개가 없습니다"
          description="유효성 테스트 PASS 후 분개가 자동으로 생성됩니다. FAIL 결과는 K-IFRS 1109호 6.5.6에 따라 분개를 생성하지 않으며, 이는 정상 동작입니다."
          hint="유효성 테스트를 실행하고 PASS 여부를 확인하세요."
        />
      </Card>
    )
  }

  // ── 로딩 스켈레톤 ───────────────────────────────────────────────────────────
  if (isLoading) {
    const description = hedgeRelationshipId
      ? `${hedgeRelationshipId} 분개 이력`
      : '분개 이력'

    return (
      <Card title="분개 이력" description={description} actions={downloadButtons}>
        <div
          className="overflow-hidden rounded-lg border border-slate-200"
          aria-busy="true"
          aria-label="분개 이력 불러오는 중"
        >
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                {TABLE_HEADERS.map((h) => (
                  <th
                    key={h.label}
                    scope="col"
                    className={clsx(
                      'px-4 py-2.5 text-xs font-semibold uppercase tracking-wider text-slate-500',
                      h.align === 'right' ? 'text-right' : 'text-left',
                    )}
                  >
                    {h.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {Array.from({ length: 3 }).map((_, rowIndex) => (
                <tr key={rowIndex}>
                  {Array.from({ length: 8 }).map((__, cellIndex) => (
                    <td key={cellIndex} className="px-4 py-3">
                      <div
                        className="h-3.5 animate-pulse rounded bg-slate-200"
                        style={{ width: cellIndex === 7 ? '7rem' : '60%' }}
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

  // ── 오류 ────────────────────────────────────────────────────────────────────
  if (isError) {
    const description = hedgeRelationshipId
      ? `${hedgeRelationshipId} 분개 이력`
      : '분개 이력'

    return (
      <Card title="분개 이력" description={description} actions={downloadButtons}>
        <ErrorAlert error={error} onRetry={() => window.location.reload()} />
      </Card>
    )
  }

  // ── 분개 없음 ────────────────────────────────────────────────────────────────
  if (items.length === 0) {
    const description = hedgeRelationshipId
      ? `${hedgeRelationshipId} 분개 이력`
      : '분개 이력'

    return (
      <Card title="분개 이력" description={description} actions={downloadButtons}>
        <EmptyState
          icon="clipboard"
          title="아직 분개가 없습니다"
          description="유효성 테스트 PASS 후 분개가 자동으로 생성됩니다. 유효성 테스트 결과가 FAIL이면 K-IFRS 1109호 6.5.6에 따라 분개를 생성하지 않으며, 이는 정상 동작입니다."
          hint="유효성 테스트를 먼저 실행하고 결과를 확인하세요."
        />
      </Card>
    )
  }

  // ── 필터 결과 없음 ───────────────────────────────────────────────────────────
  if (filteredItems.length === 0) {
    const description = hedgeRelationshipId
      ? `${hedgeRelationshipId} 분개 이력`
      : '분개 이력'

    return (
      <Card title="분개 이력" description={description} actions={downloadButtons}>
        <div className="space-y-4">
          <FilterPanel
            filterScope={filterScope}
            selectedFilterValue={selectedFilterValue}
            filterOptions={filterOptions}
            totalElements={0}
            onFilterScopeChange={handleFilterScopeChange}
            onFilterValueChange={handleFilterValueChange}
          />
          <EmptyState
            icon="search"
            title="해당 기간에 분개가 없습니다"
            description="기간 필터는 클라이언트에서 처리되며 서버 API를 변경하지 않습니다."
            hint="전체, 연도, 월, 일 필터를 바꿔 다른 분개 내역을 확인하세요."
          />
        </div>
      </Card>
    )
  }

  // ── 정상 렌더 ────────────────────────────────────────────────────────────────
  const description = hedgeRelationshipId
    ? `${hedgeRelationshipId} · ${totalElements.toLocaleString('ko-KR')}건`
    : `전체 · ${totalElements.toLocaleString('ko-KR')}건`

  return (
    <Card title="분개 이력" description={description} actions={downloadButtons}>
      <div className="space-y-4">
        <FilterPanel
          filterScope={filterScope}
          selectedFilterValue={selectedFilterValue}
          filterOptions={filterOptions}
          totalElements={totalElements}
          onFilterScopeChange={handleFilterScopeChange}
          onFilterValueChange={handleFilterValueChange}
        />

        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="min-w-[1180px] w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                {TABLE_HEADERS.map((h) => (
                  <th
                    key={h.label}
                    scope="col"
                    className={clsx(
                      'px-4 py-2.5 text-xs font-semibold uppercase tracking-wider text-slate-500',
                      h.align === 'right' ? 'text-right' : 'text-left',
                    )}
                  >
                    {h.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {pagedItems.map((item) => {
                const badge = ENTRY_TYPE_BADGE[item.entryType] ?? {
                  label: item.entryType,
                  className: 'bg-slate-100 text-slate-600',
                }
                const debitTone  = getAccountTone(item.debitAccount)
                const creditTone = getAccountTone(item.creditAccount)
                const createdAt  = item.createdAt ? formatDate(item.createdAt.slice(0, 10)) : '-'

                return (
                  <tr key={item.journalEntryId} className="transition-colors hover:bg-slate-50">
                    {/* 분개일 */}
                    <td className="px-4 py-3 text-xs font-financial tabular-nums text-slate-700">
                      {formatDate(item.entryDate)}
                    </td>

                    {/* 분개유형 */}
                    <td className="px-4 py-3">
                      <span
                        className={clsx(
                          'inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold whitespace-nowrap',
                          badge.className,
                        )}
                      >
                        {badge.label}
                      </span>
                    </td>

                    {/* 차변 계정 */}
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="flex flex-nowrap items-center gap-2">
                        <span className="text-xs font-medium text-slate-700 whitespace-nowrap">{item.debitAccountName}</span>
                        <span
                          className={clsx(
                            'inline-flex items-center rounded px-1.5 py-0.5 text-[11px] font-semibold whitespace-nowrap',
                            debitTone.className,
                          )}
                        >
                          {debitTone.label}
                        </span>
                      </div>
                    </td>

                    {/* 차변 금액 */}
                    <td className="px-4 py-3 text-right font-financial tabular-nums">
                      <span
                        className={clsx(
                          'text-xs',
                          item.amount < 0 ? 'text-red-600' : 'text-slate-700',
                        )}
                      >
                        {formatKrw(Math.abs(item.amount))}
                      </span>
                    </td>

                    {/* 대변 계정 */}
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="flex flex-nowrap items-center gap-2">
                        <span className="text-xs font-medium text-slate-700 whitespace-nowrap">{item.creditAccountName}</span>
                        <span
                          className={clsx(
                            'inline-flex items-center rounded px-1.5 py-0.5 text-[11px] font-semibold whitespace-nowrap',
                            creditTone.className,
                          )}
                        >
                          {creditTone.label}
                        </span>
                      </div>
                    </td>

                    {/* 대변 금액 */}
                    <td className="px-4 py-3 text-right font-financial tabular-nums">
                      <span className="text-xs text-slate-700">
                        {formatKrw(Math.abs(item.amount))}
                      </span>
                    </td>

                    {/* K-IFRS 근거 */}
                    <td className="px-4 py-3">
                      <span className="whitespace-nowrap text-xs font-mono text-blue-600">
                        {item.ifrsReference}
                      </span>
                    </td>

                    {/* 생성일 */}
                    <td className="px-4 py-3 whitespace-nowrap text-xs font-financial tabular-nums text-slate-400">
                      {createdAt}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        {/* ── 페이지네이션 ───────────────────────────────────────────────── */}
        <Pagination
          page={safePage}
          totalPages={totalPages}
          totalElements={totalElements}
          size={PAGE_SIZE}
          onPageChange={handlePageChange}
        />
      </div>
    </Card>
  )
}
