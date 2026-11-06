import type {BookedSeat, Booking} from '../api/bookingApi'

const SECOND_MS = 1000
const SEOUL_OFFSET_MS = 9 * 60 * 60 * SECOND_MS
const MOCK_DELAY_MS = 300

// 가짜 예매 시간의 기준 (처음 연 시각을 탭에 저장 → 새로고침해도 유지, 탭을 닫으면 다시 정해짐)
const OPENED_AT_KEY = 'tikit-mock-opened-at'
const OPENED_AT_MS = getOpenedAtMs()

// 서버 시계가 PC보다 빠른 예매 (예매 번호: 몇 초 빠른지)
const SERVER_CLOCK_SKEW_SECONDS: Record<number, number> = {9: 120}

const mockSeats: BookedSeat[] = [
    {seatId: 101, label: 'A열 12번', grade: 'VIP', price: 150000},
    {seatId: 102, label: 'A열 13번', grade: 'VIP', price: 150000},
]

const baseBooking: Booking = {
    bookingId: 0,
    sessionId: 1,
    performanceId: 1,
    performanceTitle: '디즈니 인 콘서트 〈Beyond the Magic〉',
    startsAt: '2026-11-20T19:30:00+09:00',
    status: 'PENDING_PAYMENT',
    seats: mockSeats,
    totalAmount: 300000,
    expiresAt: afterSeconds(300),
    paymentAttemptId: null,
    paymentStatus: null,
    cancellationId: null,
    cancellationStatus: null,
    createdAt: afterSeconds(0),
    serverTime: afterSeconds(0),
}

/**
 * 결제 화면 확인용 가짜 예매
 *
 * 주소창에 /bookings/{bookingId}/payment 입력해서 확인
 * 1 결제 전 / 2 READY / 3 FAILED / 4 PROCESSING(10초 후 0) / 5 RECONCILING(기한 지남)
 * 6 APPROVED / 7 결제 전(15초 후 시간 초과) / 8 만료된 예매
 * 9 서버 시계가 PC보다 2분 빠름 (타이머가 05:00부터 시작해야 정상)
 */
export const mockBookings: Booking[] = [
    {...baseBooking, bookingId: 1},
    {...baseBooking, bookingId: 2, expiresAt: afterSeconds(240), paymentAttemptId: 20, paymentStatus: 'READY'},
    {...baseBooking, bookingId: 3, expiresAt: afterSeconds(180), paymentAttemptId: 30, paymentStatus: 'FAILED'},
    {...baseBooking, bookingId: 4, expiresAt: afterSeconds(10), paymentAttemptId: 40, paymentStatus: 'PROCESSING'},
    {...baseBooking, bookingId: 5, expiresAt: afterSeconds(-30), paymentAttemptId: 50, paymentStatus: 'RECONCILING'},
    {...baseBooking, bookingId: 6, expiresAt: afterSeconds(120), paymentAttemptId: 60, paymentStatus: 'APPROVED'},
    {...baseBooking, bookingId: 7, expiresAt: afterSeconds(15)},
    {...baseBooking, bookingId: 8, status: 'EXPIRED', expiresAt: afterSeconds(-60)},
    {...baseBooking, bookingId: 9, expiresAt: afterSeconds(300 + 120)},
]

/**
 * 가짜 예매 상세 조회
 *
 * 실제 서버처럼 serverTime만 부를 때마다 현재 시각으로 바꿔서 반환
 * (서버 시계가 빠른 예매는 그만큼 더한 시각)
 */
export async function getMockBooking(bookingId: string): Promise<Booking> {
    await new Promise(resolve => setTimeout(resolve, MOCK_DELAY_MS))

    const booking = mockBookings.find(item => item.bookingId === Number(bookingId))

    if (!booking) {
        throw new Error('예매 정보를 찾을 수 없습니다.')
    }

    const skewSeconds = SERVER_CLOCK_SKEW_SECONDS[booking.bookingId] ?? 0

    return {...booking, serverTime: toSeoulIsoString(Date.now() + skewSeconds * SECOND_MS)}
}

// 처음 연 시각을 sessionStorage에서 꺼냄 (없으면 지금 시각을 저장)
function getOpenedAtMs(): number {
    try {
        const saved = Number(sessionStorage.getItem(OPENED_AT_KEY))

        if (saved > 0) {
            return saved
        }

        const now = Date.now()
        sessionStorage.setItem(OPENED_AT_KEY, String(now))
        return now
    } catch {
        return Date.now()
    }
}

// 기준 시각에서 몇 초 뒤를 한국 시간 문자열로 (음수면 이미 지난 시각)
function afterSeconds(seconds: number): string {
    return toSeoulIsoString(OPENED_AT_MS + seconds * SECOND_MS)
}

// 실제 서버 응답처럼 한국 시간 문자열로 변환 (예: 2026-11-03T14:05:00.000+09:00)
function toSeoulIsoString(epochMs: number): string {
    return new Date(epochMs + SEOUL_OFFSET_MS).toISOString().replace('Z', '+09:00')
}