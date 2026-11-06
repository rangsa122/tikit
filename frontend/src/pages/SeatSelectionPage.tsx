import {useState} from 'react'
import type {ReactNode} from 'react'
import {useQuery} from '@tanstack/react-query'
import {useLocation, useNavigate, useParams} from 'react-router'
import {BookingApiError, createBooking} from '../api/bookingApi'
import {leaveQueue} from '../api/queueApi'
import {getSessionSeats} from '../api/sessionSeatApi'
import type {SessionSeat} from '../api/sessionSeatApi'
import checkIcon from '../assets/seat/check.svg'
import chevronLeftIcon from '../assets/seat/chevron-left.svg'
import closeIcon from '../assets/seat/close.svg'
import timerIcon from '../assets/seat/timer.svg'
import {usePaymentCountdown} from '../hooks/usePaymentCountdown'
import {mockSessionSeats} from '../mocks/sessionSeats'
import {clearAdmission, getAdmission} from '../utils/admissionStorage'
import type {StoredAdmission} from '../utils/admissionStorage'
import {formatDateTime} from '../utils/bookingFormat'
import type {QueuePageState} from './queue/QueuePage'

// 백엔드 없이 화면만 볼 때 true (입장 권한 없이 들어가고 예매는 만들지 않음)
const USE_MOCK_SEATS = false

// 피그마 기준 최대 선택 매수 (회원별 실제 제한은 서버가 확인)
const MAX_SEATS = 4
// 템플릿 A는 4번, 16번 좌석 뒤에 통로가 있음
const AISLE_AFTER_SEATS = [4, 16]
const GRADE_ORDER = ['VIP', 'R', 'A']
const GRADE_COLORS: Record<string, string> = {
    VIP: 'bg-[#4c1d95]',
    R: 'bg-[#2563eb]',
    A: 'bg-[#7dd3fc]',
}
const DEFAULT_GRADE_COLOR = GRADE_COLORS.R

function mockAdmission(): StoredAdmission {
    return {
        admissionId: 'mock-admission',
        queueEntryId: 'mock-entry',
        expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
    }
}

/**
 * 좌석 선택 화면 (/sessions/:sessionId/seats)
 *
 * 피그마 "좌석 선택 – 템플릿 A (중극장 240석)"
 * 대기열에서 받은 입장 권한이 있어야 들어올 수 있고, 남은 시간은 입장 권한 만료 시각 기준
 */
export default function SeatSelectionPage() {
    const {sessionId = ''} = useParams()
    const navigate = useNavigate()
    const pageState = (useLocation().state ?? {}) as QueuePageState
    const [admission] = useState(() => USE_MOCK_SEATS ? mockAdmission() : getAdmission(sessionId))
    const [admissionRejected, setAdmissionRejected] = useState(false)
    const countdown = usePaymentCountdown(admission?.expiresAt, 0)

    const goToQueue = () => navigate(`/sessions/${sessionId}/queue`, {replace: true, state: pageState})

    if (!admission) {
        return (
            <BlockedView
                pageState={pageState}
                title="입장 권한이 없어요"
                description="대기열에서 순서를 받은 뒤 좌석을 고를 수 있어요."
                action="대기열로 이동"
                onAction={goToQueue}
            />
        )
    }

    if (countdown.isExpired || admissionRejected) {
        return (
            <BlockedView
                pageState={pageState}
                title="입장 시간이 지났어요"
                description="다시 줄을 서면 맨 뒤 순서부터 시작해요."
                action="다시 줄 서기"
                onAction={goToQueue}
            />
        )
    }

    return (
        <SeatSelection
            sessionId={sessionId}
            admission={admission}
            remainingLabel={countdown.label}
            pageState={pageState}
            onAdmissionRejected={() => setAdmissionRejected(true)}
        />
    )
}

function SeatSelection({sessionId, admission, remainingLabel, pageState, onAdmissionRejected}: {
    sessionId: string
    admission: StoredAdmission
    remainingLabel: string
    pageState: QueuePageState
    onAdmissionRejected: () => void
}) {
    const navigate = useNavigate()
    const [selectedIds, setSelectedIds] = useState<number[]>([])
    const [notice, setNotice] = useState<string | null>(null)
    const [submitting, setSubmitting] = useState(false)

    const seatsQuery = useQuery({
        queryKey: ['sessions', sessionId, 'seats'],
        queryFn: () => USE_MOCK_SEATS ? mockSessionSeats : getSessionSeats(Number(sessionId)),
        staleTime: 0,
        retry: false,
    })
    const seats = seatsQuery.data ?? []
    // 다시 조회해서 팔린 좌석은 선택에서 빠짐
    const selectedSeats = seats.filter(seat => selectedIds.includes(seat.seatId) && isAvailable(seat))

    function toggleSeat(seat: SessionSeat) {
        setNotice(null)
        if (selectedIds.includes(seat.seatId)) {
            setSelectedIds(ids => ids.filter(id => id !== seat.seatId))
            return
        }
        if (selectedSeats.length >= MAX_SEATS) {
            setNotice(`한 번에 최대 ${MAX_SEATS}석까지 선택할 수 있어요.`)
            return
        }
        setSelectedIds(ids => [...ids, seat.seatId])
    }

    function resetSeats() {
        setNotice(null)
        setSelectedIds([])
    }

    // 나가면 입장 자리를 다음 사람에게 넘김. 실패해도 입장 권한 만료 때 정리됨
    function handleBack() {
        clearAdmission(sessionId)
        if (!USE_MOCK_SEATS) {
            leaveQueue(admission.queueEntryId).catch(() => undefined)
        }
        navigate(pageState.performanceId ? `/performances/${pageState.performanceId}` : '/', {replace: true})
    }

    async function handlePay() {
        if (USE_MOCK_SEATS) {
            setNotice('목업 좌석이라 예매를 만들지 않아요.')
            return
        }

        setSubmitting(true)
        setNotice(null)
        try {
            const booking = await createBooking(
                Number(sessionId),
                selectedSeats.map(seat => seat.seatId),
                admission.admissionId,
            )
            clearAdmission(sessionId)
            navigate(`/bookings/${booking.bookingId}/payment`, {replace: true})
        } catch (error) {
            setSubmitting(false)
            if (!(error instanceof BookingApiError)) {
                setNotice('예매를 만들지 못했어요. 다시 시도해 주세요.')
                return
            }
            if (error.code === 'ADMISSION_INVALID') {
                onAdmissionRejected()
                return
            }
            if (error.code === 'SEAT_UNAVAILABLE') {
                setNotice('방금 다른 분이 선택한 좌석이 있어요. 좌석을 다시 골라 주세요.')
                void seatsQuery.refetch()
                return
            }
            setNotice(error.message)
        }
    }

    return (
        <div className="min-h-svh bg-white">
            <Header pageState={pageState} onBack={handleBack}>
                <Steps />
                <Timer label={remainingLabel} />
            </Header>

            <main className="mx-auto flex w-full max-w-[1280px] flex-col gap-8 px-4 pt-6 pb-20 lg:flex-row lg:items-start">
                <section className="flex min-w-0 flex-1 flex-col gap-5 rounded-2xl border border-[#e4e6eb] p-6">
                    {seatsQuery.isLoading && <Message>좌석 정보를 불러오는 중이에요.</Message>}
                    {seatsQuery.isError && (
                        <Message alert>
                            {seatsQuery.error instanceof Error ? seatsQuery.error.message : '좌석 정보를 불러오지 못했습니다.'}
                        </Message>
                    )}
                    {seatsQuery.isSuccess && seats.length === 0 && <Message>등록된 좌석이 없어요.</Message>}
                    {seats.length > 0 && (
                        <>
                            <Legend seats={seats} />
                            <SeatMap seats={seats} selectedSeats={selectedSeats} onToggle={toggleSeat} />
                        </>
                    )}
                </section>

                <SelectedSeatsPanel
                    seats={selectedSeats}
                    notice={notice}
                    submitting={submitting}
                    onRemove={toggleSeat}
                    onReset={resetSeats}
                    onPay={handlePay}
                />
            </main>
        </div>
    )
}

function isAvailable(seat: SessionSeat) {
    return seat.status === 'AVAILABLE'
}

function gradeColor(grade: string) {
    return GRADE_COLORS[grade] ?? DEFAULT_GRADE_COLOR
}

function formatPrice(price: number) {
    return `${price.toLocaleString()}원`
}

// 피그마에 나오는 등급 순서, 그 외 등급은 뒤에 붙임
function gradesOf(seats: SessionSeat[]) {
    const grades = [...new Set(seats.map(seat => seat.grade))]
    const rank = (grade: string) => {
        const index = GRADE_ORDER.indexOf(grade)
        return index === -1 ? GRADE_ORDER.length : index
    }
    return grades.sort((a, b) => rank(a) - rank(b))
}

function Header({pageState, onBack, children}: {
    pageState: QueuePageState
    onBack: () => void
    children?: ReactNode
}) {
    const sub = [pageState.sessionStartsAt && formatDateTime(pageState.sessionStartsAt), pageState.venue]
        .filter(Boolean)
        .join(', ')

    return (
        <header className="border-b border-[#e4e6eb]">
            <div className="mx-auto flex w-full max-w-[1280px] items-center justify-between gap-4 px-4 py-3.5">
                <div className="flex min-w-0 items-center gap-3">
                    <button
                        type="button"
                        aria-label="공연으로 돌아가기"
                        onClick={onBack}
                        className="flex size-11 shrink-0 items-center justify-center"
                    >
                        <img src={chevronLeftIcon} alt="" width={22} height={22} />
                    </button>
                    <div className="flex min-w-0 flex-col gap-0.5">
                        <p className="truncate text-lg font-bold text-[#111318]">
                            {pageState.performanceTitle ?? '좌석 선택'}
                        </p>
                        {sub && <p className="truncate text-sm text-[#5b616e]">{sub}</p>}
                    </div>
                </div>
                {children}
            </div>
        </header>
    )
}

function Steps() {
    return (
        <ol className="hidden shrink-0 items-center gap-2 lg:flex" aria-label="예매 단계">
            <Step number={1} label="좌석 선택" active />
            <li aria-hidden className="text-sm text-[#c9cdd4]">—</li>
            <Step number={2} label="결제" />
            <li aria-hidden className="text-sm text-[#c9cdd4]">—</li>
            <Step number={3} label="예매 완료" />
        </ol>
    )
}

function Step({number, label, active = false}: {number: number; label: string; active?: boolean}) {
    return (
        <li className="flex items-center gap-1.5" aria-current={active ? 'step' : undefined}>
            <span
                className={`flex size-6 items-center justify-center rounded-xl text-xs font-bold ${
                    active ? 'bg-[#4338ca] text-white' : 'bg-[#f2f4f7] text-[#5b616e]'
                }`}
            >
                {number}
            </span>
            <span className={`text-sm ${active ? 'font-bold text-[#111318]' : 'text-[#5b616e]'}`}>{label}</span>
        </li>
    )
}

function Timer({label}: {label: string}) {
    return (
        <div className="flex shrink-0 items-center gap-2 rounded-full bg-[#f2f4f7] px-3.5 py-2" role="timer">
            <img src={timerIcon} alt="" width={18} height={18} />
            <span className="hidden text-sm font-medium text-[#111318] sm:inline">남은 시간</span>
            <span className="text-base font-bold text-[#111318] tabular-nums">{label}</span>
        </div>
    )
}

function Legend({seats}: {seats: SessionSeat[]}) {
    return (
        <ul className="flex flex-wrap items-center gap-x-5 gap-y-2">
            {gradesOf(seats).map(grade => (
                <LegendItem
                    key={grade}
                    color={gradeColor(grade)}
                    label={`${grade}석`}
                    remaining={seats.filter(seat => seat.grade === grade && isAvailable(seat)).length}
                />
            ))}
            <LegendItem color="bg-[#111318]" label="선택한 좌석" />
            <LegendItem color="bg-[#e4e6eb]" label="선택 불가" />
        </ul>
    )
}

function LegendItem({color, label, remaining}: {color: string; label: string; remaining?: number}) {
    return (
        <li className="flex items-center gap-1.5 text-[13px]">
            <span className={`size-4 rounded ${color}`} />
            <span className="text-[#3a3f48]">{label}</span>
            {remaining !== undefined && <span className="text-[#5b616e]">잔여 {remaining}석</span>}
        </li>
    )
}

function SeatMap({seats, selectedSeats, onToggle}: {
    seats: SessionSeat[]
    selectedSeats: SessionSeat[]
    onToggle: (seat: SessionSeat) => void
}) {
    const rows = [...new Set(seats.map(seat => seat.rowLabel))].map(rowLabel => ({
        rowLabel,
        seats: seats.filter(seat => seat.rowLabel === rowLabel).sort((a, b) => a.seatNumber - b.seatNumber),
    }))

    return (
        <div className="overflow-x-auto">
            <div className="flex min-w-max flex-col items-center gap-1.5">
                <div className="flex h-9 w-[600px] items-center justify-center rounded-b-[40px] bg-[#f2f4f7]">
                    <span className="text-[13px] font-medium tracking-[4px] text-[#5b616e]">STAGE</span>
                </div>
                <div className="h-[22px]" />
                {rows.map((row, index) => (
                    <SeatRowGroup
                        key={row.rowLabel}
                        rowLabel={row.rowLabel}
                        seats={row.seats}
                        startsNewGrade={index > 0 && rows[index - 1].seats[0]?.grade !== row.seats[0]?.grade}
                        selectedSeats={selectedSeats}
                        onToggle={onToggle}
                    />
                ))}
            </div>
        </div>
    )
}

function SeatRowGroup({rowLabel, seats, startsNewGrade, selectedSeats, onToggle}: {
    rowLabel: string
    seats: SessionSeat[]
    startsNewGrade: boolean
    selectedSeats: SessionSeat[]
    onToggle: (seat: SessionSeat) => void
}) {
    return (
        <>
            {startsNewGrade && <div className="h-3" />}
            <div className="flex items-center gap-1.5">
                <RowLabel label={rowLabel} />
                {seats.map(seat => (
                    <SeatCell
                        key={seat.seatId}
                        seat={seat}
                        selected={selectedSeats.some(selected => selected.seatId === seat.seatId)}
                        onToggle={onToggle}
                    />
                ))}
                <RowLabel label={rowLabel} />
            </div>
        </>
    )
}

function RowLabel({label}: {label: string}) {
    return <span className="w-6 text-center text-xs text-[#5b616e]">{label}</span>
}

function SeatCell({seat, selected, onToggle}: {
    seat: SessionSeat
    selected: boolean
    onToggle: (seat: SessionSeat) => void
}) {
    const available = isAvailable(seat)
    const color = selected ? 'bg-[#111318]' : available ? gradeColor(seat.grade) : 'bg-[#e4e6eb]'

    return (
        <>
            <button
                type="button"
                disabled={!available}
                aria-pressed={selected}
                aria-label={`${seatName(seat)}, ${formatPrice(seat.price)}, ${available ? '선택 가능' : '선택 불가'}`}
                title={seatName(seat)}
                onClick={() => onToggle(seat)}
                className={`flex size-[26px] shrink-0 items-center justify-center rounded-t-[6px] rounded-b-[4px] disabled:cursor-default ${color}`}
            >
                {selected && <img src={checkIcon} alt="" width={14} height={14} />}
            </button>
            {AISLE_AFTER_SEATS.includes(seat.seatNumber) && <div className="h-[26px] w-[18px] shrink-0" />}
        </>
    )
}

function seatName(seat: SessionSeat) {
    return `${seat.grade}석 ${seat.rowLabel}열 ${seat.seatNumber}번`
}

function SelectedSeatsPanel({seats, notice, submitting, onRemove, onReset, onPay}: {
    seats: SessionSeat[]
    notice: string | null
    submitting: boolean
    onRemove: (seat: SessionSeat) => void
    onReset: () => void
    onPay: () => void
}) {
    const total = seats.reduce((sum, seat) => sum + seat.price, 0)
    const payLabel = seats.length === 0 ? '좌석을 선택해 주세요' : `${seats.length}석 결제하기`

    return (
        <aside className="flex w-full flex-col gap-4 rounded-2xl border border-[#e4e6eb] p-5 lg:w-[380px] lg:shrink-0">
            <div className="flex items-center justify-between">
                <h2 className="text-[17px] font-bold text-[#111318]">선택한 좌석</h2>
                <p className="text-sm text-[#5b616e]">{seats.length} / {MAX_SEATS}석</p>
            </div>

            {seats.length > 0 && (
                <ul className="flex flex-col gap-2">
                    {seats.map(seat => (
                        <li
                            key={seat.seatId}
                            className="flex items-center gap-2.5 rounded-[10px] border border-[#e4e6eb] py-2.5 pr-2 pl-3.5"
                        >
                            <span className={`size-2.5 shrink-0 rounded-[3px] ${gradeColor(seat.grade)}`} />
                            <span className="min-w-0 flex-1 text-sm font-medium text-[#111318]">{seatName(seat)}</span>
                            <span className="shrink-0 text-sm font-medium text-[#111318]">{formatPrice(seat.price)}</span>
                            <button
                                type="button"
                                aria-label={`${seatName(seat)} 선택 해제`}
                                onClick={() => onRemove(seat)}
                                className="flex size-9 shrink-0 items-center justify-center"
                            >
                                <img src={closeIcon} alt="" width={16} height={16} />
                            </button>
                        </li>
                    ))}
                </ul>
            )}

            <div className="h-px w-full bg-[#e4e6eb]" />

            <div className="flex flex-col gap-2">
                {gradesOf(seats).map(grade => {
                    const gradeSeats = seats.filter(seat => seat.grade === grade)
                    return (
                        <div key={grade} className="flex items-center justify-between text-sm text-[#3a3f48]">
                            <span>{grade}석 × {gradeSeats.length}</span>
                            <span>{formatPrice(gradeSeats[0].price)} × {gradeSeats.length}</span>
                        </div>
                    )
                })}
                <div className="flex items-center justify-between font-bold text-[#111318]">
                    <span className="text-sm">총 결제 금액</span>
                    <span className="text-[22px]">{formatPrice(total)}</span>
                </div>
            </div>

            {notice && <p role="alert" className="text-[13px] text-[#b93815]">{notice}</p>}

            <button
                type="button"
                disabled={seats.length === 0 || submitting}
                onClick={onPay}
                className="flex h-[54px] w-full items-center justify-center rounded-xl bg-[#4338ca] text-[17px] font-bold text-white transition-colors hover:bg-[#3730a3] disabled:cursor-default disabled:bg-[#e4e6eb] disabled:text-[#5b616e]"
            >
                {submitting ? '좌석을 잡는 중이에요' : payLabel}
            </button>
            <button
                type="button"
                disabled={seats.length === 0 || submitting}
                onClick={onReset}
                className="flex h-11 w-full items-center justify-center rounded-xl border border-[#e4e6eb] bg-white text-[15px] text-[#111318] transition-colors hover:bg-[#f6f7f9] disabled:cursor-default disabled:text-[#5b616e] disabled:hover:bg-white"
            >
                좌석 다시 선택
            </button>

            <p className="text-xs leading-[1.6] text-[#5b616e]">
                남은 시간 안에 결제하지 않으면 선택한 좌석은 자동으로 해제돼요. 예매는 전체 취소만 가능해요.
            </p>
        </aside>
    )
}

function Message({alert = false, children}: {alert?: boolean; children: ReactNode}) {
    return (
        <p role={alert ? 'alert' : undefined} className={`py-16 text-center text-sm ${alert ? 'text-[#b93815]' : 'text-[#5b616e]'}`}>
            {children}
        </p>
    )
}

// 피그마에 없는 상태. 대기열 화면과 같은 카드로 보여 줌
function BlockedView({pageState, title, description, action, onAction}: {
    pageState: QueuePageState
    title: string
    description: string
    action: string
    onAction: () => void
}) {
    const navigate = useNavigate()

    return (
        <div className="flex min-h-svh flex-col bg-[#f6f7f9]">
            <div className="bg-white">
                <Header
                    pageState={pageState}
                    onBack={() => navigate(pageState.performanceId ? `/performances/${pageState.performanceId}` : '/')}
                />
            </div>
            <main className="flex flex-1 items-center justify-center px-4 py-10">
                <section className="flex w-full max-w-[520px] flex-col items-center gap-7 rounded-[20px] border border-[#e4e6eb] bg-white p-6 sm:p-10">
                    <div className="flex flex-col items-center gap-2 text-center">
                        <h1 className="text-xl font-bold text-[#111318]">{title}</h1>
                        <p className="text-sm text-[#5b616e]">{description}</p>
                    </div>
                    <button
                        type="button"
                        onClick={onAction}
                        className="flex h-[54px] w-full items-center justify-center rounded-xl bg-[#4338ca] text-[17px] font-bold text-white transition-colors hover:bg-[#3730a3]"
                    >
                        {action}
                    </button>
                </section>
            </main>
        </div>
    )
}
