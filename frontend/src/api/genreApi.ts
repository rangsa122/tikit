
import {apiClient} from './apiClient.ts'
import type {ApiResponse} from './apiError.ts'

export interface Genre {
    code: string
    name: string
}

export interface GenreListResponse {
    items: Genre[]
}

export async function getGenres(): Promise<Genre[]> {
    const response = await apiClient.get<ApiResponse<GenreListResponse>>(
        '/api/v1/genres',
    )

    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '장르 목록을 불러오지 못했습니다')
    }

    return response.data.data.items
}
