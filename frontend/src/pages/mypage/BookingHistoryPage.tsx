import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import {
    getMyBookings,
    type BookingPage,
    type BookingStatus,
} from '../../api/bookingApi'
import MyPageSidebar from './MyPageSidebar'

// 예매 상태별 표시 문구
const statusLabels: Record<BookingStatus, string> = {
    PENDING_PAYMENT: '결제 대기',
    CONFIRMED: '예매 확정',
    EXPIRED: '기한 만료',
    CANCELED: '예매 취소',
}

// 공연 일시 표시
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

// 예매일 표시
function formatBookingDate(dateString: string): string {
    const date = new Date(dateString)

    if (Number.isNaN(date.getTime())) {
        return '예매일 확인 불가'
    }

    const parts = new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    }).formatToParts(date)

    const getPart = (type: string) =>
        parts.find((part) => part.type === type)?.value ?? ''

    return `${getPart('year')}.${getPart('month')}.${getPart('day')}`
}

// 마이페이지 예매 내역 화면
export default function BookingHistoryPage() {
    const [bookings, setBookings] = useState<BookingPage | null>(null)
    const [currentPage, setCurrentPage] = useState(0)
    const [isLoading, setIsLoading] = useState(true)
    const [error, setError] = useState('')

    // 내 예매 목록 API 조회
    useEffect(() => {
        let active = true

        async function loadBookings() {
            setIsLoading(true)
            setError('')

            try {
                const data = await getMyBookings(currentPage, 10, 'CONFIRMED')

                if (!active) return

                setBookings(data)
            } catch {
                if (!active) return

                setBookings(null)
                setError('예매 내역을 불러오지 못했습니다.')
            } finally {
                if (active) setIsLoading(false)
            }
        }

        void loadBookings()

        return () => {
            active = false
        }
    }, [currentPage])

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

                    <div className="pb-2">
                        <h2 className="text-[26px] font-bold">
                            예매 내역
                        </h2>

                        <div className="mt-1 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                            <p className="text-[14px] text-[#777]">
                                예매한 공연과 티켓 정보를 확인할 수 있습니다.
                            </p>

                            <p className="shrink-0 text-right text-[14px] text-[#555]">
                                총{' '}
                                <span className="text-[19px] font-bold text-[#4338CA]">
                                    {bookings?.totalElements ?? '—'}
                                </span>
                                건
                            </p>
                        </div>
                    </div>

                    {/* 예매 목록 */}
                    <div className="mt-6 space-y-5">
                        {isLoading ? (
                            <p className="py-12 text-center text-[14px] text-[#777]">
                                예매 내역을 불러오는 중...
                            </p>
                        ) : error ? (
                            <p
                                role="alert"
                                className="py-12 text-center text-[14px] text-red-600"
                            >
                                {error}
                            </p>
                        ) : bookings && bookings.items.length === 0 ? (
                            <div className="rounded-xl border border-[#ddd] px-5 py-16 text-center">
                                <p className="text-[14px] text-[#777]">
                                    예매 내역이 없습니다.
                                </p>
                            </div>
                        ) : (
                            bookings?.items.map((booking) => (
                                <article
                                    key={booking.bookingId}
                                    className="overflow-hidden rounded-xl border border-[#ddd] bg-white"
                                >
                                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#e5e5e5] bg-[#fafafa] px-5 py-4">
                                        <span className="text-[14px] font-semibold text-[#222]">
                                            {formatBookingDate(booking.createdAt)} 예매
                                        </span>

                                        <span className="text-[13px] text-[#888]">
                                            예매번호 {booking.bookingId}
                                        </span>
                                    </div>

                                    {/* 공연 정보 */}
                                    <div className="flex flex-col gap-5 p-5 sm:flex-row sm:items-start">

                                        {/* 포스터 임시 영역 */}
                                        <div className="flex aspect-[3/4] w-[100px] shrink-0 flex-col items-center justify-center gap-2 rounded-md bg-[#29264e] text-center sm:w-[112px]">
                                            <span className="text-[17px] font-bold tracking-wider text-[#c5bfff]">
                                                TIKIT
                                            </span>
                                            <span className="text-[11px] text-white">
                                                공연 이미지
                                            </span>
                                        </div>

                                        {/* 공연 상세 정보 */}
                                        <div className="min-w-0 flex-1">
                                            <span
                                                className={
                                                    booking.status === 'CONFIRMED'
                                                        ? 'inline-block rounded bg-[#eeecff] px-2 py-1 text-[12px] font-medium text-[#4338CA]'
                                                        : 'inline-block rounded bg-[#f2f2f2] px-2 py-1 text-[12px] font-medium text-[#555]'
                                                }
                                            >
                                                {statusLabels[booking.status]}
                                            </span>

                                            <h3 className="mt-3 break-words text-[18px] font-bold text-[#222]">
                                                {booking.performanceTitle}
                                            </h3>

                                            <dl className="mt-4 space-y-2 text-[13px]">
                                                <div className="flex gap-4">
                                                    <dt className="w-[62px] shrink-0 text-[#888]">
                                                        관람일시
                                                    </dt>
                                                    <dd className="font-medium text-[#333]">
                                                        {formatDate(booking.startsAt)}
                                                    </dd>
                                                </div>

                                                <div className="flex gap-4">
                                                    <dt className="w-[62px] shrink-0 text-[#888]">
                                                        좌석
                                                    </dt>
                                                    <dd className="font-medium text-[#333]">
                                                        {booking.seats.length > 0
                                                            ? booking.seats
                                                                .map((seat) => seat.label)
                                                                .join(', ')
                                                            : '좌석 정보 없음'}
                                                    </dd>
                                                </div>

                                                <div className="flex gap-4">
                                                    <dt className="w-[62px] shrink-0 text-[#888]">
                                                        금액
                                                    </dt>
                                                    <dd className="font-medium text-[#333]">
                                                        {booking.totalAmount.toLocaleString()}원
                                                    </dd>
                                                </div>
                                            </dl>
                                        </div>

                                        <div className="flex shrink-0 justify-end sm:self-start">
                                            <Link
                                                to={`/mypage/bookings/${booking.bookingId}`}
                                                className="rounded-md border border-[#4338CA] px-5 py-2 text-center text-[13px] font-medium text-[#4338CA] transition-colors hover:bg-[#4338CA] hover:text-white"
                                            >
                                                상세보기
                                            </Link>
                                        </div>
                                    </div>
                                </article>
                            ))
                        )}
                    </div>

                    {/* 예매 목록 페이지네이션 */}
                    {!isLoading &&
                        !error &&
                        bookings !== null &&
                        bookings.totalPages > 1 && (
                            <div className="mt-8 flex items-center justify-center gap-5">
                                <button
                                    type="button"
                                    disabled={currentPage === 0}
                                    onClick={() => setCurrentPage((p) => p - 1)}
                                    className="rounded border border-[#ddd] px-4 py-2 text-[13px] hover:bg-[#f5f5f5] disabled:opacity-40"
                                >
                                    이전
                                </button>

                                <span className="text-[13px] text-[#555]">
                                    {currentPage + 1} / {bookings.totalPages}
                                </span>

                                <button
                                    type="button"
                                    disabled={
                                        currentPage >= bookings.totalPages - 1
                                    }
                                    onClick={() => setCurrentPage((p) => p + 1)}
                                    className="rounded border border-[#ddd] px-4 py-2 text-[13px] hover:bg-[#f5f5f5] disabled:opacity-40"
                                >
                                    다음
                                </button>
                            </div>
                        )}
                </main>
            </div>
        </div>
    )
}
