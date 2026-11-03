
type PerformanceListStateType = 'loading' | 'error' | 'empty'

interface PerformanceListStateProps {
  state: PerformanceListStateType
  onRetry?: () => void
}

export default function PerformanceListState({
  state,
  onRetry,
}: PerformanceListStateProps) {
  if (state === 'loading') {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex min-h-64 flex-col items-center justify-center gap-4 py-12"
      >
        <div
          aria-hidden="true"
          className="h-9 w-9 animate-spin rounded-full border-4 border-gray-200 border-t-[#111]"
        />
        <p className="text-sm text-gray-500">
          공연 목록을 불러오는 중입니다.
        </p>
      </div>
    )
  }

  if (state === 'error') {
    return (
      <div
        role="alert"
        className="flex min-h-64 flex-col items-center justify-center gap-4 py-12 text-center"
      >
        <p className="text-lg font-semibold text-[#111]">
          공연 목록을 불러오지 못했습니다.
        </p>
        <p className="text-sm text-gray-500">
          잠시 후 다시 시도해 주세요.
        </p>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="rounded-lg bg-[#111] px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800"
          >
            다시 시도
          </button>
        )}
      </div>
    )
  }

  return (
    <div className="flex min-h-64 flex-col items-center justify-center gap-3 py-12 text-center">
      <p className="text-lg font-semibold text-[#111]">
        등록된 공연이 없습니다.
      </p>
      <p className="text-sm text-gray-500">
        새로운 공연이 등록되면 이곳에서 확인할 수 있습니다.
      </p>
    </div>
  )
}
