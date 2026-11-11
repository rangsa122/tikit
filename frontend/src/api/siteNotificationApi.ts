import { apiClient } from './apiClient'

interface ApiResponse<T> {
    code: string
    message: string
    data: T | null
}

export interface SiteNotification {
    id: number
    openingId: number
    type: string
    title: string
    performanceTitle: string | null
    message: string
    isRead: boolean
    createdAt: string
    readAt: string | null
}

export interface SiteNotificationPage {
    content: SiteNotification[]
    empty: boolean
    number: number
    size: number
    totalElements: number
    totalPages: number
    first: boolean
    last: boolean
}

// 내 알림 목록 조회
export async function getSiteNotifications(
    page = 0,
    size = 20,
    unreadOnly = false,
): Promise<SiteNotificationPage> {
    const response = await apiClient.get<
        ApiResponse<SiteNotificationPage>
    >('/api/v1/me/notifications', {
        params: {
            page,
            size,
            unreadOnly,
        },
    })

    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(
            response.data.message || '알림 목록을 불러오지 못했습니다.',
        )
    }

    return response.data.data
}

// 알림 1건 읽음 처리
export async function markSiteNotificationAsRead(
    notificationId: number,
): Promise<SiteNotification> {
    const response = await apiClient.patch<
        ApiResponse<SiteNotification>
    >(
        `/api/v1/me/notifications/${notificationId}`,
    )

    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(
            response.data.message || '알림 읽음 처리에 실패했습니다.',
        )
    }

    return response.data.data
}

