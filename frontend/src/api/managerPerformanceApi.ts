import {apiClient} from "./apiClient.ts";

interface ApiResponse<T>{
    code: string
    message: string
    data: T | null
}

export interface PerformanceCreateRequest{
    source: 'MANUAL'
    title: string
    genreCode: string
    venueName: string
    organizerName?: string
    description: string
    posterUrl?: string
    startDate: string
    endDate: string
    bookingLimitPerMember?: number
}

export interface ManagerPerformance{
    performanceId: number
    genreCode: string
    genreName: string
    source: 'MANUAL'|'KOPIS'
    kopisId: string | null
    title: string
    venueName: string
    organizerName: string | null
    description: string
    posterUrl: string | null
    startDate: string
    endDate: string
    publicationStatus: 'DRAFT' | 'PUBLISHED'
    visibility: 'PRIVATE' | 'PUBLIC' | 'HIDDEN'
    salesStatus: 'ENABLED' | 'STOPPED'
    bookingLimitPerMember: number
    createdBy: number
    version: number
    createdAt: string
    updatedAt: string
}

export async function createPerformance(
    request: PerformanceCreateRequest,
): Promise<ManagerPerformance> {
    const response = await apiClient.post<ApiResponse<ManagerPerformance>>(
        '/api/v1/manager/performances', request,
    )
    if(response.data.code !== 'SUCCESS' || !response.data.data){
        throw new Error(response.data.message || '공연을 등록하지 못했습니다')
    }
    return response.data.data
}

export interface PageResponse<T> {
    items: T[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export interface ManagerPerformanceListParams {
    page?: number
    size?: number
    publicationStatus?: 'DRAFT' | 'PUBLISHED'
}

export async function getManagerPerformances(
    params: ManagerPerformanceListParams = {},
): Promise<PageResponse<ManagerPerformance>> {
    const response = await apiClient.get<ApiResponse<PageResponse<ManagerPerformance>>>(
        '/api/v1/manager/performances',
        { params },
    )
    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '공연 목록을 불러오지 못했습니다')
    }
    return response.data.data
}