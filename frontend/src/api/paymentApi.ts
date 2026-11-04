import {apiClient} from "./apiClient.ts";
import type {BookingStatus, PaymentAttemptStatus} from "./bookingApi.ts";

interface ApiResponse<T> {
    code: string
    message: string
    data: T | null
}

/**
 * 결제 시도 응답 (백엔드 PaymentAttemptResponseDTO와 같은 모양)
 * 결제 준비, 승인, 결과 조회 세 API가 모두 이 모양으로 응답
 */
export interface PaymentAttempt {
    paymentAttemptId: number
    bookingId: number
    orderId: string
    orderName: string
    customerKey: string
    amount: number
    currency: string
    status: PaymentAttemptStatus
    bookingStatus: BookingStatus
    expiresAt: string
    retryAllowed: boolean
    failureCode: string | null
    serverTime: string
}

/**
 * 결제 승인 요청 본문 (토스 성공 URL로 받은 값)
 */
export interface PaymentConfirmRequest {
    paymentKey: string
    orderId: string
    amount: number
}

/**
 * 결제 준비 (주문 번호와 금액 발급)
 * POST /api/v1/bookings/{bookingId}/payment-attempts
 * 새로 만들면 201, 기존 READY 결제 시도를 다시 쓰면 200 (둘 다 code SUCCESS)
 */
export async function preparePaymentAttempt(bookingId: string): Promise<PaymentAttempt> {
    const response = await apiClient.post<ApiResponse<PaymentAttempt>>(
        `/api/v1/bookings/${encodeURIComponent(bookingId)}/payment-attempts`,
    )

    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '결제를 준비하지 못했습니다.')
    }

    return response.data.data
}

/**
 * 결제 승인 (토스 결제창 인증 후 우리 서버에 승인 요청)
 * POST /api/v1/payment-attempts/{paymentAttemptId}/confirmation
 * 200 = 승인 완료(APPROVED), 202 = 처리 중 또는 결과 확인 중 (둘 다 code SUCCESS)
 * 완료 여부는 code가 아니라 반환값의 status로 판단
 */
export async function confirmPayment(
    paymentAttemptId: string,
    request: PaymentConfirmRequest,
    idempotencyKey: string,
): Promise<PaymentAttempt> {
    const response = await apiClient.post<ApiResponse<PaymentAttempt>>(
        `/api/v1/payment-attempts/${encodeURIComponent(paymentAttemptId)}/confirmation`,
        request,
        {headers: {'Idempotency-Key': idempotencyKey}},
    )

    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '결제를 승인하지 못했습니다.')
    }

    return response.data.data
}

/**
 * 결제 처리 결과 조회 (승인 202 이후 3초마다 재조회용)
 * GET /api/v1/payment-attempts/{paymentAttemptId}
 * 조회만 하고 승인이나 복구를 실행하지 않음
 */
export async function getPaymentAttempt(paymentAttemptId: string): Promise<PaymentAttempt> {
    const response = await apiClient.get<ApiResponse<PaymentAttempt>>(
        `/api/v1/payment-attempts/${encodeURIComponent(paymentAttemptId)}`,
    )

    if (response.data.code !== 'SUCCESS' || !response.data.data) {
        throw new Error(response.data.message || '결제 결과를 불러오지 못했습니다.')
    }

    return response.data.data
}