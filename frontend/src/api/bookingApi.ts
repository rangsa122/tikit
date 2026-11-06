import axios from "axios";
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

/**
 * API가 실패 응답을 줬을 때 (화면은 code로 분기)
 */
export class BookingApiError extends Error {
    readonly code: string

    constructor(code: string, message: string) {
        super(message)
        this.code = code
    }
}

/**
 * 좌석 선점과 임시 예매 생성
 * POST /api/v1/bookings
 *
 * admissionId: 대기열에서 받은 입장 권한
 */
export async function createBooking(sessionId: number, seatIds: number[], admissionId: string): Promise<Booking> {
    try {
        const response = await apiClient.post<ApiResponse<Booking>>(
            '/api/v1/bookings',
            {sessionId, seatIds},
            {headers: {'X-Admission-Id': admissionId}},
        )

        if (response.data.code !== 'SUCCESS' || !response.data.data) {
            throw new BookingApiError(response.data.code, response.data.message)
        }

        return response.data.data
    } catch (error) {
        if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
            const body = error.response?.data
            throw new BookingApiError(
                body?.code ?? 'NETWORK_ERROR',
                body?.message ?? '서버에 연결하지 못했습니다. 다시 시도해 주세요.',
            )
        }
        throw error
    }
}
