import {apiClient} from './apiClient.ts'
import type {ApiResponse} from './apiError.ts'

export interface Banner {
    performanceId: string
    title: string
    bannerUrl: string
}

/**
 * 홈 배너 목록 조회.
 * 배너 이미지를 등록한 공개 공연만 최대 5개 내려온다.
 */
export async function getBanners(): Promise<Banner[]> {
    const response = await apiClient.get<ApiResponse<Banner[]>>('/api/v1/banners')

    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '배너를 불러오지 못했습니다')
    }

    return response.data.data
}