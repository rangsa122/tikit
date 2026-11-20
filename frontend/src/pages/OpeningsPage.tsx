import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {categories} from '../constants/categories'
import type {CategoryId} from '../constants/categories'
import {
    formatOpeningLabel,
    getOpeningPerformances,
    toPerformance,
} from '../utils/performanceUtils'
import PerformanceCard from '../components/performance/PerformanceCard'
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

    return (
        <div className="mx-auto max-w-[1320px] px-5 pt-8 pb-20">
            <h1 className="py-5 text-2xl leading-[normal] font-bold">
                오픈 예정
            </h1>

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
                            <PerformanceCard
                                key={performance.id}
                                performance={performance}
                                variant="opening"
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    )
}