import type {BookedSeat} from '../api/bookingApi'

const DATE_TIME_FORMAT = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
})

// 300000 → 300,000원
export function formatWon(amount: number): string {
    return `${amount.toLocaleString('ko-KR')}원`
}

// → VIP석 A열 12번
export function formatSeat(seat: BookedSeat): string {
    return `${seat.grade}석 ${seat.label}`
}

// → VIP석 A열 12번 외 1석
export function formatSeatSummary(seats: BookedSeat[]): string {
    if (seats.length === 0) {
        return ''
    }

    const first = formatSeat(seats[0])
    return seats.length === 1 ? first : `${first} 외 ${seats.length - 1}석`
}

// 2026-11-07T14:00:00+09:00 → 2026.11.07 (토) 14:00 (PC 시간대와 관계없이 한국 시간)
export function formatDateTime(isoString: string): string {
    const parts = Object.fromEntries(
        DATE_TIME_FORMAT.formatToParts(new Date(isoString)).map(part => [part.type, part.value]),
    )

    return `${parts.year}.${parts.month}.${parts.day} (${parts.weekday}) ${parts.hour}:${parts.minute}`
}