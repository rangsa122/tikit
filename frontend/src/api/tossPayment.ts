import {loadTossPayments} from "@tosspayments/tosspayments-sdk";
import type {PaymentAttempt} from "./paymentApi.ts";

const TOSS_CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY as string | undefined

/**
 * 토스 결제창 열기 (SDK v2, 카드 결제)
 *
 * 금액, 주문 번호, 주문명, customerKey는 결제 준비 응답 그대로 사용 (화면에서 계산하지 않음)
 * 카드 인증이 끝나면 토스가 successUrl 또는 failUrl로 페이지를 이동시킴
 * 이 함수가 끝났다고 결제된 것이 아님 (실제 결제는 성공 페이지에서 우리 서버가 승인할 때)
 */
export async function requestTossPayment(bookingId: string, attempt: PaymentAttempt): Promise<void> {
    if (!TOSS_CLIENT_KEY) {
        throw new Error('토스 클라이언트 키(VITE_TOSS_CLIENT_KEY가 설정되지 않았습니다.')
    }

    const tossPayments = await loadTossPayments(TOSS_CLIENT_KEY)
    const payment = tossPayments.payment({customerKey: attempt.customerKey})

    const resultPath = `${window.location.origin}/bookings/${encodeURIComponent(bookingId)}/payment`
    const query = `?paymentAttemptId=${attempt.paymentAttemptId}`

    await payment.requestPayment({
        method: 'CARD',
        amount: {currency: 'KRW', value: attempt.amount},
        orderId: attempt.orderId,
        orderName: attempt.orderName,
        successUrl: `${resultPath}/success${query}`,
        failUrl: `${resultPath}/fail${query}`,
    })
}