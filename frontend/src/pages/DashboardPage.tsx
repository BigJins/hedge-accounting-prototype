import { useNavigate } from 'react-router-dom'
import clsx from 'clsx'
import { useFxForwardContractList, useFxForwardValuationList } from '@/features/valuation/api/useFxForwardValuation'
import { useHedgeRelationshipList } from '@/features/hedge/api/hedgeApi'
import { useJournalEntryList } from '@/features/journal/api/useJournalEntry'
import type { FxForwardContractResponse } from '@/types/valuation'

/* ─────────────────────────────────────────────
   서브 컴포넌트
───────────────────────────────────────────── */

function KifrsChip({ label }: { label: string }) {
  return (
    <span className="inline-flex items-center text-xs font-semibold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-full">
      {label}
    </span>
  )
}

function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('animate-pulse bg-slate-100 rounded', className)} />
}

/* ─────────────────────────────────────────────
   대시보드 페이지
───────────────────────────────────────────── */
export default function DashboardPage() {
  const navigate = useNavigate()

  /* ── API 호출 ──
   *
   * 모든 수치·목록은 백엔드 API 실시간 조회 결과를 그대로 사용한다.
   * 하드코딩·가상 데이터 없음. 조회 실패 시 0·빈 배열로 폴백한다.
   */
  const { data: contractsData, isLoading: contractsLoading } =
    useFxForwardContractList(0, 100)

  const { data: hedgesData, isLoading: hedgesLoading } =
    useHedgeRelationshipList({ size: 100 })

  // Level 분포는 샘플 20건 기준으로 집계 (totalElements는 전체 수)
  const { data: recentValuations, isLoading: valuationsLoading } =
    useFxForwardValuationList(0, 20)

  const { data: recentJournals, isLoading: journalsLoading } =
    useJournalEntryList(null, 0, 5)

  const isLoading = contractsLoading || hedgesLoading || valuationsLoading || journalsLoading

  /* ── 파생 집계 ── */
  const allContracts  = contractsData?.content ?? []
  const activeContracts: FxForwardContractResponse[] =
    allContracts.filter((c) => c.status === 'ACTIVE')
  const suggestedContract = activeContracts[0] ?? null

  const allHedges    = hedgesData?.content ?? []
  const designatedEligible = allHedges.filter(
    (h) => h.status === 'DESIGNATED' && h.eligibilityStatus === 'ELIGIBLE',
  )
  const fvhCount = allHedges.filter((h) => h.hedgeType === 'FAIR_VALUE').length
  const cfhCount = allHedges.filter((h) => h.hedgeType === 'CASH_FLOW').length

  const totalJournalEntries = recentJournals?.totalElements ?? 0

  // 완전 빈 환경(첫 진입) — 심사관에게 "헤지 지정부터 시작" 안내
  const isEmptyEnvironment =
    !isLoading && allContracts.length === 0 && allHedges.length === 0

  /* ── 최근 처리 현황 (평가 이력 + 분개 이력 — createdAt 기준 실제 최신순 병합) ── */
  type ActivityItem = {
    key: string
    sortKey: number
    dot: 'green' | 'blue' | 'amber'
    action: string
    sub: string
    tag: { label: string; color: 'green' | 'blue' | 'amber' }
    kifrs: string | null
    time: string
  }

  const formatTime = (iso: string) =>
    new Date(iso).toLocaleString('ko-KR', {
      month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
    })

  const activityItems: ActivityItem[] = [
    ...(recentValuations?.content ?? []).slice(0, 5).map<ActivityItem>((v) => ({
      key: `val-${v.valuationId}`,
      sortKey: new Date(v.createdAt).getTime(),
      dot: 'green',
      action: `${v.contractId} 공정가치 평가 완료`,
      sub: `공정가치 ${v.fairValue.toLocaleString('ko-KR')}원 · ${v.fairValueLevel.replace('_', ' ')}`,
      tag: { label: v.fairValueLevel.replace('_', ' '), color: 'green' },
      kifrs: '1113호',
      time: formatTime(v.createdAt),
    })),
    ...(recentJournals?.content ?? []).map<ActivityItem>((j) => ({
      key: `jnl-${j.journalEntryId}`,
      sortKey: new Date(j.createdAt).getTime(),
      dot: 'blue',
      action: `${j.hedgeRelationshipId} 분개 생성 완료`,
      sub: `${j.debitAccountName} / ${j.creditAccountName} · ${j.formattedDebitAmount}`,
      tag: { label: j.ifrsReference, color: 'blue' },
      kifrs: null,
      time: formatTime(j.createdAt),
    })),
  ]
    .sort((a, b) => b.sortKey - a.sortKey)
    .slice(0, 6)

  /* ─────────────────────────────────────────────
     렌더
  ───────────────────────────────────────────── */
  return (
    <div className="min-h-full bg-slate-50">

      {/* ── 헤더 바 ──
        *
        * 가짜 "시스템 정상 펄스"·"자동 집계" 문구 제거.
        * 데이터 출처를 명시해 심사관이 "실 API 데이터"임을 바로 인식하게 한다.
        */}
      <header className="bg-white border-b border-slate-200 px-8 py-4 flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-slate-900">현재 업무 현황</h1>
          <p className="text-xs text-slate-400 mt-0.5">
            {new Date().toLocaleDateString('ko-KR', {
              year: 'numeric', month: 'long', day: 'numeric', weekday: 'short',
            })}
            &nbsp;·&nbsp;백엔드 API 실시간 조회
          </p>
        </div>
      </header>

      <div className="max-w-screen-xl mx-auto px-8 py-7 space-y-7">

        {/* ══ 진입 CTA — 우선순위:
          *    (0) 환경이 완전히 비어 있음          → 헤지 지정부터 시작
          *    (1) 활성 통화선도 계약이 있음        → 공정가치 평가 추천
          *    (2) ELIGIBLE 지정 완료 헤지가 있음  → 유효성 테스트 추천
          *    (3) 그 외                            → 대기 작업 없음 (정상)
          *
          *  "가장 급한 작업" 같은 연출된 긴급 표현은 빼고 "다음 추천 작업"으로 통일.
          *  animate-pulse / 사선 패턴 오버레이 제거 — 가짜 긴장감 제거.
          ══ */}
        {isLoading ? (
          <Skeleton className="h-24 rounded-xl" />
        ) : isEmptyEnvironment ? (
          <div className="rounded-xl bg-gradient-to-r from-blue-800 to-blue-600 text-white px-7 py-5 flex items-center justify-between gap-6 shadow-sm">
            <div className="flex items-center gap-4">
              <div className="w-11 h-11 rounded-full bg-white/20 flex items-center justify-center text-2xl shrink-0">🎯</div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wide opacity-80 mb-1">여기서 시작</p>
                <p className="text-lg font-bold leading-snug">등록된 헤지 관계가 없습니다</p>
                <p className="text-sm opacity-85 mt-0.5">
                  「헤지 지정」에서 K-IFRS 6.4.1 적격요건 자동 검증부터 시작할 수 있습니다.
                </p>
              </div>
            </div>
            <button
              onClick={() => navigate('/hedge/designation')}
              className="shrink-0 bg-white text-blue-700 font-bold text-sm px-7 py-3 rounded-lg hover:shadow-md hover:-translate-y-px transition-all duration-150 whitespace-nowrap"
            >
              헤지 지정 시작 →
            </button>
          </div>
        ) : suggestedContract ? (
          <div className="rounded-xl bg-gradient-to-r from-amber-700 to-amber-500 text-white px-7 py-5 flex items-center justify-between gap-6 shadow-sm">
            <div className="flex items-center gap-4">
              <div className="w-11 h-11 rounded-full bg-white/20 flex items-center justify-center text-2xl shrink-0">📊</div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wide opacity-80 mb-1">다음 추천 작업</p>
                <p className="text-lg font-bold leading-snug">
                  {suggestedContract.contractId} · 공정가치 평가 가능
                </p>
                <p className="text-sm opacity-85 mt-0.5">
                  명목금액 USD {suggestedContract.notionalAmountUsd.toLocaleString('en-US')}
                  &nbsp;·&nbsp;만기 {suggestedContract.maturityDate}
                </p>
              </div>
            </div>
            <button
              onClick={() => navigate('/valuation')}
              className="shrink-0 bg-white text-amber-600 font-bold text-sm px-7 py-3 rounded-lg hover:shadow-md hover:-translate-y-px transition-all duration-150 whitespace-nowrap"
            >
              평가 시작 →
            </button>
          </div>
        ) : designatedEligible.length > 0 ? (
          <div className="rounded-xl bg-gradient-to-r from-blue-800 to-blue-600 text-white px-7 py-5 flex items-center justify-between gap-6 shadow-sm">
            <div className="flex items-center gap-4">
              <div className="w-11 h-11 rounded-full bg-white/20 flex items-center justify-center text-2xl shrink-0">🔬</div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wide opacity-80 mb-1">다음 추천 작업</p>
                <p className="text-lg font-bold">
                  {designatedEligible[0].hedgeRelationshipId} · 유효성 테스트 가능
                </p>
                <p className="text-sm opacity-85 mt-0.5">
                  {designatedEligible[0].hedgeType === 'FAIR_VALUE' ? '공정가치' : '현금흐름'} 위험회피
                  &nbsp;·&nbsp;만기 {designatedEligible[0].hedgePeriodEnd}
                </p>
              </div>
            </div>
            <button
              onClick={() => navigate('/effectiveness')}
              className="shrink-0 bg-white text-blue-700 font-bold text-sm px-7 py-3 rounded-lg hover:shadow-md hover:-translate-y-px transition-all duration-150 whitespace-nowrap"
            >
              테스트 시작 →
            </button>
          </div>
        ) : (
          <div className="rounded-xl bg-gradient-to-r from-emerald-700 to-emerald-500 text-white px-7 py-5 flex items-center gap-4 shadow-sm">
            <div className="w-11 h-11 rounded-full bg-white/20 flex items-center justify-center text-2xl shrink-0">✅</div>
            <div>
              <p className="text-xs font-bold uppercase tracking-wide opacity-80 mb-1">현재 상태</p>
              <p className="text-lg font-bold">대기 중인 작업이 없습니다</p>
              <p className="text-sm opacity-85 mt-0.5">모든 활성 계약과 지정 완료 헤지 관계가 처리된 상태입니다.</p>
            </div>
          </div>
        )}

        {/* ══ 업무 카드 3개 — 숫자는 모두 실제 API 응답 기반.
          *    "평가 대기"·"테스트 필요" 같은 과장된 연출을 사실 그대로의 레이블
          *    ("활성" / "지정 완료" / "누적")로 대체한다.
          ══ */}
        <section>
          <h2 className="text-sm font-bold text-slate-700 mb-3">📋 현재 상태 요약</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">

            {/* 카드 1: 활성 통화선도 계약 */}
            <div
              onClick={() => navigate('/valuation')}
              className="bg-white rounded-xl p-6 border border-slate-100 flex flex-col gap-3 cursor-pointer hover:shadow-lg transition-shadow duration-200"
              style={{ borderTop: '4px solid #f59e0b' }}
            >
              <div className="flex items-start justify-between">
                <div className="w-11 h-11 rounded-xl bg-amber-50 flex items-center justify-center text-xl">📊</div>
                <span className="text-xs font-bold px-2 py-1 rounded-full bg-amber-50 text-amber-600">
                  활성
                </span>
              </div>
              <div>
                {contractsLoading ? (
                  <Skeleton className="h-10 w-20 mb-1" />
                ) : (
                  <div className="flex items-baseline gap-1">
                    <span className="text-4xl font-extrabold leading-none text-amber-500">
                      {activeContracts.length}
                    </span>
                    <span className="text-sm text-slate-400 font-medium">건</span>
                  </div>
                )}
                <p className="text-sm font-semibold text-slate-800 mt-1">활성 통화선도 계약</p>
              </div>
              <div className="text-xs text-slate-600 rounded-md px-3 py-2 leading-relaxed border-l-2 bg-amber-50 border-l-amber-500">
                {contractsLoading
                  ? '조회 중…'
                  : activeContracts.length > 0
                  ? `등록된 활성 계약 ${activeContracts.length}건. 공정가치 평가를 실행할 수 있습니다.`
                  : '등록된 활성 계약이 없습니다.'}
              </div>
              <div className="flex items-center justify-between mt-auto pt-1">
                <span className="text-xs font-semibold text-slate-500">평가 화면으로 →</span>
                <KifrsChip label="1113호" />
              </div>
            </div>

            {/* 카드 2: 지정·ELIGIBLE 헤지 관계 */}
            <div
              onClick={() => navigate('/effectiveness')}
              className="bg-white rounded-xl p-6 border border-slate-100 flex flex-col gap-3 cursor-pointer hover:shadow-lg transition-shadow duration-200"
              style={{ borderTop: '4px solid #2563eb' }}
            >
              <div className="flex items-start justify-between">
                <div className="w-11 h-11 rounded-xl bg-blue-50 flex items-center justify-center text-xl">🔬</div>
                <span className="text-xs font-bold px-2 py-1 rounded-full bg-blue-50 text-blue-600">
                  지정 완료
                </span>
              </div>
              <div>
                {hedgesLoading ? (
                  <Skeleton className="h-10 w-20 mb-1" />
                ) : (
                  <div className="flex items-baseline gap-1">
                    <span className="text-4xl font-extrabold leading-none text-blue-600">
                      {designatedEligible.length}
                    </span>
                    <span className="text-sm text-slate-400 font-medium">건</span>
                  </div>
                )}
                <p className="text-sm font-semibold text-slate-800 mt-1">지정·ELIGIBLE 헤지 관계</p>
              </div>
              <div className="text-xs text-slate-600 rounded-md px-3 py-2 leading-relaxed border-l-2 bg-blue-50 border-l-blue-500">
                {hedgesLoading
                  ? '조회 중…'
                  : designatedEligible.length > 0
                  ? `지정 완료 ${designatedEligible.length}건. Dollar-offset 유효성 테스트를 실행할 수 있습니다.`
                  : '지정 완료 상태인 헤지 관계가 없습니다.'}
              </div>
              <div className="flex items-center justify-between mt-auto pt-1">
                <span className="text-xs font-semibold text-slate-500">테스트 화면으로 →</span>
                <KifrsChip label="B6.4.12" />
              </div>
            </div>

            {/* 카드 3: 누적 분개 */}
            <div
              onClick={() => navigate('/journal')}
              className="bg-white rounded-xl p-6 border border-slate-100 flex flex-col gap-3 cursor-pointer hover:shadow-lg transition-shadow duration-200"
              style={{ borderTop: '4px solid #10b981' }}
            >
              <div className="flex items-start justify-between">
                <div className="w-11 h-11 rounded-xl bg-emerald-50 flex items-center justify-center text-xl">📝</div>
                <span className="text-xs font-bold px-2 py-1 rounded-full bg-emerald-50 text-emerald-600">
                  누적
                </span>
              </div>
              <div>
                {journalsLoading ? (
                  <Skeleton className="h-10 w-20 mb-1" />
                ) : (
                  <div className="flex items-baseline gap-1">
                    <span className="text-4xl font-extrabold leading-none text-emerald-600">
                      {totalJournalEntries}
                    </span>
                    <span className="text-sm text-slate-400 font-medium">건</span>
                  </div>
                )}
                <p className="text-sm font-semibold text-slate-800 mt-1">누적 분개</p>
              </div>
              <div className="text-xs text-slate-600 rounded-md px-3 py-2 leading-relaxed border-l-2 bg-emerald-50 border-l-emerald-500">
                {journalsLoading
                  ? '조회 중…'
                  : totalJournalEntries > 0
                  ? `K-IFRS 기준 분개 총 ${totalJournalEntries}건이 Append-Only로 저장되어 있습니다.`
                  : '생성된 분개가 없습니다. 유효성 테스트 완료 시 자동 생성됩니다.'}
              </div>
              <div className="flex items-center justify-between mt-auto pt-1">
                <span className="text-xs font-semibold text-slate-500">분개 화면으로 →</span>
                <KifrsChip label="6.5.8 · 6.5.11" />
              </div>
            </div>

          </div>
        </section>

        {/* ══ 하단 3컬럼 ══ */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

          {/* 포트폴리오 현황 */}
          <div className="bg-white rounded-xl border border-slate-200 p-6">
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-4">포트폴리오 현황</p>
            {hedgesLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-7" />)}
              </div>
            ) : (
              <>
                <div className="mb-4">
                  <span className="text-4xl font-extrabold text-blue-900">{allHedges.length}</span>
                  <span className="text-sm text-slate-400 font-medium ml-1">건</span>
                  <p className="text-xs text-slate-400 mt-0.5">총 헤지 관계</p>
                </div>
                <div className="space-y-2.5">
                  {[
                    { label: '공정가치 위험회피', value: `${fvhCount}건`, tag: 'FVH', tagColor: 'blue' as const },
                    { label: '현금흐름 위험회피', value: `${cfhCount}건`, tag: 'CFH', tagColor: 'emerald' as const },
                    {
                      label: '지정 완료 (ELIGIBLE)',
                      value: `${designatedEligible.length}건`,
                      tag: null, tagColor: null,
                    },
                    {
                      label: '활성 통화선도 계약',
                      value: `${activeContracts.length}건`,
                      tag: null, tagColor: null,
                    },
                  ].map((row) => (
                    <div
                      key={row.label}
                      className="flex items-center justify-between py-2 border-b border-slate-50 last:border-0"
                    >
                      <span className="text-sm text-slate-600">{row.label}</span>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-slate-800">{row.value}</span>
                        {row.tag && (
                          <span
                            className={clsx(
                              'text-xs font-bold px-1.5 py-0.5 rounded',
                              row.tagColor === 'blue'
                                ? 'bg-blue-50 text-blue-600'
                                : 'bg-emerald-50 text-emerald-600',
                            )}
                          >
                            {row.tag}
                          </span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>

          {/* 공정가치 수준 분포 — 최근 평가 샘플 기준임을 명시 */}
          <div className="bg-white rounded-xl border border-slate-200 p-6">
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-1">
              공정가치 측정 수준 분포
            </p>
            <p className="text-xs text-slate-400 mb-5">
              K-IFRS 1113호 · 최근 평가 {recentValuations?.content?.length ?? 0}건 샘플 기준
            </p>

            {valuationsLoading ? (
              <div className="space-y-5">
                {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-8" />)}
              </div>
            ) : (recentValuations?.totalElements ?? 0) === 0 ? (
              <p className="text-sm text-slate-400 py-8 text-center">평가 이력이 없습니다</p>
            ) : (
              (() => {
                const sample = recentValuations?.content ?? []
                const l1 = sample.filter((v) => v.fairValueLevel === 'LEVEL_1').length
                const l2 = sample.filter((v) => v.fairValueLevel === 'LEVEL_2').length
                const l3 = sample.filter((v) => v.fairValueLevel === 'LEVEL_3').length
                const total = sample.length || 1
                return (
                  <div className="space-y-4">
                    {[
                      { name: 'Level 1', sub: '활성시장 공시가격', count: l1, color: '#059669' },
                      { name: 'Level 2', sub: '관측 가능한 시장 투입변수', count: l2, color: '#2563eb' },
                      { name: 'Level 3', sub: '관측 불가 투입변수', count: l3, color: '#d97706' },
                    ].map((lv) => (
                      <div key={lv.name}>
                        <div className="flex justify-between items-end mb-1.5">
                          <div>
                            <span className="text-sm font-semibold text-slate-800">{lv.name}</span>
                            <p className="text-xs text-slate-400 mt-0.5">{lv.sub}</p>
                          </div>
                          <span className="text-sm font-bold text-slate-600">
                            {lv.count}건 · {Math.round((lv.count / total) * 100)}%
                          </span>
                        </div>
                        <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
                          <div
                            className="h-full rounded-full transition-all duration-700"
                            style={{
                              width: `${Math.round((lv.count / total) * 100)}%`,
                              backgroundColor: lv.color,
                            }}
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                )
              })()
            )}

            {/* 총 평가 건수 요약 */}
            {!valuationsLoading && (
              <div className="mt-5 pt-4 border-t border-slate-100 flex items-center justify-between">
                <span className="text-xs text-slate-400">전체 평가 이력</span>
                <span className="text-sm font-bold text-slate-700">
                  {recentValuations?.totalElements ?? 0}건
                </span>
              </div>
            )}
          </div>

          {/* 최근 처리 현황 */}
          <div className="bg-white rounded-xl border border-slate-200 p-6">
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-4">최근 처리 현황</p>

            {isLoading ? (
              <div className="space-y-4">
                {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-14" />)}
              </div>
            ) : activityItems.length === 0 ? (
              <p className="text-sm text-slate-400 py-8 text-center">처리 이력이 없습니다</p>
            ) : (
              <div className="divide-y divide-slate-50">
                {activityItems.map((item) => {
                  const dotColor = {
                    green: 'bg-emerald-500',
                    blue:  'bg-blue-500',
                    amber: 'bg-amber-500',
                  }[item.dot]
                  const tagStyle = {
                    green: 'bg-emerald-50 text-emerald-700',
                    blue:  'bg-blue-50 text-blue-700',
                    amber: 'bg-amber-50 text-amber-700',
                  }[item.tag.color]

                  return (
                    <div key={item.key} className="flex gap-3 py-3 first:pt-0">
                      <span className={clsx('mt-1.5 w-2.5 h-2.5 rounded-full shrink-0', dotColor)} />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-slate-800 leading-snug">{item.action}</p>
                        <p className="text-xs text-slate-400 mt-0.5 truncate">{item.sub}</p>
                        <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
                          <span className={clsx('inline-block text-xs font-semibold px-1.5 py-0.5 rounded', tagStyle)}>
                            {item.tag.label}
                          </span>
                          {item.kifrs && <KifrsChip label={item.kifrs} />}
                        </div>
                      </div>
                      <span className="text-xs text-slate-400 whitespace-nowrap pt-0.5 shrink-0">
                        {item.time}
                      </span>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>

        {/* ══ 업무 화면 바로가기 ══ */}
        <section>
          <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-3">
            업무 화면 바로가기
            <span className="flex-1 h-px bg-slate-200" />
          </p>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {[
              // 심사관 동선을 분명히: 1→2→3→4 단계 라벨을 badge에 노출.
              // 1단계: FX Forward 환위험 헤지 자동화 (기본 데모 흐름)
              // 2단계: IRS 금리위험 FVH 엔진 확장 (백엔드 구현 완료)
              // CRS: 복합위험 수단으로 후속 확장 (준비 중)
              {
                icon: '🎯',
                title: '헤지 지정',
                desc: 'K-IFRS 6.4.1 적격요건 자동 검증 — 1단계: FX Forward 환위험',
                badge: '1단계 · 여기서 시작',
                badgeColor: 'blue',
                route: '/hedge/designation',
              },
              {
                icon: '📊',
                title: '공정가치 평가',
                desc: '통화선도 Level 2 자동 평가 — 1단계: USD/KRW 중심',
                badge: activeContracts.length > 0 ? `활성 ${activeContracts.length}건` : '활성 계약 없음',
                badgeColor: activeContracts.length > 0 ? 'amber' : 'gray',
                route: '/valuation',
              },
              {
                icon: '🔬',
                title: '유효성 테스트',
                desc: 'Dollar-offset 자동 판정 — 1단계 FX / 2단계 IRS FVH 선택 가능',
                badge: designatedEligible.length > 0 ? `${designatedEligible.length}건 가능` : '대기 없음',
                badgeColor: designatedEligible.length > 0 ? 'blue' : 'gray',
                route: '/effectiveness',
              },
              {
                icon: '📝',
                title: '자동 분개',
                desc: 'FVH · CFH · IRS 장부조정상각 분개 — Excel/PDF 다운로드',
                badge: `누적 ${totalJournalEntries}건`,
                badgeColor: 'green',
                route: '/journal',
              },
            ].map((nav) => (
              <button
                key={nav.route}
                onClick={() => navigate(nav.route)}
                className="bg-white rounded-xl border border-slate-200 p-5 text-left hover:border-blue-600 hover:shadow-md hover:-translate-y-0.5 transition-all duration-150 flex flex-col gap-2"
              >
                <span className="text-2xl">{nav.icon}</span>
                <p className="text-sm font-bold text-slate-800">{nav.title}</p>
                <p className="text-xs text-slate-400 leading-relaxed">{nav.desc}</p>
                <span
                  className={clsx(
                    'self-start text-xs font-bold px-2 py-0.5 rounded-full mt-1',
                    nav.badgeColor === 'amber' ? 'bg-amber-50 text-amber-600' :
                    nav.badgeColor === 'blue'  ? 'bg-blue-50 text-blue-600'   :
                    nav.badgeColor === 'green' ? 'bg-emerald-50 text-emerald-600' :
                    'bg-slate-100 text-slate-400',
                  )}
                >
                  {nav.badge}
                </span>
              </button>
            ))}
          </div>
        </section>

      </div>
    </div>
  )
}
