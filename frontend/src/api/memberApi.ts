import { apiClient } from './apiClient'

export interface MemberProfile {
    id: number
    email: string
    name: string
    notificationEnabled: boolean
    createdAt: string
}

interface ApiResponse<T> {
    success: boolean
    data: T
    message?: string
}

// 내 회원정보 조회
export async function getMyProfile(): Promise<MemberProfile> {
    const response = await apiClient.get<ApiResponse<MemberProfile>>(
        '/api/v1/me'
    )

    return response.data.data
}

// 내 회원정보 수정
export async function updateMyProfile(
    name: string
): Promise<MemberProfile> {
    const response = await apiClient.patch<ApiResponse<MemberProfile>>(
        '/api/v1/me',
        { name }
    )

    return response.data.data
}
