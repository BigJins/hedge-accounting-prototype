import { useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import clsx from 'clsx'
import PageLayout from '@/components/layout/PageLayout'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorAlert } from '@/components/ui/ErrorAlert'
import { HedgeDesignationForm } from '../components/HedgeDesignationForm'
import { LiveEligibilityPanel } from '../components/LiveEligibilityPanel'
import { useHedgeDesignationMutation, HedgeDesignationApiError, useHedgeRelationshipList } from '../api/hedgeApi'
import type { HedgeDesignationRequest, HedgeDesignationResponse, HedgeRelationshipSummary } from '@/types/hedge'
import type { FormValues } from '../components/HedgeDesignationForm'
import { formatDate } from '@/utils/formatters'

// ─── 상수 ─────────────────────────────────────────────────────────────────────

const PAGE_SIZE = 10

// ─── 배지 매핑 ────────────────────────────────────────────────────────────────

const HEDGE_TYPE_LABEL: Record<string, string> = {
  FAIR_VALUE: '공정가치',
  CASH_FLOW:  '현금흐름',
}

const STATUS_BADGE: Record<string, { label: string; className: string }> = {
  DESIGNATED:    { label: '지정',   className: 'bg-emerald-100 text-emerald-800' },
  DISCONTINUED:  { label: '중단',   className: 'bg-red-100 text-red-700' },
  REBALANCED:    { label: '재조정', className: 'bg-amber-100 text-amber-800' },
  MATURED:       { label: '만기',   className: 'bg-slate-100 text-slate-600' },
}

const ELIGIBILITY_BADGE: Record<string, { label: string; className: string }> = {
  ELIGIBLE:   { label: '적격',   className: 'bg-emerald-100 text-emerald-800' },
  INELIGIBLE: { label: '비적격', className: 'bg-red-100 text-red-700' },
  PENDING:    { label: '검토중', className: 'bg-slate-100 text-slate-600' },
}

const RISK_LABEL: Record<string, string> = {
  FOREIGN_CURRENCY: '외화위험',
  INTEREST_RATE:    '이자율위험',
  COMMODITY:        '상품위험',
  CREDIT:           '신용위험',
}

// ─── 메인 페이지 ──────────────────────────────────────────────────────────────

/**
 * 헤지 지정 페이지 — K-IFRS 1109호 6.4.1 실시간 적격요건 검증
 *
 * 레이아웃: [폼 (좌)] | [실시간 검증 패널 (우, sticky)]
 *
 * 흐름:
 * 1. 폼 입력 → 우측 패널에 클라이언트 사전 검토 실시간 표시
 * 2. [저장 및 적격요건 검증] 클릭 → POST /api/v1/hedge-relationships
 * 3. 201 성공: 적격요건 PASS + 문서화 생성 표시
 *    422 실패: FAIL 사유 상세 표시 (저장 안 됨)
 * 4. [문서 생성 및 다운로드] 버튼 활성화 (PASS 시)
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용조건)
 */
export default function HedgeDesignationPage() {
  const mutation = useHedgeDesignationMutation()

  /** 검증 결과 (성공/422 모두) */
  const [resultState, setResultState] = useState<{
    response: HedgeDesignationResponse
    isSaved: boolean
  } | null>(null)

  /** 네트워크/서버 에러 메시지 (422 제외) */
  const [systemError, setSystemError] = useState<string | null>(null)

  /** 폼 실시간 값 (LiveEligibilityPanel에 전달) */
  const [liveValues, setLiveValues] = useState<Partial<FormValues> | null>(null)

  /** 폼 재마운트 키 (재지정 시 defaultValues 재적용) */
  const [formKey, setFormKey] = useState<string>('new')

  /** 재지정 시 폼 초기값 */
  const [formInitialValues, setFormInitialValues] = useState<
    Parameters<typeof HedgeDesignationForm>[0]['initialValues']
  >(undefined)

  // ── 핸들러 ──────────────────────────────────────────────────────────────────

  const handleSubmit = (request: HedgeDesignationRequest) => {
    setResultState(null)
    setSystemError(null)
    mutation.mutate(request, {
      onSuccess: (response) => {
        setResultState({ response, isSaved: true })
      },
      onError: (err) => {
        if (err instanceof HedgeDesignationApiError && err.isEligibilityFail) {
          setResultState({ response: err.response, isSaved: false })
        } else {
          setSystemError(err.message ?? '서버 오류가 발생했습니다.')
        }
      },
    })
  }

  const handleEditClick = () => {
    setResultState(null)
    setSystemError(null)
    mutation.reset()
  }

  const handleRedesignate = (item: HedgeRelationshipSummary) => {
    setResultState(null)
    setSystemError(null)
    mutation.reset()
    setFormKey(`redesignate-${item.hedgeRelationshipId}`)
    setFormInitialValues({
      hedgeType:    item.hedgeType,
      hedgedRisk:   'FOREIGN_CURRENCY',
      hedgeRatio:   item.hedgeRatio,
      designationDate: item.designationDate,
      hedgePeriodEnd:  item.hedgePeriodEnd,
      fxForwardContractId: item.fxForwardContractId,
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  // useCallback으로 안정적인 참조 유지
  const handleValuesChange = useCallback((values: FormValues) => {
    setLiveValues(values)
  }, [])

  // ── 렌더 ────────────────────────────────────────────────────────────────────

  return (
    <PageLayout
      title="헤지 지정"
      subtitle="K-IFRS 1109호 6.4.1 — 위험회피관계 지정 및 적격요건 자동 검증 · 1단계: FX Forward / USD/KRW 중심"
      badge="K-IFRS 1109호"
    >
      {/* ── 2열 레이아웃: 폼(좌) + 실시간 패널(우) ─────────────────── */}
      <div className="flex flex-col lg:grid lg:grid-cols-[1fr_380px] gap-6 items-start">

        {/* ── 좌측: 입력 폼 ──────────────────────────────────────────── */}
        <div className="min-w-0 w-full">
          <HedgeDesignationForm
            key={formKey}
            onSubmit={handleSubmit}
            isLoading={mutation.isPending}
            initialValues={formInitialValues}
            onValuesChange={handleValuesChange}
          />
        </div>

        {/* ── 우측: 실시간 K-IFRS 적격요건 검증 패널 (sticky) ─────────── */}
        <div className="w-full lg:sticky lg:top-6 lg:max-h-[calc(100vh-5rem)] lg:overflow-y-auto">
          <LiveEligibilityPanel
            liveValues={liveValues}
            serverResult={resultState}
            systemError={systemError}
            isLoading={mutation.isPending}
            onEditClick={handleEditClick}
          />
        </div>
      </div>

      {resultState?.isSaved && resultState.response.hedgeRelationshipId && (
        <div className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50 px-5 py-4 flex items-center justify-between gap-4 flex-wrap">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-emerald-700">다음 단계</p>
            <p className="mt-1 text-sm font-semibold text-emerald-900">
              지정된 위험회피관계로 유효성 테스트를 실행하세요.
            </p>
            <p className="mt-0.5 text-xs text-emerald-700 font-mono">
              관계 ID: {resultState.response.hedgeRelationshipId}
            </p>
          </div>
          <Link
            to="/effectiveness"
            className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 transition-colors whitespace-nowrap"
          >
            유효성 테스트로 이동 →
          </Link>
        </div>
      )}

      {/* ── 하단: 헤지관계 이력 목록 (전체 너비) ─────────────────────── */}
      <HedgeRelationshipList onRedesignate={handleRedesignate} />
    </PageLayout>
  )
}

// ─── 헤지관계 목록 컴포넌트 ───────────────────────────────────────────────────

interface HedgeRelationshipListProps {
  onRedesignate: (item: HedgeRelationshipSummary) => void
}

function HedgeRelationshipList({ onRedesignate }: HedgeRelationshipListProps) {
  const [page, setPage] = useState(0)
  const [selectedItem, setSelectedItem] = useState<HedgeRelationshipSummary | null>(null)

  const { data, isLoading, isError, error } = useHedgeRelationshipList({
    page,
    size: PAGE_SIZE,
  })

  const handlePageChange = (newPage: number) => {
    setPage(newPage)
    setSelectedItem(null)
  }

  if (isLoading) {
    return (
      <Card title="헤지관계 이력" description="지정된 위험회피관계 목록">
        <div className="overflow-hidden rounded-lg border border-slate-200" aria-busy="true" aria-label="이력 불러오는 중">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                {['관계ID', '헤지유형', '위험유형', '지정일', '상태', '적격여부', '작업'].map((h) => (
                  <th key={h} scope="col" className="px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider text-left">{h}</th>
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

  if (isError) {
    return (
      <Card title="헤지관계 이력" description="지정된 위험회피관계 목록">
        <ErrorAlert error={error} onRetry={() => window.location.reload()} />
      </Card>
    )
  }

  if (!data || data.empty) {
    return (
      <Card title="헤지관계 이력" description="지정된 위험회피관계 목록">
        <EmptyState
          icon="document"
          title="아직 지정된 헤지관계가 없습니다"
          description="위험회피회계를 적용하려면 먼저 헤지 지정을 등록해야 합니다."
          hint="위 입력 폼에서 헤지 유형·위험 유형·통화선도 계약을 선택하고 '저장 및 적격요건 검증'을 클릭하세요."
        />
      </Card>
    )
  }

  return (
    <Card title="헤지관계 이력" description={`지정된 위험회피관계 목록 — ${data.totalElements}건`}>
      <div className="space-y-4">
        <div className="overflow-hidden rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                {['관계ID', '헤지유형', '위험유형', '지정일', '상태', '적격여부', '작업'].map((h) => (
                  <th key={h} scope="col" className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((item) => {
                const isSelected = selectedItem?.hedgeRelationshipId === item.hedgeRelationshipId
                const statusBadge = STATUS_BADGE[item.status] ?? { label: item.status, className: 'bg-slate-100 text-slate-600' }
                const eligBadge = ELIGIBILITY_BADGE[item.eligibilityStatus] ?? { label: item.eligibilityStatus, className: 'bg-slate-100 text-slate-600' }
                return (
                  <tr
                    key={item.hedgeRelationshipId}
                    className={clsx('transition-colors cursor-pointer', isSelected ? 'bg-blue-50' : 'hover:bg-slate-50')}
                    onClick={() => setSelectedItem(isSelected ? null : item)}
                    aria-selected={isSelected}
                  >
                    <td className="px-4 py-3 text-slate-700 font-medium font-financial text-xs">{item.hedgeRelationshipId.slice(0, 8)}…</td>
                    <td className="px-4 py-3 text-slate-700">{HEDGE_TYPE_LABEL[item.hedgeType] ?? item.hedgeType}</td>
                    <td className="px-4 py-3 text-slate-700">{RISK_LABEL[item.hedgedRisk] ?? item.hedgedRisk}</td>
                    <td className="px-4 py-3 text-slate-700 font-financial tabular-nums">{formatDate(item.designationDate)}</td>
                    <td className="px-4 py-3">
                      <span className={clsx('inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold', statusBadge.className)}>{statusBadge.label}</span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={clsx('inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold', eligBadge.className)}>{eligBadge.label}</span>
                    </td>
                    <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                      <Button variant="secondary" size="sm" onClick={() => setSelectedItem(isSelected ? null : item)} aria-pressed={isSelected}>
                        {isSelected ? '닫기' : '상세'}
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        {selectedItem && (
          <HedgeRelationshipDetailPanel item={selectedItem} onRedesignate={onRedesignate} onClose={() => setSelectedItem(null)} />
        )}

        <Pagination page={data.number} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size} onPageChange={handlePageChange} />
      </div>
    </Card>
  )
}

// ─── 헤지관계 상세 패널 ───────────────────────────────────────────────────────

function HedgeRelationshipDetailPanel({
  item, onRedesignate, onClose,
}: { item: HedgeRelationshipSummary; onRedesignate: (item: HedgeRelationshipSummary) => void; onClose: () => void }) {
  const statusBadge = STATUS_BADGE[item.status] ?? { label: item.status, className: 'bg-slate-100 text-slate-600' }
  const eligBadge = ELIGIBILITY_BADGE[item.eligibilityStatus] ?? { label: item.eligibilityStatus, className: 'bg-slate-100 text-slate-600' }

  return (
    <div role="region" aria-label="헤지관계 상세" className="rounded-lg border border-blue-200 bg-blue-50 p-5">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h4 className="text-sm font-semibold text-blue-900">헤지관계 상세</h4>
          <p className="text-xs text-blue-600 mt-0.5 font-financial break-all">ID: {item.hedgeRelationshipId}</p>
        </div>
        <button type="button" onClick={onClose} aria-label="상세 패널 닫기" className="text-blue-400 hover:text-blue-600 transition-colors p-1">
          <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
          </svg>
        </button>
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 mb-4">
        {([
          ['헤지유형',        `${HEDGE_TYPE_LABEL[item.hedgeType] ?? item.hedgeType} 위험회피`],
          ['위험유형',        RISK_LABEL[item.hedgedRisk] ?? item.hedgedRisk],
          ['헤지비율',        `${(item.hedgeRatio * 100).toFixed(0)}%`],
          ['지정일',          formatDate(item.designationDate)],
          ['위험회피기간 종료', formatDate(item.hedgePeriodEnd)],
          ['계약 ID',         item.fxForwardContractId],
        ] as [string, string][]).map(([label, value]) => (
          <div key={label}>
            <p className="text-xs text-slate-500 mb-1">{label}</p>
            <p className="text-sm text-slate-800 font-financial">{value}</p>
          </div>
        ))}
        <div>
          <p className="text-xs text-slate-500 mb-1">상태</p>
          <span className={clsx('inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold', statusBadge.className)}>{statusBadge.label}</span>
        </div>
        <div>
          <p className="text-xs text-slate-500 mb-1">적격여부</p>
          <span className={clsx('inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold', eligBadge.className)}>{eligBadge.label}</span>
        </div>
      </div>
      <div className="flex justify-end pt-3 border-t border-blue-200">
        <Button variant="primary" size="sm" type="button" onClick={() => onRedesignate(item)}>
          이 값으로 재지정
        </Button>
      </div>
    </div>
  )
}
