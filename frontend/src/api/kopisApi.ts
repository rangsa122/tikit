import {apiClient} from './apiClient.ts'
import type {ApiResponse} from './apiError.ts'

export interface KopisPerformanceItem {
    kopisId: string
    title: string
    venueName: string | null
    startDate: string | null
    endDate: string | null
    posterUrl: string | null
    area: string | null
    genreName: string | null
    state: string | null
    alreadyRegistered: boolean
}

export interface KopisSearchResponse {
    items: KopisPerformanceItem[]
    page: number
    size: number
}

export interface KopisSearchParams {
    startDate: string
    endDate: string
    title?: string
    genreCode?: string
    page?: number
    size?: number
}

export async function searchKopisPerformances(
    params: KopisSearchParams,
): Promise<KopisSearchResponse> {
    const response = await apiClient.get<ApiResponse<KopisSearchResponse>>(
        '/api/v1/manager/kopis/performances',
        {params},
    )
    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || 'KOPIS 공연을 검색하지 못했습니다')
    }
    return response.data.data
}

export interface KopisPerformanceDetail {
    kopisId: string
    title: string
    genreCode: string | null      // 매핑 실패 시 null
    genreName: string | null
    venueName: string | null
    organizerName: string | null
    description: string
    posterUrl: string | null
    startDate: string | null
    endDate: string | null
    cast: string | null
    runtime: string | null
    age: string | null
    timeGuidance: string | null
    priceGuidance: string | null
    state: string | null
    alreadyRegistered: boolean
}

export async function getKopisPerformance(
    kopisId: string,
): Promise<KopisPerformanceDetail> {
    const response = await apiClient.get<ApiResponse<KopisPerformanceDetail>>(
        `/api/v1/manager/kopis/performances/${encodeURIComponent(kopisId)}`,
    )
    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || 'KOPIS 공연 정보를 불러오지 못했습니다')
    }
    return response.data.data
}