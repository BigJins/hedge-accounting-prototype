import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import PageLayout from '@/components/layout/PageLayout'
import { FxForwardContractList } from '@/features/valuation/components/FxForwardContractList'
import { FxForwardValuationForm } from '@/features/valuation/components/FxForwardValuationForm'
import { FxForwardValuationResult } from '@/features/valuation/components/FxForwardValuationResult'
import {
  FxForwardValuationHistory,
  PAGE_SIZE as HISTORY_PAGE_SIZE,
} from '@/features/valuation/components/FxForwardValuationHistory'
import { useFxForwardContractListAll } from '@/features/valuation/api/useFxForwardValuation'
import { IrsContractForm } from '@/features/valuation/components/IrsContractForm'
import { IrsValuationForm } from '@/features/valuation/components/IrsValuationForm'
import { IrsValuationResult } from '@/features/valuation/components/IrsValuationResult'
import { IrsContractList } from '@/features/valuation/components/IrsContractList'
import type {
  FxForwardContractResponse,
  FxForwardValuationResponse,
  IrsContractResponse,
  IrsValuationResponse,
  PageResponse,
} from '@/types/valuation'
import clsx from 'clsx'

// ─── 탭 타입 ─────────────────────────────────────────────────────────────────

type ActiveTab = 'fx-forward' | 'irs'

// ─── FlowChainBanner ──────────────────────────────────────────────────────────

function FlowChainBanner() {
  const orderedSteps: Array<{ n: number; label: string; kifrs: string; current?: boolean }> = [
    { n: 1, label: '공정가치 평가', kifrs: '1113호', current: true },
    { n: 2, label: '헤지 지정', kifrs: '6.4.1' },
    { n: 3, label: '유효성 테스트', kifrs: 'B6.4.12' },
    { n: 4, label: '자동 분개', kifrs: '6.5.8·11' },
  ]

  return (
    <div className="mb-6 rounded-xl border border-slate-200 bg-white px-5 py-3.5 flex items-center gap-3 overflow-x-auto">
      {orderedSteps.map((s, i) => (
        <div key={s.n} className="flex items-center gap-2 whitespace-nowrap">
          <span className={
            s.current
              ? 'w-6 h-6 rounded-full bg-blue-700 text-white text-xs font-bold flex items-center justify-center'
              : 'w-6 h-6 rounded-full bg-slate-100 text-slate-500 text-xs font-bold flex items-center justify-center'
          }>
            {s.n}
          </span>
          <span className={s.current ? 'text-sm font-semibold text-blue-700' : 'text-sm text-slate-500'}>
            {s.label}
          </span>
          <span className="text-[10px] font-mono text-slate-400">K-IFRS {s.kifrs}</span>
          {i < orderedSteps.length - 1 && <span className="text-slate-300 mx-1" aria-hidden="true">→</span>}
        </div>
      ))}
    </div>
  )
}

// ─── 탭 바 ────────────────────────────────────────────────────────────────────

function TabBar({ active, onChange }: { active: ActiveTab; onChange: (t: ActiveTab) => void }) {
  return (
    <div className="mb-6 flex items-center gap-0 border-b border-slate-200">
      <button
        type="button"
        onClick={() => onChange('fx-forward')}
        className={clsx(
          'px-5 py-3 text-sm font-semibold border-b-2 -mb-px transition-colors',
          active === 'fx-forward'
            ? 'border-blue-600 text-blue-700'
            : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300',
        )}
      >
        FX Forward
        <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold bg-sky-100 text-sky-700">
          1단계
        </span>
      </button>

      <button
        type="button"
        onClick={() => onChange('irs')}
        className={clsx(
          'px-5 py-3 text-sm font-semibold border-b-2 -mb-px transition-colors',
          active === 'irs'
            ? 'border-blue-600 text-blue-700'
            : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300',
        )}
      >
        IRS 금리위험
        <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold bg-blue-100 text-blue-700">
          2단계
        </span>
      </button>

      <span className="px-5 py-3 text-sm text-slate-300 cursor-not-allowed select-none border-b-2 border-transparent -mb-px">
        CRS 복합위험
        <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-100 text-slate-400">
          준비 중
        </span>
      </span>
    </div>
  )
}

// ─── FX Forward 탭 ───────────────────────────────────────────────────────────

function FxForwardTab() {
  const queryClient = useQueryClient()

  const [result, setResult] = useState<FxForwardValuationResponse | null>(null)
  const [selectedContractId, setSelectedContractId] = useState<string | null>(null)
  const [historyRefreshKey, setHistoryRefreshKey] = useState(0)
  const { data: allContracts } = useFxForwardContractListAll()
  const [formInitialValues, setFormInitialValues] = useState<
    Partial<{
      contractId: string
      notionalAmountUsd: number
      contractForwardRate: number
      contractDate: string
      maturityDate: string
      hedgeDesignationDate: string
      valuationDate: string
      spotRate: number
      krwInterestRate: number
      usdInterestRate: number
    }> | undefined
  >(undefined)
  const [formKey, setFormKey] = useState<string>('new')

  const handleSuccess = (res: FxForwardValuationResponse) => {
    setResult(res)
    setSelectedContractId(res.contractId)

    const contractKey = ['fx-forward-valuations', 'contract', res.contractId, 0, HISTORY_PAGE_SIZE]
    queryClient.setQueryData<PageResponse<FxForwardValuationResponse>>(contractKey, (old) => {
      if (!old) return old
      return {
        ...old,
        content: [res, ...old.content].slice(0, old.size),
        totalElements: old.totalElements + 1,
      }
    })
    setHistoryRefreshKey((k) => k + 1)

    setTimeout(() => {
      document.getElementById('fx-valuation-result')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 100)
  }

  const handleEditContract = (contract: FxForwardContractResponse) => {
    setResult(null)
    setFormKey(contract.contractId)
    setFormInitialValues({
      contractId:           contract.contractId,
      notionalAmountUsd:    contract.notionalAmountUsd,
      contractForwardRate:  contract.contractForwardRate,
      contractDate:         contract.contractDate,
      maturityDate:         contract.maturityDate,
      hedgeDesignationDate: contract.hedgeDesignationDate,
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleEditFromHistory = (item: FxForwardValuationResponse) => {
    const contract = allContracts?.find((c) => c.contractId === item.contractId)
    setResult(null)
    setFormKey(`history-${item.valuationId}`)
    setFormInitialValues({
      contractId:           item.contractId,
      notionalAmountUsd:    contract?.notionalAmountUsd,
      contractForwardRate:  contract?.contractForwardRate,
      contractDate:         contract?.contractDate,
      maturityDate:         contract?.maturityDate,
      hedgeDesignationDate: contract?.hedgeDesignationDate,
      valuationDate:        item.valuationDate,
      spotRate:             item.spotRate,
      krwInterestRate:      item.krwInterestRate,
      usdInterestRate:      item.usdInterestRate,
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return (
    <>
      <div className="mb-6 rounded-xl border border-blue-200 bg-blue-50 px-5 py-4">
        <p className="text-xs font-semibold uppercase tracking-wider text-blue-700 mb-1.5">
          1단계 엔진 범위
        </p>
        <div className="flex flex-wrap gap-x-6 gap-y-0.5 text-xs">
          <span className="text-blue-900 font-medium">✓ FX Forward · USD/KRW · Level 2 공정가치 (IRP 기반)</span>
          <span className="text-blue-500">○ 2단계: IRS 금리위험 FVH (탭 전환)</span>
        </div>
        <p className="mt-2 text-xs text-blue-600">
          KRW ACT/365 · USD ACT/360 · 현물환율·금리 기반 관측가능 투입변수 사용
        </p>
        <p className="mt-2 text-xs text-blue-700 border-t border-blue-200 pt-2">
          평가 결과는 이력으로 바로 저장됩니다. 먼저 평가된 계약을 헤지수단으로 지정한 뒤 유효성 테스트를 수행합니다.
        </p>
      </div>

      <FxForwardValuationForm
        key={formKey}
        onSuccess={handleSuccess}
        initialValues={formInitialValues}
      />

      {result && (
        <div id="fx-valuation-result">
          <FxForwardValuationResult result={result} />
          <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-5 py-4 flex items-center justify-between gap-4 flex-wrap">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-emerald-700">다음 단계</p>
              <p className="mt-1 text-sm font-semibold text-emerald-900">
                이 평가 결과를 헤지수단으로 지정하세요.
              </p>
              <p className="mt-0.5 text-xs text-emerald-700">
                위험회피관계 지정 후 유효성 테스트를 실행하면 분개가 자동으로 생성됩니다.
              </p>
            </div>
            <Link
              to="/hedge/designation"
              className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 transition-colors whitespace-nowrap"
            >
              헤지 지정으로 이동 →
            </Link>
          </div>
        </div>
      )}

      <FxForwardContractList
        onEdit={handleEditContract}
        onSelectContract={(id) => setSelectedContractId(id)}
        selectedContractId={selectedContractId}
      />

      <FxForwardValuationHistory
        contractId={selectedContractId}
        refreshKey={historyRefreshKey}
        onEdit={handleEditFromHistory}
      />
    </>
  )
}

// ─── IRS 탭 ──────────────────────────────────────────────────────────────────

function IrsTab() {
  const [registeredContract, setRegisteredContract] = useState<IrsContractResponse | null>(null)
  const [valuationResult, setValuationResult] = useState<IrsValuationResponse | null>(null)
  const [selectedContractId, setSelectedContractId] = useState<string | null>(null)

  const handleContractSuccess = (contract: IrsContractResponse) => {
    setRegisteredContract(contract)
    // 계약 등록 후 평가 폼에 contractId 자동 채우기
    setTimeout(() => {
      document.getElementById('irs-valuation-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 100)
  }

  const handleValuationSuccess = (result: IrsValuationResponse) => {
    setValuationResult(result)
    setSelectedContractId(result.contractId)
    setTimeout(() => {
      document.getElementById('irs-valuation-result')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 100)
  }

  return (
    <>
      {/* 2단계 엔진 안내 배너 */}
      <div className="mb-6 rounded-xl border border-blue-200 bg-blue-50 px-5 py-4">
        <p className="text-xs font-semibold uppercase tracking-wider text-blue-700 mb-1.5">
          2단계 엔진 범위
        </p>
        <div className="flex flex-wrap gap-x-6 gap-y-0.5 text-xs">
          <span className="text-blue-900 font-medium">
            ✓ IRS 금리위험 FVH · 고정/변동 다리 PV 분리 계산 · K-IFRS 1113호 Level 2
          </span>
          <span className="text-blue-500">○ 1단계: FX Forward (탭 전환)</span>
        </div>
        <p className="mt-2 text-xs text-blue-600">
          수취고정(payFixedReceiveFloating=false) 구조 · 할인율 무위험이자율 · 변동금리 CD 91일 또는 SOFR
        </p>
        <p className="mt-2 text-xs text-blue-700 border-t border-blue-200 pt-2">
          계약 등록 → 공정가치 평가 → 유효성 테스트 → 분개 자동 생성 순으로 진행합니다.
        </p>
      </div>

      {/* 1. IRS 계약 등록 */}
      <IrsContractForm onSuccess={handleContractSuccess} />

      {/* 계약 등록 성공 안내 */}
      {registeredContract && (
        <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-5 py-3 flex items-center gap-3">
          <span className="text-emerald-600 text-lg">✓</span>
          <div>
            <p className="text-sm font-semibold text-emerald-800">
              계약 등록 완료 — {registeredContract.contractId}
            </p>
            <p className="text-xs text-emerald-600">
              명목금액 {registeredContract.notionalAmount.toLocaleString('ko-KR')}원 · 고정금리 {(registeredContract.fixedRate * 100).toFixed(3)}%
              · {registeredContract.payFixedReceiveFloating ? 'CFH' : 'FVH'} 구조
            </p>
          </div>
        </div>
      )}

      {/* 2. IRS 공정가치 평가 */}
      <div id="irs-valuation-form" className="mt-6">
        <IrsValuationForm
          onSuccess={handleValuationSuccess}
          initialContractId={registeredContract?.contractId}
        />
      </div>

      {/* 3. 평가 결과 */}
      {valuationResult && (
        <div id="irs-valuation-result" className="mt-6">
          <IrsValuationResult result={valuationResult} />
        </div>
      )}

      {/* 4. IRS 계약 목록 */}
      <div className="mt-6">
        <IrsContractList
          onSelectContract={(id) => setSelectedContractId(id)}
          selectedContractId={selectedContractId}
        />
      </div>
    </>
  )
}

// ─── ValuationPage ────────────────────────────────────────────────────────────

/**
 * 공정가치 평가 페이지.
 *
 * - FX Forward 탭 (1단계 기본): IRP 기반 환위험 평가
 * - IRS 탭 (2단계 확장): 다리별 PV 분리 금리위험 평가
 * - CRS 탭: 준비 중 (비활성)
 *
 * @see K-IFRS 1113호 (공정가치 측정)
 * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 손익 인식)
 */
export default function ValuationPage() {
  const [activeTab, setActiveTab] = useState<ActiveTab>('fx-forward')

  return (
    <PageLayout
      title="공정가치 평가"
      subtitle="K-IFRS 1113호 Level 2 중심 평가 엔진 · FX Forward(1단계) · IRS 금리위험(2단계)"
      badge="K-IFRS 1109호"
    >
      {/* 체인 안내 */}
      <FlowChainBanner />

      {/* 탭 바 */}
      <TabBar active={activeTab} onChange={setActiveTab} />

      {/* 탭 콘텐츠 */}
      {activeTab === 'fx-forward' && <FxForwardTab />}
      {activeTab === 'irs'        && <IrsTab />}
    </PageLayout>
  )
}
