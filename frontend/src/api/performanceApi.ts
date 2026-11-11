
import {apiClient} from './apiClient'

interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
}

export interface PublicPerformanceDetail {
  performanceId: string
  title: string
  genreCode: string
  venueName: string
  posterUrl: string | null
  startDate: string
  endDate: string
  minPrice: number | null
  salesStatus: string
  nextSalesOpenAt: string | null
  description: string | null
  kopisId: string | null
  saleOpenings: {
    openingId: string
    salesOpenAt: string
    notified: boolean
  }[]
  bookingLimitPerMember: number | null
  cancellationPolicy: string | null
}

export interface PerformanceSession {
  sessionId: string
  performanceId: string
  startsAt: string
  salesOpenAt: string | null
  salesCloseAt: string | null
  cancellationDeadline: string | null
  bookingAvailability: string
  gradePrices: {
    grade: string
    price: number
  }[]
  serverTime: string
}

export interface PerformanceSessionList {
  items: PerformanceSession[]
}

/**
 * 공연 목록 조회
 */
export async function getPerformances(): Promise<unknown> {
  const response = await apiClient.get<ApiResponse<unknown>>(
    '/api/v1/performances',
  )

  if (response.data.code !== 'SUCCESS') {
    throw new Error(
      response.data.message || '공연 목록을 불러오지 못했습니다.',
    )
  }

  return response.data.data
}

/**
 * 공연 상세 조회
 */
export async function getPerformanceDetail(
  performanceId: string,
): Promise<PublicPerformanceDetail> {
  const response = await apiClient.get<
    ApiResponse<PublicPerformanceDetail>
  >(`/api/v1/performances/${encodeURIComponent(performanceId)}`)

  if (response.data.code !== 'SUCCESS' || !response.data.data) {
    throw new Error(
      response.data.message || '공연 상세 정보를 불러오지 못했습니다.',
    )
  }

  return response.data.data
}

/**
 * 공연 회차 목록 조회
 */
export async function getPerformanceSessions(
  performanceId: string,
): Promise<PerformanceSessionList> {
  const response = await apiClient.get<
    ApiResponse<PerformanceSessionList>
  >(
    `/api/v1/performances/${encodeURIComponent(performanceId)}/sessions`,
  )

  if (
    response.data.code !== 'SUCCESS' ||
    !response.data.data ||
    !Array.isArray(response.data.data.items)
  ) {
    throw new Error(
      response.data.message || '공연 회차 정보를 불러오지 못했습니다.',
    )
  }

  return response.data.data
}
