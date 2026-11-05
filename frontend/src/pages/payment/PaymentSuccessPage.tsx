import {useQuery} from '@tanstack/react-query'
import {isAxiosError} from 'axios'
import {useNavigate, useParams, useSearchParams} from 'react-router'
import type {PaymentAttemptStatus} from '../../api/bookingApi'
import {confirmPayment, getPaymentAttempt} from '../../api/paymentApi'
import type {PaymentAttempt} from '../../api/paymentApi'
import Button from '../../components/common/Button'
import {
    AlertIcon,
    CenterColumn,
    CheckIcon,
    ClockIcon,
    Notice,
    SpinnerIcon,
    StatusCard,
} from '../../components/payment/PaymentUi'
import {formatWon} from '../../utils/bookingFormat'

type SuccessView = 'confirming' | 'checking' | 'approved' | 'failed' | 'expired' | 'error'

const POLL_INTERVAL_MS = 3000

interface ConfirmationParams {
    paymentAttemptId: string
    paymentKey: string
    orderId: string
    amount: number
}

/**
 * 토스 결제 성공 URL 페이지 (/bookings/:bookingId/payment/success)
 *
 * 토스가 이 주소로 보냈다는 것만으로 결제 완료가 아님 → 우리 서버 승인 응답으로만 판단
 * 새로고침해도 같은 멱등키로 다시 요청하고, 서버가 이미 승인된 결제는 토스를 다시 부르지 않으므로 결제는 한 번만 됨
 */
export default function PaymentSuccessPage() {
    const {bookingId} = useParams()
    const navigate = useNavigate()
    const [searchParams] = useSearchParams()
    const confirmation = readConfirmation(searchParams)

    // 승인 요청 (한 번만: 재시도 없음, 창 포커스·재연결 때 다시 보내지 않음)
    const confirmQuery = useQuery({
        queryKey: ['payment-attempts', confirmation?.paymentAttemptId, 'confirmation', confirmation?.paymentKey],
        queryFn: () => confirmPayment(
            confirmation!.paymentAttemptId,
            {paymentKey: confirmation!.paymentKey, orderId: confirmation!.orderId, amount: confirmation!.amount},
            `confirm-${confirmation!.paymentKey}`,
        ),
        enabled: confirmation !== null,
        retry: false,
        staleTime: Infinity,
        gcTime: Infinity,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
    })

    // 202(처리 중·확인 중)이거나 응답을 못 받았으면 결과 조회로 전환
    const needsPolling = confirmQuery.data
        ? isInProgress(confirmQuery.data.status)
        : isNoResponse(confirmQuery.error)

    // 결과 조회 (진행 중이면 3초마다, 결과가 나오면 멈춤)
    const pollQuery = useQuery({
        queryKey: ['payment-attempts', confirmation?.paymentAttemptId, 'detail'],
        queryFn: () => getPaymentAttempt(confirmation!.paymentAttemptId),
        enabled: confirmation !== null && needsPolling,
        refetchInterval: query => {
            const status = query.state.data?.status
            return !status || isInProgress(status) ? POLL_INTERVAL_MS : false
        },
        staleTime: 0,
    })

    const attempt = pollQuery.data ?? confirmQuery.data
    const view = getSuccessView(confirmation !== null, attempt, confirmQuery.error, needsPolling)
    const paymentPath = `/bookings/${bookingId}/payment`

    return (
        <main className="mx-auto max-w-[1320px] px-5 py-8">
            <CenterColumn>
                {view === 'confirming' && (
                    <StatusCard icon={<SpinnerIcon />} title="결제를 승인하고 있어요">
                        잠시만 기다려 주세요. 보통 몇 초 안에 끝나요.
                    </StatusCard>
                )}

                {view === 'checking' && (
                    <>
                        <StatusCard icon={<SpinnerIcon />} title="결제 결과를 확인하고 있어요">
                            결제 응답이 늦어지고 있어요.
                            <br />
                            결과가 확인되면 자동으로 화면이 바뀌어요.
                        </StatusCard>
                        <Notice>
                            창을 닫아도 결제 결과는 서버에서 확인돼요. 확인 전까지 좌석은 그대로 유지되고, 결제가 두 번 되지 않아요.
                        </Notice>
                    </>
                )}

                {view === 'approved' && attempt && (
                    <>
                        <StatusCard icon={<CheckIcon />} title="결제가 완료됐어요">
                            {attempt.orderName}
                            <br />
                            결제 금액 {formatWon(attempt.amount)}
                        </StatusCard>
                        <Button onClick={() => navigate('/mypage')}>예매 내역 보기</Button>
                    </>
                )}

                {view === 'failed' && (
                    <>
                        <StatusCard icon={<AlertIcon />} title="결제가 승인되지 않았어요">
                            카드사에서 결제를 승인하지 않았어요.
                            <br />
                            남은 시간 안에 다시 결제할 수 있어요.
                        </StatusCard>
                        <Button onClick={() => navigate(paymentPath, {replace: true})}>다시 결제하기</Button>
                    </>
                )}

                {view === 'expired' && (
                    <>
                        <StatusCard icon={<ClockIcon />} title="결제 시간이 지났어요">
                            제한시간이 지나 결제를 진행하지 않았어요.
                            <br />
                            카드 결제 금액은 청구되지 않아요.
                        </StatusCard>
                        <Button onClick={() => navigate('/')}>메인으로</Button>
                    </>
                )}

                {view === 'error' && (
                    <>
                        <StatusCard icon={<AlertIcon />} title="결제 결과를 확인하지 못했어요">
                            {getErrorMessage(confirmQuery.error)}
                        </StatusCard>
                        <Button onClick={() => navigate(paymentPath, {replace: true})}>결제 화면으로</Button>
                    </>
                )}
            </CenterColumn>
        </main>
    )
}

/**
 * 보여 줄 화면 결정
 *
 * 결제 시도 상태(조회 결과 우선)가 있으면 그것으로, 없으면 승인 오류 코드로 판단
 */
function getSuccessView(
    hasParams: boolean,
    attempt: PaymentAttempt | undefined,
    confirmError: unknown,
    needsPolling: boolean,
): SuccessView {
    if (!hasParams) {
        return 'error'
    }

    if (attempt) {
        if (attempt.status === 'APPROVED') {
            return 'approved'
        }

        return attempt.status === 'FAILED' ? 'failed' : 'checking'
    }

    if (!confirmError) {
        return 'confirming'
    }

    if (needsPolling) {
        return 'checking'
    }

    const code = getErrorCode(confirmError)

    if (code === 'BOOKING_EXPIRED') {
        return 'expired'
    }

    return code === 'PAYMENT_DECLINED' ? 'failed' : 'error'
}

// 토스가 붙여 준 값 + 우리가 붙여 둔 paymentAttemptId (하나라도 없으면 null)
function readConfirmation(searchParams: URLSearchParams): ConfirmationParams | null {
    const paymentAttemptId = searchParams.get('paymentAttemptId')
    const paymentKey = searchParams.get('paymentKey')
    const orderId = searchParams.get('orderId')
    const amountText = searchParams.get('amount')
    const amount = Number(amountText)

    if (!paymentAttemptId || !paymentKey || !orderId || !amountText || !Number.isSafeInteger(amount)) {
        return null
    }

    return {paymentAttemptId, paymentKey, orderId, amount}
}

function isInProgress(status: PaymentAttemptStatus): boolean {
    return status === 'PROCESSING' || status === 'RECONCILING'
}

// 요청은 보냈지만 응답을 못 받음 (timeout, 연결 끊김) → 실패로 단정하지 않고 조회로 확인
function isNoResponse(error: unknown): boolean {
    return isAxiosError(error) && !error.response
}

function getErrorCode(error: unknown): string | undefined {
    return isAxiosError<{code: string, message: string}>(error) ? error.response?.data?.code : undefined
}

function getErrorMessage(error: unknown): string {
    if (isAxiosError<{code: string, message: string}>(error) && error.response?.data?.message) {
        return error.response.data.message
    }

    return '결제 정보가 올바르지 않아요. 결제 화면에서 다시 확인해 주세요.'
}