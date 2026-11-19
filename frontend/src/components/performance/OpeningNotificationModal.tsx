import {useEffect, useRef} from 'react'
import type {MouseEvent} from 'react'

interface OpeningNotificationModalProps {
  isOpen: boolean
  isSubmitting: boolean
  error: string | null
  onClose: () => void
  onConfirm: () => void
}

export default function OpeningNotificationModal({
  isOpen,
  isSubmitting,
  error,
  onClose,
  onConfirm,
}: OpeningNotificationModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

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

  function handleClose() {
    if (!isSubmitting) {
      onClose()
    }
  }

  function handleBackdropClick(
    event: MouseEvent<HTMLDialogElement>,
  ) {
    if (event.target === event.currentTarget) {
      handleClose()
    }
  }

  return (
    <dialog
      ref={dialogRef}
      onClick={handleBackdropClick}
      onCancel={event => {
        event.preventDefault()
        handleClose()
      }}
      aria-labelledby="opening-notification-title"
      aria-describedby="opening-notification-description"
      className="m-auto w-[400px] max-w-[calc(100%-32px)] overflow-hidden rounded-2xl bg-white p-0 shadow-xl backdrop:bg-black/50"
    >
      <div className="relative p-7">
        <button
          type="button"
          onClick={handleClose}
          disabled={isSubmitting}
          aria-label="모달 닫기"
          className="absolute top-4 right-4 flex size-8 items-center justify-center rounded-full text-2xl text-gray-400 hover:bg-gray-100 disabled:opacity-50"
        >
          ×
        </button>

        <div className="flex flex-col items-center text-center">
          <div className="flex size-14 items-center justify-center rounded-full bg-blue-50 text-blue-600">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="28"
              height="28"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
              <path d="M10 21h4" />
            </svg>
          </div>

          <h2
            id="opening-notification-title"
            className="mt-5 text-xl font-bold text-gray-900"
          >
            관심 공연 등록 완료
          </h2>

          <p className="mt-3 text-sm text-gray-500">
            관심 공연으로 등록되었습니다.
          </p>

          <p
            id="opening-notification-description"
            className="mt-2 text-sm leading-6 text-gray-700"
          >
            해당 공연의 예매 시작 알림을
            받으시겠습니까?
          </p>
        </div>

        {error && (
          <p
            role="alert"
            className="mt-4 rounded-lg bg-red-50 p-3 text-center text-sm text-red-600"
          >
            {error}
          </p>
        )}

        <div className="mt-7 flex gap-3">
          <button
            type="button"
            onClick={handleClose}
            disabled={isSubmitting}
            className="h-12 flex-1 rounded-xl border border-gray-300 bg-white text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            아니요
          </button>

          <button
            type="button"
            onClick={onConfirm}
            disabled={isSubmitting}
            className="h-12 flex-1 rounded-xl bg-blue-600 text-sm font-semibold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isSubmitting ? '신청 중...' : '예'}
          </button>
        </div>
      </div>
    </dialog>
  )
}