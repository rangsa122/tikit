
import {useQuery} from '@tanstack/react-query'
import {getPerformances} from '../api/performanceApi'
import type {PerformanceListParams} from '../api/performanceApi'

/**
 * 공개 공연 목록 조회
 */
export function usePerformances(
  params: PerformanceListParams = {},
  enabled = true,
) {
  return useQuery({
    queryKey: ['performances', params],
    queryFn: () => getPerformances(params),
    enabled,
  })
}
