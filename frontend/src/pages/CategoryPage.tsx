
import {useEffect, useRef} from 'react'
import {useQuery} from '@tanstack/react-query'
import {Link, useParams} from 'react-router'
import {categories} from '../constants/categories'
import {useInfinitePerformances} from '../hooks/usePerformances'
import {
  getOpeningPerformances,
  toPerformance,
} from '../utils/performanceUtils'
import PerformanceSection from '../components/performance/PerformanceSection'
import PerformanceListState from '../components/performance/PerformanceListState'
import PerformanceBanner from '../components/performance/PerformanceBanner'

const PAGE_SIZE = 10

function CategoryContent({categoryId}: {categoryId: string}) {
    const category = categories.find(
        item => item.id === categoryId,
    )

    const loadMoreRef = useRef<HTMLDivElement | null>(null)

    const performanceQuery = useInfinitePerformances(
        {
            size: PAGE_SIZE,
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

    // 다음 페이지가 존재하면 자동 조회
    const {
        hasNextPage,
        isFetchingNextPage,
        isFetchNextPageError,
        fetchNextPage,
    } = performanceQuery

    useEffect(() => {
        const target = loadMoreRef.current

        if (!target || !hasNextPage || isFetchNextPageError) return

        // 목록 하단 접근 감지
        const observer = new IntersectionObserver(
            entries => {
                if (entries[0]?.isIntersecting && !isFetchingNextPage) {
                    void fetchNextPage()
                }
            },
            {
                rootMargin: '300px 0px',
            },
        )

        observer.observe(target)

        return () => observer.disconnect()
    }, [
        hasNextPage,
        isFetchingNextPage,
        isFetchNextPageError,
        fetchNextPage,
    ])

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

    const performances = (performanceQuery.data?.pages ?? [])
        .flatMap(page => page.items)
        .map(item => toPerformance(item, category.id))

    return (
        <div className="pb-20">
            <div className="mx-auto max-w-[1320px]">
                <h1 className="px-5 pt-10 text-3xl font-bold">
                    {category.label} 공연
                </h1>
            </div>

            <PerformanceBanner variant="category" genreCode={category.id}/>

            <div className="mx-auto max-w-[1320px]">
                {openingQuery.isPending || openingQuery.isError ? (

                <section className="px-5 pt-8">
                <h2 className="py-5 text-2xl font-bold">
                  오픈 예정
                </h2>

                <PerformanceListState state={openingQuery.isError ? 'error' : 'loading'} onRetry={() => void openingQuery.refetch()}/>
                </section>
                ) : (
                  <PerformanceSection
                    key={`${category.id}-opening`}
                    title="오픈 예정"
                    performances={openingPerformances}
                    variant="opening"
                  />
                )}

                {performanceQuery.isPending || performances.length === 0 ? (
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
                  <PerformanceSection key={`${category.id}-list`} title="전체 공연" performances={performances} showAll/>
                )}

                {isFetchingNextPage && (
                    <p
                        role="status"
                        className="px-5 py-8 text-center text-sm text-[#777]"
                    >
                        공연을 더 불러오는 중...
                    </p>
                )}

                {/* 추가 공연 조회 실패 시 재시도 */}
                {isFetchNextPageError && (
                    <div className="px-5 py-8 text-center">
                        <p role="alert" className="text-sm text-red-600">
                            추가 공연을 불러오지 못했습니다.
                        </p>
                        <button
                            type="button"
                            onClick={() => void fetchNextPage()}
                            disabled={isFetchingNextPage}
                            className="mt-3 rounded-md border border-[#ddd] px-5 py-2 text-sm disabled:opacity-50"
                        >
                            다시 시도
                        </button>
                    </div>
                )}
                {/* 스크롤 하단 감지 */}
                <div ref={loadMoreRef} className="h-1" aria-hidden="true" />
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
