
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


const SEOUL_TIME_ZONE = 'Asia/Seoul'

/** 한국 시간 기준 날짜 키 (YYYY-MM-DD) */
function toSeoulDateKey(date: Date): string {
  return date.toLocaleDateString('en-CA', {timeZone: SEOUL_TIME_ZONE})
}

/** 오픈 예정 페이지 오픈 시각 표시 (오늘 18:00 / 내일 09:00 / 11.25(수) 18:00) */
export function formatOpeningLabel(
    date: string | null,
    now: Date = new Date(),
): string {
  if (!date) {
    return ''
  }

  const openingAt = new Date(date)

  if (Number.isNaN(openingAt.getTime())) {
    return ''
  }

  const time = openingAt.toLocaleTimeString('en-GB', {
    timeZone: SEOUL_TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })

  const openingDay = toSeoulDateKey(openingAt)
  const today = toSeoulDateKey(now)
  const tomorrow = toSeoulDateKey(
      new Date(now.getTime() + 24 * 60 * 60 * 1000),
  )

  if (openingDay === today) {
    return `오늘 ${time}`
  }

  if (openingDay === tomorrow) {
    return `내일 ${time}`
  }

  const weekday = openingAt.toLocaleDateString('ko-KR', {
    timeZone: SEOUL_TIME_ZONE,
    weekday: 'short',
  })
  const [, month, day] = openingDay.split('-')

  return `${month}.${day}(${weekday}) ${time}`
}
