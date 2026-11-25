interface FavoriteHeartButtonProps {
    performanceId: string
    title: string
    isFavorite: boolean
    disabled: boolean
    onToggle: (performanceId: string) => void
}

/** 공연 카드 포스터 위 찜 하트 버튼 (오른쪽 위) */
export default function FavoriteHeartButton({
                                                performanceId,
                                                title,
                                                isFavorite,
                                                disabled,
                                                onToggle,
                                            }: FavoriteHeartButtonProps) {
    return (
        <button
            type="button"
            aria-pressed={isFavorite}
            aria-label={isFavorite ? `${title} 찜 해제` : `${title} 찜하기`}
            disabled={disabled}
            onClick={() => onToggle(performanceId)}
            className="absolute top-2 right-2 flex h-10 w-10 items-center justify-center rounded-full disabled:opacity-60"
        >
            <svg
                viewBox="0 0 24 24"
                aria-hidden="true"
                className="h-7 w-7 drop-shadow"
            >
                <path
                    d="M12 20.5s-7.5-4.6-9.5-9.2C1 7.7 3.2 4.5 6.6 4.5c2.1 0 3.6 1.1 5.4 3.2 1.8-2.1 3.3-3.2 5.4-3.2 3.4 0 5.6 3.2 4.1 6.8-2 4.6-9.5 9.2-9.5 9.2z"
                    fill={isFavorite ? '#ff3b4e' : 'rgba(0, 0, 0, 0.15)'}
                    stroke="#fff"
                    strokeWidth="1.8"
                    strokeLinejoin="round"
                />
            </svg>
        </button>
    )
}