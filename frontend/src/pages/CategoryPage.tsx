
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

const PAGE_SIZE = 5

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

  return (
    <div className="pb-20">
        <div className="mx-auto max-w-[1320px]">
            <h1 className="px-5 pt-10 text-3xl font-bold">{category.label} 공연</h1>

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

        {performanceQuery.isPending ||
        performanceQuery.isError ||
        performances.length === 0 ? (
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
          <PerformanceSection
            key={`${category.id}-list`}
            title="전체 공연"
            performances={performances}
          />
        )}

        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={setCurrentPage}
        />
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
