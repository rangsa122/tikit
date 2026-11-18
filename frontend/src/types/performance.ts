
import type {CategoryId} from '../constants/categories'

export interface Performance {
  id: string
  categoryId: CategoryId
  title: string
  venue: string
  period: string
  openingLabel: string
  posterUrl?: string | null
}
