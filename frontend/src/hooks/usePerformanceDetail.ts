
import {useQuery} from '@tanstack/react-query'
import {
  getPerformanceDetail,
  getPerformanceSessions,
  type PublicPerformanceDetail,
  type PerformanceSessionList,
} from '../api/performanceApi'

/**
 * 공연 상세 정보 조회
 * GET /api/v1/performances/{performanceId}
 */
export function usePerformanceDetail(
  performanceId: string | undefined,
) {
  return useQuery<PublicPerformanceDetail, Error>({
    queryKey: ['performances', performanceId, 'detail'],

    queryFn: () => getPerformanceDetail(performanceId!),

    enabled: Boolean(performanceId),
  })
}

/**
 * 공연별 회차 목록 조회
 * GET /api/v1/performances/{performanceId}/sessions
 */
export function usePerformanceSessions(
  performanceId: string | undefined,
) {
  return useQuery<PerformanceSessionList, Error>({
    queryKey: ['performances', performanceId, 'sessions'],

    queryFn: () => getPerformanceSessions(performanceId!),

    enabled: Boolean(performanceId),
  })
}
