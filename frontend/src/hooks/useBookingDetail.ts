import {useQuery} from '@tanstack/react-query'
import {getBooking} from '../api/bookingApi'
import type {Booking} from '../api/bookingApi'
import {getMockBooking} from '../mocks/bookings'
import {getClockOffsetMs} from './usePaymentCountdown'

// A파트 GET /bookings/{bookingId}가 준비되면 true로 변경
const USE_REAL_API = false

export interface BookingDetail {
    booking: Booking
    clockOffsetMs: number
}

/**
 * 결제 화면용 예매 상세 조회
 *
 * 응답을 받은 직후 서버 시계 차이도 같이 계산해서 반환
 * 결제 상태가 바뀔 수 있으므로 화면에 들어올 때마다 새로 조회
 */
export function useBookingDetail(bookingId: string | undefined) {
    return useQuery({
        queryKey: ['bookings', bookingId, 'detail'],
        queryFn: async (): Promise<BookingDetail> => {
            const booking = USE_REAL_API
                ? await getBooking(bookingId!)
                : await getMockBooking(bookingId!)

            return {booking, clockOffsetMs: getClockOffsetMs(booking.serverTime)}
        },
        enabled: Boolean(bookingId),
        staleTime: 0,
    })
}