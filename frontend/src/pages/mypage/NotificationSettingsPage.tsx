import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import MyPageSidebar from './MyPageSidebar'
import {
    getNotificationPreference,
    updateNotificationPreference,
    getOpeningSubscriptions,
    deleteOpeningSubscription,
    subscribeOpeningSubscription,
    type OpeningSubscriptionPage,
} from '../../api/notificationApi'

const PAGE_SIZE = 10

// 공연 일정 표시 - YY.MM.DD
function formatPerformanceDate(value: string): string {
    const [year, month, day] = value.split('-')
    return `${year.slice(-2)}.${month}.${day}`
}

// 예매 오픈일 표시 - YY.MM.DD HH:MM
function formatOpeningDate(value: string): string {
    const date = new Date(value)

    if (Number.isNaN(date.getTime())) {
        return '일정 확인 불가'
    }

    const parts = new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul',
        year: '2-digit',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
    }).formatToParts(date)

    const part = (type: string) =>
        parts.find(item => item.type === type)?.value ?? ''

    return `${part('year')}.${part('month')}.${part('day')} ${part('hour')}:${part('minute')}`
}

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

    // 현재 조회 중인 페이지
    const [currentPage, setCurrentPage] = useState(0)

    // 공연별 알림 토글 처리 상태
    const [updatingPerformanceId, setUpdatingPerformanceId] = useState<number | null>(null)
    const [toggleError, setToggleError] = useState('')

    // 전체 알림 수신 설정 조회
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

    // 공연별 알림 신청 목록 조회
    // 현재 페이지가 변경되면 해당 페이지를 다시 조회
    useEffect(() => {
        let active = true

        async function loadSubscriptions(showLoading = true) {
            if (showLoading) {
                setIsSubscriptionsLoading(true)
                setSubscriptionsError('')
            }

            try {
                const data = await getOpeningSubscriptions(currentPage, PAGE_SIZE)

                if (data.content.length === 0 && currentPage > 0) {
                    setCurrentPage(currentPage - 1)
                } else {
                    setSubscriptions(data)
                }
            } catch {
                if (active) {
                    setSubscriptionsError('알림 신청 목록을 불러오지 못했습니다.')
                }
            } finally {
                if (active && showLoading) {
                    setIsSubscriptionsLoading(false)
                }
            }
        }

        void loadSubscriptions()

        // 예매 오픈일 경과 여부를 주기적으로 갱신
        const intervalId = window.setInterval(() => {
            void loadSubscriptions(false)
        }, 30_000)

        return () => {
            active = false
            window.clearInterval(intervalId)
        }
    }, [currentPage])

    // 전체 알림 수신 설정 <<변경>>
    async function handleToggle() {
        if (enabled === null || isSaving || updatingPerformanceId !== null) return

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

    // 공연별 알림 ON/OFF 변경
    async function handleToggleSubscription(performanceId: number) {
        if (enabled === null || isSaving || updatingPerformanceId !== null) return

        setUpdatingPerformanceId(performanceId)
        setToggleError('')

        try {
            if (enabled) {
                // 개별 OFF: 해당 공연의 신청 기록 삭제
                await deleteOpeningSubscription(performanceId)
            } else {
                // 개별 ON: 신청 및 전체 알림 자동 ON
                await subscribeOpeningSubscription(performanceId)
            }

            // 변경된 전체 알림 상태와 신청 목록 조회
            const [updatedEnabled, updatedSubscriptions] = await Promise.all([
                getNotificationPreference(),
                getOpeningSubscriptions(currentPage, PAGE_SIZE),
            ])

            setEnabled(updatedEnabled)

            // 마지막 항목을 해제했다면 이전 페이지 조회
            if (updatedSubscriptions.content.length === 0 && currentPage > 0) {
                setCurrentPage(currentPage - 1)
            } else {
                setSubscriptions(updatedSubscriptions)
            }
        } catch {
            setToggleError( '알림 설정 변경 또는 목록 갱신에 실패했습니다. 새로고침 후 상태를 확인해 주세요.')
        } finally {
            setUpdatingPerformanceId(null)
        }
    }

    // 이전 페이지 이동
    function handlePreviousPage() {
        if (currentPage <= 0) return

        setCurrentPage(currentPage - 1)
    }

    // 다음 페이지 이동
    function handleNextPage() {
        if (!subscriptions || subscriptions.last) return

        setCurrentPage(currentPage + 1)
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

                    {/* 전체 알림 수신 설정 */}
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
                                    enabled ? 'bg-[#4338CA]' : 'bg-[#ccc]'
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

                    {/* 공연별 알림 설정 목록 */}
                    <section className="mt-10">
                        <h3 className="mb-2 text-[20px] font-semibold">
                            공연별 알림 설정
                        </h3>

                        <p className="mb-6 text-[13px] text-[#777]">
                            현재 알림을 신청한 공연을 확인하고 공연별 알림을 해제할 수 있습니다.
                        </p>

                        <div className="rounded-md border border-[#ddd]">
                            {toggleError && (
                                <p role="alert" className="px-6 pt-4 text-[13px] text-red-600">
                                    {toggleError}
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
                                    {/* 전체 신청 건수 */}
                                    <p className="px-6 py-4 text-[13px] text-[#777]">
                                        전체 {subscriptions.totalElements}건
                                    </p>

                                    {/* 현재 페이지의 공연 목록 */}
                                    {subscriptions.content.map((subscription) => {
                                        // 전체 알림이 OFF면 공연별 토글도 OFF로 표시
                                        const isOn = enabled === true

                                        return (
                                            <div
                                                key={subscription.performanceId}
                                                className="px-6 py-5"
                                            >
                                                {/* 공연 제목 및 알림 상태 */}
                                                <div className="flex flex-wrap items-center gap-2">
                                                    <span className="text-[15px] font-semibold text-[#222]">{subscription.title}</span>

                                                    <span
                                                        className={`rounded px-2 py-1 text-[12px] font-medium ${
                                                            isOn
                                                                ? 'bg-green-100 text-green-700'
                                                                : 'bg-[#eee] text-[#777]'
                                                        }`}
                                                    >
                                                        {isOn ? '알림 신청' : '알림 해제'}
                                                    </span>
                                                </div>

                                                {/* 공연장, 공연 일정 및 토글 */}
                                                <div className="mt-3 flex items-center justify-between gap-4">
                                                    <p className="min-w-0 text-[13px] text-[#777]">
                                                        {subscription.venueName} ·{' '}
                                                        {formatPerformanceDate(subscription.startDate)}
                                                        {' ~ '}
                                                        {formatPerformanceDate(subscription.endDate)}
                                                    </p>

                                                    <button
                                                        type="button"
                                                        role="switch"
                                                        aria-checked={isOn}
                                                        aria-label={`${subscription.title} 알림 수신`}
                                                        onClick={() =>
                                                            void handleToggleSubscription(subscription.performanceId)
                                                        }
                                                        disabled={
                                                            enabled === null ||
                                                            isSaving ||
                                                            updatingPerformanceId !== null
                                                        }
                                                        className={`flex h-7 w-12 shrink-0 items-center rounded-full px-1 transition-colors disabled:opacity-60 ${
                                                            isOn ? 'bg-[#4338CA]' : 'bg-[#ccc]'
                                                        }`}
                                                    >
                                                        <span
                                                            className={`h-5 w-5 rounded-full bg-white transition-transform ${
                                                                isOn ? 'translate-x-5' : ''
                                                            }`}
                                                        />
                                                    </button>
                                                </div>

                                                {/* 예매 오픈일 */}
                                                <p className="mt-2 text-[13px] text-[#777]">
                                                    예매 오픈일 {formatOpeningDate(subscription.opensAt)}
                                                </p>
                                            </div>
                                        )
                                    })}

                                </div>
                            )}
                        </div>

                        {/* 페이지네이션 */}
                        {!isSubscriptionsLoading &&
                            !subscriptionsError &&
                            subscriptions !== null &&
                            subscriptions.totalPages > 1 && (
                                <div className="mt-6 flex items-center justify-center gap-4">
                                    <button
                                        type="button"
                                        onClick={handlePreviousPage}
                                        disabled={
                                            currentPage === 0 ||
                                            updatingPerformanceId  !== null
                                        }
                                        className="rounded border border-[#ddd] px-4 py-2 text-[13px] text-[#555] hover:bg-[#f7f7f7] disabled:cursor-not-allowed disabled:opacity-40"
                                    >
                                        이전
                                    </button>

                                    <span className="text-[13px] text-[#555]">
                                        {currentPage + 1} / {subscriptions.totalPages} 페이지
                                    </span>

                                    <button
                                        type="button"
                                        onClick={handleNextPage}
                                        disabled={
                                            subscriptions.last ||
                                            updatingPerformanceId  !== null
                                        }
                                        className="rounded border border-[#ddd] px-4 py-2 text-[13px] text-[#555] hover:bg-[#f7f7f7] disabled:cursor-not-allowed disabled:opacity-40"
                                    >
                                        다음
                                    </button>
                                </div>
                            )}
                    </section>

                    {/* 알림 안내 */}
                    <section className="mt-10 rounded-md bg-[#f7f7f7] p-6">
                        <h3 className="mb-3 text-[15px] font-semibold">
                            알림 안내
                        </h3>

                        <ul className="list-disc space-y-2 pl-5 text-[13px] leading-6 text-[#666]">
                            <li>
                                예매 시작 알림은 사이트 내에서 제공됩니다.
                            </li>
                            <li>
                                전체 알림을 OFF로 변경하면 공연별 알림도 OFF로 표시되지만 신청 기록은 유지됩니다.
                            </li>
                            <li>
                                전체 알림이 OFF인 상태에서 공연별 알림을 ON으로 변경하면
                                전체 알림도 ON으로 전환되며 기존에 신청한 공연의 알림도 다시 활성화됩니다.
                            </li>
                            <li>
                                공연별 알림을 OFF로 변경하면 해당 공연의 알림 신청이 해제되어 목록에서 사라집니다.
                            </li>
                            <li>
                                예매 오픈일이 지난 공연은 목록에서 자동으로 제외됩니다.
                            </li>
                        </ul>
                    </section>
                </main>
            </div>
        </div>
    )
}
