import { useState, useRef, useEffect, useId, useMemo } from 'react'
import clsx from 'clsx'
import { useHedgeRelationshipList } from '@/features/hedge/api/hedgeApi'
import { formatDate } from '@/utils/formatters'
import type { HedgeRelationshipSummary } from '@/types/hedge'

// ─── 배지 매핑 ────────────────────────────────────────────────────────────────

const HEDGE_TYPE_BADGE: Record<
  HedgeRelationshipSummary['hedgeType'],
  { label: string; className: string }
> = {
  FAIR_VALUE: { label: 'FVH', className: 'bg-blue-100 text-blue-800' },
  CASH_FLOW:  { label: 'CFH', className: 'bg-emerald-100 text-emerald-800' },
}

const STATUS_BADGE: Record<
  HedgeRelationshipSummary['status'],
  { label: string; className: string }
> = {
  DESIGNATED:   { label: '지정',     className: 'bg-emerald-50 text-emerald-700' },
  REBALANCED:   { label: '재조정',   className: 'bg-amber-50 text-amber-700' },
  DISCONTINUED: { label: '중단',     className: 'bg-slate-100 text-slate-500' },
  MATURED:      { label: '만기',     className: 'bg-slate-100 text-slate-500' },
}

const ELIGIBILITY_BADGE: Record<
  HedgeRelationshipSummary['eligibilityStatus'],
  { label: string; className: string }
> = {
  ELIGIBLE:   { label: 'ELIGIBLE',   className: 'bg-emerald-100 text-emerald-800' },
  INELIGIBLE: { label: 'INELIGIBLE', className: 'bg-red-100 text-red-700' },
  PENDING:    { label: 'PENDING',    className: 'bg-slate-100 text-slate-500' },
}

// ─── 통화·risk 요약 ──────────────────────────────────────────────────────────

/**
 * 관계 요약에서 "통화" 표시를 만들어낸다.
 *
 * `HedgeRelationshipSummary` 에는 currency 필드가 없으므로 다음 휴리스틱을 쓴다:
 *   - hedgedRisk === FOREIGN_CURRENCY + fxForwardContractId 존재 → 'USD/KRW'
 *     (본 PoC 의 FxForwardContract 는 USD/KRW 단일 기초로 고정)
 *   - 그 외 → hedgedRisk 한글 라벨
 *
 * 본개발에서 다통화를 지원하게 되면 이 헬퍼를 summary 의 currency 필드로 교체한다.
 */
function deriveCurrencyLabel(rel: HedgeRelationshipSummary): string {
  if (rel.hedgedRisk === 'FOREIGN_CURRENCY' && rel.fxForwardContractId) {
    return 'USD/KRW'
  }
  const riskLabel: Record<HedgeRelationshipSummary['hedgedRisk'], string> = {
    FOREIGN_CURRENCY: '외화',
    INTEREST_RATE:    '이자율',
    COMMODITY:        '상품',
    CREDIT:           '신용',
  }
  return `${riskLabel[rel.hedgedRisk]} 위험`
}

// ─── 정렬·분류 ───────────────────────────────────────────────────────────────

/**
 * 활성(DESIGNATED/REBALANCED) 관계를 먼저, 그 뒤로 종료된 관계를 나열하되
 * 각 그룹 안에서는 designationDate 내림차순(최근 생성이 위).
 */
function orderForPicker(list: HedgeRelationshipSummary[]): HedgeRelationshipSummary[] {
  const activeOrder: Record<HedgeRelationshipSummary['status'], number> = {
    DESIGNATED:   0,
    REBALANCED:   1,
    DISCONTINUED: 2,
    MATURED:      3,
  }
  return [...list].sort((a, b) => {
    const byStatus = activeOrder[a.status] - activeOrder[b.status]
    if (byStatus !== 0) return byStatus
    return b.designationDate.localeCompare(a.designationDate)
  })
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface HedgeRelationshipSelectorProps {
  /** 선택된 hedgeRelationshipId — 빈 문자열이면 미선택 */
  value: string
  /** 선택 시 호출 — hedgeRelationshipId 전달 (빈 문자열 = 해제) */
  onChange: (hedgeRelationshipId: string) => void
  /** 선택/해제 시 전체 관계 요약 객체 전달 (선택적 — 상위 자동 채움 등에 사용) */
  onRelationshipChange?: (relationship: HedgeRelationshipSummary | null) => void
  /** react-hook-form 유효성 오류 메시지 (테두리용, 텍스트는 Field 가 표시) */
  error?: string
  disabled?: boolean
  /** 트리거 버튼 id (Field label htmlFor 연동) */
  inputId?: string
}

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

/**
 * 헤지 관계(HedgeRelationship) 선택 UI — 유효성 테스트용.
 *
 * 기존엔 사용자가 hedgeRelationshipId(UUID) 를 헤지 지정 이력 화면에서 복사해
 * 유효성 테스트 화면 텍스트 입력에 붙여넣어야 했다. 이 컴포넌트는 그 동선을
 * "드롭다운에서 바로 선택" 으로 바꾼다.
 *
 * UX:
 * 1. 트리거 버튼 → 최근 지정일 순으로 정렬된 관계 목록 드롭다운 열림.
 * 2. ID / 계약ID / hedgeType / 통화로 실시간 필터링.
 * 3. 관계 선택 → 선택 상세 카드 표시 (ID · hedgeType · 통화 · 지정일 · 만기 · 계약 ID).
 * 4. [선택 해제 ✕] 버튼으로 재선택 가능.
 *
 * 재테스트 플로우에서 value 가 미리 주입되면 selectedCard 가 즉시 표시된다
 * (선택자와 외부 상태가 어긋나지 않도록 value → 요약 매칭으로 렌더).
 *
 * @see EffectivenessTestForm — 사용처
 * @see FxContractSelector — 동일 패턴(통화선도 계약 선택자)
 */
export function HedgeRelationshipSelector({
  value,
  onChange,
  onRelationshipChange,
  error,
  disabled = false,
  inputId,
}: HedgeRelationshipSelectorProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(-1)

  const containerRef = useRef<HTMLDivElement>(null)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const listboxId = useId()

  // 전체 조회 (size=100 — PoC 범위에서 충분). placeholderData 덕에 페이지 전환
  // 시에도 이전 데이터를 유지한다 (api 훅 내부 설정).
  const { data: page, isLoading, isError } = useHedgeRelationshipList({ size: 100 })

  const relationships = useMemo(
    () => orderForPicker(page?.content ?? []),
    [page],
  )

  // value 에 매칭되는 관계를 찾는다. 목록이 로딩 중이거나 value 가 목록 바깥이면
  // null 로 폴백하여 "목록에 없는 ID" 문구로 안내한다.
  const selectedRelationship = relationships.find((r) => r.hedgeRelationshipId === value) ?? null

  const filtered: HedgeRelationshipSummary[] = relationships.filter((r) => {
    const q = searchQuery.toLowerCase().trim()
    if (!q) return true
    const currencyLabel = deriveCurrencyLabel(r).toLowerCase()
    const typeLabel = HEDGE_TYPE_BADGE[r.hedgeType].label.toLowerCase()
    return (
      r.hedgeRelationshipId.toLowerCase().includes(q) ||
      (r.fxForwardContractId ?? '').toLowerCase().includes(q) ||
      r.hedgeType.toLowerCase().includes(q) ||
      typeLabel.includes(q) ||
      currencyLabel.includes(q) ||
      r.designationDate.includes(q)
    )
  })

  // ── 외부 클릭 시 닫기 ─────────────────────────────────────────────────────
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        closeDropdown()
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const openDropdown = () => {
    if (disabled) return
    setIsOpen(true)
    setSearchQuery('')
    setActiveIndex(-1)
    requestAnimationFrame(() => searchInputRef.current?.focus())
  }

  const closeDropdown = () => {
    setIsOpen(false)
    setSearchQuery('')
    setActiveIndex(-1)
  }

  const handleSelect = (relationship: HedgeRelationshipSummary) => {
    onChange(relationship.hedgeRelationshipId)
    onRelationshipChange?.(relationship)
    closeDropdown()
  }

  const handleClear = () => {
    onChange('')
    onRelationshipChange?.(null)
  }

  // ── 키보드 내비게이션 ─────────────────────────────────────────────────────
  const handleTriggerKeyDown = (e: React.KeyboardEvent<HTMLButtonElement>) => {
    if (disabled) return
    if (['Enter', ' ', 'ArrowDown'].includes(e.key)) {
      e.preventDefault()
      openDropdown()
    }
  }

  const handleSearchKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    switch (e.key) {
      case 'Escape':
        e.preventDefault()
        closeDropdown()
        break
      case 'ArrowDown':
        e.preventDefault()
        setActiveIndex((i) => Math.min(i + 1, filtered.length - 1))
        break
      case 'ArrowUp':
        e.preventDefault()
        setActiveIndex((i) => Math.max(i - 1, 0))
        break
      case 'Enter':
        e.preventDefault()
        if (activeIndex >= 0 && filtered[activeIndex]) {
          handleSelect(filtered[activeIndex])
        }
        break
    }
  }

  // ── 렌더 ──────────────────────────────────────────────────────────────────
  return (
    <div ref={containerRef} className="relative">

      {/* 트리거 버튼 */}
      <button
        id={inputId}
        type="button"
        onClick={openDropdown}
        onKeyDown={handleTriggerKeyDown}
        disabled={disabled || isLoading}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-controls={isOpen ? listboxId : undefined}
        aria-label="위험회피관계 선택"
        className={clsx(
          'w-full border rounded-md px-3 py-2 text-sm text-left flex items-center justify-between gap-2',
          'transition-colors duration-150',
          'focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-blue-600',
          'disabled:bg-slate-50 disabled:text-slate-400 disabled:cursor-not-allowed',
          error  ? 'border-red-400 bg-red-50' :
          isOpen ? 'border-blue-600 ring-2 ring-blue-600/20 bg-white' :
                   'border-slate-300 bg-white hover:border-slate-400',
        )}
      >
        <span className="flex items-center gap-2 min-w-0 flex-1">
          {isLoading ? (
            <span className="flex items-center gap-2 text-slate-400">
              <svg className="animate-spin w-3.5 h-3.5 flex-shrink-0" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
              </svg>
              관계 목록 불러오는 중...
            </span>
          ) : selectedRelationship ? (
            <>
              <svg className="w-4 h-4 text-emerald-600 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
              </svg>
              <span className="font-financial font-semibold text-slate-900 truncate">
                {selectedRelationship.hedgeRelationshipId}
              </span>
              <span className="text-slate-400 text-xs hidden sm:inline truncate">
                — {HEDGE_TYPE_BADGE[selectedRelationship.hedgeType].label} · {deriveCurrencyLabel(selectedRelationship)} · 지정 {formatDate(selectedRelationship.designationDate)}
              </span>
            </>
          ) : value ? (
            // 목록에 없지만 값이 지정된 경우(드문 edge case: 재테스트 대상 관계가 size 한도 밖)
            <>
              <span className="font-financial font-semibold text-slate-700 truncate">{value}</span>
              <span className="text-amber-600 text-xs flex-shrink-0">(목록에 없음)</span>
            </>
          ) : (
            <span className="text-slate-400">
              헤지 관계를 선택하세요
              {!isLoading && relationships.length > 0 && (
                <span className="ml-1.5 text-slate-300 text-xs font-normal">
                  — 전체 {relationships.length}건
                </span>
              )}
            </span>
          )}
        </span>

        {/* 캐럿 */}
        <svg
          className={clsx('w-4 h-4 text-slate-400 flex-shrink-0 transition-transform duration-200', isOpen && 'rotate-180')}
          viewBox="0 0 20 20"
          fill="currentColor"
          aria-hidden="true"
        >
          <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
        </svg>
      </button>

      {/* 드롭다운 패널 */}
      {isOpen && (
        <div className="absolute z-50 left-0 right-0 mt-1 bg-white border border-slate-200 rounded-lg shadow-xl overflow-hidden">

          {/* 검색 */}
          <div className="p-2 border-b border-slate-100 bg-slate-50">
            <div className="relative">
              <svg
                className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none"
                viewBox="0 0 20 20"
                fill="currentColor"
                aria-hidden="true"
              >
                <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd" />
              </svg>
              <input
                ref={searchInputRef}
                type="text"
                value={searchQuery}
                onChange={(e) => { setSearchQuery(e.target.value); setActiveIndex(-1) }}
                onKeyDown={handleSearchKeyDown}
                placeholder="관계 ID · 계약 ID · FVH/CFH · USD/KRW · 지정일로 검색..."
                className="w-full pl-8 pr-8 py-1.5 text-sm border border-slate-200 rounded-md bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                aria-label="관계 검색"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => { setSearchQuery(''); setActiveIndex(-1); searchInputRef.current?.focus() }}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 p-0.5 rounded"
                  aria-label="검색어 지우기"
                >
                  <svg className="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                  </svg>
                </button>
              )}
            </div>
          </div>

          {/* 목록 */}
          <ul
            id={listboxId}
            role="listbox"
            aria-label="위험회피관계 목록"
            className="max-h-72 overflow-y-auto overscroll-contain"
          >
            {isError ? (
              <li className="px-4 py-5 text-sm text-red-600 text-center">
                <p className="font-medium">관계 목록을 불러오지 못했습니다</p>
                <p className="text-xs text-red-400 mt-0.5">서버 연결을 확인하세요</p>
              </li>
            ) : filtered.length === 0 ? (
              <li className="px-4 py-5 text-center">
                {searchQuery ? (
                  <>
                    <p className="text-sm text-slate-500">&apos;{searchQuery}&apos;에 해당하는 관계가 없습니다</p>
                    <p className="text-xs text-slate-400 mt-0.5">다른 검색어를 입력해 보세요</p>
                  </>
                ) : (
                  <>
                    <p className="text-sm text-slate-500">등록된 헤지 관계가 없습니다</p>
                    <p className="text-xs text-slate-400 mt-0.5">「헤지 지정」 화면에서 먼저 관계를 등록하세요</p>
                  </>
                )}
              </li>
            ) : (
              filtered.map((rel, idx) => {
                const typeBadge = HEDGE_TYPE_BADGE[rel.hedgeType]
                const statusBadge = STATUS_BADGE[rel.status]
                const eligBadge = ELIGIBILITY_BADGE[rel.eligibilityStatus]
                const isActiveRow = idx === activeIndex
                const isSelected = rel.hedgeRelationshipId === value
                const isInactive = rel.status === 'DISCONTINUED' || rel.status === 'MATURED'

                return (
                  <li
                    key={rel.hedgeRelationshipId}
                    role="option"
                    aria-selected={isSelected}
                    className={clsx(
                      'px-4 py-3 cursor-pointer transition-colors duration-100',
                      'border-b border-slate-50 last:border-b-0',
                      isInactive && 'opacity-60',
                      isActiveRow ? 'bg-blue-50' :
                      isSelected  ? 'bg-emerald-50' :
                                    'hover:bg-slate-50',
                    )}
                    onMouseEnter={() => setActiveIndex(idx)}
                    onClick={() => handleSelect(rel)}
                  >
                    {/* 1행: 관계 ID + hedgeType + status + eligibility */}
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-1.5 min-w-0">
                        {isSelected && (
                          <svg
                            className="w-3.5 h-3.5 text-emerald-600 flex-shrink-0"
                            viewBox="0 0 20 20"
                            fill="currentColor"
                            aria-hidden="true"
                          >
                            <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                          </svg>
                        )}
                        <span className="text-sm font-semibold text-slate-900 font-financial truncate">
                          {rel.hedgeRelationshipId}
                        </span>
                      </div>
                      <div className="flex items-center gap-1 flex-shrink-0">
                        <span className={clsx('inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold', typeBadge.className)}>
                          {typeBadge.label}
                        </span>
                        <span className={clsx('inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold', statusBadge.className)}>
                          {statusBadge.label}
                        </span>
                        <span className={clsx('inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold', eligBadge.className)}>
                          {eligBadge.label}
                        </span>
                      </div>
                    </div>

                    {/* 2행: 통화 · 지정일 · 만기일 · 계약 ID */}
                    <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-slate-500 font-financial">
                      <span className="tabular-nums">{deriveCurrencyLabel(rel)}</span>
                      <span className="text-slate-300" aria-hidden="true">·</span>
                      <span>지정 <span className="tabular-nums">{formatDate(rel.designationDate)}</span></span>
                      <span className="text-slate-300" aria-hidden="true">·</span>
                      <span>만기 <span className="tabular-nums">{formatDate(rel.hedgePeriodEnd)}</span></span>
                      {rel.fxForwardContractId && (
                        <>
                          <span className="text-slate-300" aria-hidden="true">·</span>
                          <span className="tabular-nums">계약 {rel.fxForwardContractId}</span>
                        </>
                      )}
                    </div>

                    {isInactive && (
                      <p className="mt-1 text-xs text-amber-600 font-medium">
                        ⚠ {statusBadge.label} 상태 — 유효성 테스트에 사용하려면 활성 관계를 선택하세요
                      </p>
                    )}
                  </li>
                )
              })
            )}
          </ul>

          {/* 요약 */}
          {relationships.length > 0 && (
            <div className="px-4 py-2 bg-slate-50 border-t border-slate-100">
              <p className="text-xs text-slate-400">
                전체 {relationships.length}건
                {searchQuery && ` · 검색 결과 ${filtered.length}건`}
              </p>
            </div>
          )}
        </div>
      )}

      {/* 선택된 관계 상세 카드 (드롭다운 닫힘 상태에서만) */}
      {selectedRelationship && !isOpen && (
        <SelectedRelationshipCard
          relationship={selectedRelationship}
          onClear={handleClear}
          disabled={disabled}
        />
      )}

      {/* 목록에 없는 ID 가 value 로 들어온 경우 안내 */}
      {!selectedRelationship && value && !isLoading && !isOpen && (
        <div className="mt-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 flex items-start justify-between gap-2">
          <div className="text-xs text-amber-700">
            <p className="font-medium">선택된 ID 가 현재 목록에 없습니다.</p>
            <p className="text-amber-600 mt-0.5 break-all font-financial">{value}</p>
          </div>
          <button
            type="button"
            onClick={handleClear}
            disabled={disabled}
            className="text-xs font-medium text-amber-700 hover:text-amber-900 underline flex-shrink-0"
          >
            해제
          </button>
        </div>
      )}
    </div>
  )
}

// ─── 선택된 관계 상세 카드 ───────────────────────────────────────────────────

function SelectedRelationshipCard({
  relationship,
  onClear,
  disabled,
}: {
  relationship: HedgeRelationshipSummary
  onClear: () => void
  disabled?: boolean
}) {
  const typeBadge = HEDGE_TYPE_BADGE[relationship.hedgeType]
  const statusBadge = STATUS_BADGE[relationship.status]
  const eligBadge = ELIGIBILITY_BADGE[relationship.eligibilityStatus]
  const isInactive = relationship.status === 'DISCONTINUED' || relationship.status === 'MATURED'

  return (
    <div
      role="region"
      aria-label="선택된 위험회피관계 정보"
      className="mt-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3.5"
    >
      {/* 헤더 */}
      <div className="flex items-start justify-between gap-2 mb-2.5">
        <div className="flex items-center gap-2">
          <svg
            className="w-4 h-4 text-emerald-600 flex-shrink-0"
            viewBox="0 0 20 20"
            fill="currentColor"
            aria-hidden="true"
          >
            <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
          </svg>
          <span className="text-xs font-semibold text-emerald-800">위험회피관계 선택됨</span>
        </div>
        <div className="flex items-center gap-1.5 flex-shrink-0">
          <span className={clsx('inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold', typeBadge.className)}>
            {typeBadge.label}
          </span>
          <span className={clsx('inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold', statusBadge.className)}>
            {statusBadge.label}
          </span>
          <span className={clsx('inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold', eligBadge.className)}>
            {eligBadge.label}
          </span>
          {!disabled && (
            <button
              type="button"
              onClick={onClear}
              aria-label="관계 선택 해제 후 재선택"
              title="다른 관계로 변경"
              className="text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors p-0.5 rounded"
            >
              <svg className="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* 관계 ID */}
      <p className="text-sm font-bold text-slate-900 font-financial mb-3 break-all">
        {relationship.hedgeRelationshipId}
      </p>

      {/* 핵심 4 필드 — 요구사항(ID · hedgeType · 통화 · 지정일) 충족 */}
      <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
        <RelationshipDetailItem
          label="헤지 유형"
          value={`${typeBadge.label} · ${relationship.hedgeType === 'CASH_FLOW' ? '현금흐름 위험회피' : '공정가치 위험회피'}`}
        />
        <RelationshipDetailItem label="통화" value={deriveCurrencyLabel(relationship)} />
        <RelationshipDetailItem label="지정일" value={formatDate(relationship.designationDate)} />
        <RelationshipDetailItem label="헤지기간 종료" value={formatDate(relationship.hedgePeriodEnd)} />
        {relationship.fxForwardContractId && (
          <RelationshipDetailItem
            label="연결 계약"
            value={relationship.fxForwardContractId}
          />
        )}
        <RelationshipDetailItem
          label="헤지비율"
          value={`${(relationship.hedgeRatio * 100).toFixed(0)}%`}
        />
      </dl>

      {isInactive && (
        <div className="mt-2.5 px-3 py-2 bg-amber-50 border border-amber-200 rounded-md">
          <p className="text-xs text-amber-700 font-medium">
            ⚠ 이 관계는 &lsquo;{statusBadge.label}&rsquo; 상태입니다.
          </p>
          <p className="text-xs text-amber-600 mt-0.5">
            중단·만기된 관계에 대한 유효성 테스트는 이력 목적 외에는 권장되지 않습니다.
          </p>
        </div>
      )}
    </div>
  )
}

// ─── 헬퍼 ────────────────────────────────────────────────────────────────────

function RelationshipDetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-500 mb-0.5">{label}</dt>
      <dd className="text-xs font-semibold text-slate-800 font-financial tabular-nums break-all">{value}</dd>
    </div>
  )
}
