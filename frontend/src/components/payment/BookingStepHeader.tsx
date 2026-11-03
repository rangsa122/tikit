export type TimerPillState = 'default' | 'urgent' | 'checking' | 'expired'

export interface TimerPillProps {
    state: TimerPillState
    time?: string
}

interface BookingStepHeaderProps {
    title: string
    subtitle?: string
    currentStep: 1 | 2 | 3
    timer?: TimerPillProps | null
    onBack?: () => void
}

type StepState = 'done' | 'active' | 'inactive'

const STEP_LABELS = ['좌석 선택', '결제', '예매 완료']

const STEP_CIRCLE_STYLES: Record<StepState, string> = {
    done: 'bg-indigo-50 text-indigo-700',
    active: 'bg-indigo-700 text-white',
    inactive: 'bg-gray-100 text-gray-400',
}

const STEP_LABEL_STYLES: Record<StepState, string> = {
    done: 'text-gray-900',
    active: 'font-bold text-gray-900',
    inactive: 'text-gray-400',
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
 * 예매 단계 머리 부분 (좌석 선택 → 결제 → 예매 완료)
 *
 * 피그마 "Tikit 결제 화면 (좌석 화면 스타일)"의 Booking Header
 * 좌석 선택 화면(A파트)과 같이 쓸 수 있도록 결제 정보에 의존하지 않음
 */
export default function BookingStepHeader({
                                              title,
                                              subtitle,
                                              currentStep,
                                              timer,
                                              onBack,
                                          }: BookingStepHeaderProps) {
    return (
        <header className="border-b border-gray-200 bg-white">
            <div className="mx-auto grid max-w-[1320px] grid-cols-[1fr_auto_1fr] items-center gap-6 px-5 py-4">
                <div className="flex min-w-0 items-center gap-3">
                    {onBack && (
                        <button
                            type="button"
                            aria-label="뒤로 가기"
                            onClick={onBack}
                            className="flex size-8 shrink-0 items-center justify-center rounded-full hover:bg-gray-100"
                        >
                            <BackIcon />
                        </button>
                    )}

                    <div className="min-w-0">
                        <h1 className="truncate text-[17px] font-bold text-gray-900">{title}</h1>
                        {subtitle && <p className="truncate text-xs text-gray-500">{subtitle}</p>}
                    </div>
                </div>

                <ol className="flex items-center gap-2.5">
                    {STEP_LABELS.map((label, index) => {
                        const stepNumber = index + 1
                        const state = getStepState(stepNumber, currentStep)

                        return (
                            <li key={label} className="flex items-center gap-2.5">
                                {index > 0 && <span aria-hidden="true" className="text-xs text-gray-300">—</span>}
                                <span
                                    aria-current={state === 'active' ? 'step' : undefined}
                                    className="flex items-center gap-1.5"
                                >
                  <span className={`flex size-[22px] items-center justify-center rounded-full text-[11px] font-bold ${STEP_CIRCLE_STYLES[state]}`}>
                    {stepNumber}
                  </span>
                  <span className={`text-[13px] max-sm:hidden ${STEP_LABEL_STYLES[state]}`}>{label}</span>
                </span>
                            </li>
                        )
                    })}
                </ol>

                <div className="flex justify-end">
                    {timer && <TimerPill state={timer.state} time={timer.time} />}
                </div>
            </div>
        </header>
    )
}

function TimerPill({state, time}: TimerPillProps) {
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

function getStepState(stepNumber: number, currentStep: number): StepState {
    if (stepNumber < currentStep) {
        return 'done'
    }

    return stepNumber === currentStep ? 'active' : 'inactive'
}

function BackIcon() {
    return (
        <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="m15 18-6-6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
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