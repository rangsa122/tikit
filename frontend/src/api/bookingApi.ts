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

/** 내 예매 목록 페이지 응답 */
export interface BookingPage {
    items: Booking[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

/**
 * 내 예매 목록 조회
 * GET /api/v1/me/bookings
 */
export async function getMyBookings(
    page: number = 0,
    size: number = 20,
    status?: BookingStatus,
): Promise<BookingPage> {
    const response = await apiClient.get<ApiResponse<BookingPage>>(
        '/api/v1/me/bookings',
        {
            params: {
                page,
                size,
                ...(status ? { status } : {}),
            },
        },
    )

    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(
            response.data.message || '예매 내역을 불러오지 못했습니다.',
        )
    }

    return response.data.data
}

/** 티켓 상태 */
export type TicketStatus = 'VALID' | 'VOID'

/** 발급 티켓 정보 */
export interface TicketItem {
    ticketId: number
    bookingId: number
    seatId: number
    seatLabel: string
    performanceTitle: string
    startsAt: string
    status: TicketStatus
}

/** 티켓 목록 응답 */
export interface TicketListResponse {
    items: TicketItem[]
}

/**
 * 예매별 발급 티켓 조회
 * GET /api/v1/bookings/{bookingId}/tickets
 */
export async function getTickets(
    bookingId: string,
): Promise<TicketListResponse> {
    const response = await apiClient.get<ApiResponse<TicketListResponse>>(
        `/api/v1/bookings/${encodeURIComponent(bookingId)}/tickets`,
    )

    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(
            response.data.message || '발급 티켓을 불러오지 못했습니다.',
        )
    }

    return response.data.data
}

/** 전체 취소 예상 금액 조회 응답 */
export interface CancellationQuote {
    bookingId: number
    eligible: boolean
    reasonCode: string | null
    paidAmount: number | null
    feeAmount: number | null
    refundAmount: number | null
    policyVersion: string | null
    cancellationDeadline: string | null
    quotedAt: string | null
    serverTime: string | null
}

/**
 * 전체 취소 예상 금액 조회
 * GET /api/v1/bookings/{bookingId}/cancellation-quote
 */
export async function getCancellationQuote(
    bookingId: string,
): Promise<CancellationQuote> {
    try {
        const response = await apiClient.get<ApiResponse<CancellationQuote>>(
            `/api/v1/bookings/${encodeURIComponent(bookingId)}/cancellation-quote`,
        )

        // API 응답 검증
        if (response.data.code !== 'SUCCESS' || !response.data.data) {
            throw new BookingApiError(
                response.data.code,
                response.data.message || '취소 견적을 불러오지 못했습니다.',
            )
        }

        return response.data.data
    } catch (error) {
        // 서버 오류 코드 유지
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

/** 전체 취소 요청 데이터 */
export interface CancellationCreateRequest {
    reason: string
    expectedRefundAmount: number
    policyVersion: string
}

/** 전체 취소 처리 결과 */
export interface CancellationResponse {
    cancellationId: number
    bookingId: number
    status: string
    refundAmount: number | null
    feeAmount: number | null
    reason: string
    initiatorRole: string
    retryAllowed: boolean
    failureCode: string | null
    createdAt: string
    completedAt: string | null
    serverTime: string
}

/**
 * 예매 전체 취소 요청
 * POST /api/v1/bookings/{bookingId}/cancellations
 */
export async function createCancellation(
    bookingId: string,
    request: CancellationCreateRequest,
    idempotencyKey: string,
): Promise<CancellationResponse> {
    try {
        const response = await apiClient.post<ApiResponse<CancellationResponse>>(
            `/api/v1/bookings/${encodeURIComponent(bookingId)}/cancellations`,
            request,
            {
                headers: {
                    'Idempotency-Key': idempotencyKey,
                },
            },
        )

        if (response.data.code !== 'SUCCESS' || !response.data.data) {
            throw new BookingApiError(
                response.data.code,
                response.data.message || '예매 취소 요청에 실패했습니다.',
            )
        }

        return response.data.data
    } catch (error) {
        // HTTP 오류 및 네트워크 오류 처리
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

/**
 * 전체 취소 처리 결과 조회
 * GET /api/v1/cancellations/{cancellationId}
 */
export async function getCancellation(
    cancellationId: number,
): Promise<CancellationResponse> {
    try {
        // 취소 ID로 최신 처리 결과 조회
        const response = await apiClient.get<ApiResponse<CancellationResponse>>(
            `/api/v1/cancellations/${cancellationId}`,
        )

        // API 응답 검증
        if (response.data.code !== 'SUCCESS' || !response.data.data) {
            throw new BookingApiError(
                response.data.code,
                response.data.message || '취소 처리 결과를 불러오지 못했습니다.',
            )
        }

        return response.data.data
    } catch (error) {
        if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
            const body = error.response?.data

            throw new BookingApiError(
                body?.code ?? 'NETWORK_ERROR',
                body?.message ?? '취소 처리 결과를 조회하지 못했습니다.',
            )
        }

        throw error
    }
}
