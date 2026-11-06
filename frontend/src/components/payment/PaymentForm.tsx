import {useState} from 'react'
import type {BookedSeat, Booking} from '../../api/bookingApi'
import Button from '../common/Button'
import {Card, PosterPlaceholder} from './PaymentUi'
import {formatDateTime, formatSeat, formatWon} from '../../utils/bookingFormat'

interface PaymentFormProps {
    booking: Booking
    onPay: () => void
}

/**
 * 결제 화면 본문
 *
 * 왼쪽: 예매 정보, 선택한 좌석, 결제 수단, 취소·환불 안내
 * 오른쪽: 결제 금액 + 정책 동의 + 결제하기
 */
export default function PaymentForm({booking, onPay}: PaymentFormProps) {
    const [agreed, setAgreed] = useState(false)

    return (
        <div className="grid items-start gap-6 lg:grid-cols-[1fr_380px]">
            <div className="flex flex-col gap-4">
                <Card>
                    <div className="flex items-center gap-5">
                        <PosterPlaceholder className="h-28 w-[84px]" />
                        <div className="min-w-0">
                            <p className="text-lg font-bold text-gray-900">{booking.performanceTitle}</p>
                            <p className="mt-1 text-sm text-gray-700">{formatDateTime(booking.startsAt)}</p>
                        </div>
                    </div>
                </Card>

                <Card>
                    <div className="flex items-center justify-between">
                        <h2 className="text-[15px] font-bold text-gray-900">선택한 좌석</h2>
                        <span className="text-[13px] font-medium text-gray-500">{booking.seats.length}석</span>
                    </div>
                    <ul className="mt-3 flex flex-col gap-2.5">
                        {booking.seats.map(seat => (
                            <li
                                key={seat.seatId}
                                className="flex items-center gap-2.5 rounded-[10px] border border-gray-200 px-3.5 py-3 text-sm font-medium text-gray-900"
                            >
                                <span aria-hidden="true" className="size-2 rounded-full bg-blue-600" />
                                <span className="flex-1">{formatSeat(seat)}</span>
                                <span>{formatWon(seat.price)}</span>
                            </li>
                        ))}
                    </ul>
                </Card>

                <Card>
                    <h2 className="text-[15px] font-bold text-gray-900">결제 수단</h2>
                    <p className="mt-1.5 text-[13px] text-gray-500">
                        결제하기를 누르면 토스페이먼츠 결제창에서 카드나 간편결제를 선택할 수 있어요.
                    </p>
                </Card>

                <Card>
                    <h2 className="text-[15px] font-bold text-gray-900">취소·환불 안내</h2>
                    <ul className="mt-1.5 flex flex-col gap-1 text-[13px] text-gray-700">
                        <li>· 예매 한 건의 모든 좌석을 함께 취소하는 전체 취소만 가능해요</li>
                        <li>· 취소 수수료는 취소 시점의 정책에 따라 부과돼요</li>
                    </ul>
                </Card>
            </div>

            <Card className="lg:sticky lg:top-6">
                <h2 className="text-[15px] font-bold text-gray-900">결제 금액</h2>
                <ul className="mt-3 flex flex-col gap-1 text-[13px] text-gray-500">
                    {groupSeatsByGrade(booking.seats).map(group => (
                        <li key={group.grade} className="flex justify-between">
                            <span>{group.grade}석 × {group.count}</span>
                            <span>{formatWon(group.price)} × {group.count}</span>
                        </li>
                    ))}
                </ul>
                <div className="mt-3.5 flex items-center justify-between border-t border-gray-200 pt-3.5">
                    <span className="text-sm font-bold text-gray-900">총 결제 금액</span>
                    <span className="text-2xl font-bold text-gray-900">{formatWon(booking.totalAmount)}</span>
                </div>

                <label className="mt-3.5 flex cursor-pointer items-center gap-2 text-[13px] text-gray-700">
                    <input
                        type="checkbox"
                        checked={agreed}
                        onChange={event => setAgreed(event.target.checked)}
                        className="size-[18px] accent-indigo-700"
                    />
                    취소·환불 정책을 확인했어요 (필수)
                </label>

                <div className="mt-3.5 flex flex-col gap-2.5">
                    <Button onClick={onPay} disabled={!agreed}>
                        {formatWon(booking.totalAmount)} 결제하기
                    </Button>
                    <Button variant="secondary" disabled>
                        좌석 다시 선택
                    </Button>
                </div>
                <p className="mt-3.5 text-xs text-gray-500">
                    남은 시간 안에 결제하지 않으면 선택한 좌석은 자동으로 해제돼요. 예매는 전체 취소만 가능해요.
                </p>
            </Card>
        </div>
    )
}

// 등급별로 묶기 (R석 2장 → {grade: 'R', price: 150000, count: 2})
function groupSeatsByGrade(seats: BookedSeat[]) {
    const groups = new Map<string, {grade: string; price: number; count: number}>()

    for (const seat of seats) {
        const group = groups.get(seat.grade)

        if (group) {
            group.count += 1
        } else {
            groups.set(seat.grade, {grade: seat.grade, price: seat.price, count: 1})
        }
    }

    return [...groups.values()]
}