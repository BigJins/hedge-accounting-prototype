import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import type { IrsAmortizationRequest, JournalEntryRequest, JournalEntryResponse } from '@/types/journal'
import type { PageResponse } from '@/types/common'

const QUERY_KEY = ['journal-entries'] as const
const DEFAULT_PAGE_SIZE = 200

/**
 * 분개 생성 뮤테이션.
 *
 * POST /api/v1/journal-entries -> 201 Created
 * 서버가 생성한 분개 목록(JournalEntryResponse[])을 반환합니다.
 *
 * @see K-IFRS 1109 6.5.8
 * @see K-IFRS 1109 6.5.11
 */
export function useJournalEntryMutation() {
  const queryClient = useQueryClient()

  return useMutation<JournalEntryResponse[], Error, JournalEntryRequest>({
    mutationFn: async (request) => {
      const response = await apiClient.post<JournalEntryResponse[]>(
        '/v1/journal-entries',
        request,
      )
      return response.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY })
    },
  })
}

/**
 * IRS FVH 장부조정상각 분개 생성 뮤테이션.
 *
 * POST /api/v1/journal-entries/irs-fvh-amortization → 201 Created
 * 서버가 생성한 상각 분개 목록(JournalEntryResponse[])을 반환합니다.
 *
 * @see K-IFRS 1109호 6.5.9 (공정가치위험회피 피헤지항목 장부금액 조정 상각)
 */
export function useIrsAmortizationMutation() {
  const queryClient = useQueryClient()

  return useMutation<JournalEntryResponse[], Error, IrsAmortizationRequest>({
    mutationFn: async (request) => {
      const response = await apiClient.post<JournalEntryResponse[]>(
        '/v1/journal-entries/irs-fvh-amortization',
        request,
      )
      return response.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY })
    },
  })
}

/**
 * 분개 이력 단일 페이지 조회.
 *
 * @see K-IFRS 1107
 */
export function useJournalEntryList(
  hedgeRelationshipId: string | null | undefined,
  page = 0,
  size = 10,
) {
  return useQuery<PageResponse<JournalEntryResponse>, Error>({
    queryKey: [...QUERY_KEY, 'list', hedgeRelationshipId ?? null, page, size],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (hedgeRelationshipId) {
        params.set('hedgeRelationshipId', hedgeRelationshipId)
      }
      params.set('page', String(page))
      params.set('size', String(size))

      const response = await apiClient.get<PageResponse<JournalEntryResponse>>(
        `/v1/journal-entries?${params.toString()}`,
      )
      return response.data
    },
    enabled: true,
    placeholderData: (previousData) => previousData,
  })
}

/**
 * 분개 이력 전체 조회.
 *
 * 기간 필터를 프런트에서 처리하기 위해 단일 대용량 페이지로 전체 목록을 가져옵니다.
 * DEFAULT_PAGE_SIZE(200)으로 충분하지 않으면 size 파라미터를 늘려 사용합니다.
 *
 * @see K-IFRS 1107
 */
export function useJournalEntryListAll(
  hedgeRelationshipId: string | null | undefined,
  size = DEFAULT_PAGE_SIZE,
) {
  return useQuery<JournalEntryResponse[], Error>({
    queryKey: [...QUERY_KEY, 'all', hedgeRelationshipId ?? null],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (hedgeRelationshipId) {
        params.set('hedgeRelationshipId', hedgeRelationshipId)
      }
      params.set('page', '0')
      params.set('size', String(size))

      const response = await apiClient.get<PageResponse<JournalEntryResponse>>(
        `/v1/journal-entries?${params.toString()}`,
      )
      return response.data.content
    },
    placeholderData: (previousData) => previousData,
  })
}
