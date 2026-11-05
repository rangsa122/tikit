import {useState} from 'react'
import type {ReactNode} from 'react'
import {isAxiosError} from 'axios'
import {Link, useNavigate, useParams} from 'react-router'
import type {Booking} from '../../api/bookingApi'
import {preparePaymentAttempt} from '../../api/paymentApi'
import {requestTossPayment} from '../../api/tossPayment'
import Button from '../../components/common/Button'
import BookingStepHeader from '../../components/booking/BookingStepHeader.tsx'
import type {TimerPillProps} from '../../components/booking/BookingStepHeader.tsx'
import PaymentForm from '../../components/payment/PaymentForm'
import {
    AlertIcon,
    Card,
    CenterColumn,
    CheckIcon,
    ClockIcon,
    Notice,
    PosterPlaceholder,
    SpinnerIcon,
    StatusCard,
} from '../../components/payment/PaymentUi'
import {useBookingDetail} from '../../hooks/useBookingDetail'
import {usePaymentCountdown} from '../../hooks/usePaymentCountdown'
import type {PaymentCountdown} from '../../hooks/usePaymentCountdown'
import {formatDateTime, formatSeat, formatSeatSummary, formatWon} from '../../utils/bookingFormat'

type PaymentView = 'payment' | 'processing' | 'reconciling' | 'approved' | 'failed' | 'expired'

const URGENT_SECONDS = 60

/**
 * 결제 화면 (/bookings/:bookingId/payment)
 *
 * 기한 판단의 최종 기준은 서버이며, 화면 타이머는 표시용
 */
export default function PaymentPage() {
    const {bookingId} = useParams()
    const navigate = useNavigate()
    const {data, isLoading, isError, refetch} = useBookingDetail(bookingId)
    const countdown = usePaymentCountdown(data?.booking.expiresAt, data?.clockOffsetMs ?? 0)
    const [isPaying, setIsPaying] = useState(false)
    const [payError, setPayError] = useState<string | null>(null)

    if (isLoading) {
        return <PageMessage message="예매 정보를 불러오는 중이에요." />
    }

    if (isError || !data) {
        return <PageMessage message="예매 정보를 불러오지 못했어요." showHomeLink />
    }

    const {booking} = data
    const view = getPaymentView(booking, countdown.isExpired)
    const performancePath = `/performances/${booking.performanceId}`

    // 결제 준비(주문 번호·금액 발급) → 토스 결제창 열기
    // 결제창에서 인증이 끝나면 토스가 성공·실패 페이지로 이동시키므로 여기서 결제 완료를 판단하지 않음
    async function handlePay() {
        if (!bookingId || isPaying) {
            return
        }

        setIsPaying(true)
        setPayError(null)

        try {
            const attempt = await preparePaymentAttempt(bookingId)
            await requestTossPayment(bookingId, attempt)
        } catch (error) {
            if (isTossUserCancel(error)) {
                return
            }

            setPayError(getPayErrorMessage(error))
            void refetch()
        } finally {
            setIsPaying(false)
        }
    }

    return (
        <div className="min-h-svh bg-white">
            <BookingStepHeader
                title={booking.performanceTitle}
                subtitle={formatDateTime(booking.startsAt)}
                currentStep={view === 'approved' ? 3 : 2}
                timer={getTimerPill(view, countdown)}
                onBack={view === 'approved' ? undefined : () => navigate(performancePath)}
            />

            <main className="mx-auto max-w-[1320px] px-5 py-8">
                {payError && (view === 'payment' || view === 'failed') && (
                    <div className="mb-4">
                        <Notice>{payError}</Notice>
                    </div>
                )}

                {view === 'payment' && <PaymentForm booking={booking} onPay={handlePay} />}

                {view === 'processing' && (
                    <CenterColumn>
                        <StatusCard icon={<SpinnerIcon />} title="결제를 승인하고 있어요">
                            잠시만 기다려 주세요. 보통 몇 초 안에 끝나요.
                        </StatusCard>
                        <Notice>
                            결제가 끝나면 자동으로 다음 화면으로 이동해요. 결제 금액은 {formatWon(booking.totalAmount)}이에요.
                        </Notice>
                    </CenterColumn>
                )}

                {view === 'reconciling' && (
                    <CenterColumn>
                        <StatusCard icon={<SpinnerIcon />} title="결제 결과를 확인하고 있어요">
                            결제 응답이 늦어지고 있어요.
                            <br />
                            결과가 확인되면 자동으로 화면이 바뀌어요.
                        </StatusCard>
                        <Notice>
                            창을 닫아도 결제 결과는 서버에서 확인돼요. 확인 전까지 좌석은 그대로 유지되고, 결제가 두 번 되지 않아요.
                        </Notice>
                    </CenterColumn>
                )}

                {view === 'approved' && (
                    <CenterColumn>
                        <StatusCard icon={<CheckIcon />} title="예매가 완료됐어요">
                            예매 번호 {booking.bookingId}
                        </StatusCard>
                        <Card>
                            <h2 className="text-base font-bold text-gray-900">{booking.performanceTitle}</h2>
                            <dl className="mt-3 grid grid-cols-[72px_1fr] gap-y-2 text-[13px]">
                                <dt className="text-gray-500">일시</dt>
                                <dd className="text-gray-900">{formatDateTime(booking.startsAt)}</dd>
                                <dt className="text-gray-500">좌석</dt>
                                <dd className="text-gray-900">{booking.seats.map(formatSeat).join(', ')}</dd>
                                <dt className="mt-2 border-t border-gray-200 pt-3 text-gray-500">결제 금액</dt>
                                <dd className="mt-2 border-t border-gray-200 pt-3 font-bold text-gray-900">
                                    {formatWon(booking.totalAmount)}
                                </dd>
                            </dl>
                        </Card>
                        <Button onClick={() => navigate('/mypage')}>예매 내역 보기</Button>
                    </CenterColumn>
                )}

                {view === 'failed' && (
                    <CenterColumn>
                        <StatusCard icon={<AlertIcon />} title="결제가 완료되지 않았어요">
                            결제가 승인되지 않았어요. 다른 결제 수단으로 다시 시도해 주세요.
                        </StatusCard>
                        <Notice>선택한 좌석은 남은 시간 동안 유지돼요. 시간 안에 다시 결제할 수 있어요.</Notice>
                        <ButtonRow>
                            <Button variant="secondary" disabled>
                                좌석 다시 선택
                            </Button>
                            <Button onClick={handlePay} disabled={isPaying}>
                                다시 결제하기
                            </Button>
                        </ButtonRow>
                    </CenterColumn>
                )}

                {view === 'expired' && (
                    <CenterColumn>
                        <StatusCard icon={<ClockIcon />} title="결제 시간이 지났어요">
                            선택한 좌석이 해제됐어요.
                            <br />
                            다시 예매하려면 대기열부터 다시 참여해 주세요.
                        </StatusCard>
                        <Card>
                            <div className="flex items-center gap-4">
                                <PosterPlaceholder className="h-[74px] w-14" />
                                <div className="min-w-0">
                                    <p className="truncate text-[15px] font-bold text-gray-900">{booking.performanceTitle}</p>
                                    <p className="text-[13px] text-gray-500">{formatDateTime(booking.startsAt)}</p>
                                    <p className="text-[13px] text-gray-700">{formatSeatSummary(booking.seats)}</p>
                                </div>
                            </div>
                        </Card>
                        <ButtonRow>
                            <Button variant="secondary" onClick={() => navigate(performancePath)}>
                                공연 상세로
                            </Button>
                            <Button onClick={() => navigate(performancePath)}>다시 예매하기</Button>
                        </ButtonRow>
                    </CenterColumn>
                )}
            </main>
        </div>
    )
}

/**
 * 예매·결제 상태로 보여 줄 화면 결정
 *
 * 결제가 진행 중이면 기한보다 먼저 봄 (서버도 진행 중 검사를 기한 검사보다 먼저 함)
 * Y-06 전까지는 승인돼도 예매가 PENDING_PAYMENT라 paymentStatus로 완료 판단
 */
function getPaymentView(booking: Booking, isTimeOver: boolean): PaymentView {
    if (booking.paymentStatus === 'PROCESSING') {
        return 'processing'
    }

    if (booking.paymentStatus === 'RECONCILING') {
        return 'reconciling'
    }

    if (booking.paymentStatus === 'APPROVED' || booking.status === 'CONFIRMED') {
        return 'approved'
    }

    if (booking.status === 'EXPIRED' || booking.status === 'CANCELED' || isTimeOver) {
        return 'expired'
    }

    return booking.paymentStatus === 'FAILED' ? 'failed' : 'payment'
}

// 회원이 토스 결제창을 닫은 경우 (실패가 아니므로 안내 없이 결제 화면 유지)
function isTossUserCancel(error: unknown): boolean {
    return typeof error === 'object' && error !== null && 'code' in error && error.code === 'USER_CANCEL'
}

// 결제창을 열기까지의 오류 안내 (서버 오류는 서버 message, 토스·설정 오류는 Error message)
function getPayErrorMessage(error: unknown): string {
    if (isAxiosError<{code: string, message: string}>(error)) {
        return error.response?.data?.message ?? '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.'
    }

    if (error instanceof Error && error.message) {
        return error.message
    }

    return '결제창을 열지 못했어요. 다시 시도해 주세요.'
}

function getTimerPill(view: PaymentView, countdown: PaymentCountdown): TimerPillProps | null {
    if (view === 'approved') {
        return null
    }

    if (view === 'processing' || view === 'reconciling') {
        return {state: 'checking'}
    }

    if (view === 'expired') {
        return {state: 'expired', time: '00:00'}
    }

    const state = countdown.remainingSeconds < URGENT_SECONDS ? 'urgent' : 'default'
    return {state, time: countdown.label}
}

function PageMessage({message, showHomeLink}: {message: string; showHomeLink?: boolean}) {
    return (
        <main className="mx-auto max-w-[1320px] px-5 py-20 text-center">
            <p className="text-lg font-bold text-gray-900">{message}</p>
            {showHomeLink && (
                <Link to="/" className="mt-6 inline-block text-sm underline">
                    메인으로 이동
                </Link>
            )}
        </main>
    )
}

// 버튼 두 개를 반씩 나란히
function ButtonRow({children}: {children: ReactNode}) {
    return <div className="grid grid-cols-2 gap-3">{children}</div>
}