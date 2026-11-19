
import {getPerformances} from '../api/performanceApi'
import type {PublicPerformanceSummary} from '../api/performanceApi'
import type {CategoryId} from '../constants/categories'
import type {Performance} from '../types/performance'

const OPENING_PAGE_SIZE = 100

/** 공연 날짜 표시 형식 변환 */
export function formatDate(date: string): string {
  return date.slice(2, 10).replace(/-/g, '.')
}

/** 티켓 오픈 일시 표시 형식 변환 */
export function formatOpeningDate(date: string | null): string {
  if (!date) {
    return ''
  }

  return new Date(date).toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

/** API 응답을 공연 카드 데이터로 변환 */
export function toPerformance(
  item: PublicPerformanceSummary,
  categoryId: CategoryId = item.genreCode as CategoryId,
): Performance {
  return {
    id: item.performanceId,
    categoryId,
    title: item.title,
    venue: item.venueName,
    period: `${formatDate(item.startDate)} ~ ${formatDate(item.endDate)}`,
    openingLabel: formatOpeningDate(item.nextSalesOpenAt),
    posterUrl: item.posterUrl,
  }
}

/** 오픈 예정 공연 조회 */
export async function getOpeningPerformances(
  genreCode?: string,
): Promise<PublicPerformanceSummary[]> {
  const firstPage = await getPerformances({
    page: 0,
    size: OPENING_PAGE_SIZE,
    genreCode,
    sort: 'startDate',
  })

  const items = [...firstPage.items]

  for (let page = 1; page < firstPage.totalPages; page++) {
    const response = await getPerformances({
      page,
      size: OPENING_PAGE_SIZE,
      genreCode,
      sort: 'startDate',
    })

    items.push(...response.items)
  }

  return items
    .filter(item =>
      item.salesStatus === 'ENABLED' &&
      item.nextSalesOpenAt !== null &&
      Date.parse(item.nextSalesOpenAt) > Date.now(),
    )
    .sort((a, b) =>
      Date.parse(a.nextSalesOpenAt!) -
      Date.parse(b.nextSalesOpenAt!),
    )
}
