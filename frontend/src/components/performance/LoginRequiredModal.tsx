import {useEffect, useRef} from 'react'
import type {MouseEvent} from 'react'
import {useNavigate} from 'react-router'

interface LoginRequiredModalProps {
    isOpen: boolean
    message: string
    onClose: () => void
}

/** 로그인이 필요한 기능을 눌렀을 때 띄우는 안내 모달 (찜 하트, 오픈 알림 배너) */
export default function LoginRequiredModal({
                                               isOpen,
                                               message,
                                               onClose,
                                           }: LoginRequiredModalProps) {
    const dialogRef = useRef<HTMLDialogElement>(null)
    const navigate = useNavigate()

    useEffect(() => {
        const dialog = dialogRef.current

        if (!dialog) return

        if (isOpen && !dialog.open) {
            dialog.showModal()
        }

        if (!isOpen && dialog.open) {
            dialog.close()
        }
    }, [isOpen])

    function handleBackdropClick(event: MouseEvent<HTMLDialogElement>) {
        if (event.target === event.currentTarget) {
            onClose()
        }
    }

    function handleLogin() {
        onClose()
        navigate('/login')
    }

    return (
        <dialog
            ref={dialogRef}
            onClick={handleBackdropClick}
            onCancel={event => {
                event.preventDefault()
                onClose()
            }}
            aria-labelledby="login-required-title"
            aria-describedby="login-required-description"
            className="m-auto w-[400px] max-w-[calc(100%-32px)] overflow-hidden rounded-2xl bg-white p-0 shadow-xl backdrop:bg-black/50"
        >
            <div className="relative p-7">
                <button
                    type="button"
                    onClick={onClose}
                    aria-label="모달 닫기"
                    className="absolute top-4 right-4 flex size-8 items-center justify-center rounded-full text-2xl text-gray-400 hover:bg-gray-100"
                >
                    ×
                </button>

                <div className="flex flex-col items-center text-center">
                    <div className="flex size-14 items-center justify-center rounded-full bg-red-50">
                        <svg
                            viewBox="0 0 24 24"
                            aria-hidden="true"
                            className="h-7 w-7"
                        >
                            <path
                                d="M12 20.5s-7.5-4.6-9.5-9.2C1 7.7 3.2 4.5 6.6 4.5c2.1 0 3.6 1.1 5.4 3.2 1.8-2.1 3.3-3.2 5.4-3.2 3.4 0 5.6 3.2 4.1 6.8-2 4.6-9.5 9.2-9.5 9.2z"
                                fill="#ff3b4e"
                            />
                        </svg>
                    </div>

                    <h2
                        id="login-required-title"
                        className="mt-5 text-xl font-bold text-gray-900"
                    >
                        로그인이 필요합니다
                    </h2>

                    <p
                        id="login-required-description"
                        className="mt-3 text-sm leading-6 text-gray-700"
                    >
                        {message}
                    </p>
                </div>

                <div className="mt-7 flex gap-3">
                    <button
                        type="button"
                        onClick={onClose}
                        className="h-12 flex-1 rounded-xl border border-gray-300 bg-white text-sm font-semibold text-gray-700 hover:bg-gray-50"
                    >
                        닫기
                    </button>

                    <button
                        type="button"
                        onClick={handleLogin}
                        className="h-12 flex-1 rounded-xl bg-blue-600 text-sm font-semibold text-white hover:bg-blue-700"
                    >
                        로그인하기
                    </button>
                </div>
            </div>
        </dialog>
    )
}