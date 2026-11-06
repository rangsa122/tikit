import {categories} from '../constants/categories'
import type {CategoryId} from '../constants/categories'

export interface Performance {
  id: string
  categoryId: CategoryId
  title: string
  venue: string
  period: string
  openingLabel: string
}

export const mockPerformances: Performance[] = categories.flatMap(category =>
  Array.from({length: 10}, (_, index) => ({
    id: `${category.id}-${index + 1}`,
    categoryId: category.id,
    title: category.label,
    venue: '블루스퀘어 우리은행홀',
    period: '26.10.16 ~ 26.10.24',
    openingLabel: '오늘 16:00',
  })),
)

export const mockOpeningPerformances: Performance[] = mockPerformances.map(performance => ({
  ...performance,
  title: '디즈니 인 콘서트 〈Beyond the Magic〉',
}))
