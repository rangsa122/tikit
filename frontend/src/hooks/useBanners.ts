import {useQuery} from '@tanstack/react-query'
import {getBanners, type Banner} from '../api/bannerApi.ts'

/**
 * 홈 배너 조회.
 * 카테고리 배너는 비율이 달라 사용하지 않으므로 enabled로 호출을 막는다.
 */
export function useBanners(enabled: boolean) {
    return useQuery<Banner[], Error>({
        queryKey: ['banners'],
        queryFn: getBanners,
        enabled,
    })
}