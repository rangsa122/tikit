
import {useState} from 'react'
import {Link, useSearchParams} from 'react-router'
import {popularCategories} from '../constants/categories'
import type {CategoryId} from '../constants/categories'
import {mockOpeningPerformances, mockPerformances} from '../mocks/performances'
import PerformanceBanner from '../components/performance/PerformanceBanner'
import PerformanceSection from '../components/performance/PerformanceSection'
import PromotionBanner from '../components/performance/PromotionBanner'

export default function HomePage() {
  const [selectedCategory, setSelectedCategory] = useState<CategoryId>('theater')
  const [searchParams] = useSearchParams()

  const searchKeyword = (searchParams.get('q') ?? '').trim()
  const normalizedKeyword = searchKeyword.toLocaleLowerCase()
  const isSearching = normalizedKeyword.length > 0

  const popularPerformances = mockPerformances.filter(
    performance => performance.categoryId === selectedCategory,
  )

  const searchResults = isSearching
    ? mockPerformances.filter(performance =>
        performance.title.toLocaleLowerCase().includes(normalizedKeyword),
      )
    : []

  return (
    <div className="pb-20">
      <h1 className="sr-only">TIKIT 공연</h1>

      {!isSearching && <PerformanceBanner />}

      <div className="mx-auto max-w-[1320px]">
        {isSearching ? (
          <section
            aria-label="공연 검색 결과"
            className="px-5 py-12"
          >
            <h2 className="mb-2 text-2xl font-bold">
              공연 검색 결과
            </h2>

            <p className="mb-8 text-sm text-gray-500">
              &apos;{searchKeyword}&apos; 검색 결과 {searchResults.length}건
            </p>

            {searchResults.length > 0 ? (
              <div className="grid grid-cols-2 gap-5 md:grid-cols-3 lg:grid-cols-4">
                {searchResults.map(performance => (
                  <Link
                    key={performance.id}
                    to={`/performances/${performance.id}`}
                    className="group overflow-hidden rounded-xl border border-[#e5e5ec] bg-white transition-shadow hover:shadow-md"
                  >
                    <div
                      aria-hidden="true"
                      className="aspect-[3/4] bg-[#e5e5ec]"
                    />

                    <div className="p-4">
                      <h3 className="font-bold text-[#111] group-hover:underline">
                        {performance.title}
                      </h3>

                      <p className="mt-2 text-sm text-gray-500">
                        {performance.venue}
                      </p>

                      <p className="mt-1 text-sm text-gray-500">
                        {performance.period}
                      </p>
                    </div>
                  </Link>
                ))}
              </div>
            ) : (
              <div className="py-20 text-center">
                <p className="text-lg font-semibold text-[#111]">
                  검색 결과가 없습니다.
                </p>
                <p className="mt-2 text-sm text-gray-500">
                  다른 공연 제목으로 검색해 보세요.
                </p>
              </div>
            )}
          </section>
        ) : (
          <>
            <PerformanceSection
              title="오픈 예정"
              performances={mockOpeningPerformances.slice(0, 10)}
              variant="opening"
            />

            <PerformanceSection
              key={selectedCategory}
              title="TIKIT 장르별 인기"
              performances={popularPerformances}
            >
              <div
                aria-label="인기 공연 장르"
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
        )}
      </div>
    </div>
  )
}
