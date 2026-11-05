import StepIndicator from './StepIndicator'
import TimerPill from './TimerPill'
import type {TimerPillProps} from './TimerPill'

// 결제 화면이 이 파일에서 가져다 쓰던 타입을 그대로 쓸 수 있게 다시 내보냄
export type {TimerPillProps, TimerPillState} from './TimerPill'

interface BookingStepHeaderProps {
    title: string
    subtitle?: string
    currentStep: 1 | 2 | 3
    timer?: TimerPillProps | null
    onBack?: () => void
}

/**
 * 예매 단계 머리 부분 (뒤로 가기 + 공연명 | 단계 표시 | 남은 시간)
 *
 * 피그마 "Tikit 결제 화면 (좌석 화면 스타일)"의 Booking Header
 * 좌석 선택 화면(A파트)과 같이 쓸 수 있도록 결제 정보에 의존하지 않음
 */
export default function BookingStepHeader({title, subtitle, currentStep, timer, onBack}: BookingStepHeaderProps) {
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

                <StepIndicator currentStep={currentStep} />

                <div className="flex justify-end">{timer && <TimerPill state={timer.state} time={timer.time} />}</div>
            </div>
        </header>
    )
}

function BackIcon() {
    return (
        <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="m15 18-6-6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
    )
}