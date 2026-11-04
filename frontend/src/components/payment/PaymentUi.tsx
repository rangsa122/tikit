import type {ReactNode} from 'react'

/**
 * 결제 화면 공통 조각
 *
 * 카드, 상태 카드, 안내 박스, 포스터 자리, 아이콘 (버튼은 components/common/Button)
 */

export function Card({children, className = ''}: {children: ReactNode; className?: string}) {
    return (
        <section className={`rounded-[14px] border border-gray-200 bg-white px-6 py-[22px] ${className}`}>
            {children}
        </section>
    )
}

export function StatusCard({icon, title, children}: {icon: ReactNode; title: string; children: ReactNode}) {
    return (
        <Card className="flex flex-col items-center gap-2.5 pt-9 pb-8 text-center">
            {icon}
            <h2 className="text-[22px] font-bold text-gray-900">{title}</h2>
            <p className="text-sm text-gray-500">{children}</p>
        </Card>
    )
}

export function Notice({children}: {children: ReactNode}) {
    return (
        <div className="flex gap-2.5 rounded-xl bg-gray-100 px-4 py-3.5 text-[13px] text-gray-700">
            <InfoIcon />
            <p>{children}</p>
        </div>
    )
}

export function PosterPlaceholder({className}: {className: string}) {
    return (
        <div
            aria-hidden="true"
            className={`flex shrink-0 items-center justify-center rounded-lg bg-gray-100 text-xs text-gray-400 ${className}`}
        >
            포스터
        </div>
    )
}

export function SpinnerIcon() {
    return (
        <svg aria-hidden="true" width="56" height="56" viewBox="0 0 56 56" fill="none" className="animate-spin">
            <circle cx="28" cy="28" r="22" stroke="#EEF2FF" strokeWidth="6" />
            <path d="M28 6a22 22 0 0 1 22 22" stroke="#4338CA" strokeWidth="6" strokeLinecap="round" />
        </svg>
    )
}

export function CheckIcon() {
    return (
        <svg aria-hidden="true" width="56" height="56" viewBox="0 0 56 56" fill="none">
            <circle cx="28" cy="28" r="28" fill="#4338CA" />
            <path d="m18 28.5 7 7 13-14" stroke="#fff" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
    )
}

export function AlertIcon() {
    return (
        <svg aria-hidden="true" width="56" height="56" viewBox="0 0 56 56" fill="none">
            <circle cx="28" cy="28" r="28" fill="#FEF2F2" />
            <circle cx="28" cy="28" r="14" stroke="#DC2626" strokeWidth="2.5" />
            <path d="M28 21v8" stroke="#DC2626" strokeWidth="2.5" strokeLinecap="round" />
            <circle cx="28" cy="34" r="1.6" fill="#DC2626" />
        </svg>
    )
}

export function ClockIcon() {
    return (
        <svg aria-hidden="true" width="56" height="56" viewBox="0 0 56 56" fill="none">
            <circle cx="28" cy="28" r="28" fill="#F3F4F6" />
            <circle cx="28" cy="29" r="13" stroke="#9CA3AF" strokeWidth="2.5" />
            <path d="M28 23v6l4 3" stroke="#9CA3AF" strokeWidth="2.5" strokeLinecap="round" />
        </svg>
    )
}

function InfoIcon() {
    return (
        <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" className="mt-0.5 shrink-0">
            <circle cx="12" cy="12" r="9" stroke="#9CA3AF" strokeWidth="2" />
            <path d="M12 11v5" stroke="#9CA3AF" strokeWidth="2" strokeLinecap="round" />
            <circle cx="12" cy="8" r="1.2" fill="#9CA3AF" />
        </svg>
    )
}