
import {useQuery} from '@tanstack/react-query'
import {getPerformances} from '../api/performanceApi'

/**
 * 게시된 공연 목록 조회
 *
 * 백엔드 응답 DTO 확정 후 데이터 타입 구체화 예정
 */
export function usePerformances() {
  return useQuery({
    queryKey: ['performances'],
    queryFn: getPerformances,
  })
}
