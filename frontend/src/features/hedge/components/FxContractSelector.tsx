import { useState, useRef, useEffect, useId } from 'react'
import clsx from 'clsx'
import { useFxForwardContractListAll } from '@/features/valuation/api/useFxForwardValuation'
import { formatDate, formatUsd, formatRate } from '@/utils/formatters'
import type { FxForwardContractResponse } from '@/types/valuation'

// ─── 상태 배지 ────────────────────────────────────────────────────────────────

const STATUS_BADGE: Record<
  FxForwardContractResponse['status'],
  { label: string; className: string }
> = {
  ACTIVE:     { label: '활성', className: 'bg-emerald-100 text-emerald-800' },
  MATURED:    { label: '만기', className: 'bg-slate-100 text-slate-600' },
  TERMINATED: { label: '종료', className: 'bg-red-100 text-red-700' },
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface FxContractSelectorProps {
  /** 현재 선택된 contractId — 빈 문자열이면 미선택 */
  value: string
  /** 계약 선택 시 호출 — contractId 전달 */
  onChange: (contractId: string) => void
  /**
   * 계약 선택/해제 시 전체 계약 객체를 전달하는 콜백.
   * 폼 자동 채우기(auto-fill) 연동에 사용합니다.
   * 선택 해제 시 null 전달.
   */
  onContractChange?: (contract: FxForwardContractResponse | null) => void
  /** react-hook-form 유효성 오류 메시지 (테두리 색상에만 사용, 텍스트 출력은 부모 Field 담당) */
  error?: string
  disabled?: boolean
  /** 트리거 버튼의 id (Field 컴포넌트 label htmlFor 연동) */
  inputId?: string
}

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

/**
 * 통화선도 계약 선택 UI (Searchable Dropdown + 선택 상세 카드)
 *
 * 기존 텍스트 입력(fxForwardContractId 직접 타이핑)을 대체합니다.
 *
 * UX 흐름:
 * 1. 트리거 버튼 클릭 → 검색 input + 계약 목록 드롭다운 열림
 * 2. 계약번호 / 금액으로 실시간 필터링
 * 3. 계약 선택 → 드롭다운 닫힘 + 선택 상세 카드 표시
 * 4. [선택 해제 ✕] 버튼으로 재선택 가능
 *
 * @see K-IFRS 1109호 6.5.4 (통화선도 = 적격 헤지수단)
 */
export function FxContractSelector({
  value,
  onChange,
  onContractChange,
  error,
  disabled = false,
  inputId,
}: FxContractSelectorProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(-1)

  const containerRef = useRef<HTMLDivElement>(null)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const listboxId = useId()

  const { data: contracts, isLoading, isError } = useFxForwardContractListAll()

  // 선택된 계약 객체 (value → 계약)
  const selectedContract = contracts?.find((c) => c.contractId === value) ?? null

  // 실시간 필터링: 계약번호 · 명목금액 · 만기일 · 상태(영문/한글) 매칭
  const filtered: FxForwardContractResponse[] = contracts
    ? contracts.filter((c) => {
        const q = searchQuery.toLowerCase().trim()
        if (!q) return true
        const statusLabel = (STATUS_BADGE[c.status] ?? STATUS_BADGE.TERMINATED).label
        return (
          c.contractId.toLowerCase().includes(q) ||
          c.notionalAmountUsd.toString().includes(q) ||
          c.maturityDate.includes(q) ||
          c.status.toLowerCase().includes(q) ||
          statusLabel.includes(q)          // 한글 상태명(활성/만기/종료)으로도 검색 가능
        )
      })
    : []

  // ── 클릭 외부 시 닫기 ─────────────────────────────────────────────────────
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        closeDropdown()
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // ── 드롭다운 열기/닫기 ────────────────────────────────────────────────────
  const openDropdown = () => {
    if (disabled) return
    setIsOpen(true)
    setSearchQuery('')
    setActiveIndex(-1)
    // 다음 틱에 search input focus
    requestAnimationFrame(() => searchInputRef.current?.focus())
  }

  const closeDropdown = () => {
    setIsOpen(false)
    setSearchQuery('')
    setActiveIndex(-1)
  }

  const handleSelect = (contract: FxForwardContractResponse) => {
    onChange(contract.contractId)
    onContractChange?.(contract)
    closeDropdown()
  }

  const handleClear = () => {
    onChange('')
    onContractChange?.(null)
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

      {/* ── 트리거 버튼 ─────────────────────────────────────────────── */}
      <button
        id={inputId}
        type="button"
        onClick={openDropdown}
        onKeyDown={handleTriggerKeyDown}
        disabled={disabled || isLoading}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-controls={isOpen ? listboxId : undefined}
        aria-label="통화선도 계약 선택"
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
              계약 목록 불러오는 중...
            </span>
          ) : selectedContract ? (
            <>
              <svg className="w-4 h-4 text-emerald-600 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
              </svg>
              <span className="font-financial font-semibold text-slate-900 truncate">
                {selectedContract.contractId}
              </span>
              <span className="text-slate-400 text-xs hidden sm:inline truncate">
                — {formatUsd(selectedContract.notionalAmountUsd)} · 만기 {formatDate(selectedContract.maturityDate)}
              </span>
            </>
          ) : (
            <span className="text-slate-400">
                계약을 선택하세요
                {!isLoading && contracts && contracts.length > 0 && (
                  <span className="ml-1.5 text-slate-300 text-xs font-normal">
                    — 활성 {contracts.filter((c) => c.status === 'ACTIVE').length}건
                  </span>
                )}
              </span>
          )}
        </span>

        {/* 캐럿 아이콘 */}
        <svg
          className={clsx('w-4 h-4 text-slate-400 flex-shrink-0 transition-transform duration-200', isOpen && 'rotate-180')}
          viewBox="0 0 20 20"
          fill="currentColor"
          aria-hidden="true"
        >
          <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
        </svg>
      </button>

      {/* ── 드롭다운 패널 ────────────────────────────────────────────── */}
      {isOpen && (
        <div className="absolute z-50 left-0 right-0 mt-1 bg-white border border-slate-200 rounded-lg shadow-xl overflow-hidden">

          {/* 검색 입력 */}
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
                placeholder="계약번호, 금액, 만기일로 검색..."
                className="w-full pl-8 pr-8 py-1.5 text-sm border border-slate-200 rounded-md bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                aria-label="계약 검색"
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

          {/* 계약 목록 */}
          <ul
            id={listboxId}
            role="listbox"
            aria-label="통화선도 계약 목록"
            className="max-h-64 overflow-y-auto overscroll-contain"
          >
            {isError ? (
              <li className="px-4 py-5 text-sm text-red-600 text-center">
                <p className="font-medium">계약 목록을 불러오지 못했습니다</p>
                <p className="text-xs text-red-400 mt-0.5">서버 연결을 확인하세요</p>
              </li>
            ) : filtered.length === 0 ? (
              <li className="px-4 py-5 text-center">
                {searchQuery ? (
                  <>
                    <p className="text-sm text-slate-500">'{searchQuery}'에 해당하는 계약이 없습니다</p>
                    <p className="text-xs text-slate-400 mt-0.5">다른 검색어를 입력해 보세요</p>
                  </>
                ) : (
                  <>
                    <p className="text-sm text-slate-500">등록된 계약이 없습니다</p>
                    <p className="text-xs text-slate-400 mt-0.5">공정가치 평가 화면에서 계약을 먼저 등록하세요</p>
                  </>
                )}
              </li>
            ) : (
              filtered.map((contract, idx) => {
                const badge = STATUS_BADGE[contract.status] ?? STATUS_BADGE.TERMINATED
                const isActive = idx === activeIndex
                const isSelected = contract.contractId === value

                return (
                  <li
                    key={contract.contractId}
                    role="option"
                    aria-selected={isSelected}
                    className={clsx(
                      'px-4 py-3 cursor-pointer transition-colors duration-100',
                      'border-b border-slate-50 last:border-b-0',
                      contract.status !== 'ACTIVE' && 'opacity-60',
                      isActive   ? 'bg-blue-50' :
                      isSelected ? 'bg-emerald-50' :
                                   'hover:bg-slate-50',
                    )}
                    onMouseEnter={() => setActiveIndex(idx)}
                    onClick={() => handleSelect(contract)}
                  >
                    {/* 계약 ID + 상태 배지 행 */}
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
                          {contract.contractId}
                        </span>
                      </div>
                      <span
                        className={clsx(
                          'inline-flex items-center px-1.5 py-0.5 rounded text-xs font-semibold flex-shrink-0',
                          badge.className,
                        )}
                      >
                        {badge.label}
                      </span>
                    </div>

                    {/* 계약 핵심 정보 행 */}
                    <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-slate-500 font-financial">
                      <span className="tabular-nums">{formatUsd(contract.notionalAmountUsd)}</span>
                      <span className="text-slate-300" aria-hidden="true">·</span>
                      <span>선물환율 <span className="tabular-nums">{formatRate(contract.contractForwardRate)}</span></span>
                      <span className="text-slate-300" aria-hidden="true">·</span>
                      <span>만기 <span className="tabular-nums">{formatDate(contract.maturityDate)}</span></span>
                    </div>

                    {/* 비활성 계약 경고 */}
                    {contract.status !== 'ACTIVE' && (
                      <p className="mt-1 text-xs text-amber-600 font-medium">
                        ⚠ {badge.label} 상태 — 신규 헤지 지정에 사용하지 않는 것을 권장합니다
                      </p>
                    )}
                  </li>
                )
              })
            )}
          </ul>

          {/* 계약 수 요약 */}
          {contracts && contracts.length > 0 && (
            <div className="px-4 py-2 bg-slate-50 border-t border-slate-100">
              <p className="text-xs text-slate-400">
                전체 {contracts.length}건
                {searchQuery && ` · 검색 결과 ${filtered.length}건`}
              </p>
            </div>
          )}
        </div>
      )}

      {/* ── 선택된 계약 상세 카드 (드롭다운 닫힌 상태에서 표시) ──── */}
      {selectedContract && !isOpen && (
        <SelectedContractCard
          contract={selectedContract}
          onClear={handleClear}
          disabled={disabled}
        />
      )}
    </div>
  )
}

// ─── 선택된 계약 상세 카드 ────────────────────────────────────────────────────

/**
 * 계약 선택 후 표시되는 상세 정보 카드.
 * 사용자가 "이 계약을 골랐다"는 확신을 얻을 수 있도록 핵심 정보를 노출합니다.
 */
function SelectedContractCard({
  contract,
  onClear,
  disabled,
}: {
  contract: FxForwardContractResponse
  onClear: () => void
  disabled?: boolean
}) {
  const badge = STATUS_BADGE[contract.status] ?? STATUS_BADGE.TERMINATED

  return (
    <div
      role="region"
      aria-label="선택된 통화선도 계약 정보"
      className="mt-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3.5"
    >
      {/* 카드 헤더 */}
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
          <span className="text-xs font-semibold text-emerald-800">헤지수단 계약 선택됨</span>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <span
            className={clsx(
              'inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold',
              badge.className,
            )}
          >
            {badge.label}
          </span>
          {!disabled && (
            <button
              type="button"
              onClick={onClear}
              aria-label="계약 선택 해제 후 재선택"
              title="다른 계약으로 변경"
              className="text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors p-0.5 rounded"
            >
              <svg className="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* 계약 ID */}
      <p className="text-sm font-bold text-slate-900 font-financial mb-3">
        {contract.contractId}
      </p>

      {/* 상세 정보 그리드 — 가장 중요한 4가지 */}
      <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
        <ContractDetailItem label="통화" value="USD / KRW" />
        <ContractDetailItem label="명목원금" value={formatUsd(contract.notionalAmountUsd)} />
        <ContractDetailItem label="만기일" value={formatDate(contract.maturityDate)} />
        <ContractDetailItem
          label="계약 선물환율"
          value={`${formatRate(contract.contractForwardRate)} KRW/USD`}
        />
      </dl>

      {/* 비활성 계약 경고 배너 */}
      {contract.status !== 'ACTIVE' && (
        <div className="mt-2.5 px-3 py-2 bg-amber-50 border border-amber-200 rounded-md">
          <p className="text-xs text-amber-700 font-medium">
            ⚠ 이 계약은 &lsquo;{badge.label}&rsquo; 상태입니다.
          </p>
          <p className="text-xs text-amber-600 mt-0.5">
            만기·종료된 계약은 신규 헤지 지정에 사용하지 않는 것이 원칙입니다.
          </p>
        </div>
      )}

      {/* 헤지수단 적격성 안내 */}
      <div className="mt-3 pt-2.5 border-t border-emerald-200">
        <p className="text-xs text-emerald-700">
          통화선도는 K-IFRS 1109호 6.5.4에 따른 적격 헤지수단입니다.
          명목금액과 만기일이 헤지대상 항목과 일치하는지 확인하세요.
        </p>
      </div>
    </div>
  )
}

// ─── 헬퍼 ────────────────────────────────────────────────────────────────────

function ContractDetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-500 mb-0.5">{label}</dt>
      <dd className="text-xs font-semibold text-slate-800 font-financial tabular-nums">{value}</dd>
    </div>
  )
}
