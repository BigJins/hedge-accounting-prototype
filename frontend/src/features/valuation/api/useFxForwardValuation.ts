import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import type {
  FxForwardContractResponse,
  FxForwardValuationRequest,
  FxForwardValuationResponse,
  PageResponse,
} from '@/types/valuation'

const QUERY_KEY = 'fx-forward-valuations'
const CONTRACT_QUERY_KEY = 'fx-forward-contracts'

/** 공정가치 평가 실행 뮤테이션 */
export function useFxForwardValuationMutation() {
  const queryClient = useQueryClient()

  return useMutation<FxForwardValuationResponse, Error, FxForwardValuationRequest>({
    mutationFn: async (request) => {
      const response = await apiClient.post<FxForwardValuationResponse>(
        '/v1/valuations/fx-forward',
        request,
      )
      return response.data
    },
    onSuccess: () => {
      // 평가 이력 + 계약 목록 모두 갱신
      void queryClient.invalidateQueries({ queryKey: [QUERY_KEY] })
      void queryClient.invalidateQueries({ queryKey: [CONTRACT_QUERY_KEY] })
    },
  })
}

/**
 * 전체 평가 이력 조회 (페이징)
 *
 * @param page 페이지 번호 (0-based)
 * @param size 페이지 크기
 * @param enabled 쿼리 활성화 여부 (기본 true)
 */
export function useFxForwardValuationList(page = 0, size = 10, enabled = true) {
  return useQuery<PageResponse<FxForwardValuationResponse>, Error>({
    queryKey: [QUERY_KEY, 'list', page, size],
    queryFn: async () => {
      const response = await apiClient.get<PageResponse<FxForwardValuationResponse>>(
        `/v1/valuations/fx-forward?page=${page}&size=${size}`,
      )
      return response.data
    },
    enabled,
    placeholderData: (prev) => prev,
  })
}

/**
 * 계약별 평가 이력 조회 (페이징)
 *
 * @param contractId 계약 ID
 * @param page 페이지 번호 (0-based)
 * @param size 페이지 크기
 * @param enabled 쿼리 활성화 여부 (기본 true)
 */
export function useFxForwardValuationByContract(contractId: string, page = 0, size = 10, enabled = true) {
  return useQuery<PageResponse<FxForwardValuationResponse>, Error>({
    queryKey: [QUERY_KEY, 'contract', contractId, page, size],
    queryFn: async () => {
      const response = await apiClient.get<PageResponse<FxForwardValuationResponse>>(
        `/v1/valuations/fx-forward/contract/${contractId}?page=${page}&size=${size}`,
      )
      return response.data
    },
    enabled: enabled && !!contractId,
    placeholderData: (prev) => prev,
  })
}

/**
 * 전체 계약 목록 조회 (페이징)
 *
 * @param page 페이지 번호 (0-based)
 * @param size 페이지 크기
 */
export function useFxForwardContractList(page = 0, size = 10) {
  return useQuery<PageResponse<FxForwardContractResponse>, Error>({
    queryKey: [CONTRACT_QUERY_KEY, 'list', page, size],
    queryFn: async () => {
      const response = await apiClient.get<PageResponse<FxForwardContractResponse>>(
        `/v1/valuations/fx-forward/contracts?page=${page}&size=${size}`,
      )
      return response.data
    },
    placeholderData: (prev) => prev,
  })
}

/**
 * 전체 계약 목록 조회 — 폼 select/dropdown 전용 (페이지네이션 없음)
 *
 * 헤지 지정 폼의 통화선도 계약 선택 UI에서 사용합니다.
 * 한 번에 최대 100건을 조회합니다 (PoC 시 충분한 수량).
 *
 * @see HedgeDesignationForm — fxForwardContractId 선택 UI
 */
export function useFxForwardContractListAll() {
  return useQuery<FxForwardContractResponse[], Error>({
    queryKey: [CONTRACT_QUERY_KEY, 'all'],
    queryFn: async () => {
      const response = await apiClient.get<PageResponse<FxForwardContractResponse>>(
        '/v1/valuations/fx-forward/contracts?page=0&size=100',
      )
      return response.data.content
    },
    staleTime: 30_000, // 30초 캐시 — 자주 변경되지 않으므로 재조회 최소화
  })
}

/** 계약 삭제 */
export function useDeleteFxForwardContract() {
  const queryClient = useQueryClient()

  return useMutation<void, Error, string>({
    mutationFn: async (contractId) => {
      await apiClient.delete(`/v1/valuations/fx-forward/contracts/${contractId}`)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [CONTRACT_QUERY_KEY] })
    },
  })
}

/** 평가 삭제 */
export function useDeleteFxForwardValuation() {
  const queryClient = useQueryClient()

  return useMutation<void, Error, number>({
    mutationFn: async (id) => {
      await apiClient.delete(`/v1/valuations/fx-forward/${id}`)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [QUERY_KEY] })
    },
  })
}
