import {apiClient} from "./apiClient.ts";

interface ApiResponse<T> {
    code: string
    message: string
    data: T | null
}

export type BookingStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'EXPIRED' | 'CANCELED'

export type PaymentAttemptStatus =
    | 'READY'
    | 'PROCESSING'
    | 'APPROVED'
    | 'FAILED'
    | 'RECONCILING'

export interface BookedSeat {
    seatId: number
    label: string
    grade: string
    price: number
}

/**
 * 예매 응답 (백엔드 BookingResponseDTO와 같은 모양)
 */
export interface Booking {
    bookingId: number
    sessionId: number
    performanceId: number
    performanceTitle: string
    startsAt: string
    status: BookingStatus
    seats: BookedSeat[]
    totalAmount: number
    expiresAt: string
    paymentAttemptId: number | null
    paymentStatus: PaymentAttemptStatus | null
    cancellationId: number | null
    cancellationStatus: string | null
    createdAt: string
    serverTime: string
}

/**
 * 예매 상세 조회 (결제 화면 새로고침, 재진입용)
 * GET /api/v1/bookings/{bookingId}
 */
export async function getBooking(bookingId: string): Promise<Booking> {
    const response = await apiClient.get<ApiResponse<Booking>>(
        `/api/v1/bookings/${encodeURIComponent(bookingId)}`,
    )

    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '예매 정보를 불러오지 못했습니다.')
    }

    return response.data.data
}