import { useState } from 'react'
import { Link } from 'react-router-dom'
import { HedgeRelationshipSelector } from '@/features/hedge/components/HedgeRelationshipSelector'
import { JournalEntryHistory } from '@/features/journal/components/JournalEntryHistory'
import { IrsAmortizationCard } from '@/features/journal/components/IrsAmortizationCard'
import type { HedgeRelationshipSummary } from '@/types/hedge'

const HEDGE_TYPE_LABEL: Record<HedgeRelationshipSummary['hedgeType'], string> = {
  FAIR_VALUE: '공정가치 위험회피 (FVH)',
  CASH_FLOW:  '현금흐름 위험회피 (CFH)',
}

const STATUS_LABEL: Record<HedgeRelationshipSummary['status'], string> = {
  DESIGNATED:   '지정',
  REBALANCED:   '재조정',
  DISCONTINUED: '중단',
  MATURED:      '만기',
}

export default function JournalEntryPage() {
  const [selectedRelationshipId, setSelectedRelationshipId] = useState<string>('')
  const [selectedRelationship, setSelectedRelationship] = useState<HedgeRelationshipSummary | null>(null)

  const isFiltered = Boolean(selectedRelationshipId)

  return (
    <div className="min-h-full bg-slate-50">
      {/* ─── 페이지 헤더 ────────────────────────────────────────────────── */}
      <header className="bg-white border-b border-slate-200 px-8 py-5">
        <div className="flex items-start justify-between gap-6 flex-wrap">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <h2 className="text-xl font-bold text-slate-900">자동 분개</h2>
              <span className="text-xs font-semibold text-blue-700 bg-blue-50 border border-blue-200 px-2 py-0.5 rounded-full font-mono">
                K-IFRS 1109 6.5.8 / 6.5.11 / 6.5.12
              </span>
            </div>
            <p className="text-sm text-slate-500">
              평가와 유효성 테스트 결과를 바탕으로 생성된 분개를 확인합니다.
            </p>
          </div>

          {/*
            단계 순서를 사이드바 네비·업무 흐름과 일치시킨다:
            1 지정 → 2 평가 → 3 테스트 → 4 분개. (기존에는 1/2 가 뒤바뀌어 있었음.)
          */}
          <div className="hidden md:flex items-center gap-1 text-xs text-slate-400">
            {[
              { step: 1, label: '지정' },
              { step: 2, label: '평가' },
              { step: 3, label: '테스트' },
              { step: 4, label: '분개' },
            ].map((item, index) => (
              <div key={item.step} className="flex items-center gap-1">
                <div
                  className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                    item.step === 4
                      ? 'bg-blue-700 text-white'
                      : 'bg-slate-200 text-slate-500'
                  }`}
                >
                  {item.step}
                </div>
                <span className={item.step === 4 ? 'text-blue-700 font-medium' : ''}>
                  {item.label}
                </span>
                {index < 3 && <span className="mx-1">→</span>}
              </div>
            ))}
          </div>
        </div>
      </header>

      <div className="max-w-screen-xl mx-auto px-8 py-7 space-y-7">

        {/*
          안내 배너.
          "자동 분개는 유효성 테스트 PASS 결과를 입력으로 만들어진다"는 체인을
          배너 첫 줄에 명시한다. 아래 기존 FAIL 안내 (JournalEntryHistory 의 EmptyState)
          와 톤을 맞춰, 심사관이 평가 직후 분개가 없는 상황에서도 여기서 이유를 바로
          읽을 수 있게 한다.
        */}
        <div className="bg-blue-50 border border-blue-200 rounded-xl px-5 py-4 flex items-start gap-4">
          <span className="text-lg mt-0.5 shrink-0">ⓘ</span>
          <div className="space-y-2">
            <p className="text-sm font-semibold text-blue-900">자동 분개 확인 화면</p>
            <p className="text-xs text-blue-800">
              이 목록은 공정가치 평가 → 유효성 테스트 PASS 결과를 바탕으로 시스템이
              자동으로 생성한 분개입니다. 테스트가 WARNING·FAIL 이면 분개는 만들어지지 않으며,
              K-IFRS 1109호 6.5.5 / 6.5.6 에 따라 재조정·중단 경로로 처리됩니다.
            </p>
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-blue-700 pt-1">
              <span><strong>6.5.8</strong> 공정가치위험회피 손익 인식</span>
              <span><strong>6.5.9</strong> IRS FVH 장부조정상각 (2단계)</span>
              <span><strong>6.5.11</strong> 현금흐름위험회피 OCI / P&amp;L 분리</span>
              <span><strong>6.5.12</strong> 비유효부분 당기손익 인식</span>
            </div>
            {/* 단계 범위 안내 */}
            <div className="flex flex-wrap gap-2 pt-1 border-t border-blue-100">
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold bg-blue-100 text-blue-800 px-2.5 py-1 rounded-full">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 flex-shrink-0" />
                1단계: FX Forward 환위험 헤지 자동화
              </span>
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold bg-blue-100 text-blue-800 px-2.5 py-1 rounded-full">
                <span className="w-1.5 h-1.5 rounded-full bg-blue-500 flex-shrink-0" />
                2단계: IRS 금리위험 FVH 엔진 확장
              </span>
              <span className="inline-flex items-center gap-1.5 text-xs font-medium bg-slate-100 text-slate-500 px-2.5 py-1 rounded-full">
                <span className="w-1.5 h-1.5 rounded-full bg-slate-400 flex-shrink-0" />
                CRS: 복합위험 수단으로 후속 확장 (준비 중)
              </span>
            </div>
          </div>
        </div>

        {/* ─── 헤지 관계 선택 필터 ────────────────────────────────────────── */}
        <section className="bg-white rounded-xl border border-slate-200 p-5">
          <div className="flex items-center justify-between gap-4 mb-4">
            {/* 전체 보기 / 관계별 보기 토글 */}
            <div className="flex items-center gap-1 rounded-lg bg-slate-100 p-1">
              <button
                type="button"
                onClick={() => {
                  setSelectedRelationshipId('')
                  setSelectedRelationship(null)
                }}
                className={`rounded-md px-3 py-1.5 text-xs font-semibold transition-colors ${
                  !isFiltered
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                전체 보기
              </button>
              <span
                className={`rounded-md px-3 py-1.5 text-xs font-semibold ${
                  isFiltered
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-500'
                }`}
                aria-live="polite"
              >
                관계별 보기
                {isFiltered && (
                  <span className="ml-1.5 inline-flex items-center justify-center w-4 h-4 rounded-full bg-blue-700 text-white text-[10px] font-bold">
                    1
                  </span>
                )}
              </span>
            </div>

            {/* 빠른 이동 링크 */}
            <div className="flex gap-2 flex-wrap">
              <Link
                to="/valuation"
                className="inline-flex items-center rounded-lg bg-blue-700 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-800 transition-colors"
              >
                공정가치 평가
              </Link>
              <Link
                to="/effectiveness"
                className="inline-flex items-center rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
              >
                유효성 테스트
              </Link>
            </div>
          </div>

          {/* 헤지 관계 셀렉터 */}
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">
              헤지 관계 선택
              <span className="ml-1.5 text-slate-400 font-normal">
                — 선택하지 않으면 전체 분개를 표시합니다
              </span>
            </label>
            <HedgeRelationshipSelector
              value={selectedRelationshipId}
              onChange={setSelectedRelationshipId}
              onRelationshipChange={(rel) => setSelectedRelationship(rel)}
            />
          </div>

          {/* 관계별 보기 컨텍스트 요약 */}
          {isFiltered && selectedRelationship && (
            <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 rounded-md bg-slate-50 border border-slate-200 px-4 py-2.5 text-xs text-slate-600">
              <span className="font-semibold text-slate-800">관계별 보기 중</span>
              <span className="text-slate-300" aria-hidden="true">|</span>
              <span>
                유형:{' '}
                <strong className="text-slate-800">
                  {HEDGE_TYPE_LABEL[selectedRelationship.hedgeType]}
                </strong>
              </span>
              <span className="text-slate-300" aria-hidden="true">|</span>
              <span>
                상태:{' '}
                <strong className="text-slate-800">
                  {STATUS_LABEL[selectedRelationship.status]}
                </strong>
              </span>
              {selectedRelationship.fxForwardContractId && (
                <>
                  <span className="text-slate-300" aria-hidden="true">|</span>
                  <span>
                    연결 계약:{' '}
                    <span className="font-mono text-slate-700">
                      {selectedRelationship.fxForwardContractId}
                    </span>
                  </span>
                </>
              )}
              <button
                type="button"
                onClick={() => {
                  setSelectedRelationshipId('')
                  setSelectedRelationship(null)
                }}
                className="ml-auto text-xs text-slate-400 hover:text-slate-600 underline"
              >
                전체 보기로 돌아가기
              </button>
            </div>
          )}

          {/* 전체 보기 안내 */}
          {!isFiltered && (
            <p className="mt-3 text-xs text-slate-400">
              헤지 관계를 선택하면 해당 관계에 속한 분개만 표시됩니다.
              Excel / PDF 다운로드도 선택된 관계 기준으로 실행됩니다.
            </p>
          )}
        </section>

        {/* ─── 확인 포인트 (전체 보기일 때만 노출) ──────────────────────── */}
        {!isFiltered && (
          <section className="bg-white rounded-xl border border-slate-200 px-6 py-5">
            <div className="grid gap-3 md:grid-cols-3">
              <div className="rounded-lg bg-slate-50 border border-slate-200 px-4 py-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">확인 포인트 1</p>
                <p className="mt-1 text-sm text-slate-700">FVH는 손익(P/L), CFH는 OCI/P&amp;L 분리가 맞는지 확인합니다.</p>
              </div>
              <div className="rounded-lg bg-slate-50 border border-slate-200 px-4 py-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">확인 포인트 2</p>
                <p className="mt-1 text-sm text-slate-700">OCI는 파란 태그, 손익 계정은 빨간 태그로 빠르게 구분할 수 있습니다.</p>
              </div>
              <div className="rounded-lg bg-slate-50 border border-slate-200 px-4 py-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">확인 포인트 3</p>
                <p className="mt-1 text-sm text-slate-700">원화 금액은 소수점 없이 표시해 분개 금액을 읽기 쉽게 정리했습니다.</p>
              </div>
            </div>
          </section>
        )}

        {/* ─── IRS 상각 분개 생성 (접이식 카드) ─────────────────────────── */}
        <IrsAmortizationCard />

        {/* ─── 분개 이력 ──────────────────────────────────────────────────── */}
        <JournalEntryHistory
          hedgeRelationshipId={isFiltered ? selectedRelationshipId : null}
        />
      </div>
    </div>
  )
}
