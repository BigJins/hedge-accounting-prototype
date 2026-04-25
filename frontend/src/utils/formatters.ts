/**
 * 금융 데이터 포맷 유틸리티.
 * 금융권 표준: 천단위 콤마, 통화기호, 등폭 숫자
 */

/** 원화 금액 포맷 (₩1,234,567) */
export function formatKrw(value: number, abbreviated = false): string {
  if (abbreviated) {
    const abs = Math.abs(value)
    const sign = value < 0 ? '-' : ''
    if (abs >= 1_0000_0000) {
      return `${sign}${(abs / 1_0000_0000).toFixed(1)}억원`
    }
    if (abs >= 10_000) {
      return `${sign}${(abs / 10_000).toFixed(0)}만원`
    }
  }
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency: 'KRW',
    maximumFractionDigits: 0,
  }).format(value)
}

/** 환율 포맷 (1,380.00) */
export function formatRate(value: number, decimals = 2): string {
  return new Intl.NumberFormat('ko-KR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value)
}

/** 이자율 포맷 (3.50%) */
export function formatPercent(value: number): string {
  return `${(value * 100).toFixed(2)}%`
}

/** USD 금액 포맷 ($10,000,000) */
export function formatUsd(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(value)
}

/** 날짜 포맷 (2024-03-31 → 2024.03.31) */
export function formatDate(dateStr: string): string {
  return dateStr.replace(/-/g, '.')
}

/** 공정가치 변동 부호 판단 */
export function isProfit(value: number): boolean {
  return value > 0
}

