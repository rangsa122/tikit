import {useState} from 'react'
import {Link} from 'react-router'
import type {Performance} from '../../types/performance'

interface OpeningPerformanceCardProps {
    performance: Performance
    isFavorite: boolean
    favoriteDisabled: boolean
    onToggleFavorite: (performanceId: string) => void
}

/** 오픈 예정 페이지 공연 카드 (찜 하트 + 오픈 시각 + 공연명 + 공연장) */
export default function OpeningPerformanceCard({
                                                   performance,
                                                   isFavorite,
                                                   favoriteDisabled,
                                                   onToggleFavorite,
                                               }: OpeningPerformanceCardProps) {
    const [failedPosterUrl, setFailedPosterUrl] = useState<string | null>(null)

    const hasPoster =
        Boolean(performance.posterUrl) &&
        performance.posterUrl !== failedPosterUrl

    return (
        <article className="relative min-w-0 text-[#111]">
            <Link
                to={`/performances/${performance.id}`}
                aria-label={`${performance.title} 공연 상세 보기`}
                className="block rounded-xl focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-black"
            >
                <div className="aspect-[235.2/313.59] w-full overflow-hidden rounded-xl bg-[#999]">
                    {hasPoster ? (
                        <img
                            src={performance.posterUrl ?? undefined}
                            alt={`${performance.title} 포스터`}
                            loading="lazy"
                            onError={() => setFailedPosterUrl(performance.posterUrl ?? null)}
                            className="h-full w-full object-cover"
                        />
                    ) : (
                        <div className="flex h-full w-full items-center justify-center text-sm text-white">
                            이미지 없음
                        </div>
                    )}
                </div>

                <div className="flex flex-col gap-1.5 pt-4 pr-2 pl-1 leading-[normal]">
                    <p className="text-lg leading-[normal] font-semibold text-[#4338CA]">
                        {performance.openingLabel}
                    </p>

                    <h3 className="line-clamp-2 text-lg leading-[normal] font-semibold break-words">
                        {performance.title}
                    </h3>

                    <p className="truncate text-sm text-[#5b616e]">
                        {performance.venue}
                    </p>
                </div>
            </Link>

            <button
                type="button"
                aria-pressed={isFavorite}
                aria-label={
                    isFavorite
                        ? `${performance.title} 찜 해제`
                        : `${performance.title} 찜하기`
                }
                disabled={favoriteDisabled}
                onClick={() => onToggleFavorite(performance.id)}
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
        </article>
    )
}