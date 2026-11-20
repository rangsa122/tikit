
import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {useSearchParams} from 'react-router'
import {popularCategories} from '../constants/categories'
import type {CategoryId} from '../constants/categories'
import {usePerformances} from '../hooks/usePerformances'
import {
  getOpeningPerformances,
  toPerformance,
} from '../utils/performanceUtils'
import PerformanceBanner from '../components/performance/PerformanceBanner'
import PerformanceCard from '../components/performance/PerformanceCard'
import PerformanceSection from '../components/performance/PerformanceSection'
import PerformanceListState from '../components/performance/PerformanceListState'
import Pagination from '../components/performance/Pagination'
import PromotionBanner from '../components/performance/PromotionBanner'

const SEARCH_PAGE_SIZE = 12
const CATEGORY_PAGE_SIZE = 10

/** 공연 검색 결과 */
function SearchResults({keyword}: {keyword: string}) {
  const [currentPage, setCurrentPage] = useState(1)

  const searchQuery = usePerformances({
    page: currentPage - 1,
    size: SEARCH_PAGE_SIZE,
    keyword,
    sort: 'startDate',
  })

  const performances = (searchQuery.data?.items ?? []).map(
    item => toPerformance(item),
  )

  const totalElements = searchQuery.data?.totalElements ?? 0
  const totalPages = searchQuery.data?.totalPages ?? 0

  return (
    <section
      aria-label="공연 검색 결과"
      className="px-5 py-12"
    >
      <h2 className="mb-2 text-2xl font-bold">
        공연 검색 결과
      </h2>

      <p className="mb-8 text-sm text-gray-500">
        &apos;{keyword}&apos; 검색 결과
        {searchQuery.isSuccess && ` ${totalElements}건`}
      </p>

      {searchQuery.isPending ||
      searchQuery.isError ||
      performances.length === 0 ? (
        <PerformanceListState
          state={
            searchQuery.isError
              ? 'error'
              : searchQuery.isPending
                ? 'loading'
                : 'empty'
          }
          onRetry={() => void searchQuery.refetch()}
        />
      ) : (
        <div className="grid grid-cols-2 gap-5 md:grid-cols-3 lg:grid-cols-4">
          {performances.map(performance => (
            <PerformanceCard
              key={performance.id}
              performance={performance}
            />
          ))}
        </div>
      )}

      <Pagination
        currentPage={currentPage}
        totalPages={totalPages}
        onPageChange={setCurrentPage}
      />
    </section>
  )
}

/** 메인 페이지 공연 목록 */
function HomeContent() {
  const [selectedCategory, setSelectedCategory] =
    useState<CategoryId>('theater')

  const openingQuery = useQuery({
    queryKey: ['opening-performances', 'all'],
    queryFn: () => getOpeningPerformances(),
  })

  const categoryQuery = usePerformances({
    page: 0,
    size: CATEGORY_PAGE_SIZE,
    genreCode: selectedCategory,
    sort: 'startDate',
  })

  const openingPerformances = (openingQuery.data ?? [])
    .slice(0, 10)
    .map(item => toPerformance(item))

  const categoryPerformances = (categoryQuery.data?.items ?? [])
    .map(item => toPerformance(item))

  return (
    <>
      <PerformanceSection
        title="오픈 예정"
        performances={openingPerformances}
        variant="opening"
        moreLink="/openings"
        isLoading={openingQuery.isPending}
        isError={openingQuery.isError}
        onRetry={() => void openingQuery.refetch()}
      />

      <PerformanceSection
        key={selectedCategory}
        title="TIKIT 장르별 공연"
        performances={categoryPerformances}
        isLoading={categoryQuery.isPending}
        isError={categoryQuery.isError}
        onRetry={() => void categoryQuery.refetch()}
      >
        <div
          aria-label="공연 장르"
          className="-my-1 flex gap-2 overflow-x-auto py-1"
        >
          {popularCategories.map(category => (
            <button
              key={category.id}
              type="button"
              aria-pressed={selectedCategory === category.id}
              onClick={() => setSelectedCategory(category.id)}
              className={`min-w-20 shrink-0 rounded-full border border-[#e5e5ec] px-4 py-[11px] text-base leading-[normal] font-semibold ${
                selectedCategory === category.id
                  ? 'bg-[#111] text-white'
                  : 'bg-white text-[#111]'
              }`}
            >
              {category.label}
            </button>
          ))}
        </div>
      </PerformanceSection>

      <PromotionBanner />
    </>
  )
}

export default function HomePage() {
  const [searchParams] = useSearchParams()

  const searchKeyword = (searchParams.get('q') ?? '').trim()
  const isSearching = searchKeyword.length > 0

  return (
    <div className="pb-20">
      <h1 className="sr-only">TIKIT 공연</h1>

      {!isSearching && <PerformanceBanner />}

      <div className="mx-auto max-w-[1320px]">
        {isSearching ? (
          <SearchResults
            key={searchKeyword}
            keyword={searchKeyword}
          />
        ) : (
          <HomeContent />
        )}
      </div>
    </div>
  )
}
