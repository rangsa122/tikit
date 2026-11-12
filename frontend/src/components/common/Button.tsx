import type {ButtonHTMLAttributes, ReactNode} from 'react'

export type ButtonVariant = 'primary' | 'secondary'

interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
    variant?: ButtonVariant
    children: ReactNode
}

const BASE_STYLE =
    'flex h-14 w-full shrink-0 items-center justify-center rounded-[10px] text-[15px] transition-colors disabled:cursor-not-allowed disabled:opacity-40'

const VARIANT_STYLES: Record<ButtonVariant, string> = {
    primary: 'bg-indigo-700 font-bold text-white hover:bg-indigo-800 disabled:hover:bg-indigo-700',
    secondary: 'border border-gray-200 bg-white font-medium text-gray-900 hover:bg-gray-50 disabled:hover:bg-white',
}

/**
 * 공통 버튼 (피그마 Components의 Button, Type=Primary/Secondary)
 *
 * 폭은 부모를 꽉 채움 → 나란히 놓을 때는 부모에서 grid로 나눔
 * onClick, disabled, aria-label 등 button 속성은 그대로 전달
 */
export default function Button({variant = 'primary', type = 'button', children, ...buttonProps}: ButtonProps) {
    return (
        <button type={type} className={`${BASE_STYLE} ${VARIANT_STYLES[variant]}`} {...buttonProps}>
            {children}
        </button>
    )
}