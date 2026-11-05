import { apiClient } from './apiClient'

interface ApiResponse<T> {
    code: string
    message: string
    data: T
}

export interface OpeningSubscriptionPage {
    content: number[]
    empty: boolean
    number: number
    size: number
    totalElements: number
    totalPages: number
    last: boolean
}

// 전체 알림 수신 설정 조회
export async function getNotificationPreference(): Promise<boolean> {
    const response = await apiClient.get<ApiResponse<boolean>>(
        '/api/v1/me/notification-preference'
    )

    return response.data.data
}

// 전체 알림 수신 설정 <<변경>>
export async function updateNotificationPreference(
    enabled: boolean
): Promise<void> {
    await apiClient.put(
        '/api/v1/me/notification-preference',
        { enabled }
    )
}

// 공연별 알림 신청 목록 조회
export async function getOpeningSubscriptions(page = 0, size = 10): Promise<OpeningSubscriptionPage> {
    const response = await apiClient.get<ApiResponse<OpeningSubscriptionPage>>(
        '/api/v1/me/opening-subscriptions',
        {
            params: { page, size },
        }
    )

    return response.data.data
}

// 공연별 알림 신청 <<취소>>
export async function deleteOpeningSubscription(
    performanceId: number
): Promise<void> {
    await apiClient.delete(`/api/v1/me/opening-subscriptions/${performanceId}`)
}

// 공연별 예매 시작 알림 신청
export async function subscribeOpeningSubscription(
    performanceId: number
): Promise<void> {
    await apiClient.put(
        `/api/v1/me/opening-subscriptions/${performanceId}`
    )
}
