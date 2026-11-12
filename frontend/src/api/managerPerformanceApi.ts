import {apiClient} from "./apiClient.ts";

interface ApiResponse<T>{
    code: string
    message: string
    data: T | null
}

export interface PerformanceCreateRequest{
    source: 'MANUAL' | 'KOPIS'
    kopisId?: string
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

export async function getManagerPerformance(
    performanceId: number,
): Promise<ManagerPerformance> {
    const response = await apiClient.get<ApiResponse<ManagerPerformance>>(
        `/api/v1/manager/performances/${performanceId}`,
    )
    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '공연을 불러오지 못했습니다')
    }
    return response.data.data
}

// 게시·공개 상태 변경 응답. 공연 전체가 아니라 상태 필드만 온다
export interface PerformancePublication {
    performanceId: number
    publicationStatus: 'DRAFT' | 'PUBLISHED'
    visibility: 'PRIVATE' | 'PUBLIC' | 'HIDDEN'
    salesStatus: 'ENABLED' | 'STOPPED'
    version: number
    publishedAt: string | null
    publishedBy: number | null
}

// 공연 게시. 같은 요청이 두 번 가도 한 번만 처리되도록 멱등키를 함께 보낸다
export async function publishPerformance(
    performanceId: number,
    expectedVersion: number,
): Promise<PerformancePublication> {
    const response = await apiClient.post<ApiResponse<PerformancePublication>>(
        `/api/v1/manager/performances/${performanceId}/publication`,
        {expectedVersion},
        {headers: {'Idempotency-Key': crypto.randomUUID()}},
    )
    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '공연을 게시하지 못했습니다')
    }
    return response.data.data
}

// 신규 판매 중지·재개
export async function changeSalesStatus(
    performanceId: number,
    salesStatus: 'ENABLED' | 'STOPPED',
    expectedVersion: number,
): Promise<ManagerPerformance> {
    const response = await apiClient.patch<ApiResponse<ManagerPerformance>>(
        `/api/v1/manager/performances/${performanceId}/sales-status`,
        {salesStatus, expectedVersion},
    )
    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '판매 상태를 변경하지 못했습니다')
    }
    return response.data.data
}

// 게시 중단(숨김)·복구
export async function changeVisibility(
    performanceId: number,
    visibility: 'PUBLIC' | 'HIDDEN',
    expectedVersion: number,
    reason?: string,
): Promise<PerformancePublication> {
    const response = await apiClient.patch<ApiResponse<PerformancePublication>>(
        `/api/v1/manager/performances/${performanceId}/visibility`,
        {visibility, expectedVersion, reason},
    )
    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '공개 상태를 변경하지 못했습니다')
    }
    return response.data.data
}

// 부분 수정. 보내지 않은 필드는 서버가 건드리지 않는다
export interface PerformanceUpdateRequest {
    expectedVersion: number
    title?: string
    genreCode?: string
    venueName?: string
    organizerName?: string
    description?: string
    posterUrl?: string
    startDate?: string
    endDate?: string
    bookingLimitPerMember?: number
}

export async function updatePerformance(
    performanceId: number,
    request: PerformanceUpdateRequest,
): Promise<ManagerPerformance> {
    const response = await apiClient.patch<ApiResponse<ManagerPerformance>>(
        `/api/v1/manager/performances/${performanceId}`,
        request,
    )
    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '공연을 수정하지 못했습니다')
    }
    return response.data.data
}
