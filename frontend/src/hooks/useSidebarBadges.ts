import { useFxForwardContractList } from '@/features/valuation/api/useFxForwardValuation'
import { useHedgeRelationshipList } from '@/features/hedge/api/hedgeApi'

/**
 * 사이드바 badge 계산 훅.
 *
 * 각 메뉴 항목에 표시할 실제 데이터 기반 badge 건수를 반환합니다.
 * 쿼리 실패 또는 데이터 없을 시 null 반환 (badge 미표시).
 *
 * 계산 기준:
 * - 공정가치 평가: status === 'ACTIVE' 인 계약 건수
 *   → 아직 만기되지 않아 평가가 필요한 통화선도 계약 수
 * - 유효성 테스트: status === 'DESIGNATED' 인 헤지관계 건수
 *   → 현재 지정 상태로 매 보고기간 말 유효성 평가가 필요한 헤지관계 수
 * - 자동 분개: badge 없음
 *   → "분개 대기" 개념이 API에 없으므로 미표시
 */
export function useSidebarBadges() {
  // ACTIVE 계약 — 페이지당 최대 100건 조회 후 클라이언트 필터
  const { data: contractsPage } = useFxForwardContractList(0, 100)

  // DESIGNATED 헤지관계 — size=1로 totalElements만 취득
  const { data: hedgesPage } = useHedgeRelationshipList({
    status: 'DESIGNATED',
    page: 0,
    size: 1,
  })

  const activeContracts =
    contractsPage?.content.filter((c) => c.status === 'ACTIVE').length ?? 0

  const designatedHedges = hedgesPage?.totalElements ?? 0

  return {
    /** 공정가치 평가 메뉴 badge */
    valuationBadge: activeContracts > 0 ? `${activeContracts}건` : null,
    /** 유효성 테스트 메뉴 badge */
    effectivenessBadge: designatedHedges > 0 ? `${designatedHedges}건` : null,
    /** 자동 분개 메뉴 badge — 항상 null (API에 "대기" 개념 없음) */
    journalBadge: null as null,
  }
}
