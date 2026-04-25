import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import type { EffectivenessTestRequest, EffectivenessTestResponse } from '@/types/effectiveness'
import type { PageResponse } from '@/types/common'

const QUERY_KEY = 'effectiveness-tests'

/**
 * 유효성 테스트 실행 뮤테이션.
 *
 * POST /api/v1/effectiveness-tests
 *
 * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 평가)
 */
export function useEffectivenessTestMutation() {
  const queryClient = useQueryClient()

  return useMutation<EffectivenessTestResponse, Error, EffectivenessTestRequest>({
    mutationFn: async (request) => {
      const response = await apiClient.post<EffectivenessTestResponse>(
        '/v1/effectiveness-tests',
        request,
      )
      return response.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [QUERY_KEY] })
    },
  })
}

/**
 * 위험회피관계별 유효성 테스트 이력 조회 (페이징).
 *
 * GET /api/v1/effectiveness-tests?hedgeRelationshipId=xxx&page=0&size=10
 *
 * @param hedgeRelationshipId 위험회피관계 ID — 없으면 쿼리 비활성화
 * @param page 페이지 번호 (0-based)
 * @param size 페이지 크기
 *
 * @see K-IFRS 1107호 (헤지회계 공시 — 유효성 테스트 이력)
 */
export function useEffectivenessTestList(
  hedgeRelationshipId: string | null | undefined,
  page = 0,
  size = 10,
) {
  return useQuery<PageResponse<EffectivenessTestResponse>, Error>({
    queryKey: [QUERY_KEY, 'list', hedgeRelationshipId, page, size],
    queryFn: async () => {
      const response = await apiClient.get<PageResponse<EffectivenessTestResponse>>(
        `/v1/effectiveness-tests?hedgeRelationshipId=${encodeURIComponent(hedgeRelationshipId ?? '')}&page=${page}&size=${size}`,
      )
      return response.data
    },
    enabled: !!hedgeRelationshipId,
    placeholderData: (prev) => prev,
  })
}
