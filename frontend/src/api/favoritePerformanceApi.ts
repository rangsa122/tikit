
import {apiClient} from './apiClient'

interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
}

export interface FavoritePerformance {
  performanceId: string
  title: string
  genreCode: string
  venueName: string
  posterUrl: string | null
  startDate: string
  endDate: string
  minPrice: number
  salesStatus: string
  nextSalesOpenAt: string | null
}

export interface FavoritePerformancePage {
  items: FavoritePerformance[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface FavoriteRegistrationResponse {
  performanceId: string
  favorited: boolean
}

/**
 * 내 관심 공연 목록 조회
 * GET /api/v1/me/favorites?page=0&size=20
 */
export async function getFavoritePerformances(
  page = 0,
  size = 20,
): Promise<FavoritePerformancePage> {
  const response = await apiClient.get<ApiResponse<FavoritePerformancePage>>(
    '/api/v1/me/favorites',
    {
      params: {page, size},
    },
  )

  if (response.data.code !== 'SUCCESS') {
    throw new Error(
      response.data.message || '관심 공연 목록을 불러오지 못했습니다.',
    )
  }

  const data = response.data.data

  if (
    !data ||
    !Array.isArray(data.items) ||
    typeof data.page !== 'number' ||
    typeof data.totalElements !== 'number' ||
    typeof data.totalPages !== 'number'
  ) {
    throw new Error('관심 공연 목록 응답 형식이 올바르지 않습니다.')
  }

  return data
}

/**
 * 관심 공연 등록
 * PUT /api/v1/me/favorites/{performanceId}
 */
export async function addFavoritePerformance(
  performanceId: number | string,
): Promise<void> {
  const response = await apiClient.put<ApiResponse<FavoriteRegistrationResponse>>(
    `/api/v1/me/favorites/${performanceId}`,
  )

  if (
    response.data.code !== 'SUCCESS' ||
    response.data.data?.favorited !== true
  ) {
    throw new Error(
      response.data.message || '관심 공연 등록에 실패했습니다.',
    )
  }
}

/**
 * 관심 공연 해제
 * DELETE /api/v1/me/favorites/{performanceId}
 */
export async function removeFavoritePerformance(
  performanceId: number | string,
): Promise<void> {
  await apiClient.delete(
    `/api/v1/me/favorites/${performanceId}`,
  )
}
