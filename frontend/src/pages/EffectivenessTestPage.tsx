import { useState } from 'react'
import { Link } from 'react-router-dom'
import clsx from 'clsx'
import PageLayout from '@/components/layout/PageLayout'
import { EffectivenessTestForm } from '@/features/effectiveness/components/EffectivenessTestForm'
import { EffectivenessTestResult } from '@/features/effectiveness/components/EffectivenessTestResult'
import { EffectivenessTestHistory } from '@/features/effectiveness/components/EffectivenessTestHistory'
import type { EffectivenessTestResponse } from '@/types/effectiveness'

// ─── 배지 매핑 (페이지 상단 배너용) ──────────────────────────────────────────

const TOP_BANNER: Record<
  string,
  {
    label: string
    sub: string
    containerClass: string
    labelClass: string
    subClass: string
    dotClass: string
  }
> = {
  PASS: {
    label: 'PASS — 헤지관계 유효',
    sub: '위험회피 유효성 테스트를 통과하였습니다. 헤지관계를 계속 유지하세요.',
    containerClass: 'bg-emerald-50 border-emerald-200',
    labelClass: 'text-emerald-800',
    subClass: 'text-emerald-600',
    dotClass: 'bg-emerald-500',
  },
  WARNING: {
    label: 'WARNING — 재조정 필요',
    sub: 'Dollar-offset 비율이 참고 범위를 벗어났습니다. 이를 절대 기준으로 보지 말고 헤지비율 재조정을 함께 검토하세요.',
    containerClass: 'bg-amber-50 border-amber-200',
    labelClass: 'text-amber-800',
    subClass: 'text-amber-600',
    dotClass: 'bg-amber-500',
  },
  FAIL: {
    label: 'FAIL — 헤지관계 중단 필요',
    sub: '경제적 관계가 성립하지 않습니다. 위험회피관계를 전진적으로 중단하세요.',
    containerClass: 'bg-red-50 border-red-200',
    labelClass: 'text-red-800',
    subClass: 'text-red-600',
    dotClass: 'bg-red-500',
  },
}

// ─── 단계 표시 (진행 흐름 안내) ──────────────────────────────────────────────

type Step = 'input' | 'result'

function StepIndicator({ current }: { current: Step }) {
  const steps: { id: Step; label: string }[] = [
    { id: 'input',  label: '1. 테스트 입력' },
    { id: 'result', label: '2. 결과 확인'  },
  ]

  return (
    <div className="flex items-center gap-3 mb-6" role="navigation" aria-label="유효성 테스트 진행 단계">
      {steps.map((step, index) => {
        const isActive    = step.id === current
        const isPastInput = current === 'result' && step.id === 'input'
        return (
          <div key={step.id} className="flex items-center gap-2">
            {index > 0 && (
              <div
                className={clsx(
                  'w-8 h-0.5 rounded',
                  isPastInput || isActive ? 'bg-blue-400' : 'bg-slate-200',
                )}
                aria-hidden="true"
              />
            )}
            <div className="flex items-center gap-1.5">
              <span
                className={clsx(
                  'flex items-center justify-center w-5 h-5 rounded-full text-xs font-bold flex-shrink-0',
                  isActive    && 'bg-blue-600 text-white',
                  isPastInput && 'bg-emerald-500 text-white',
                  !isActive && !isPastInput && 'bg-slate-200 text-slate-500',
                )}
                aria-current={isActive ? 'step' : undefined}
              >
                {isPastInput ? (
                  <svg className="w-3 h-3" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                ) : (
                  index + 1
                )}
              </span>
              <span
                className={clsx(
                  'text-xs font-medium',
                  isActive    && 'text-blue-700',
                  isPastInput && 'text-emerald-600',
                  !isActive && !isPastInput && 'text-slate-400',
                )}
              >
                {step.label}
              </span>
            </div>
          </div>
        )
      })}
    </div>
  )
}

// ─── 상단 결과 배너 (결론 우선) ───────────────────────────────────────────────

function ResultBanner({ result }: { result: EffectivenessTestResponse }) {
  const banner = TOP_BANNER[result.testResult]
  if (!banner) return null

  const ratioAbs     = Math.abs(result.effectivenessRatio)
  const displayRatio = (ratioAbs * 100).toFixed(1)
  const isOpposite   = result.effectivenessRatio < 0

  return (
    <div
      className={clsx(
        'rounded-xl border-2 px-6 py-5 flex items-start gap-5',
        banner.containerClass,
      )}
      role="status"
      aria-live="polite"
      aria-label={`유효성 테스트 결과: ${banner.label}`}
    >
      {/* 아이콘 */}
      <div
        className={clsx(
          'flex-shrink-0 w-12 h-12 rounded-full flex items-center justify-center',
          result.testResult === 'PASS'    && 'bg-emerald-100',
          result.testResult === 'WARNING' && 'bg-amber-100',
          result.testResult === 'FAIL'    && 'bg-red-100',
        )}
        aria-hidden="true"
      >
        {result.testResult === 'PASS' && (
          <svg className="w-7 h-7 text-emerald-600" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
          </svg>
        )}
        {result.testResult === 'WARNING' && (
          <svg className="w-7 h-7 text-amber-600" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
        )}
        {result.testResult === 'FAIL' && (
          <svg className="w-7 h-7 text-red-600" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
          </svg>
        )}
      </div>

      {/* 텍스트 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-3 flex-wrap">
          <h3 className={clsx('text-lg font-bold', banner.labelClass)}>
            {banner.label}
          </h3>
          <div className="flex items-center gap-2">
            <span
              className={clsx(
                'font-financial text-2xl font-bold tabular-nums',
                result.testResult === 'PASS'    && 'text-emerald-700',
                result.testResult === 'WARNING' && 'text-amber-700',
                result.testResult === 'FAIL'    && 'text-red-700',
              )}
            >
              {displayRatio}%
            </span>
            <span className={clsx('text-xs font-medium', banner.subClass)}>
              {isOpposite ? '반대방향' : '동방향'}
            </span>
          </div>
        </div>
        <p className={clsx('text-sm mt-1', banner.subClass)}>
          {banner.sub}
        </p>
        <div className="flex items-center gap-4 mt-2 text-xs flex-wrap">
          <span className={clsx('font-medium', banner.subClass)}>
            헤지관계: {result.hedgeRelationshipId}
          </span>
          <span className={clsx(banner.subClass, 'opacity-70')}>
            평가기준일: {result.testDate.replace(/-/g, '.')}
          </span>
          <span className={clsx(banner.subClass, 'opacity-70')}>
            {result.hedgeType === 'CASH_FLOW' ? '현금흐름 위험회피' : '공정가치 위험회피'}
          </span>
        </div>
      </div>
    </div>
  )
}

// ─── 페이지 ───────────────────────────────────────────────────────────────────

/**
 * 유효성 테스트 페이지 — DEMO_SCENARIO 화면 3
 *
 * 흐름:
 * 1. "시연 데이터 채우기" → 폼 자동 채우기
 * 2. [유효성 테스트 실행] 클릭 → POST /api/v1/effectiveness-tests
 * 3. 상단 배너에 PASS/WARNING/FAIL 결론 표시 (결론 우선)
 * 4. 결과 패널 — Dollar-offset 게이지 + 조치 권고 + 금액 상세
 * 5. 이력 테이블 — 행 클릭 시 상세 / "이 값으로 재테스트"
 *
 * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 평가)
 * @see K-IFRS 1109호 6.5.5  (재조정)
 * @see K-IFRS 1109호 6.5.6  (위험회피관계 중단)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 헤지 OCI/P&L 분리)
 */
export default function EffectivenessTestPage() {
  /** 최신 테스트 결과 */
  const [testResult, setTestResult] = useState<EffectivenessTestResponse | null>(null)

  /**
   * 이력에 표시할 hedgeRelationshipId.
   * 폼 제출 성공 시 자동 업데이트.
   */
  const [activeHedgeRelationshipId, setActiveHedgeRelationshipId] = useState<string | null>(null)

  /** 폼 재마운트 키 — 재테스트 시 defaultValues 재적용 */
  const [formKey, setFormKey] = useState<string>('new')

  /** 재테스트 시 폼 초기값 */
  const [formInitialValues, setFormInitialValues] = useState<
    Partial<Parameters<typeof EffectivenessTestForm>[0]['initialValues'] & object> | undefined
  >(undefined)

  /** 현재 진행 단계 */
  const currentStep: Step = testResult ? 'result' : 'input'

  const handleSuccess = (result: EffectivenessTestResponse) => {
    setTestResult(result)
    setActiveHedgeRelationshipId(result.hedgeRelationshipId)
    // 결과 배너로 자동 스크롤
    setTimeout(() => {
      document.getElementById('effectiveness-result-banner')?.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
      })
    }, 100)
  }

  /** 이력에서 "이 값으로 재테스트" 클릭 */
  const handleRetest = (item: EffectivenessTestResponse) => {
    setTestResult(null)
    setFormKey(`retest-${item.effectivenessTestId}`)
    setFormInitialValues({
      hedgeRelationshipId: item.hedgeRelationshipId,
      testDate:            item.testDate,
      testType:            item.testType,
      hedgeType:           item.hedgeType,
      instrumentFvChange:  item.instrumentFvChange,
      hedgedItemPvChange:  item.hedgedItemPvChange,
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  /** 새 테스트 시작 */
  const handleNewTest = () => {
    setTestResult(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return (
    <PageLayout
      title="유효성 테스트"
      subtitle="K-IFRS 1109호 B6.4.12 — Dollar-offset 방법 위험회피 유효성 자동 평가"
      badge="K-IFRS 1109호"
    >
      {/* ── 표준 흐름 안내 배너 ─────────────────────────────────────── */}
      <div className="rounded-xl bg-blue-950 px-5 py-4 flex items-start gap-4 mb-2">
        <span className="text-lg mt-0.5 flex-shrink-0">📋</span>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-bold text-white mb-1">표준 유효성 테스트 흐름</p>
          <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-blue-300 mb-2">
            <span>
              <strong className="text-white">① 헤지관계 선택</strong> → 위험회피 유형 자동 맞춤
            </span>
            <span>
              <strong className="text-white">② 당기 변동액 입력</strong> → 헤지수단 FV · 피헤지항목 PV
            </span>
            <span>
              <strong className="text-white">③ 기간별(Periodic) 테스트 실행</strong> → PASS / WARNING / FAIL
            </span>
          </div>
          {/* 단계 범위 안내 */}
          <div className="flex flex-wrap gap-2 mt-2">
            <span className="inline-flex items-center gap-1.5 text-xs font-semibold bg-blue-800 text-white px-2.5 py-1 rounded-full">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 flex-shrink-0" />
              1단계: FX Forward 환위험 헤지 자동화
            </span>
            <span className="inline-flex items-center gap-1.5 text-xs font-semibold bg-blue-700 text-white px-2.5 py-1 rounded-full">
              <span className="w-1.5 h-1.5 rounded-full bg-blue-300 flex-shrink-0" />
              2단계: IRS 금리위험 FVH 엔진 확장
            </span>
            <span className="inline-flex items-center gap-1.5 text-xs font-medium bg-slate-700 text-slate-300 px-2.5 py-1 rounded-full">
              <span className="w-1.5 h-1.5 rounded-full bg-slate-500 flex-shrink-0" />
              CRS: 복합위험 수단으로 후속 확장 (준비 중)
            </span>
          </div>
          <p className="text-xs text-blue-400 mt-1.5">
            * <strong className="text-blue-200">IRS FVH</strong>는 고급 옵션 → 위험회피수단 유형에서 선택 가능합니다 (2단계).
            CRS는 분석 진행 중으로 비활성화됩니다. 누적(Cumulative)은 보조 분석 목적으로 고급 옵션에서만 제공합니다.
          </p>
        </div>
      </div>

      {/* ── 진행 단계 표시 ──────────────────────────────────────────── */}
      <StepIndicator current={currentStep} />

      {/* ── 상단 결과 배너 (결론 우선 — 결과 있을 때만) ────────────── */}
      {testResult && (
        <div id="effectiveness-result-banner">
          <ResultBanner result={testResult} />

          {/* 다음 단계 / 새 테스트 버튼 */}
          <div className="flex justify-end gap-3 mt-2 mb-2 flex-wrap">
            <Link
              to={`/journal?hedgeRelationshipId=${encodeURIComponent(testResult.hedgeRelationshipId)}`}
              className="inline-flex items-center rounded-lg bg-blue-700 px-4 py-2 text-xs font-semibold text-white hover:bg-blue-800 transition-colors"
            >
              자동 분개 확인 →
            </Link>
            <button
              type="button"
              onClick={handleNewTest}
              className="text-xs font-medium text-slate-600 hover:text-slate-800 underline transition-colors"
            >
              새 테스트 입력하기
            </button>
          </div>
        </div>
      )}

      {/* ── 유효성 테스트 폼 (결과가 없을 때 표시, 있으면 접힘) ────── */}
      {!testResult && (
        <EffectivenessTestForm
          key={formKey}
          onSuccess={handleSuccess}
          initialValues={formInitialValues}
        />
      )}

      {/* ── 결과 상세 패널 (제출 성공 후) ──────────────────────────── */}
      {testResult && (
        <EffectivenessTestResult result={testResult} />
      )}

      {/* ── 유효성 테스트 이력 ──────────────────────────────────────── */}
      <EffectivenessTestHistory
        hedgeRelationshipId={activeHedgeRelationshipId}
        onRetest={handleRetest}
      />

      {/* ── K-IFRS 근거 푸터 ────────────────────────────────────────── */}
      <div className="rounded-lg border border-slate-200 bg-slate-50 px-5 py-4">
        <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
          K-IFRS 근거
        </p>
        <div className="flex flex-wrap gap-2">
          {[
            { code: 'B6.4.12', desc: '매 보고기간 말 유효성 평가 의무' },
            { code: 'B6.4.13', desc: 'Dollar-offset 방법 허용' },
            { code: 'BC6.234', desc: '원칙 기반 평가 — 80%~125% 는 참고 지표' },
            { code: '6.5.5',  desc: '헤지비율 재조정 (Rebalancing)' },
            { code: '6.5.6',  desc: '위험회피관계 중단 조건' },
            { code: '6.5.8',  desc: '공정가치 헤지 비효과성 P&L' },
            { code: '6.5.11', desc: '현금흐름 헤지 OCI/P&L 분리' },
          ].map(({ code, desc }) => (
            <span
              key={code}
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200"
            >
              <span className="font-bold">K-IFRS 1109호 {code}</span>
              <span className="text-blue-400">— {desc}</span>
            </span>
          ))}
        </div>
      </div>
    </PageLayout>
  )
}
   
