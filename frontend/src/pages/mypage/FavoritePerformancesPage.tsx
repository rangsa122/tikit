
import {useCallback, useEffect, useState} from 'react'
import {Link} from 'react-router'
import {
  getFavoritePerformances,
  removeFavoritePerformance,
  type FavoritePerformance,
} from '../../api/favoritePerformanceApi'

const PAGE_SIZE = 20

export default function FavoritePerformancesPage() {
  const [favorites, setFavorites] = useState<FavoritePerformance[]>([])
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [removingId, setRemovingId] = useState<string | null>(null)

  const loadFavorites = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const data = await getFavoritePerformances(page, PAGE_SIZE)

      setFavorites(data.items)
      setTotalElements(data.totalElements)
      setTotalPages(data.totalPages)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : '관심 공연 목록을 불러오지 못했습니다.',
      )
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    void loadFavorites()
  }, [loadFavorites])

  const handleRemove = async (performanceId: string) => {
    if (removingId !== null) return

    try {
      setRemovingId(performanceId)
      setError(null)

      await removeFavoritePerformance(performanceId)

      // 마지막 공연을 해제한 경우 이전 페이지로 이동
      if (favorites.length === 1 && page > 0) {
        setPage((previous) => previous - 1)
      } else {
        await loadFavorites()
      }
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : '관심 공연 해제에 실패했습니다.',
      )
    } finally {
      setRemovingId(null)
    }
  }

  const formatPrice = (price: number) =>
    `${price.toLocaleString('ko-KR')}원부터`

  return (
    <div className="mx-auto w-full max-w-[1320px] px-5 py-10">
      <h1 className="border-b border-[#333] pb-5 text-[28px] font-semibold">
        관심 공연
      </h1>

      <div className="mt-8">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-[20px] font-semibold">
            나의 관심 공연
          </h2>

          {!loading && !error && (
            <span className="text-sm text-[#777]">
              총 {totalElements}개
            </span>
          )}
        </div>

        {error && (
          <div
            role="alert"
            className="mb-5 rounded-lg border border-red-200 bg-red-50 p-4"
          >
            <p className="text-sm text-red-700">{error}</p>

            {!loading && (
              <button
                type="button"
                onClick={() => void loadFavorites()}
                className="mt-3 text-sm font-medium text-red-700 underline"
              >
                다시 시도
              </button>
            )}
          </div>
        )}

        {loading ? (
          <div
            role="status"
            className="flex min-h-80 items-center justify-center rounded-lg border border-[#ddd]"
          >
            <p className="text-[#777]">
              관심 공연을 불러오는 중입니다...
            </p>
          </div>
        ) : error && favorites.length === 0 ? null : favorites.length === 0 ? (
          <div className="flex min-h-80 flex-col items-center justify-center rounded-lg border border-[#ddd] px-5 py-12 text-center">
            <p className="text-[18px] font-semibold text-[#111]">
              아직 등록한 관심 공연이 없습니다.
            </p>

            <p className="mt-3 text-[14px] text-[#777]">
              마음에 드는 공연을 관심 공연으로 등록해 보세요.
            </p>

            <Link
              to="/"
              className="mt-7 rounded-md bg-[#333] px-6 py-3 text-[14px] font-medium text-white hover:bg-black"
            >
              공연 둘러보기
            </Link>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
              {favorites.map((performance) => (
                <div
                  key={performance.performanceId}
                  className="overflow-hidden rounded-lg border border-[#ddd] bg-white"
                >
                  <Link
                    to={`/performances/${performance.performanceId}`}
                    className="block"
                  >
                    <div className="aspect-[3/4] bg-[#f5f5f5]">
                      {performance.posterUrl ? (
                        <img
                          src={performance.posterUrl}
                          alt={`${performance.title} 포스터`}
                          className="h-full w-full object-cover"
                        />
                      ) : (
                        <div className="flex h-full items-center justify-center text-sm text-[#999]">
                          포스터 없음
                        </div>
                      )}
                    </div>

                    <div className="p-4">
                      <h3 className="line-clamp-2 text-base font-semibold text-[#111]">
                        {performance.title}
                      </h3>

                      <p className="mt-2 text-sm text-[#666]">
                        {performance.venueName}
                      </p>

                      <p className="mt-1 text-sm text-[#888]">
                        {performance.startDate} ~ {performance.endDate}
                      </p>

                      <p className="mt-2 text-sm font-semibold text-[#333]">
                        {formatPrice(performance.minPrice)}
                      </p>
                    </div>
                  </Link>

                  <div className="px-4 pb-4">
                    <button
                      type="button"
                      onClick={() =>
                        void handleRemove(performance.performanceId)
                      }
                      disabled={removingId !== null}
                      className="w-full rounded-md border border-[#ddd] px-4 py-2 text-sm font-medium text-[#555] hover:bg-[#f5f5f5] disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {removingId === performance.performanceId
                        ? '해제 중...'
                        : '관심 공연 해제'}
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {totalPages > 1 && (
              <div className="mt-10 flex items-center justify-center gap-4">
                <button
                  type="button"
                  onClick={() => setPage((previous) => previous - 1)}
                  disabled={page === 0 || loading}
                  className="rounded-md border border-[#ddd] px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
                >
                  이전
                </button>

                <span className="text-sm text-[#555]">
                  {page + 1} / {totalPages}
                </span>

                <button
                  type="button"
                  onClick={() => setPage((previous) => previous + 1)}
                  disabled={page >= totalPages - 1 || loading}
                  className="rounded-md border border-[#ddd] px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
                >
                  다음
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
