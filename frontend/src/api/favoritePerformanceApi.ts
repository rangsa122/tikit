
import {apiClient} from './apiClient'

interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
}

export interface FavoritePerformance {
  id: number
  title: string
  venue: string
  period: string
  posterUrl?: string | null
}

/**
 * 내 관심 공연 목록 조회
 * GET /api/v1/me/favorites
 *
 * 백엔드 GET API 구현 후 실제 연동 가능
 * 응답 DTO가 확정되면 타입을 맞춰 수정해야 함
 */
export async function getFavoritePerformances(): Promise<FavoritePerformance[]> {
  const response = await apiClient.get<ApiResponse<FavoritePerformance[]>>(
    '/api/v1/me/favorites',
  )

  if (response.data.code !== 'SUCCESS') {
    throw new Error(
      response.data.message || '관심 공연 목록을 불러오지 못했습니다.',
    )
  }

  if (!Array.isArray(response.data.data)) {
    throw new Error('관심 공연 목록 응답 형식이 올바르지 않습니다.')
  }

  return response.data.data
}
