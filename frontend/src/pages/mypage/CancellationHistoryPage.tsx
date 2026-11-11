import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import {
    getMyBookings,
    type Booking,
} from '../../api/bookingApi'
import MyPageSidebar from './MyPageSidebar'

const PAGE_SIZE = 20

// 취소 이력이 있는 예매만 표시
function hasCancellationHistory(booking: Booking): boolean {
    return (
        booking.status === 'CANCELED' ||
        booking.cancellationId !== null ||
        booking.cancellationStatus !== null
    )
}

// 취소 처리 상태 표시
function getCancellationStatusLabel(booking: Booking): string {
    switch (booking.cancellationStatus) {
        case 'SUCCEEDED':
            return '취소 완료'
        case 'FAILED':
            return '취소 실패'
        case 'REQUESTED':
            return '취소 요청 접수'
        case 'PROCESSING':
            return '취소 처리 중'
        case 'RECONCILING':
            return '취소 결과 확인 중'
        default:
            return booking.status === 'CANCELED'
                ? '예매 취소'
                : '상태 확인 필요'
    }
}

// 날짜
function formatDate(value: string): string {
    const date = new Date(value)

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

function formatBookingDate(value: string): string {
    const date = new Date(value)

    if (Number.isNaN(date.getTime())) {
        return '날짜 확인 불가'
    }

    const parts = new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    }).formatToParts(date)

    const year = parts.find((part) => part.type === 'year')?.value
    const month = parts.find((part) => part.type === 'month')?.value
    const day = parts.find((part) => part.type === 'day')?.value

    return `${year}.${month}.${day}`
}

// 취소/환불 내역 화면
export default function CancellationHistoryPage() {
    const [bookings, setBookings] = useState<Booking[]>([])
    const [nextPage, setNextPage] = useState(1)
    const [hasMore, setHasMore] = useState(false)
    const [isLoading, setIsLoading] = useState(true)
    const [error, setError] = useState('')

    // 최초 예매 목록 조회
    useEffect(() => {
        let active = true

        async function loadInitial() {
            setIsLoading(true)
            setError('')

            try {
                const result = await getMyBookings(0, PAGE_SIZE)

                if (!active) return

                setBookings(result.items)
                setHasMore(result.items.length === PAGE_SIZE)
                setNextPage(1)
            } catch {
                if (active) {
                    setError('취소/환불 내역을 불러오지 못했습니다.')
                }
            } finally {
                if (active) setIsLoading(false)
            }
        }

        void loadInitial()

        return () => {
            active = false
        }
    }, [])

    // 이전 페이지의 예매 기록 추가 조회
    async function handleLoadMore() {
        if (isLoading || !hasMore) return

        setIsLoading(true)
        setError('')

        try {
            const result = await getMyBookings(nextPage, PAGE_SIZE)

            setBookings((prev) => [...prev, ...result.items])
            setNextPage((prev) => prev + 1)
            setHasMore(result.items.length === PAGE_SIZE)
        } catch {
            setError('추가 내역을 불러오지 못했습니다.')
        } finally {
            setIsLoading(false)
        }
    }

    // 전체 예매 중 취소 이력이 있는 예매만 필터링
    const cancellationBookings = bookings.filter(hasCancellationHistory)

    return (
        <div className="mx-auto w-full max-w-[1320px] px-5 py-10">
            {/* 마이페이지 제목 */}
            <h1 className="border-b border-[#333] pb-5 text-[28px] font-semibold">
                <Link to="/mypage" className="cursor-pointer">
                    마이페이지
                </Link>
            </h1>

            <div className="mt-8 flex flex-col gap-10 md:flex-row">
                <MyPageSidebar />

                <main className="min-w-0 flex-1">
                    {/* 취소/환불 내역 제목 */}
                    <div className="border-b border-[#ddd] pb-5">
                        <h2 className="text-[26px] font-bold text-[#222]">
                            취소/환불 내역
                        </h2>

                        <p className="mt-3 text-[14px] text-[#777]">
                            예매 취소 내역과 환불 처리 상태를 확인할 수 있습니다.
                        </p>
                    </div>

                    {/* 취소/환불 내역이 없는 경우 */}
                    {cancellationBookings.length === 0 &&
                        !isLoading &&
                        !hasMore &&
                        !error && (
                            <p className="py-20 text-center text-[14px] text-[#888]">
                                취소/환불 내역이 없습니다.
                            </p>
                        )}

                    {/* 취소/환불 내역 카드 목록 */}
                    <div className="mt-6 space-y-5">
                        {cancellationBookings.map((booking) => (
                            <article
                                key={booking.bookingId}
                                className="overflow-hidden rounded-xl border border-[#ddd] bg-white"
                            >
                                {/* 카드 상단: 예매일 및 예매번호 */}
                                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#e5e5e5] bg-[#fafafa] px-5 py-4">
                                    <span className="text-[13px] font-semibold text-[#222]">
                                        {formatBookingDate(booking.createdAt)} 예매
                                    </span>

                                    <span className="text-[12px] text-[#999]">
                                        예매번호 {booking.bookingId}
                                    </span>
                                </div>

                                {/* 카드 본문 */}
                                <div className="flex flex-col gap-5 px-5 py-5 sm:flex-row sm:items-start sm:justify-between">
                                    <div className="min-w-0 flex-1">
                                        {/* 취소 처리 상태 배지 */}
                                        <span className="inline-block rounded-md bg-[#eeecff] px-3 py-1.5 text-[12px] font-semibold text-[#4338CA]">
                                            {getCancellationStatusLabel(booking)}
                                        </span>

                                        {/* 공연명 */}
                                        <h3 className="mt-3 break-words text-[18px] font-bold text-[#222]">
                                            {booking.performanceTitle}
                                        </h3>

                                        {/* 공연 및 예매 정보 */}
                                        <dl className="mt-4 space-y-2 text-[13px]">
                                            <div className="flex gap-4">
                                                <dt className="w-[70px] shrink-0 text-[#888]">
                                                    관람일시
                                                </dt>

                                                <dd className="text-[#333]">
                                                    {formatDate(booking.startsAt)}
                                                </dd>
                                            </div>

                                            <div className="flex gap-4">
                                                <dt className="w-[70px] shrink-0 text-[#888]">
                                                    좌석
                                                </dt>

                                                <dd className="break-words text-[#333]">
                                                    {booking.seats.length > 0
                                                        ? booking.seats
                                                            .map((seat) => seat.label)
                                                            .join(', ')
                                                        : '좌석 정보 없음'}
                                                </dd>
                                            </div>

                                            <div className="flex gap-4">
                                                <dt className="w-[70px] shrink-0 text-[#888]">
                                                    예매 금액
                                                </dt>

                                                <dd className="font-medium text-[#333]">
                                                    {booking.totalAmount.toLocaleString()}원
                                                </dd>
                                            </div>
                                        </dl>
                                    </div>

                                    {/* 예매 상세보기 이동 */}
                                    <Link
                                        to={`/mypage/bookings/${booking.bookingId}`}
                                        className="self-start whitespace-nowrap rounded-md border border-[#4338CA] px-4 py-2 text-[13px] font-semibold text-[#4338CA] transition-colors hover:bg-[#f8f7ff]"
                                    >
                                        상세보기
                                    </Link>
                                </div>
                            </article>
                        ))}
                    </div>

                    {error && (
                        <p
                            role="alert"
                            className="mt-5 text-center text-[13px] text-red-600"
                        >
                            {error}
                        </p>
                    )}

                    {/* 이전 내역 추가 조회 */}
                    {hasMore && (
                        <button
                            type="button"
                            onClick={() => void handleLoadMore()}
                            disabled={isLoading}
                            className="mt-6 w-full rounded-lg border border-[#ddd] py-3 text-[14px] text-[#555] transition-colors enabled:hover:bg-[#fafafa] disabled:opacity-50"
                        >
                            {isLoading ? '불러오는 중...' : '이전 내역 더 보기'}
                        </button>
                    )}

                    {/* 최초 조회 로딩 */}
                    {isLoading && bookings.length === 0 && (
                        <p className="py-12 text-center text-[14px] text-[#888]">
                            내역을 불러오는 중...
                        </p>
                    )}
                </main>
            </div>
        </div>
    )
}
