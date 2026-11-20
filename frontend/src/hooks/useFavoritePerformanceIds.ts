import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {
    addFavoritePerformance,
    getFavoritePerformances,
    removeFavoritePerformance,
} from '../api/favoritePerformanceApi'
import {useAuthStore} from '../stores/authStore'

const FAVORITE_PAGE_SIZE = 100
const FAVORITE_IDS_QUERY_KEY = ['favorite-performance-ids']

interface FavoriteToggleRequest {
    performanceId: string
    favorited: boolean
}

/** 내 관심 공연 ID 전체 조회 */
async function getAllFavoritePerformanceIds(): Promise<string[]> {
    const firstPage = await getFavoritePerformances(0, FAVORITE_PAGE_SIZE)

    const ids = firstPage.items.map(item => item.performanceId)

    for (let page = 1; page < firstPage.totalPages; page++) {
        const response = await getFavoritePerformances(page, FAVORITE_PAGE_SIZE)

        ids.push(...response.items.map(item => item.performanceId))
    }

    return ids
}

/** 공연 목록에서 찜 상태 확인과 등록·해제 */
export function useFavoritePerformanceIds() {
    const queryClient = useQueryClient()

    const isLoggedIn = useAuthStore(
        state => Boolean(state.tokens?.accessToken),
    )

    const favoriteQuery = useQuery({
        queryKey: FAVORITE_IDS_QUERY_KEY,
        queryFn: getAllFavoritePerformanceIds,
        enabled: isLoggedIn,
    })

    const toggleMutation = useMutation({
        mutationFn: ({performanceId, favorited}: FavoriteToggleRequest) =>
            favorited
                ? removeFavoritePerformance(performanceId)
                : addFavoritePerformance(performanceId),
        onSuccess: (_, {performanceId, favorited}) => {
            queryClient.setQueryData<string[]>(
                FAVORITE_IDS_QUERY_KEY,
                (ids = []) =>
                    favorited
                        ? ids.filter(id => id !== performanceId)
                        : [...ids, performanceId],
            )
        },
    })

    const favoriteIds = new Set(
        isLoggedIn ? favoriteQuery.data ?? [] : [],
    )

    return {
        isLoggedIn,
        isReady: isLoggedIn && favoriteQuery.isSuccess,
        favoriteIds,
        savingId: toggleMutation.isPending
            ? toggleMutation.variables.performanceId
            : null,
        toggleFavorite: (performanceId: string) =>
            toggleMutation.mutateAsync({
                performanceId,
                favorited: favoriteIds.has(performanceId),
            }),
    }
}