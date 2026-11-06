
import {useState} from 'react'
import {Link, useParams} from 'react-router'
import {categories} from '../constants/categories'
import {mockOpeningPerformances, mockPerformances} from '../mocks/performances'
import PerformanceBanner from '../components/performance/PerformanceBanner'
import PerformanceSection from '../components/performance/PerformanceSection'
import Pagination from '../components/performance/Pagination'
import PromotionBanner from '../components/performance/PromotionBanner'

const PAGE_SIZE = 5

function CategoryContent({categoryId}: {categoryId: string}) {
  const [currentPage, setCurrentPage] = useState(1)
  const category = categories.find(item => item.id === categoryId)

  if (!category) {
    return (
      <div className="px-5 py-20 text-center">
        <h1 className="text-2xl font-bold">존재하지 않는 카테고리입니다.</h1>
        <Link to="/" className="mt-6 inline-block underline">
          메인으로 이동
        </Link>
      </div>
    )
  }

  const openingPerformances = mockOpeningPerformances.filter(
    performance => performance.categoryId === category.id,
  )

  const rankedPerformances = mockPerformances.filter(
    performance => performance.categoryId === category.id,
  )

  const totalPages = Math.ceil(rankedPerformances.length / PAGE_SIZE)

  const safeCurrentPage = Math.min(
    Math.max(currentPage, 1),
    Math.max(totalPages, 1),
  )

  const paginatedPerformances = rankedPerformances.slice(
    (safeCurrentPage - 1) * PAGE_SIZE,
    safeCurrentPage * PAGE_SIZE,
  )

  return (
    <div className="pb-20">
      <h1 className="sr-only">{category.label} 공연</h1>
      <PerformanceBanner variant="category" />

      <div className="mx-auto max-w-[1320px]">
        <PerformanceSection
          key={`${category.id}-opening`}
          title="오픈 예정"
          performances={openingPerformances}
          variant="opening"
        />

        <PerformanceSection
          key={`${category.id}-ranking`}
          title="일간 랭킹"
          performances={paginatedPerformances}
        />

        <Pagination
          currentPage={safeCurrentPage}
          totalPages={totalPages}
          onPageChange={setCurrentPage}
        />

        <PromotionBanner />
      </div>
    </div>
  )
}

export default function CategoryPage() {
  const {categoryId} = useParams()

  return <CategoryContent key={categoryId} categoryId={categoryId ?? ''} />
}
