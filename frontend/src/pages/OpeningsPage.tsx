import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {isAxiosError} from 'axios'
import {Link} from 'react-router'
import {categories} from '../constants/categories'
import type {CategoryId} from '../constants/categories'
import {
    formatOpeningLabel,
    getOpeningPerformances,
    toPerformance,
} from '../utils/performanceUtils'
import {useFavoritePerformanceIds} from '../hooks/useFavoritePerformanceIds'
import OpeningPerformanceCard from '../components/performance/OpeningPerformanceCard'
import OpeningHighlightSlider from '../components/performance/OpeningHighlightSlider'
import OpeningAlarmBanner from '../components/performance/OpeningAlarmBanner'
import PerformanceListState from '../components/performance/PerformanceListState'
import OpeningNotificationModal from '../components/performance/OpeningNotificationModal'
import LoginRequiredModal from '../components/performance/LoginRequiredModal'
import {subscribeOpeningSubscription} from '../api/notificationApi'

const ALL_GENRES = 'all'

type GenreFilter = CategoryId | typeof ALL_GENRES

const HIGHLIGHT_COUNT = 10

const genreOptions = [
    {id: ALL_GENRES, label: '전체'} as const,
    ...categories,
]

/** 오픈 예정 공연 전체보기 */
export default function OpeningsPage() {
    const [selectedGenre, setSelectedGenre] =
        useState<GenreFilter>(ALL_GENRES)
    const [favoriteNotice, setFavoriteNotice] = useState<string | null>(null)
    // 로그인 안내 모달 문구 (null이면 모달 닫힘)
    const [loginMessage, setLoginMessage] = useState<string | null>(null)

    // 찜 등록 직후 예매 시작 알림 신청을 물어볼 공연 ID (null이면 모달 닫힘)
    const [openingTargetId, setOpeningTargetId] = useState<string | null>(null)
    const [openingSubmitting, setOpeningSubmitting] = useState(false)
    const [openingError, setOpeningError] = useState<string | null>(null)

    const favorite = useFavoritePerformanceIds()

    const highlightQuery = useQuery({
        queryKey: ['opening-performances', ALL_GENRES],
        queryFn: () => getOpeningPerformances(),
    })

    const openingQuery = useQuery({
        queryKey: ['opening-performances', selectedGenre],
        queryFn: () =>
            getOpeningPerformances(
                selectedGenre === ALL_GENRES ? undefined : selectedGenre,
            ),
    })

    const performances = (openingQuery.data ?? []).map(item => ({
        ...toPerformance(item),
        openingLabel: formatOpeningLabel(item.nextSalesOpenAt),
    }))

    const highlights = (highlightQuery.data ?? [])
        .slice(0, HIGHLIGHT_COUNT)
        .map(item => ({
            ...toPerformance(item),
            openingLabel: formatOpeningLabel(item.nextSalesOpenAt),
        }))

    async function handleToggleFavorite(performanceId: string) {
        setFavoriteNotice(null)

        if (!favorite.isLoggedIn) {
            setLoginMessage('로그인 후 관심 공연을 찜할 수 있습니다.')
            return
        }

        // 누르기 전 상태로 등록인지 해제인지 판단
        const wasFavorite = favorite.favoriteIds.has(performanceId)

        try {
            await favorite.toggleFavorite(performanceId)
        } catch {
            setFavoriteNotice('찜 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.')
            return
        }

        // 새로 찜한 경우에만 알림 신청 모달 표시 (해제는 묻지 않음)
        if (!wasFavorite) {
            setOpeningError(null)
            setOpeningTargetId(performanceId)
        }
    }

    // 알림 신청 모달 닫기 (신청 중에는 닫지 않음)
    function handleOpeningClose() {
        if (openingSubmitting) return

        setOpeningTargetId(null)
        setOpeningError(null)
    }

    // 예매 시작 알림 신청
    async function handleOpeningConfirm() {
        if (openingTargetId === null || openingSubmitting) return

        setOpeningSubmitting(true)
        setOpeningError(null)

        try {
            await subscribeOpeningSubscription(Number(openingTargetId))
            setFavoriteNotice('관심 공연 등록 및 예매 시작 알림 신청이 완료되었습니다.')
            setOpeningTargetId(null)
        } catch (error) {
            const message = isAxiosError<{message?: string}>(error)
                ? error.response?.data?.message
                : undefined
            setOpeningError(message || '알림 신청에 실패했습니다. 다시 시도해 주세요.')
        } finally {
            setOpeningSubmitting(false)
        }
    }

    return (
        <div className="mx-auto max-w-[1320px] px-5 pt-8 pb-20">
            <LoginRequiredModal
                isOpen={loginMessage !== null}
                message={loginMessage ?? ''}
                onClose={() => setLoginMessage(null)}
            />
            <OpeningNotificationModal
                isOpen={openingTargetId !== null}
                isSubmitting={openingSubmitting}
                error={openingError}
                onClose={handleOpeningClose}
                onConfirm={() => void handleOpeningConfirm()}
            />
            <Link
                to="/"
                className="text-sm text-gray-500 hover:text-blue-600"
            >
                ← 공연 목록으로
            </Link>
            <h1 className="py-5 text-2xl leading-[normal] font-bold">
                오픈 예정
            </h1>

            {favoriteNotice && (
                <p
                    role="status"
                    className="mb-4 rounded-lg bg-[#f6f7f9] px-4 py-3 text-sm text-[#3a3f48]"
                >
                    {favoriteNotice}
                </p>
            )}

            <OpeningHighlightSlider performances={highlights} />

            <div className="pt-10 pb-12">
                <OpeningAlarmBanner
                    isLoggedIn={favorite.isLoggedIn}
                    onLoginRequired={() =>
                        setLoginMessage('로그인하고 관심 공연의 오픈 알림을 받아보세요.')
                    }
                />
            </div>

            <div
                role="group"
                aria-label="공연 장르"
                className="-my-1 flex gap-2 overflow-x-auto py-1"
            >
                {genreOptions.map(option => (
                    <button
                        key={option.id}
                        type="button"
                        aria-pressed={selectedGenre === option.id}
                        onClick={() => setSelectedGenre(option.id)}
                        className={`min-w-20 shrink-0 rounded-full border border-[#e5e5ec] px-4 py-[11px] text-base leading-[normal] font-semibold ${
                            selectedGenre === option.id
                                ? 'bg-[#111] text-white'
                                : 'bg-white text-[#111]'
                        }`}
                    >
                        {option.label}
                    </button>
                ))}
            </div>

            <div className="pt-7">
                {openingQuery.isError ? (
                    <PerformanceListState
                        state="error"
                        onRetry={() => void openingQuery.refetch()}
                    />
                ) : openingQuery.isPending ? (
                    <PerformanceListState state="loading" />
                ) : performances.length === 0 ? (
                    <PerformanceListState state="empty" />
                ) : (
                    <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-5">
                        {performances.map(performance => (
                            <OpeningPerformanceCard
                                key={performance.id}
                                performance={performance}
                                isFavorite={favorite.favoriteIds.has(performance.id)}
                                favoriteDisabled={
                                    favorite.isLoggedIn &&
                                    (!favorite.isReady || favorite.savingId === performance.id)
                                }
                                onToggleFavorite={id => void handleToggleFavorite(id)}
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    )
}