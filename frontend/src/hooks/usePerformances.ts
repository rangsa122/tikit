
import {useInfiniteQuery, useQuery} from '@tanstack/react-query'
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

/**
 * 장르별 전체 공연 스크롤 조회
 */
export function useInfinitePerformances(
    params: Omit<PerformanceListParams, 'page'> = {},
    enabled = true,
) {
  return useInfiniteQuery({
    queryKey: ['performances', 'infinite', params],

    initialPageParam: 0,

    // 현재 페이지의 공연 목록 조회
    queryFn: ({pageParam}) =>
        getPerformances({
          ...params,
          page: pageParam,
        }),

    // 다음 페이지 존재 여부 확인
    getNextPageParam: lastPage => {
      const nextPage = lastPage.page + 1

      return nextPage < lastPage.totalPages
          ? nextPage
          : undefined
    },

    enabled,
  })
}
