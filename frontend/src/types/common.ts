/**
 * Spring Data 페이지 응답 공통 타입
 *
 * @remarks
 * Spring Boot Page<T> 응답 구조와 1:1 대응합니다.
 * number 필드는 0-based 현재 페이지 번호입니다 (UI 표시 시 +1 필요).
 */
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  /** 현재 페이지 (0-based) */
  number: number
  first: boolean
  last: boolean
  empty: boolean
}
