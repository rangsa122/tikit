
import {useQuery} from '@tanstack/react-query'
import {
  getPerformanceDetail,
  getPerformanceSessions,
} from '../api/performanceApi'

/**
 * 공연 상세 정보 조회
 *
 * performanceId가 있을 때만 API 요청
 * 백엔드 응답 DTO 확정 후 반환 타입 구체화 예정
 */
export function usePerformanceDetail(performanceId: string | undefined) {
  return useQuery({
    queryKey: ['performances', performanceId, 'detail'],
    queryFn: () => getPerformanceDetail(performanceId!),
    enabled: Boolean(performanceId),
  })
}

/**
 * 공연별 회차 목록 조회
 *
 * performanceId가 있을 때만 API 요청
 * 백엔드 응답 DTO 확정 후 반환 타입 구체화 예정
 */
export function usePerformanceSessions(performanceId: string | undefined) {
  return useQuery({
    queryKey: ['performances', performanceId, 'sessions'],
    queryFn: () => getPerformanceSessions(performanceId!),
    enabled: Boolean(performanceId),
  })
}
