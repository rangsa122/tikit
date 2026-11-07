
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

/**
 * 관심 공연 등록
 * PUT /api/v1/me/favorites/{performanceId}
 */
export async function addFavoritePerformance(
  performanceId: number,
): Promise<void> {
  const response = await apiClient.put<ApiResponse<null>>(
    `/api/v1/me/favorites/${performanceId}`,
  )

  if (response.data.code !== 'SUCCESS') {
    throw new Error(
      response.data.message || '관심 공연 등록에 실패했습니다.',
    )
  }
}

/**
 * 관심 공연 해제
 * DELETE /api/v1/me/favorites/{performanceId}
 *
 * 백엔드는 204 No Content를 반환하므로
 * 응답 본문을 검사하지 않습니다.
 */
export async function removeFavoritePerformance(
  performanceId: number,
): Promise<void> {
  await apiClient.delete(
    `/api/v1/me/favorites/${performanceId}`,
  )
}
