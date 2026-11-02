
import {apiClient} from './apiClient'

interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
}

/**
 * 게시된 공연 목록 조회
 * GET /api/v1/performances
 *
 * 실제 응답 DTO 확정 후 반환 타입 구체화 예정
 */
export async function getPerformances(): Promise<unknown> {
  const response = await apiClient.get<ApiResponse<unknown>>('/api/v1/performances')

  if (response.data.code !== 'SUCCESS') {
    throw new Error(response.data.message || '공연 목록을 불러오지 못했습니다.')
  }

  return response.data.data
}

/**
 * 공연 상세 조회
 * GET /api/v1/performances/{performanceId}
 *
 * 예상 API 경로이며 백엔드 구현 확인 후 조정 예정
 */
export async function getPerformanceDetail(performanceId: string): Promise<unknown> {
  const response = await apiClient.get<ApiResponse<unknown>>(
    `/api/v1/performances/${encodeURIComponent(performanceId)}`,
  )

  if (response.data.code !== 'SUCCESS') {
    throw new Error(response.data.message || '공연 상세 정보를 불러오지 못했습니다.')
  }

  return response.data.data
}

/**
 * 공연별 회차 목록 조회
 * GET /api/v1/performances/{performanceId}/sessions
 *
 * 예상 API 경로이며 백엔드 구현 확인 후 조정 예정
 */
export async function getPerformanceSessions(performanceId: string): Promise<unknown> {
  const response = await apiClient.get<ApiResponse<unknown>>(
    `/api/v1/performances/${encodeURIComponent(performanceId)}/sessions`,
  )

  if (response.data.code !== 'SUCCESS') {
    throw new Error(response.data.message || '공연 회차 정보를 불러오지 못했습니다.')
  }

  return response.data.data
}
