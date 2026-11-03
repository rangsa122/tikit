export type TimerPillState = 'default' | 'urgent' | 'checking' | 'expired'

export interface TimerPillProps {
    state: TimerPillState
    time?: string
}

const PILL_STYLES: Record<TimerPillState, string> = {
    default: 'bg-gray-100 text-gray-900',
    urgent: 'bg-red-50 text-red-600',
    checking: 'bg-indigo-50 text-indigo-700',
    expired: 'bg-gray-100 text-gray-400',
}

const PILL_LABELS: Record<TimerPillState, string> = {
    default: '남은 시간',
    urgent: '남은 시간',
    checking: '결제 확인 중',
    expired: '시간 만료',
}

/**
 * 남은 시간 알약
 *
 * checking 상태에서는 시간을 보여 주지 않음 (결제 확인 중에는 시간 초과를 띄우지 않는 규칙)
 * 시간 계산은 하지 않고 받은 글자만 보여 줌 → 계산은 usePaymentCountdown
 */
export default function TimerPill({state, time}: TimerPillProps) {
    const showTime = state !== 'checking' && Boolean(time)

    return (
        <div
            role="timer"
            aria-label={showTime ? `${PILL_LABELS[state]} ${time}` : PILL_LABELS[state]}
            className={`flex shrink-0 items-center gap-2 rounded-full py-2 pr-4 pl-3.5 ${PILL_STYLES[state]}`}
        >
            <ClockIcon />
            <span className="text-[13px] font-medium">{PILL_LABELS[state]}</span>
            {showTime && <span className="text-base font-bold tabular-nums">{time}</span>}
        </div>
    )
}

function ClockIcon() {
    return (
        <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="13" r="8" stroke="currentColor" strokeWidth="2" />
            <path d="M12 9v4l2.5 2.5M9 2h6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
    )
}