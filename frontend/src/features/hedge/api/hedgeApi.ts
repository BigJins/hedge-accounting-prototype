import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { apiClient } from '@/api/client'
import type {
  HedgeDesignationRequest,
  HedgeDesignationResponse,
  HedgeRelationshipSummary,
  PageResponse,
} from '@/types/hedge'

const QUERY_KEY = 'hedge-relationships'

// ─── 헤지 지정 뮤테이션 ──────────────────────────────────────────────────────

/**
 * 헤지 지정 + K-IFRS 적격요건 자동 검증 뮤테이션
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용조건)
 *
 * @remarks
 * 검증 통과 시 201 Created + HedgeDesignationResponse 반환.
 * 검증 실패 시 422 Unprocessable Entity + HedgeDesignationResponse 반환.
 * 422도 결과 객체를 완전히 채워서 반환하므로, onError 대신
 * onSuccess(422 처리) 또는 별도 422 인터셉트 로직을 사용합니다.
 */
export function useHedgeDesignationMutation() {
  const queryClient = useQueryClient()

  return useMutation<HedgeDesignationResponse, HedgeDesignationApiError, HedgeDesignationRequest>({
    mutationFn: async (request) => {
      try {
        const response = await apiClient.post<HedgeDesignationResponse>(
          '/v1/hedge-relationships',
          request,
        )
        return response.data
      } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 422) {
          // 422: 적격요건 미충족 — 검증 결과 객체를 그대로 반환 (에러 아님)
          // 상위에서 isEligibilityFail 플래그로 구분
          const data = err.response.data as HedgeDesignationResponse
          throw new HedgeDesignationApiError(data, 422)
        }
        throw err
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [QUERY_KEY] })
    },
  })
}

// ─── 단건 조회 ────────────────────────────────────────────────────────────────

/** 헤지관계 단건 조회 */
export function useHedgeRelationship(id: string | null) {
  return useQuery<HedgeDesignationResponse, Error>({
    queryKey: [QUERY_KEY, id],
    queryFn: async () => {
      const response = await apiClient.get<HedgeDesignationResponse>(
        `/v1/hedge-relationships/${id}`,
      )
      return response.data
    },
    enabled: !!id,
  })
}

// ─── 목록 조회 ────────────────────────────────────────────────────────────────

export interface HedgeRelationshipListParams {
  hedgeType?: string
  status?: string
  eligibilityStatus?: string
  page?: number
  size?: number
}

/**
 * 헤지관계 목록 조회 (페이징)
 *
 * @remarks
 * page/size 파라미터는 HedgeRelationshipListParams에 포함되어 전달됩니다.
 * placeholderData로 페이지 전환 시 깜박임을 방지합니다.
 */
export function useHedgeRelationshipList(params?: HedgeRelationshipListParams) {
  return useQuery<PageResponse<HedgeRelationshipSummary>, Error>({
    queryKey: [QUERY_KEY, 'list', params],
    queryFn: async () => {
      const response = await apiClient.get<PageResponse<HedgeRelationshipSummary>>(
        '/v1/hedge-relationships',
        { params },
      )
      return response.data
    },
    placeholderData: (prev) => prev,
  })
}

// ─── 422 에러 클래스 ─────────────────────────────────────────────────────────

/**
 * 헤지 적격요건 미충족 에러 — HTTP 422 응답을 래핑
 *
 * @remarks
 * EligibilityResultPanel에서 이 에러를 잡아 검증 결과를 상세 표시합니다.
 */
export class HedgeDesignationApiError extends Error {
  constructor(
    public readonly response: HedgeDesignationResponse,
    public readonly status: number,
  ) {
    super('K-IFRS 적격요건 미충족')
    this.name = 'HedgeDesignationApiError'
  }

  /** 적격요건 미충족 여부 */
  get isEligibilityFail(): boolean {
    return this.status === 422 && this.response.eligibilityStatus === 'INELIGIBLE'
  }
}
