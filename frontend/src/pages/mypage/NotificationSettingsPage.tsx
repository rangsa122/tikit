import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import MyPageSidebar from './MyPageSidebar'
import {
    getNotificationPreference,
    updateNotificationPreference,
    getOpeningSubscriptions,
    deleteOpeningSubscription,
    type OpeningSubscriptionPage,
} from '../../api/notificationApi'


// 알림 설정 화면
export default function NotificationSettingsPage() {
    // 전체 알림 수신 상태
    const [enabled, setEnabled] = useState<boolean | null>(null)
    const [isSaving, setIsSaving] = useState(false)
    const [error, setError] = useState('')

    // 공연별 알림 신청 목록
    const [subscriptions, setSubscriptions] = useState<OpeningSubscriptionPage | null>(null)
    const [isSubscriptionsLoading, setIsSubscriptionsLoading] = useState(true)
    const [subscriptionsError, setSubscriptionsError] = useState('')

    // 신청 취소 처리 상태
    const [deletingPerformanceId, setDeletingPerformanceId] = useState<number | null>(null)
    const [unsubscribeError, setUnsubscribeError] = useState('')


    // 현재 알림 수신 설정 조회
    useEffect(() => {
        let active = true

        async function loadPreference() {
            try {
                const result = await getNotificationPreference()

                if (active) {
                    setEnabled(result)
                }
            } catch {
                if (active) {
                    setError('알림 수신 설정을 불러오지 못했습니다.')
                }
            }
        }

        void loadPreference()

        return () => {
            active = false
        }
    }, [])

    // <<공연별>> 알림 신청 목록 조회
    useEffect(() => {
        let active = true

        async function loadSubscriptions() {
            try {
                const data = await getOpeningSubscriptions()

                if (active) {
                    setSubscriptions(data)
                }
            } catch {
                if (active) {
                    setSubscriptionsError('알림 신청 목록을 불러오지 못했습니다.')
                }
            } finally {
                if (active) {
                    setIsSubscriptionsLoading(false)
                }
            }
        }

        void loadSubscriptions()

        return () => {
            active = false
        }
    }, [])

    // 전체 알림 수신 설정 <<변경>>
    async function handleToggle() {
        if (enabled === null || isSaving) return

        const nextEnabled = !enabled

        setIsSaving(true)
        setError('')

        try {
            await updateNotificationPreference(nextEnabled)
            setEnabled(nextEnabled)
        } catch {
            setError('알림 수신 설정 변경에 실패했습니다.')
        } finally {
            setIsSaving(false)
        }
    }

    // 공연별 알림 신청 <<취소>>
    async function handleUnsubscribe(performanceId: number) {
        if (deletingPerformanceId !== null) return

        setDeletingPerformanceId(performanceId)
        setUnsubscribeError('')

        try {
            await deleteOpeningSubscription(performanceId)

            const updatedSubscriptions = await getOpeningSubscriptions()
            setSubscriptions(updatedSubscriptions)
        } catch {
            setUnsubscribeError('신청 취소 또는 목록 갱신에 실패했습니다. 새로고침 후 상태를 확인해 주세요.')
        } finally {
            setDeletingPerformanceId(null)
        }
    }

    return (
        <div className="mx-auto w-full max-w-[1320px] px-5 py-10">
            <h1 className="border-b border-[#333] pb-5 text-[28px] font-semibold">
                <Link to="/mypage">
                    마이페이지
                </Link>
            </h1>

            <div className="mt-8 flex flex-col gap-10 md:flex-row">
                <MyPageSidebar />

                <main className="min-w-0 flex-1">
                    <h2 className="mb-3 text-[22px] font-semibold">
                        알림 설정
                    </h2>
                    <p className="mb-8 text-[14px] text-[#777]">
                        공연별 예매 시작 알림과 전체 알림 수신 여부를 관리할 수 있습니다.
                    </p>

                    <section className="rounded-md border border-[#ddd] p-6">
                        <div className="flex items-center justify-between gap-4">
                            <div>
                                <h3 className="text-[17px] font-semibold">
                                    전체 알림 수신
                                </h3>
                                <p className="mt-2 text-[13px] text-[#777]">
                                    신청한 공연의 예매 시작 알림을 수신합니다.
                                </p>
                            </div>

                            {/* 전체 알림 수신 ON/OFF */}
                            <button
                                type="button"
                                role="switch"
                                aria-checked={enabled === true}
                                aria-label="전체 알림 수신"
                                onClick={() => void handleToggle()}
                                disabled={enabled === null || isSaving}
                                className={`flex h-7 w-12 shrink-0 items-center rounded-full px-1 transition-colors disabled:opacity-60 ${
                                    enabled ? 'bg-[#333]' : 'bg-[#ccc]'
                                }`}
                            >
                                <span className={`h-5 w-5 rounded-full bg-white transition-transform ${enabled ? 'translate-x-5' : ''}`}/>
                            </button>

                        </div>

                        {enabled === null && !error && (
                            <p className="mt-4 text-[12px] text-[#777]">
                                알림 수신 설정을 불러오는 중...
                            </p>
                        )}

                        {error && (
                            <p role="alert" className="mt-4 text-[13px] text-red-600">
                                {error}
                            </p>
                        )}
                    </section>

                    <section className="mt-10">
                        <h3 className="mb-2 text-[20px] font-semibold">
                            공연별 알림 설정
                        </h3>
                        <p className="mb-6 text-[13px] text-[#777]">
                            공연별 예매 시작 알림 신청 내역을 확인하고 관리할 수 있습니다.
                        </p>

                        {/* TODO: 필터 기능 구현 */}
                        <div className="mb-5 flex flex-wrap gap-2">
                            {['전체', '알림 신청', '알림 해제'].map((filter) => (
                                <button
                                    key={filter}
                                    type="button"
                                    disabled
                                    className="rounded border border-[#ddd] px-4 py-2 text-[13px] text-[#777] opacity-60"
                                >
                                    {filter}
                                </button>
                            ))}
                        </div>

                        {/* 공연별 알림 신청 목록 */}
                        <div className="rounded-md border border-[#ddd]">
                            {unsubscribeError && (
                                <p role="alert" className="px-6 pt-4 text-[13px] text-red-600">
                                    {unsubscribeError}
                                </p>
                            )}

                            {isSubscriptionsLoading ? (
                                <p className="px-6 py-14 text-center text-[14px] text-[#777]">
                                    알림 신청 목록을 불러오는 중...
                                </p>
                            ) : subscriptionsError ? (
                                <p role="alert" className="px-6 py-14 text-center text-[14px] text-red-600">
                                    {subscriptionsError}
                                </p>
                            ) : !subscriptions || subscriptions.content.length === 0 ? (
                                <p className="px-6 py-14 text-center text-[14px] text-[#777]">
                                    신청한 공연 알림이 없습니다.
                                </p>
                            ) : (
                                <div className="divide-y divide-[#ddd]">
                                    <p className="px-6 py-4 text-[13px] text-[#777]">
                                        전체 {subscriptions.totalElements}건
                                        (첫 페이지 최대 10건 표시)
                                    </p>

                                    {subscriptions.content.map((performanceId) => (
                                        <div key={performanceId} className="flex items-center justify-between gap-4 px-6 py-5">
                                            <span className="text-[14px] font-medium">
                                                공연 ID: {performanceId}
                                            </span>

                                            <button
                                                type="button"
                                                onClick={() => void handleUnsubscribe(performanceId)}
                                                disabled={deletingPerformanceId !== null}
                                                className="shrink-0 rounded border border-[#ccc] px-4 py-2 text-[13px] text-[#555] hover:bg-[#f7f7f7] disabled:opacity-50"
                                            >
                                                {deletingPerformanceId === performanceId
                                                    ? '취소 중...'
                                                    : '신청 취소'}
                                            </button>

                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </section>

                    <section className="mt-10 rounded-md bg-[#f7f7f7] p-6">
                        <h3 className="mb-3 text-[15px] font-semibold">
                            알림 안내
                        </h3>
                        <p className="text-[13px] leading-7 text-[#666]">
                            예매 시작 알림은 사이트 내부 알림으로 제공됩니다.
                            <br />
                            전체 알림 수신을 OFF로 변경해도 공연별 알림 신청 기록은 유지됩니다.
                        </p>
                    </section>
                </main>
            </div>
        </div>
    )
}
