
import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {Link, useParams} from 'react-router'
import {categories} from '../constants/categories'
import {usePerformances} from '../hooks/usePerformances'
import {
    getOpeningPerformances,
    toPerformance,
} from '../utils/performanceUtils'
import PerformanceSection from '../components/performance/PerformanceSection'
import PerformanceListState from '../components/performance/PerformanceListState'
import Pagination from '../components/performance/Pagination'
import OpeningHighlightSlider from '../components/performance/OpeningHighlightSlider'

const PAGE_SIZE = 5

// 상단 슬라이드에 보여 줄 최대 공연 수
const SLIDER_COUNT = 10

function CategoryContent({categoryId}: {categoryId: string}) {
    const [currentPage, setCurrentPage] = useState(1)

    const category = categories.find(
        item => item.id === categoryId,
    )

    const performanceQuery = usePerformances(
        {
            page: currentPage - 1,
            size: PAGE_SIZE,
            genreCode: category?.id,
            sort: 'startDate',
        },
        Boolean(category),
    )

    // 상단 슬라이드용 장르 공연
    const sliderQuery = usePerformances(
        {
            page: 0,
            size: 100,
            genreCode: category?.id,
            sort: 'startDate',
        },
        Boolean(category),
    )

    const openingQuery = useQuery({
        queryKey: ['opening-performances', categoryId],
        queryFn: () => getOpeningPerformances(categoryId),
        enabled: Boolean(category),
    })

    if (!category) {
        return (
            <div className="px-5 py-20 text-center">
                <h1 className="text-2xl font-bold">
                    존재하지 않는 카테고리입니다.
                </h1>
                <Link to="/" className="mt-6 inline-block underline">
                    메인으로 이동
                </Link>
            </div>
        )
    }

    const openingPerformances = (openingQuery.data ?? []).map(
        item => toPerformance(item, category.id),
    )

    const performances = (performanceQuery.data?.items ?? []).map(
        item => toPerformance(item, category.id),
    )

    const totalPages = performanceQuery.data?.totalPages ?? 0

    // 포스터가 있는 공연만, 파란 글씨 자리에 공연 기간 표시
    const sliderPerformances = (sliderQuery.data?.items ?? [])
        .filter(item => Boolean(item.posterUrl))
        .slice(0, SLIDER_COUNT)
        .map(item => {
            const performance = toPerformance(item, category.id)
            return {...performance, openingLabel: performance.period}
        })

    return (
        <div className="pb-20">
            <div className="mx-auto max-w-[1320px] px-5 pt-8">
                <Link
                    to="/"
                    className="text-sm text-gray-500 hover:text-blue-600"
                >
                    ← 공연 목록으로
                </Link>
                <h1 className="pt-5 text-3xl font-bold">
                    {category.label} 공연
                </h1>
            </div>

            <div className="mx-auto max-w-[1320px] px-5 pt-8">
                <OpeningHighlightSlider performances={sliderPerformances} />
            </div>

            <div className="mx-auto max-w-[1320px]">
                {openingQuery.isPending || openingQuery.isError ? (

                    <section className="px-5 pt-8">
                        <h2 className="py-5 text-2xl font-bold">
                            오픈 예정
                        </h2>

                        <PerformanceListState
                            state={openingQuery.isError ? 'error' : 'loading'}
                            onRetry={() => void openingQuery.refetch()}
                        />
                    </section>
                ) : (
                    <PerformanceSection
                        key={`${category.id}-opening`}
                        title="오픈 예정"
                        performances={openingPerformances}
                        variant="opening"
                    />
                )}

                {performanceQuery.isPending || performanceQuery.isError || performances.length === 0 ? (
                    <section className="px-5 pt-8">
                        <h2 className="py-5 text-2xl font-bold">
                            전체 공연
                        </h2>

                        <PerformanceListState
                            state={
                                performanceQuery.isError
                                    ? 'error'
                                    : performanceQuery.isPending
                                        ? 'loading'
                                        : 'empty'
                            }
                            onRetry={() => void performanceQuery.refetch()}
                        />
                    </section>
                ) : (
                    <PerformanceSection key={`${category.id}-list`} title="전체 공연" performances={performances}/>
                )}

                <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage}/>
            </div>
        </div>
    )
}

export default function CategoryPage() {
    const {categoryId} = useParams()

    return (
        <CategoryContent
            key={categoryId}
            categoryId={categoryId ?? ''}
        />
    )
}