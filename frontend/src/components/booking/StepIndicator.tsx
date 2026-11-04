type StepState = 'done' | 'active' | 'inactive'

interface StepIndicatorProps {
    currentStep: number
    steps?: string[]
}

const CIRCLE_STYLES: Record<StepState, string> = {
    done: 'bg-indigo-50 text-indigo-700',
    active: 'bg-indigo-700 text-white',
    inactive: 'bg-gray-100 text-gray-400',
}

const LABEL_STYLES: Record<StepState, string> = {
    done: 'text-gray-900',
    active: 'font-bold text-gray-900',
    inactive: 'text-gray-400',
}

/**
 * 예매 단계 표시 (① 좌석 선택 — ② 결제 — ③ 예매 완료)
 *
 * currentStep은 1부터 시작, 좁은 화면에서는 숫자 원만 보임
 */
export default function StepIndicator({currentStep, steps = ['좌석 선택', '결제', '예매 완료']}: StepIndicatorProps) {
    return (
        <ol className="flex items-center gap-2.5">
            {steps.map((label, index) => {
                const stepNumber = index + 1
                const state = getStepState(stepNumber, currentStep)

                return (
                    <li key={label} className="flex items-center gap-2.5">
                        {index > 0 && <span aria-hidden="true" className="text-xs text-gray-300">—</span>}
                        <span aria-current={state === 'active' ? 'step' : undefined} className="flex items-center gap-1.5">
              <span className={`flex size-[22px] items-center justify-center rounded-full text-[11px] font-bold ${CIRCLE_STYLES[state]}`}>
                {stepNumber}
              </span>
              <span className={`text-[13px] max-sm:hidden ${LABEL_STYLES[state]}`}>{label}</span>
            </span>
                    </li>
                )
            })}
        </ol>
    )
}

function getStepState(stepNumber: number, currentStep: number): StepState {
    if (stepNumber < currentStep) {
        return 'done'
    }

    return stepNumber === currentStep ? 'active' : 'inactive'
}