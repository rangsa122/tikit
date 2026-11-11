import { useEffect, useState, useRef } from 'react'
import { Link, useParams } from 'react-router'
import {
    getBooking,
    getTickets,
    type Booking,
    type BookingStatus,
    type PaymentAttemptStatus,
    type TicketListResponse,
    getCancellationQuote,
    type CancellationQuote,
    createCancellation,
    type CancellationResponse,
} from '../../api/bookingApi'
import { getPerformanceDetail } from '../../api/performanceApi'
import MyPageSidebar from './MyPageSidebar'

// 예매 상태
const statusLabels: Record<BookingStatus, string> = {
    PENDING_PAYMENT: '결제 대기',
    CONFIRMED: '예매 확정',
    EXPIRED: '기한 만료',
    CANCELED: '예매 취소',
}

// 결제 상태
const paymentStatusLabels: Record<PaymentAttemptStatus, string> = {
    READY: '결제 준비',
    PROCESSING: '결제 진행 중',
    APPROVED: '결제 승인',
    FAILED: '결제 실패',
    RECONCILING: '결제 확인 중',
}

// 날짜
function formatDate(dateString: string): string {
    const date = new Date(dateString)

    if (Number.isNaN(date.getTime())) {
        return '일시 확인 불가'
    }

    return date.toLocaleString('ko-KR', {
        timeZone: 'Asia/Seoul',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    })
}

// 예매 상세보기 화면
export default function BookingDetailPage() {
    const { bookingId } = useParams<{ bookingId: string }>()

    const [booking, setBooking] = useState <Booking | null>(null)
    const [isLoading, setIsLoading] = useState(true)
    const [error, setError] = useState('')

    const [ticketList, setTicketList] = useState<TicketListResponse | null>(null)
    const [isTicketsLoading, setIsTicketsLoading] = useState(true)
    const [ticketError, setTicketError] = useState('')

    const [venueName, setVenueName] = useState<string | null>(null)
    const [isVenueLoading, setIsVenueLoading] = useState(false)
    const [venueError, setVenueError] = useState(false)

    const [showIssuedTickets, setShowIssuedTickets] = useState(false)

    const [isCancelOpen, setIsCancelOpen] = useState(false)
    const [cancellationQuote, setCancellationQuote] = useState<CancellationQuote | null>(null)
    const [isQuoteLoading, setIsQuoteLoading] = useState(false)
    const [quoteError, setQuoteError] = useState('')
    const [cancelReason, setCancelReason] = useState('')
    const [isCancelSubmitting, setIsCancelSubmitting] = useState(false)
    const [cancelResult, setCancelResult] = useState<CancellationResponse | null>(null)
    const [cancelSubmitError, setCancelSubmitError] = useState('')
    const [isCancelStatusRefreshing, setIsCancelStatusRefreshing] = useState(false)
    const [cancelStatusRefreshError, setCancelStatusRefreshError] = useState('')

    // 취소 요청의 멱등키와 요청 내용 보관
    const cancellationRequestRef = useRef<{
        key: string
        bookingId: string
        reason: string
        expectedRefundAmount: number
        policyVersion: string
    } | null>(null)

    // 예매번호로 상세 정보 조회
    useEffect(() => {
        let active = true

        async function loadBooking() {
            setIsLoading(true)
            setError('')
            setBooking(null)

            if (!bookingId || !/^\d+$/.test(bookingId)) {
                setError('올바르지 않은 예매번호입니다.')
                setIsLoading(false)
                return
            }

            try {
                const data = await getBooking(bookingId)

                if (!active) return
                setBooking(data)
            } catch {
                if (!active) return
                setError('예매 상세 정보를 불러오지 못했습니다.')
            } finally {
                if (active) setIsLoading(false)
            }
        }

        void loadBooking()

        return () => {
            active = false
        }
    }, [bookingId])

    // 예매번호로 실제 발급 티켓 조회
    useEffect(() => {
        let active = true

        setTicketList(null)
        setTicketError('')

        if (!bookingId || !/^\d+$/.test(bookingId)) {
            setIsTicketsLoading(false)
            return
        }

        setIsTicketsLoading(true)

        void getTickets(bookingId)
            .then((data) => {
                if (!active) return
                setTicketList(data)
            })
            .catch(() => {
                if (!active) return
                setTicketError('발급 티켓 정보를 불러오지 못했습니다.')
            })
            .finally(() => {
                if (active) setIsTicketsLoading(false)
            })

        return () => {
            active = false
        }
    }, [bookingId])

    // 예매된 공연의 장소 조회
    useEffect(() => {
        const performanceId = booking?.performanceId
        let active = true

        setVenueName(null)
        setVenueError(false)

        if (performanceId == null) {
            setIsVenueLoading(false)
            return
        }

        async function loadVenue() {
            setIsVenueLoading(true)

            try {
                const data = await getPerformanceDetail(String(performanceId))

                if (!active) return

                // 공개 공연 응답의 venueName 확인
                if (
                    typeof data === 'object' &&
                    data !== null &&
                    'venueName' in data &&
                    typeof data.venueName === 'string'
                ) {
                    setVenueName(data.venueName.trim() || null)
                }
            } catch {
                if (!active) return
                setVenueError(true)
            } finally {
                if (active) setIsVenueLoading(false)
            }
        }

        void loadVenue()

        return () => {
            active = false
        }
    }, [booking?.performanceId])

    // 예매 취소 버튼 클릭 시 견적 조회
    async function handleOpenCancellationQuote() {
        if (!bookingId || isQuoteLoading) return

        setIsCancelOpen(true)
        setCancellationQuote(null)
        setQuoteError('')
        setIsQuoteLoading(true)
        setCancelReason('')

        try {
            const quote = await getCancellationQuote(bookingId)
            setCancellationQuote(quote)
        } catch (error) {
            setQuoteError(
                error instanceof Error
                    ? error.message
                    : '취소 견적을 불러오지 못했습니다.',
            )
        } finally {
            setIsQuoteLoading(false)
        }
    }

    // 취소 확인창 닫기
    function handleCloseCancellationQuote() {
        setIsCancelOpen(false)
    }

    // 전체 예매 취소 요청
    async function handleSubmitCancellation() {
        if (!bookingId || isCancelSubmitting || !cancellationQuote?.eligible) {
            return
        }

        // 이미 처리 중이거나 완료된 요청은 중복 실행 방지
        if (cancelResult && ['SUCCEEDED', 'REQUESTED', 'PROCESSING', 'RECONCILING'].includes(cancelResult.status)) {
            setCancelSubmitError('기존 취소 요청의 처리 상태를 먼저 확인해 주세요.')
            return
        }

        // 재시도가 허용되지 않은 실패는 재요청 차단
        if (cancelResult?.status === 'FAILED' && !cancelResult.retryAllowed) {
            setCancelSubmitError('현재는 취소 요청을 다시 진행할 수 없습니다.')
            return
        }

        const reason = cancelReason.trim()

        if (!reason || reason.length > 200) {
            setCancelSubmitError('취소 사유를 1~200자로 입력해 주세요.')
            return
        }

        const refundAmount = cancellationQuote.refundAmount
        const policyVersion = cancellationQuote.policyVersion?.trim()

        if (refundAmount == null || !policyVersion) {
            setCancelSubmitError('취소 견적을 다시 조회해 주세요.')
            return
        }

        // 이번 취소 요청 본문
        const request = {
            reason,
            expectedRefundAmount: refundAmount,
            policyVersion,
        }

        const previous = cancellationRequestRef.current

        // 이전 요청과 내용이 다르면 기존 멱등키로 요청하지 않음
        if (
            previous &&
            (previous.bookingId !== bookingId
                || previous.reason !== request.reason
                || previous.expectedRefundAmount !== request.expectedRefundAmount
                || previous.policyVersion !== request.policyVersion)
        ) {
            setCancelSubmitError('이전 취소 요청과 내용이 다릅니다. 기존 요청의 처리 상태를 먼저 확인해 주세요.',)
            return
        }

        // 동일한 요청이면 기존 키 재사용
        const idempotencyKey = previous?.key ?? crypto.randomUUID()

        // 새로운 요청일 때 키와 본문을 함께 저장
        if (!previous) {
            cancellationRequestRef.current = {
                key: idempotencyKey,
                bookingId,
                ...request,
            }
        }

        setIsCancelSubmitting(true)
        setCancelSubmitError('')
        setCancelResult(null)

        try {
            // 전체 취소 API 호출
            const result = await createCancellation(
                bookingId,
                request,
                idempotencyKey,
            )

            setCancelResult(result)

            // 취소 성공 시 예매 및 발급 티켓 정보 다시 조회
            if (result.status === 'SUCCEEDED') {
                setIsTicketsLoading(true)

                const [bookingRefresh, ticketsRefresh] =
                    await Promise.allSettled([
                        getBooking(bookingId),
                        getTickets(bookingId),
                    ])

                // 예매 상세 정보 갱신
                if (bookingRefresh.status === 'fulfilled') {
                    setBooking(bookingRefresh.value)
                } else {
                    setCancelSubmitError(
                        '취소 처리는 완료되었지만 예매 상태를 새로 불러오지 못했습니다. 페이지를 새로고침해 확인해 주세요.',
                    )
                }

                // 발급 티켓 정보 갱신
                if (ticketsRefresh.status === 'fulfilled') {
                    setTicketList(ticketsRefresh.value)
                    setTicketError('')
                } else {
                    setTicketList(null)
                    setTicketError(
                        '발급 티켓 정보를 새로 불러오지 못했습니다. 페이지를 새로고침해 주세요.',
                    )
                }

                setIsTicketsLoading(false)
            }

            // 재시도가 허용된 실패는 다음 요청에 새 키 사용
            if (result.status === 'FAILED' && result.retryAllowed) {
                cancellationRequestRef.current = null
            }
        } catch (error) {
            // 처리 결과가 불확실하므로 멱등키 유지
            setCancelSubmitError(
                error instanceof Error
                    ? error.message
                    : '취소 요청 결과를 확인하지 못했습니다.',
            )
        } finally {
            setIsCancelSubmitting(false)
        }
    }

    // 최신 예매 및 취소 상태 다시 조회
    async function handleRefreshCancellationStatus() {
        if (!bookingId || isCancelStatusRefreshing || isCancelSubmitting) {
            return
        }

        setIsCancelStatusRefreshing(true)
        setCancelStatusRefreshError('')

        try {
            // 예매 정보에 포함된 최신 취소 상태 조회
            const latestBooking = await getBooking(bookingId)
            setBooking(latestBooking)

            // 취소 완료된 예매는 티켓 정보도 갱신
            if (latestBooking.status === 'CANCELED') {
                setIsTicketsLoading(true)

                try {
                    const latestTickets = await getTickets(bookingId)
                    setTicketList(latestTickets)
                    setTicketError('')
                } catch {
                    setTicketError(
                        '발급 티켓 정보를 다시 불러오지 못했습니다.',
                    )
                } finally {
                    setIsTicketsLoading(false)
                }
            }
        } catch {
            setCancelStatusRefreshError(
                '취소 상태를 불러오지 못했습니다. 다시 시도해 주세요.',
            )
        } finally {
            setIsCancelStatusRefreshing(false)
        }
    }

    return (
        <div className="mx-auto w-full max-w-[1320px] px-5 py-10">
            <h1 className="border-b border-[#333] pb-5 text-[28px] font-semibold">
                <Link to="/mypage" className="cursor-pointer">
                    마이페이지
                </Link>
            </h1>

            <div className="mt-8 flex flex-col gap-10 md:flex-row">
                <MyPageSidebar />

                <main className="min-w-0 flex-1">
                    {/* 목록으로 돌아가기 */}
                    <Link
                        to="/mypage/bookings"
                        className="inline-flex items-center text-[14px] text-[#666] hover:text-[#4338CA]"
                    >
                        &lt; 예매 내역으로
                    </Link>

                    <div className="mt-5 border-b border-[#ddd] pb-5">
                        <h2 className="text-[26px] font-bold text-[#222]">
                            예매 상세보기
                        </h2>
                    </div>

                    {isLoading ? (
                        <p className="py-16 text-center text-[14px] text-[#777]">
                            예매 정보를 불러오는 중...
                        </p>
                    ) : error ? (
                        <p
                            role="alert"
                            className="py-16 text-center text-[14px] text-red-600"
                        >
                            {error}
                        </p>
                    ) : booking ? (
                        <div className="mt-8">
                            <h3 className="mb-4 text-[17px] font-semibold text-[#333]">
                                예매 확인 정보
                            </h3>

                            <article className="overflow-hidden rounded-2xl border border-[#dad7f7] bg-white shadow-sm">

                                <div className="flex flex-col lg:flex-row">
                                    <div className="min-w-0 flex-1">
                                        <div className="flex items-center justify-between bg-[#4338CA] px-6 py-4">
                                            <span className="text-[18px] font-bold tracking-wider text-white">
                                                TIKIT
                                            </span>

                                            <span className="text-[11px] font-medium tracking-[0.15em] text-[#ddd9ff]">
                                                BOOKING CONFIRMATION
                                            </span>
                                        </div>

                                        {/* 공연 정보 */}
                                        <div className="p-6 sm:p-8">
                                            <p className="text-[12px] font-semibold tracking-[0.12em] text-[#999]">
                                                PERFORMANCE
                                            </p>

                                            <h3 className="mt-3 break-words text-[24px] font-bold text-[#222]">
                                                {booking.performanceTitle}
                                            </h3>

                                            <div className="mt-8 grid grid-cols-1 gap-6 sm:grid-cols-2">
                                                <div>
                                                    <p className="text-[14px] text-[#888]">
                                                        관람일시
                                                    </p>
                                                    <p className="mt-2 text-[16px] font-semibold text-[#333]">
                                                        {formatDate(booking.startsAt)}
                                                    </p>
                                                </div>

                                                <div>
                                                    <p className="text-[14px] text-[#888]">
                                                        예매일시
                                                    </p>
                                                    <p className="mt-2 text-[16px] font-semibold text-[#333]">
                                                        {formatDate(booking.createdAt)}
                                                    </p>
                                                </div>
                                            </div>

                                            <div className="mt-6 border-t border-[#eee] pt-5">
                                                <p className="text-[16px] text-[#777]">
                                                    공연 장소
                                                </p>

                                                <p className="mt-2 text-[18px] font-semibold text-[#333]">
                                                    {isVenueLoading
                                                        ? '공연 장소 확인 중...'
                                                        : venueError
                                                            ? '공연 장소 정보를 불러오지 못했습니다.'
                                                            : venueName || '공연 장소 정보가 없습니다.'}
                                                </p>
                                            </div>

                                            {/* 좌석 정보 */}
                                            <section className="mt-8 border-t border-dashed border-[#ddd] pt-6">
                                                <div className="flex items-center justify-between">
                                                    <h4 className="text-[17px] font-bold text-[#222]">
                                                        좌석 정보
                                                    </h4>

                                                    <span className="text-[13px] text-[#777]">
                                                        총 {booking.seats.length}석
                                                    </span>
                                                </div>

                                                {booking.seats.length === 0 ? (
                                                    <p className="mt-5 text-[14px] text-[#888]">
                                                        좌석 정보가 없습니다.
                                                    </p>
                                                ) : (
                                                    <div className="mt-4 space-y-3">
                                                        {booking.seats.map((seat) => (
                                                            <div
                                                                key={seat.seatId}
                                                                className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-[#fafafa] px-4 py-3"
                                                            >
                                                                <div>
                                                                    <p className="text-[14px] font-semibold text-[#333]">
                                                                        {seat.label}
                                                                    </p>

                                                                    <p className="mt-1 text-[12px] text-[#888]">
                                                                        {seat.grade} 등급
                                                                    </p>
                                                                </div>

                                                                <span className="text-[14px] font-semibold text-[#333]">
                                                                    {seat.price.toLocaleString()}원
                                                                </span>
                                                            </div>
                                                        ))}
                                                    </div>
                                                )}
                                            </section>
                                        </div>
                                    </div>

                                    {/* 티켓 절취선 오른쪽 영역 */}
                                    <aside className="relative flex flex-col border-t-2 border-dashed border-[#c7c3ee] bg-[#f8f7ff] p-6 lg:w-[235px] lg:shrink-0 lg:border-t-0 lg:border-l-2">

                                        {/* 예매번호 */}
                                        <div>
                                            <p className="text-[11px] font-semibold tracking-[0.12em] text-[#999]">
                                                BOOKING NO.
                                            </p>

                                            <p className="mt-2 break-all text-[23px] font-bold text-[#4338CA]">
                                                {booking.bookingId}
                                            </p>
                                        </div>

                                        {/* 예매 상태 */}
                                        <div className="mt-7">
                                            <p className="text-[12px] text-[#888]">
                                                예매 상태
                                            </p>

                                            <span
                                                className={
                                                    booking.status === 'CONFIRMED'
                                                        ? 'mt-2 inline-block rounded-md bg-[#eae8ff] px-3 py-2 text-[13px] font-semibold text-[#4338CA]'
                                                        : 'mt-2 inline-block rounded-md bg-[#ececec] px-3 py-2 text-[13px] font-semibold text-[#555]'
                                                }
                                            >
                                                {statusLabels[booking.status]}
                                            </span>
                                        </div>

                                        {/* 결제 상태 */}
                                        <div className="mt-7">
                                            <p className="text-[12px] text-[#888]">
                                                결제 상태
                                            </p>

                                            <p className="mt-2 text-[14px] font-semibold text-[#333]">
                                                {booking.paymentStatus
                                                    ? paymentStatusLabels[booking.paymentStatus] ?? booking.paymentStatus
                                                    : '결제 정보 없음'}
                                            </p>
                                        </div>

                                        {/* 총 예매 금액 */}
                                        <div className="mt-7 border-t border-[#dedbf5] pt-6">
                                            <p className="text-[12px] text-[#888]">
                                                총 예매 금액
                                            </p>

                                            <p className="mt-2 break-words text-[23px] font-bold text-[#4338CA]">
                                                {booking.totalAmount.toLocaleString()}원
                                            </p>
                                        </div>

                                        {/* 티켓 콘셉트 장식 문구 */}
                                        <div className="mt-10 border-t border-dashed border-[#c7c3ee] pt-5">
                                            <p className="text-center text-[11px] font-semibold tracking-[0.2em] text-[#aaa5d5]">
                                                TIKIT · MY BOOKING
                                            </p>
                                        </div>

                                        {/* 예매 취소 버튼 */}
                                        {booking.status === 'CONFIRMED' && (
                                            <div className="mt-auto pt-10">
                                                <button
                                                    type="button"
                                                    onClick={() => void handleOpenCancellationQuote()}
                                                    disabled={isQuoteLoading}
                                                    className="w-full rounded-lg border border-[#c7c3ee] bg-white px-4 py-3 text-[14px] font-semibold text-[#4338CA] transition-colors enabled:hover:border-[#4338CA] enabled:hover:bg-[#eeecff] disabled:opacity-50"
                                                >
                                                    예매 취소
                                                </button>
                                            </div>
                                        )}
                                    </aside>
                                </div>
                            </article>

                            {/* 실제 발급 티켓 펼치기/접기 */}
                            <button
                                type="button"
                                onClick={() => setShowIssuedTickets((prev) => !prev)}
                                aria-expanded={showIssuedTickets}
                                className="mt-8 flex w-full items-center justify-between rounded-xl border border-[#d8d6ee] bg-[#f8f7ff] px-6 py-4 text-left hover:bg-[#eeecff]"
                            >
                                <span className="flex items-center gap-3">
                                    <span className="text-[15px] font-semibold text-[#333]">
                                        발급 티켓 확인
                                    </span>
                                </span>

                                <span className="text-[13px] font-medium text-[#4338CA]">
                                    {showIssuedTickets ? '접기 ▲' : '펼쳐보기 ▼'}
                                </span>
                            </button>

                            {showIssuedTickets && (
                                <section className="mt-5 rounded-2xl border border-[#ddd] bg-white p-6 sm:p-8">

                                {/* 발급 티켓 목록 */}
                                    <div className="flex items-center justify-between">
                                        <h3 className="text-[19px] font-bold text-[#222]">
                                            발급 티켓
                                        </h3>

                                        {!isTicketsLoading && !ticketError && ticketList && (
                                            <span className="text-[13px] text-[#777]">
                                        총 {ticketList.items.length}장
                                    </span>
                                        )}
                                    </div>

                                    <p className="mt-2 text-[13px] text-[#888]">
                                        예매에 발급된 티켓과 사용 가능 여부를 확인할 수 있습니다.
                                    </p>

                                    <div className="mt-5 border-t border-[#eee] pt-5">
                                        {isTicketsLoading ? (
                                            <p className="py-6 text-center text-[14px] text-[#777]">
                                                발급 티켓을 불러오는 중...
                                            </p>
                                        ) : ticketError ? (
                                            <p
                                                role="alert"
                                                className="py-6 text-center text-[14px] text-red-600"
                                            >
                                                {ticketError}
                                            </p>
                                        ) : ticketList && ticketList.items.length > 0 ? (
                                            <div className="space-y-4">
                                                {ticketList.items.map((ticket) => (
                                                    <article
                                                        key={ticket.ticketId}
                                                        className="overflow-hidden rounded-xl border border-[#ddd]"
                                                    >
                                                        <div className="flex flex-wrap items-center justify-between gap-3 bg-[#fafafa] px-5 py-4">
                                                    <span className="text-[13px] font-semibold text-[#555]">
                                                        티켓번호 {ticket.ticketId}
                                                    </span>

                                                            <span
                                                                className={
                                                                    ticket.status === 'VALID'
                                                                        ? 'rounded-md bg-[#eeecff] px-3 py-1 text-[12px] font-semibold text-[#4338CA]'
                                                                        : 'rounded-md bg-[#eee] px-3 py-1 text-[12px] font-semibold text-[#777]'
                                                                }
                                                            >
                                                        {ticket.status === 'VALID'
                                                            ? '사용 가능'
                                                            : '무효 처리'}
                                                    </span>
                                                        </div>

                                                        {/* 티켓 정보 */}
                                                        <div className="px-5 py-5">
                                                            <h4 className="text-[17px] font-bold text-[#222]">
                                                                {ticket.performanceTitle}
                                                            </h4>

                                                            <dl className="mt-4 space-y-3 text-[13px]">
                                                                <div className="flex gap-4">
                                                                    <dt className="w-[65px] shrink-0 text-[#888]">
                                                                        관람일시
                                                                    </dt>
                                                                    <dd className="font-medium text-[#333]">
                                                                        {formatDate(ticket.startsAt)}
                                                                    </dd>
                                                                </div>

                                                                <div className="flex gap-4">
                                                                    <dt className="w-[65px] shrink-0 text-[#888]">
                                                                        좌석
                                                                    </dt>
                                                                    <dd className="font-medium text-[#333]">
                                                                        {ticket.seatLabel}
                                                                    </dd>
                                                                </div>
                                                            </dl>
                                                        </div>
                                                    </article>
                                                ))}
                                            </div>
                                        ) : (
                                            <p className="py-6 text-center text-[14px] text-[#888]">
                                                발급된 티켓이 없습니다.
                                            </p>
                                        )}
                                    </div>
                                </section>
                            )}

                            <p className="mt-5 text-center text-[12px] text-[#999]">
                                예매 상태와 결제 정보는 조회 시점에 따라 달라질 수 있습니다.
                            </p>
                        </div>
                    ) : null}
                </main>
            </div>

            {/* 예매 취소 견적 확인창 */}
            {isCancelOpen && (
                <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 px-4">
                    <section
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="cancel-dialog-title"
                        className="w-full max-w-[440px] rounded-2xl bg-white p-7 shadow-xl"
                    >

                        <h2 id="cancel-dialog-title" className="text-[22px] font-bold text-[#222]">
                            예매 취소 확인
                        </h2>

                        <p className="mt-3 text-[14px] text-[#777]">
                            취소 시 예상 환불 금액을 확인해 주세요.
                            <br />
                            실제 환불 금액은 취소 처리 시 최종 확정됩니다.
                        </p>

                        {isQuoteLoading ? (
                            <p className="py-10 text-center text-[14px] text-[#777]">
                                취소 견적을 조회하고 있습니다...
                            </p>
                        ) : quoteError ? (
                            <p role="alert" className="py-8 text-[14px] text-red-600">
                                {quoteError}
                            </p>
                        ) : cancellationQuote ? (
                            <div className="mt-6">
                                {!cancellationQuote.eligible && (
                                    <p
                                        role="alert"
                                        className="mb-5 rounded-lg bg-red-50 p-4 text-[14px] text-red-600"
                                    >
                                        현재 해당 예매는 취소할 수 없습니다.
                                    </p>
                                )}

                                <div className="space-y-4 border-t border-[#eee] pt-6">
                                    <div className="flex items-center justify-between text-[14px]">
                                        <span className="text-[#888]">결제 금액</span>
                                        <span className="font-medium text-[#333]">
                                            {cancellationQuote.paidAmount == null
                                                ? '확인 불가'
                                                : `${cancellationQuote.paidAmount.toLocaleString()}원`}
                                        </span>
                                    </div>

                                    <div className="flex items-center justify-between text-[14px]">
                                        <span className="text-[#888]">취소 수수료</span>
                                        <span className="font-medium text-[#333]">
                                            {cancellationQuote.feeAmount == null
                                                ? '확인 불가'
                                                : `${cancellationQuote.feeAmount.toLocaleString()}원`}
                                        </span>
                                    </div>

                                    <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-[#F6F4FF] px-4 py-5">
                                        <span className="text-[17px] font-semibold text-[#302E44]">
                                            예상 환불 금액
                                        </span>

                                        <span className="text-[21px] font-bold text-[#4338CA]">
                                            {cancellationQuote.refundAmount == null
                                                ? '확인 불가'
                                                : `${cancellationQuote.refundAmount.toLocaleString()}원`}
                                        </span>
                                    </div>
                                </div>


                                {/* 취소 이유 입력 */}
                                {cancellationQuote.eligible && (
                                    <div className="mt-6 border-t border-[#eee] pt-5">
                                        <label
                                            htmlFor="cancel-reason"
                                            className="block text-[14px] font-semibold text-[#333]"
                                        >
                                            취소 사유 <span className="text-red-500">*</span>
                                        </label>

                                        <textarea
                                            id="cancel-reason"
                                            value={cancelReason}
                                            onChange={(event) => setCancelReason(event.target.value)}
                                            maxLength={200}
                                            rows={3}
                                            placeholder="취소 사유를 입력해 주세요."
                                            className="mt-3 w-full resize-none rounded-lg border border-[#ddd] px-4 py-3 text-[14px] text-[#333] outline-none ring-0 focus:border-[#4338CA] focus:outline-none focus:ring-0 focus-visible:outline-none focus-visible:ring-0"
                                            style={{ outline: 'none', boxShadow: 'none' }}
                                        />

                                        <div className="mt-1 flex justify-between text-[12px] text-[#888]">
                                            <span>취소 사유를 입력해야 취소 요청이 가능합니다.</span>
                                            <span>{cancelReason.length}/200</span>
                                        </div>
                                    </div>
                                )}
                            </div>
                        ) : null}

                        {/* 취소 요청 오류 메시지 */}
                        {cancelSubmitError && (
                            <p
                                role="alert"
                                className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-[13px] text-red-600"
                            >
                                {cancelSubmitError}
                            </p>
                        )}

                        {/* 취소 및 환불 처리 결과 */}
                        {cancelResult && (
                            <div
                                role="status"
                                aria-live="polite"
                                className="mt-4 rounded-lg bg-[#F6F4FF] px-4 py-4"
                            >
                                <p className="text-[14px] font-semibold text-[#4338CA]">
                                    {cancelResult.status === 'SUCCEEDED'
                                        ? '예매 취소 처리 완료'
                                        : cancelResult.status === 'FAILED'
                                            ? '예매 취소 요청 실패'
                                            : cancelResult.status === 'RECONCILING'
                                                ? '취소 결과 확인 중'
                                                : cancelResult.status === 'PROCESSING'
                                                    ? '취소 처리 중'
                                                    : cancelResult.status === 'REQUESTED'
                                                        ? '취소 요청 접수'
                                                        : '취소 상태 확인 필요'}
                                </p>

                                <p className="mt-2 text-[13px] leading-5 text-[#666]">
                                    {cancelResult.status === 'SUCCEEDED'
                                        ? '예매 및 결제 취소 처리가 완료되었습니다. 카드사 환불 반영까지는 시간이 걸릴 수 있습니다.'
                                        : cancelResult.status === 'FAILED'
                                            ? cancelResult.retryAllowed
                                                ? '취소 요청에 실패했습니다. 확인 후 다시 요청할 수 있습니다.'
                                                : '취소 요청에 실패했습니다. 현재는 재요청할 수 없습니다.'
                                            : cancelResult.status === 'RECONCILING'
                                                ? '결제 취소 결과를 확인하고 있습니다. 중복으로 요청하지 마세요.'
                                                : cancelResult.status === 'PROCESSING'
                                                    ? '결제 취소가 진행 중입니다. 처리 결과를 확인해 주세요.'
                                                    : cancelResult.status === 'REQUESTED'
                                                        ? '취소 요청이 접수되었습니다. 처리 결과를 확인해 주세요.'
                                                        : '현재 취소 상태를 다시 확인해 주세요.'}
                                </p>

                                {/* 실패 코드가 있는 경우 */}
                                {cancelResult.status === 'FAILED' && cancelResult.failureCode && (
                                    <p className="mt-2 text-[12px] text-[#888]">
                                        오류 코드: {cancelResult.failureCode}
                                    </p>
                                )}
                            </div>
                        )}

                        {/* 취소 요청 후 최신 상태 확인 */}
                        {(cancelResult || cancellationRequestRef.current) && (
                            <div className="mt-4">
                                <div className="flex items-center justify-between gap-3">
                                    <p aria-live="polite" className="text-[12px] text-[#777]">
                                        최신 조회 상태:{' '}
                                        <span className="font-semibold text-[#4338CA]">
                                            {booking?.cancellationStatus ?? '확인 전'}
                                        </span>
                                    </p>

                                    <button
                                        type="button"
                                        onClick={() => void handleRefreshCancellationStatus()}
                                        disabled={isCancelStatusRefreshing || isCancelSubmitting}
                                        className="shrink-0 rounded-lg border border-[#ddd] px-3 py-2 text-[12px] font-medium text-[#555] enabled:hover:bg-[#f8f7ff] disabled:opacity-50"
                                    >
                                        {isCancelStatusRefreshing
                                            ? '확인 중...'
                                            : '상태 다시 확인'}
                                    </button>
                                </div>

                                {cancelStatusRefreshError && (
                                    <p role="alert" className="mt-2 text-[12px] text-red-600">
                                        {cancelStatusRefreshError}
                                    </p>
                                )}
                            </div>
                        )}

                        <div className="mt-7 flex gap-3">
                            <button
                                type="button"
                                onClick={handleCloseCancellationQuote}
                                className="flex-1 rounded-lg bg-[#f1f1f1] py-3 text-[14px] font-semibold text-[#555]"
                            >
                                돌아가기
                            </button>


                            <button
                                type="button"
                                onClick={() => void handleSubmitCancellation()}
                                disabled={
                                    // 견적 조회 또는 취소 요청 진행 중
                                    isQuoteLoading || isCancelSubmitting || isCancelStatusRefreshing ||

                                    // 취소할 수 없는 예매
                                    booking?.status !== 'CONFIRMED' || !cancellationQuote?.eligible ||

                                    // 취소 사유 및 견적 정보 검사
                                    !cancelReason.trim() || cancelReason.trim().length > 200 || cancellationQuote.refundAmount == null || !cancellationQuote.policyVersion?.trim() ||

                                    // 이미 접수되었거나 완료된 요청 차단
                                    (cancelResult !== null && (cancelResult.status !== 'FAILED' || !cancelResult.retryAllowed)) ||

                                    // 예매 상세 조회에서 진행 중인 취소가 확인된 경우
                                    (booking?.cancellationStatus != null && booking.cancellationStatus !== 'FAILED') ||

                                    // 이전 취소가 실패했지만 재시도 허용 여부를 모르는 경우
                                    (booking?.cancellationStatus === 'FAILED' && !(cancelResult?.status === 'FAILED' && cancelResult.retryAllowed))
                                }
                                className="flex-1 rounded-lg bg-[#4338CA] py-3 text-[14px] font-semibold text-white transition-colors enabled:hover:bg-[#372da8] disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                {isCancelSubmitting ? '취소 요청 중...' : '예매 취소'}
                            </button>

                        </div>
                    </section>
                </div>
            )}
        </div>
    )
}
