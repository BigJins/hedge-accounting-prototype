/**
 * 유효성 테스트 API 함수.
 *
 * TanStack Query 훅은 features/effectiveness/api/useEffectivenessTest.ts에 위치합니다.
 * 이 파일은 axios 기반 원시 API 호출 함수를 제공합니다.
 *
 * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 평가)
 * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 비효과성 P&L)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 헤지 OCI/P&L 분리)
 */

import { apiClient } from './client'
import type {
  EffectivenessTestRequest,
  EffectivenessTestResponse,
} from '@/types/effectiveness'
import type { PageResponse } from '@/types/common'

/**
 * 유효성 테스트 실행.
 *
 * POST /api/v1/effectiveness-tests
 *
 * @param request 유효성 테스트 요청 (위험회피관계 ID, 평가기준일, 당기 변동액)
 * @returns 유효성 테스트 결과 (PASS/WARNING/FAIL, 유효/비유효 부분)
 * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 평가)
 */
export async function runEffectivenessTest(
  request: EffectivenessTestRequest,
): Promise<EffectivenessTestResponse> {
  const response = await apiClient.post<EffectivenessTestResponse>(
    '/v1/effectiveness-tests',
    request,
  )
  return response.data
}

/**
 * 위험회피관계별 유효성 테스트 이력 조회 (페이징).
 *
 * GET /api/v1/effectiveness-tests?hedgeRelationshipId=xxx&page=0&size=10
 *
 * @param hedgeRelationshipId 위험회피관계 ID
 * @param page 페이지 번호 (0-based)
 * @param size 페이지 크기
 * @returns 유효성 테스트 이력 페이지
 * @see K-IFRS 1107호 (헤지회계 공시 — 유효성 테스트 이력)
 */
export async function getEffectivenessTestList(
  hedgeRelationshipId: string,
  page = 0,
  size = 10,
): Promise<PageResponse<EffectivenessTestResponse>> {
  const response = await apiClient.get<PageResponse<EffectivenessTestResponse>>(
    `/v1/effectiveness-tests?hedgeRelationshipId=${encodeURIComponent(hedgeRelationshipId)}&page=${page}&size=${size}`,
  )
  return response.data
}

/**
 * 유효성 테스트 단건 조회.
 *
 * GET /api/v1/effectiveness-tests/{id}
 *
 * @param id 유효성 테스트 ID
 * @returns 유효성 테스트 결과
 */
export async function getEffectivenessTestById(
  id: number,
): Promise<EffectivenessTestResponse> {
  const response = await apiClient.get<EffectivenessTestResponse>(
    `/v1/effectiveness-tests/${id}`,
  )
  return response.data
}