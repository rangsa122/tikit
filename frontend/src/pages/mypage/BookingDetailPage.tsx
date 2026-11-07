import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import {
    getBooking,
    getTickets,
    type Booking,
    type BookingStatus,
    type PaymentAttemptStatus,
    type TicketListResponse,
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
                                    <aside className="relative border-t-2 border-dashed border-[#c7c3ee] bg-[#f8f7ff] p-6 lg:w-[235px] lg:shrink-0 lg:border-t-0 lg:border-l-2">

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
        </div>
    )
}
