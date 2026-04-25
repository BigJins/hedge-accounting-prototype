import axios from 'axios'

/** 백엔드 API 클라이언트 (localhost:8090) */
export const apiClient = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10_000,
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error)) {
      if (error.response?.status === 422) {
        return Promise.reject(error) // 원본 AxiosError 유지 (응답 본문 보존)
      }
      if (error.response?.data) {
        const data = error.response.data as { message?: string; errorCode?: string }
        return Promise.reject(new Error(data.message ?? '서버 오류가 발생했습니다.'))
      }
    }
    return Promise.reject(new Error('네트워크 오류가 발생했습니다.'))
  },
)
