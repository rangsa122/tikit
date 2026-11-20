import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {Link} from 'react-router'
import {categories} from '../constants/categories'
import type {CategoryId} from '../constants/categories'
import {
    formatOpeningLabel,
    getOpeningPerformances,
    toPerformance,
} from '../utils/performanceUtils'
import {useFavoritePerformanceIds} from '../hooks/useFavoritePerformanceIds'
import OpeningPerformanceCard from '../components/performance/OpeningPerformanceCard'
import PerformanceListState from '../components/performance/PerformanceListState'

const ALL_GENRES = 'all'

type GenreFilter = CategoryId | typeof ALL_GENRES

const genreOptions = [
    {id: ALL_GENRES, label: '전체'} as const,
    ...categories,
]

/** 오픈 예정 공연 전체보기 */
export default function OpeningsPage() {
    const [selectedGenre, setSelectedGenre] =
        useState<GenreFilter>(ALL_GENRES)
    const [favoriteNotice, setFavoriteNotice] = useState<string | null>(null)

    const favorite = useFavoritePerformanceIds()

    const openingQuery = useQuery({
        queryKey: ['opening-performances', selectedGenre],
        queryFn: () =>
            getOpeningPerformances(
                selectedGenre === ALL_GENRES ? undefined : selectedGenre,
            ),
    })

    const performances = (openingQuery.data ?? []).map(item => ({
        ...toPerformance(item),
        openingLabel: formatOpeningLabel(item.nextSalesOpenAt),
    }))

    async function handleToggleFavorite(performanceId: string) {
        setFavoriteNotice(null)

        if (!favorite.isLoggedIn) {
            setFavoriteNotice('로그인 후 찜할 수 있습니다.')
            return
        }

        try {
            await favorite.toggleFavorite(performanceId)
        } catch {
            setFavoriteNotice('찜 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.')
        }
    }

    return (
        <div className="mx-auto max-w-[1320px] px-5 pt-8 pb-20">
            <h1 className="py-5 text-2xl leading-[normal] font-bold">
                오픈 예정
            </h1>

            {favoriteNotice && (
                <p
                    role="status"
                    className="mb-4 rounded-lg bg-[#f6f7f9] px-4 py-3 text-sm text-[#3a3f48]"
                >
                    {favoriteNotice}
                    {!favorite.isLoggedIn && (
                        <Link
                            to="/login"
                            className="ml-2 font-semibold text-[#4338CA] underline"
                        >
                            로그인하기
                        </Link>
                    )}
                </p>
            )}

            <div
                role="group"
                aria-label="공연 장르"
                className="-my-1 flex gap-2 overflow-x-auto py-1"
            >
                {genreOptions.map(option => (
                    <button
                        key={option.id}
                        type="button"
                        aria-pressed={selectedGenre === option.id}
                        onClick={() => setSelectedGenre(option.id)}
                        className={`min-w-20 shrink-0 rounded-full border border-[#e5e5ec] px-4 py-[11px] text-base leading-[normal] font-semibold ${
                            selectedGenre === option.id
                                ? 'bg-[#111] text-white'
                                : 'bg-white text-[#111]'
                        }`}
                    >
                        {option.label}
                    </button>
                ))}
            </div>

            <div className="pt-7">
                {openingQuery.isError ? (
                    <PerformanceListState
                        state="error"
                        onRetry={() => void openingQuery.refetch()}
                    />
                ) : openingQuery.isPending ? (
                    <PerformanceListState state="loading" />
                ) : performances.length === 0 ? (
                    <PerformanceListState state="empty" />
                ) : (
                    <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-5">
                        {performances.map(performance => (
                            <OpeningPerformanceCard
                                key={performance.id}
                                performance={performance}
                                isFavorite={favorite.favoriteIds.has(performance.id)}
                                favoriteDisabled={
                                    favorite.isLoggedIn &&
                                    (!favorite.isReady || favorite.savingId === performance.id)
                                }
                                onToggleFavorite={id => void handleToggleFavorite(id)}
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    )
}