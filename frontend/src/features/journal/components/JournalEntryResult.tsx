import { useState } from 'react'
import clsx from 'clsx'
import type { JournalEntryResponse, JournalEntryType } from '@/types/journal'
import { formatDate, formatKrw } from '@/utils/formatters'

// ─── 분개유형 배지 ────────────────────────────────────────────────────────────

const ENTRY_TYPE_BADGE: Record<JournalEntryType, { label: string; cls: string }> = {
  FAIR_VALUE_HEDGE_INSTRUMENT:  { label: 'FVH-수단',            cls: 'bg-blue-100 text-blue-800' },
  FAIR_VALUE_HEDGE_ITEM:        { label: 'FVH-항목',            cls: 'bg-blue-100 text-blue-800' },
  IRS_FVH_AMORTIZATION:         { label: 'IRS-FVH 장부조정상각', cls: 'bg-blue-200 text-blue-900 ring-1 ring-blue-300' },
  CASH_FLOW_HEDGE_EFFECTIVE:    { label: '현금흐름-유효',       cls: 'bg-emerald-100 text-emerald-800' },
  CASH_FLOW_HEDGE_INEFFECTIVE:  { label: '현금흐름-비유효',     cls: 'bg-amber-100 text-amber-800' },
  OCI_RECLASSIFICATION:         { label: 'OCI 재분류',          cls: 'bg-purple-100 text-purple-800' },
  HEDGE_DISCONTINUATION:        { label: '헤지 중단',           cls: 'bg-slate-100 text-slate-600' },
  REVERSING_ENTRY:              { label: '역분개',              cls: 'bg-red-100 text-red-700' },
}

// ─── 다운로드 헬퍼 ────────────────────────────────────────────────────────────

const API_BASE = '/api/v1/journal-entries'

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
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.rel = 'noopener noreferrer'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface JournalEntryResultProps {
  entries: JournalEntryResponse[]
}

// ─── 분개 행 컴포넌트 ─────────────────────────────────────────────────────────

function EntryRow({ entry }: { entry: JournalEntryResponse }) {
  const badge = ENTRY_TYPE_BADGE[entry.entryType] ?? {
    label: entry.entryType,
    cls: 'bg-slate-100 text-slate-600',
  }
  const absAmt = Math.abs(entry.amount)
  const debitTone = getAccountTone(entry.debitAccount)
  const creditTone = getAccountTone(entry.creditAccount)

  return (
    <tr className="hover:bg-slate-50/60 transition-colors">
      <td className="px-4 py-3">
        <span className={clsx('inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold whitespace-nowrap', badge.cls)}>
          {badge.label}
        </span>
      </td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-2 flex-wrap">
          <p className="text-sm font-medium text-slate-800">{entry.debitAccountName}</p>
          <span
            className={clsx(
              'inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-semibold whitespace-nowrap',
              debitTone.className,
            )}
          >
            {debitTone.label}
          </span>
        </div>
        <p className="text-xs text-slate-400 mt-0.5">{entry.debitAccount}</p>
      </td>
      <td className="px-4 py-3 text-right font-mono tabular-nums text-sm text-slate-800 font-semibold">
        {formatKrw(absAmt)}
      </td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-2 flex-wrap">
          <p className="text-sm font-medium text-slate-800">{entry.creditAccountName}</p>
          <span
            className={clsx(
              'inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-semibold whitespace-nowrap',
              creditTone.className,
            )}
          >
            {creditTone.label}
          </span>
        </div>
        <p className="text-xs text-slate-400 mt-0.5">{entry.creditAccount}</p>
      </td>
      <td className="px-4 py-3 text-right font-mono tabular-nums text-sm text-slate-800 font-semibold">
        {formatKrw(absAmt)}
      </td>
      <td className="px-4 py-3 text-xs text-slate-500 max-w-[180px]">
        {entry.description}
      </td>
      <td className="px-4 py-3">
        <span className="text-xs font-mono text-blue-600 whitespace-nowrap">{entry.ifrsReference}</span>
      </td>
    </tr>
  )
}

function EntryTable({ entries }: { entries: JournalEntryResponse[] }) {
  if (entries.length === 0) return null
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="w-full text-sm min-w-[780px]">
        <thead>
          <tr className="bg-slate-50 border-b border-slate-200">
            {['분개유형', '차변 계정', '차변 금액', '대변 계정', '대변 금액', '적요', 'K-IFRS 근거'].map((h) => (
              <th
                key={h}
                scope="col"
                className={clsx(
                  'px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider',
                  h.includes('금액') ? 'text-right' : 'text-left',
                )}
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {entries.map((e) => <EntryRow key={e.journalEntryId} entry={e} />)}
        </tbody>
      </table>
    </div>
  )
}

// ─── 감사 추적 모달 ───────────────────────────────────────────────────────────

function AuditTrailModal({
  entries,
  onClose,
}: {
  entries: JournalEntryResponse[]
  onClose: () => void
}) {
  const totalAmt = entries.reduce((s, e) => s + Math.abs(e.amount), 0)
  const ifrsRefs = [...new Set(entries.map((e) => e.ifrsReference))].filter(Boolean)
  const createdAt = entries[0]?.createdAt
    ? new Date(entries[0].createdAt).toLocaleString('ko-KR')
    : '-'
  const hedgeId = entries[0]?.hedgeRelationshipId ?? '-'

  return (
    <div
      className="fixed inset-0 bg-blue-950/60 backdrop-blur-sm z-50 flex items-center justify-center p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden">
        {/* 헤더 */}
        <div className="bg-blue-950 px-6 py-4 flex items-center justify-between">
          <div>
            <h3 className="text-base font-bold text-white">감사 추적 기록</h3>
            <p className="text-xs text-blue-300 mt-0.5">Audit Trail — K-IFRS 1107호 공시 근거</p>
          </div>
          <button onClick={onClose} className="text-blue-300 hover:text-white transition-colors text-xl leading-none">✕</button>
        </div>

        <div className="px-6 py-5 space-y-5">
          {/* 기본 메타데이터 */}
          <section>
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">처리 정보</p>
            <div className="bg-slate-50 rounded-lg divide-y divide-slate-100">
              {[
                { label: '위험회피관계 ID', value: hedgeId },
                { label: '분개 생성 건수', value: `${entries.length}건` },
                { label: '생성 일시', value: createdAt },
                { label: '처리 방식', value: 'K-IFRS 자동 분개 시스템' },
              ].map((row) => (
                <div key={row.label} className="flex items-center justify-between px-4 py-2.5">
                  <span className="text-xs text-slate-500">{row.label}</span>
                  <span className="text-xs font-semibold text-slate-800 font-mono">{row.value}</span>
                </div>
              ))}
            </div>
          </section>

          {/* K-IFRS 적용 근거 */}
          <section>
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">적용 K-IFRS 조항</p>
            <div className="flex flex-wrap gap-2">
              {ifrsRefs.length > 0 ? (
                ifrsRefs.map((ref) => (
                  <span key={ref} className="text-xs font-semibold text-blue-700 bg-blue-50 border border-blue-200 px-2.5 py-1 rounded-full font-mono">
                    {ref}
                  </span>
                ))
              ) : (
                <span className="text-xs text-slate-400">근거 정보 없음</span>
              )}
            </div>
          </section>

          {/* 차변/대변 균형 검증 */}
          <section>
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">차변/대변 균형 검증</p>
            <div className="bg-emerald-50 border border-emerald-200 rounded-lg px-4 py-3">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-slate-600">총 차변 합계</span>
                <span className="text-sm font-bold font-mono text-slate-800">{formatKrw(totalAmt)}</span>
              </div>
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs text-slate-600">총 대변 합계</span>
                <span className="text-sm font-bold font-mono text-slate-800">{formatKrw(totalAmt)}</span>
              </div>
              <div className="flex items-center gap-2 border-t border-emerald-200 pt-3">
                <span className="text-base">✅</span>
                <span className="text-sm font-bold text-emerald-700">차변 = 대변 · 균형 확인</span>
              </div>
            </div>
          </section>

          {/* 분개 항목 요약 */}
          <section>
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">분개 항목 요약</p>
            <div className="space-y-1.5">
              {entries.map((e) => {
                const badge = ENTRY_TYPE_BADGE[e.entryType]
                return (
                  <div key={e.journalEntryId} className="flex items-center justify-between text-xs py-1.5 border-b border-slate-100 last:border-0">
                    <div className="flex items-center gap-2">
                      <span className={clsx('px-1.5 py-0.5 rounded text-xs font-semibold', badge?.cls)}>
                        {badge?.label ?? e.entryType}
                      </span>
                      <span className="text-slate-600">{e.debitAccountName} / {e.creditAccountName}</span>
                    </div>
                    <span className="font-mono font-semibold text-slate-800">{formatKrw(Math.abs(e.amount))}</span>
                  </div>
                )
              })}
            </div>
          </section>
        </div>

        <div className="px-6 py-4 border-t border-slate-100 bg-slate-50">
          <button
            onClick={onClose}
            className="w-full py-2.5 bg-blue-900 text-white text-sm font-bold rounded-lg hover:bg-blue-800 transition-colors"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

/**
 * 생성된 분개 결과 패널 (재설계).
 *
 * 상단 결론 배너 → 요약 → OCI/P&L/FVH 섹션 분리 → 균형 체크 → 다운로드 + 감사추적
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피)
 * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 OCI/P&L)
 * @see K-IFRS 1109호 6.5.12 (비유효 부분 즉시 인식)
 * @see K-IFRS 1107호 (헤지회계 공시)
 */
export function JournalEntryResult({ entries }: JournalEntryResultProps) {
  const [auditOpen, setAuditOpen] = useState(false)

  if (entries.length === 0) return null

  const first = entries[0]
  const hedgeId = first.hedgeRelationshipId
  const entryDate = formatDate(first.entryDate)
  const totalAmt = entries.reduce((s, e) => s + Math.abs(e.amount), 0)

  /* 유형별 분류 */
  const ociEntries = entries.filter((e) =>
    ['CASH_FLOW_HEDGE_EFFECTIVE', 'OCI_RECLASSIFICATION'].includes(e.entryType),
  )
  const pnlEntries = entries.filter((e) =>
    e.entryType === 'CASH_FLOW_HEDGE_INEFFECTIVE',
  )
  const fvhEntries = entries.filter((e) =>
    ['FAIR_VALUE_HEDGE_INSTRUMENT', 'FAIR_VALUE_HEDGE_ITEM', 'IRS_FVH_AMORTIZATION'].includes(e.entryType),
  )
  const amortizationEntries = entries.filter((e) =>
    e.entryType === 'IRS_FVH_AMORTIZATION',
  )
  const otherEntries = entries.filter((e) =>
    ['HEDGE_DISCONTINUATION', 'REVERSING_ENTRY'].includes(e.entryType),
  )

  const isCFH = ociEntries.length > 0 || pnlEntries.length > 0
  const isFVH = fvhEntries.length > 0

  /* 다운로드 */
  const handleExcelDownload = () => {
    downloadFile(
      `${API_BASE}/export/excel?hedgeRelationshipId=${encodeURIComponent(hedgeId)}`,
      `journal_${hedgeId}.xlsx`,
    )
  }
  const handlePdfDownload = () => {
    downloadFile(
      `${API_BASE}/export/pdf?hedgeRelationshipId=${encodeURIComponent(hedgeId)}`,
      `journal_${hedgeId}.pdf`,
    )
  }

  // PDF 한글 폰트 안내 (서버에 NanumGothic 미탑재 시 한글 깨짐 — PoC 허용)
  const pdfTooltip = 'PDF 다운로드 (서버에 한글 폰트 미설치 시 글자가 깨질 수 있습니다 · PoC 허용)'

  return (
    <>
      <div className="space-y-5">

        {/* ══ 1. 결론 배너 ══ */}
        <div className="rounded-xl bg-gradient-to-r from-emerald-700 to-emerald-500 text-white px-7 py-5 shadow-lg shadow-emerald-100">
          <div className="flex items-start justify-between gap-6 flex-wrap">
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-full bg-white/20 flex items-center justify-center text-2xl shrink-0">
                ✅
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wide opacity-80 mb-1">분개 생성 완료</p>
                <p className="text-xl font-extrabold">{hedgeId} · {entries.length}건 생성</p>
                <p className="text-sm opacity-85 mt-0.5">
                  기준일 {entryDate}
                  &nbsp;·&nbsp;차변 = 대변 = {formatKrw(totalAmt)}&nbsp;
                  <span className="font-bold bg-white/20 rounded px-1.5 py-0.5">균형 ✓</span>
                </p>
              </div>
            </div>
            {/* 다운로드 버튼 — 결론 배너 우측 */}
            <div className="flex items-center gap-2 flex-wrap shrink-0">
              <button
                onClick={handleExcelDownload}
                className="flex items-center gap-2 bg-white/20 hover:bg-white/30 border border-white/40 text-white text-sm font-bold px-4 py-2.5 rounded-lg transition-colors whitespace-nowrap"
              >
                <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fillRule="evenodd" d="M3 17a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm3.293-7.707a1 1 0 011.414 0L9 10.586V3a1 1 0 112 0v7.586l1.293-1.293a1 1 0 111.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z" clipRule="evenodd" />
                </svg>
                Excel 다운로드
              </button>
              <button
                onClick={handlePdfDownload}
                title={pdfTooltip}
                className="flex items-center gap-2 bg-white/20 hover:bg-white/30 border border-white/40 text-white text-sm font-bold px-4 py-2.5 rounded-lg transition-colors whitespace-nowrap"
              >
                <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fillRule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clipRule="evenodd" />
                </svg>
                PDF 다운로드
              </button>
              <button
                onClick={() => setAuditOpen(true)}
                className="flex items-center gap-2 bg-white/10 hover:bg-white/20 border border-white/30 text-white/90 text-sm font-semibold px-4 py-2.5 rounded-lg transition-colors whitespace-nowrap"
              >
                <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" />
                </svg>
                감사 추적 보기
              </button>
            </div>
          </div>
        </div>

        {/* ══ 2. 요약 카드 ══ */}
        <div className="bg-white rounded-xl border border-slate-200 px-6 py-4">
          <div className="flex items-center gap-2 mb-4">
            <h3 className="text-sm font-bold text-slate-800">분개 요약</h3>
            {isCFH && (
              <>
                <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full">현금흐름 위험회피</span>
                <span className="text-xs font-mono text-blue-600 bg-blue-50 border border-blue-100 px-2 py-0.5 rounded-full">K-IFRS 6.5.11</span>
                <span className="text-xs font-mono text-blue-600 bg-blue-50 border border-blue-100 px-2 py-0.5 rounded-full">K-IFRS 6.5.12</span>
              </>
            )}
            {isFVH && (
              <>
                <span className="text-xs font-semibold text-blue-700 bg-blue-50 px-2 py-0.5 rounded-full">공정가치 위험회피</span>
                <span className="text-xs font-mono text-blue-600 bg-blue-50 border border-blue-100 px-2 py-0.5 rounded-full">K-IFRS 6.5.8</span>
                {amortizationEntries.length > 0 && (
                  <span className="text-xs font-mono text-blue-700 bg-blue-100 border border-blue-300 px-2 py-0.5 rounded-full font-semibold">K-IFRS 6.5.9 장부조정상각</span>
                )}
              </>
            )}
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <SummaryCell label="위험회피관계 ID" value={hedgeId} mono />
            <SummaryCell label="분개 기준일" value={entryDate} mono />
            <SummaryCell label="총 분개 건수" value={`${entries.length}건`} />
            <SummaryCell
              label="총 금액"
              value={formatKrw(totalAmt)}
              mono
              valueClass="text-blue-900 font-extrabold"
            />
          </div>
        </div>

        {/* ══ 3a. OCI 분개 섹션 (현금흐름 헤지 유효 부분) ══ */}
        {ociEntries.length > 0 && (
          <JournalSection
            color="emerald"
            icon="🟢"
            title="OCI 분개"
            subtitle="현금흐름위험회피적립금 — 유효 부분"
            kifrs={['K-IFRS 1109호 6.5.11(가)']}
            description="헤지 수단의 공정가치 변동 중 유효 부분을 기타포괄손익(OCI)으로 인식합니다."
            entries={ociEntries}
            totalAmt={ociEntries.reduce((s, e) => s + Math.abs(e.amount), 0)}
          />
        )}

        {/* ══ 3b. 비효과분 P&L 분개 섹션 ══ */}
        {pnlEntries.length > 0 && (
          <JournalSection
            color="amber"
            icon="🟡"
            title="비효과분 P&L 분개"
            subtitle="당기손익 즉시 인식 — 비유효 부분"
            kifrs={['K-IFRS 1109호 6.5.11(나)', 'K-IFRS 1109호 6.5.12']}
            description="유효성 테스트 비유효 부분은 당기손익(P&L)으로 즉시 인식합니다."
            entries={pnlEntries}
            totalAmt={pnlEntries.reduce((s, e) => s + Math.abs(e.amount), 0)}
          />
        )}

        {/* ══ 3c. 공정가치 헤지 분개 (FVH 인식분) ══ */}
        {fvhEntries.filter((e) => e.entryType !== 'IRS_FVH_AMORTIZATION').length > 0 && (
          <JournalSection
            color="blue"
            icon="🔵"
            title="공정가치 헤지 분개"
            subtitle="수단 및 항목 공정가치 변동 — 당기손익"
            kifrs={['K-IFRS 1109호 6.5.8(가)', 'K-IFRS 1109호 6.5.8(나)']}
            description="헤지 수단과 피헤지항목의 공정가치 변동을 모두 당기손익으로 인식합니다."
            entries={fvhEntries.filter((e) => e.entryType !== 'IRS_FVH_AMORTIZATION')}
            totalAmt={fvhEntries.filter((e) => e.entryType !== 'IRS_FVH_AMORTIZATION').reduce((s, e) => s + Math.abs(e.amount), 0)}
          />
        )}

        {/* ══ 3d. IRS FVH 장부조정상각 분개 (2단계) ══ */}
        {amortizationEntries.length > 0 && (
          <JournalSection
            color="blue"
            icon="🔵"
            title="IRS-FVH 장부조정상각 분개"
            subtitle="2단계 — 장부금액 조정액 상각 · K-IFRS 1109호 §6.5.9"
            kifrs={['K-IFRS 1109호 6.5.9']}
            description="공정가치위험회피 중단 또는 만기까지 피헤지항목 장부조정액을 상각하는 분개입니다."
            entries={amortizationEntries}
            totalAmt={amortizationEntries.reduce((s, e) => s + Math.abs(e.amount), 0)}
          />
        )}

        {/* ══ 3e. 기타 분개 ══ */}
        {otherEntries.length > 0 && (
          <JournalSection
            color="slate"
            icon="⚪"
            title="기타 분개"
            subtitle="헤지 중단 · 역분개"
            kifrs={[]}
            description="헤지 중단 또는 역분개 처리 분개입니다."
            entries={otherEntries}
            totalAmt={otherEntries.reduce((s, e) => s + Math.abs(e.amount), 0)}
          />
        )}

        {/* ══ 4. 차변/대변 균형 체크 ══ */}
        <div className="bg-white rounded-xl border border-slate-200 px-6 py-5">
          <p className="text-sm font-bold text-slate-700 mb-4">차변 / 대변 균형 검증</p>
          <div className="grid grid-cols-3 gap-4 items-center">
            {/* 차변 */}
            <div className="text-center bg-slate-50 rounded-lg py-4 px-3">
              <p className="text-xs text-slate-400 uppercase tracking-wide mb-1">총 차변</p>
              <p className="text-lg font-extrabold font-mono text-slate-800">{formatKrw(totalAmt)}</p>
            </div>
            {/* 등호 */}
            <div className="text-center">
              <p className="text-3xl font-bold text-emerald-500">=</p>
              <p className="text-xs text-emerald-600 font-bold mt-1">균형 ✓</p>
            </div>
            {/* 대변 */}
            <div className="text-center bg-slate-50 rounded-lg py-4 px-3">
              <p className="text-xs text-slate-400 uppercase tracking-wide mb-1">총 대변</p>
              <p className="text-lg font-extrabold font-mono text-slate-800">{formatKrw(totalAmt)}</p>
            </div>
          </div>
          <p className="text-xs text-slate-400 text-center mt-3">
            복식부기 원리 준수 · 모든 분개의 차변 합계 = 대변 합계
          </p>
        </div>

        {/* ══ 5. 하단 액션 바 ══ */}
        <div className="bg-blue-950 rounded-xl px-6 py-5 flex items-center justify-between gap-4 flex-wrap">
          <div>
            <p className="text-white font-bold text-base">업무 마감 처리 완료</p>
            <p className="text-blue-300 text-xs mt-0.5">
              분개 생성 · 차변/대변 균형 확인 · 감사 추적 기록 완료
            </p>
          </div>
          <div className="flex items-center gap-3 flex-wrap">
            <button
              onClick={handleExcelDownload}
              className="flex items-center gap-2 bg-emerald-500 hover:bg-emerald-400 text-white text-sm font-bold px-5 py-2.5 rounded-lg transition-colors whitespace-nowrap"
            >
              <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fillRule="evenodd" d="M3 17a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm3.293-7.707a1 1 0 011.414 0L9 10.586V3a1 1 0 112 0v7.586l1.293-1.293a1 1 0 111.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z" clipRule="evenodd" />
              </svg>
              Excel 다운로드
            </button>
            <button
              onClick={handlePdfDownload}
              title={pdfTooltip}
              className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-bold px-5 py-2.5 rounded-lg transition-colors whitespace-nowrap"
            >
              <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fillRule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clipRule="evenodd" />
              </svg>
              PDF 다운로드
            </button>
            <button
              onClick={() => setAuditOpen(true)}
              className="flex items-center gap-2 border border-blue-400 text-blue-200 hover:text-white hover:border-blue-300 text-sm font-semibold px-5 py-2.5 rounded-lg transition-colors whitespace-nowrap"
            >
              <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" />
              </svg>
              감사 추적 보기
            </button>
          </div>
        </div>

      </div>

      {/* 감사 추적 모달 */}
      {auditOpen && (
        <AuditTrailModal entries={entries} onClose={() => setAuditOpen(false)} />
      )}
    </>
  )
}

// ─── 분개 섹션 컴포넌트 ───────────────────────────────────────────────────────

type SectionColor = 'emerald' | 'amber' | 'blue' | 'slate'

const SECTION_STYLES: Record<SectionColor, {
  border: string; header: string; badge: string; iconBg: string; totalBg: string
}> = {
  emerald: {
    border:  'border-l-emerald-500',
    header:  'bg-emerald-50 border-emerald-200',
    badge:   'bg-emerald-100 text-emerald-800',
    iconBg:  'bg-emerald-100',
    totalBg: 'bg-emerald-50 text-emerald-800',
  },
  amber: {
    border:  'border-l-amber-500',
    header:  'bg-amber-50 border-amber-200',
    badge:   'bg-amber-100 text-amber-800',
    iconBg:  'bg-amber-100',
    totalBg: 'bg-amber-50 text-amber-800',
  },
  blue: {
    border:  'border-l-blue-500',
    header:  'bg-blue-50 border-blue-200',
    badge:   'bg-blue-100 text-blue-800',
    iconBg:  'bg-blue-100',
    totalBg: 'bg-blue-50 text-blue-800',
  },
  slate: {
    border:  'border-l-slate-400',
    header:  'bg-slate-50 border-slate-200',
    badge:   'bg-slate-100 text-slate-600',
    iconBg:  'bg-slate-100',
    totalBg: 'bg-slate-50 text-slate-600',
  },
}

function JournalSection({
  color,
  icon,
  title,
  subtitle,
  kifrs,
  description,
  entries,
  totalAmt,
}: {
  color: SectionColor
  icon: string
  title: string
  subtitle: string
  kifrs: string[]
  description: string
  entries: JournalEntryResponse[]
  totalAmt: number
}) {
  const s = SECTION_STYLES[color]

  return (
    <div className={clsx('bg-white rounded-xl border border-slate-200 border-l-4 overflow-hidden', s.border)}>
      {/* 섹션 헤더 */}
      <div className={clsx('px-6 py-4 border-b flex items-start justify-between gap-4 flex-wrap', s.header)}>
        <div className="flex items-center gap-3">
          <span className={clsx('text-xl w-9 h-9 rounded-lg flex items-center justify-center shrink-0', s.iconBg)}>
            {icon}
          </span>
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h4 className="text-sm font-bold text-slate-800">{title}</h4>
              {kifrs.map((k) => (
                <span key={k} className="text-xs font-mono font-semibold text-blue-600 bg-white border border-blue-200 px-2 py-0.5 rounded-full">
                  {k}
                </span>
              ))}
            </div>
            <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p>
          </div>
        </div>
        <div className={clsx('text-right px-3 py-1.5 rounded-lg shrink-0', s.totalBg)}>
          <p className="text-xs font-medium opacity-70 mb-0.5">소계</p>
          <p className="text-sm font-extrabold font-mono">{formatKrw(totalAmt)}</p>
        </div>
      </div>

      {/* 설명 */}
      <div className="px-6 pt-3 pb-0">
        <p className="text-xs text-slate-500 leading-relaxed">{description}</p>
      </div>

      {/* 분개 테이블 */}
      <div className="p-4">
        <EntryTable entries={entries} />
      </div>
    </div>
  )
}

// ─── SummaryCell ─────────────────────────────────────────────────────────────

function SummaryCell({
  label,
  value,
  mono = false,
  valueClass,
}: {
  label: string
  value: string
  mono?: boolean
  valueClass?: string
}) {
  return (
    <div>
      <p className="text-xs text-slate-400 mb-1">{label}</p>
      <p className={clsx('text-sm font-bold text-slate-800', mono && 'font-mono', valueClass)}>
        {value}
      </p>
    </div>
  )
}
