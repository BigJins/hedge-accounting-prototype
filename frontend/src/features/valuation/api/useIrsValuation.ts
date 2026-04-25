import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import type {
  IrsContractRequest,
  IrsContractResponse,
  IrsValuationRequest,
  IrsValuationResponse,
  PageResponse,
} from '@/types/valuation'

const CONTRACT_KEY = 'irs-contracts'
const VALUATION_KEY = 'irs-valuations'

// ─── 계약 훅 ──────────────────────────────────────────────────────────────────

/**
 * IRS 계약 등록 뮤테이션.
 * 동일 contractId 재제출 시 업데이트(upsert).
 *
 * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성 — 파생상품)
 */
export function useIrsContractMutation() {
  const queryClient = useQueryClient()

  return useMutation<IrsContractResponse, Error, IrsContractRequest>({
    mutationFn: async (request) => {
      const response = await apiClient.post<IrsContractResponse>('/irs/contracts', request)
      return response.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [CONTRACT_KEY] })
    },
  })
}

/**
 * IRS 계약 목록 조회 (페이징).
 *
 * @param page 페이지 번호 (0-based)
 * @param size 페이지 크기
 */
export function useIrsContractList(page = 0, size = 10) {
  return useQuery<PageResponse<IrsContractResponse>, Error>({
    queryKey: [CONTRACT_KEY, 'list', page, size],
    queryFn: async () => {
      const response = await apiClient.get<PageResponse<IrsContractResponse>>(
        `/irs/contracts?page=${page}&size=${size}`,
      )
      return response.data
    },
    placeholderData: (prev) => prev,
  })
}

/**
 * IRS 계약 전체 목록 조회 — 헤지 지정 폼 select 전용.
 */
export function useIrsContractListAll() {
  return useQuery<IrsContractResponse[], Error>({
    queryKey: [CONTRACT_KEY, 'all'],
    queryFn: async () => {
      const response = await apiClient.get<PageResponse<IrsContractResponse>>(
        '/irs/contracts?page=0&size=100',
      )
      return response.data.content
    },
    staleTime: 30_000,
  })
}

/** IRS 계약 삭제 */
export function useDeleteIrsContract() {
  const queryClient = useQueryClient()

  return useMutation<void, Error, string>({
    mutationFn: async (contractId) => {
      await apiClient.delete(`/irs/contracts/${contractId}`)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [CONTRACT_KEY] })
    },
  })
}

// ─── 평가 훅 ──────────────────────────────────────────────────────────────────

/**
 * IRS 공정가치 평가 실행 뮤테이션.
 *
 * @see K-IFRS 1113호 72~90항 (Level 2 관측가능 투입변수)
 * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 손익 인식)
 */
export function useIrsValuationMutation() {
  const queryClient = useQueryClient()

  return useMutation<IrsValuationResponse, Error, IrsValuationRequest>({
    mutationFn: async (request) => {
      const response = await apiClient.post<IrsValuationResponse>('/irs/valuate', request)
      return response.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [VALUATION_KEY] })
    },
  })
}

/**
 * 계약별 IRS 평가 이력 조회 (페이징).
 *
 * @param contractId 계약 ID
 * @param page       페이지 번호 (0-based)
 * @param size       페이지 크기
 * @param enabled    쿼리 활성화 여부
 */
export function useIrsValuationByContract(
  contractId: string | null,
  page = 0,
  size = 5,
  enabled = true,
) {
  return useQuery<PageResponse<IrsValuationResponse>, Error>({
    queryKey: [VALUATION_KEY, 'contract', contractId, page, size],
    queryFn: async () => {
      const response = await apiClient.get<PageResponse<IrsValuationResponse>>(
        `/irs/contracts/${contractId!}/valuations?page=${page}&size=${size}`,
      )
      return response.data
    },
    enabled: enabled && !!contractId,
    placeholderData: (prev) => prev,
  })
}
