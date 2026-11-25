import {useState} from 'react'
import {isAxiosError} from 'axios'
import {subscribeOpeningSubscription} from '../api/notificationApi'
import {useFavoritePerformanceIds} from './useFavoritePerformanceIds'

/**
 * 공연 카드 찜 하트 + 새로 찜하면 예매 시작 알림 신청 모달
 *
 * 반환값 favorite은 PerformanceSection에, modal은 OpeningNotificationModal에 그대로 넘김
 */
export function useFavoriteWithOpeningAlarm() {
    const favorite = useFavoritePerformanceIds()

    const [notice, setNotice] = useState<string | null>(null)
    // 알림 신청을 물어볼 공연 ID (null이면 모달 닫힘)
    const [targetId, setTargetId] = useState<string | null>(null)
    const [submitting, setSubmitting] = useState(false)
    const [modalError, setModalError] = useState<string | null>(null)

    async function toggleFavorite(performanceId: string) {
        setNotice(null)

        if (!favorite.isLoggedIn) {
            setNotice('로그인 후 찜할 수 있습니다.')
            return
        }

        // 누르기 전 상태로 등록인지 해제인지 판단
        const wasFavorite = favorite.favoriteIds.has(performanceId)

        try {
            await favorite.toggleFavorite(performanceId)
        } catch {
            setNotice('찜 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.')
            return
        }

        // 새로 찜한 경우에만 알림 신청 모달 표시
        if (!wasFavorite) {
            setModalError(null)
            setTargetId(performanceId)
        }
    }

    // 신청 중에는 닫지 않음
    function closeModal() {
        if (submitting) return

        setTargetId(null)
        setModalError(null)
    }

    async function confirmModal() {
        if (targetId === null || submitting) return

        setSubmitting(true)
        setModalError(null)

        try {
            await subscribeOpeningSubscription(Number(targetId))
            setNotice('관심 공연 등록 및 예매 시작 알림 신청이 완료되었습니다.')
            setTargetId(null)
        } catch (error) {
            const message = isAxiosError<{message?: string}>(error)
                ? error.response?.data?.message
                : undefined
            setModalError(message || '알림 신청에 실패했습니다. 다시 시도해 주세요.')
        } finally {
            setSubmitting(false)
        }
    }

    return {
        isLoggedIn: favorite.isLoggedIn,
        notice,
        favorite: {
            isFavorite: (performanceId: string) => favorite.favoriteIds.has(performanceId),
            isDisabled: (performanceId: string) =>
                favorite.isLoggedIn &&
                (!favorite.isReady || favorite.savingId === performanceId),
            onToggle: (performanceId: string) => void toggleFavorite(performanceId),
        },
        modal: {
            isOpen: targetId !== null,
            isSubmitting: submitting,
            error: modalError,
            onClose: closeModal,
            onConfirm: () => void confirmModal(),
        },
    }
}