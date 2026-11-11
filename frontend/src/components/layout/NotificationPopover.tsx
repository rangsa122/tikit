import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuthStore } from '../../stores/authStore'
import {
    getSiteNotifications,
    markSiteNotificationAsRead,
    type SiteNotification,
} from '../../api/siteNotificationApi'
import bellIcon from '../../assets/navigation/bell.svg'

// 알림 발생 시간
function formatNotificationTime(createdAt: string) {
    const minutes = Math.floor(
        (Date.now() - new Date(createdAt).getTime()) / 60000,
    )

    if (!Number.isFinite(minutes)) return ''
    if (minutes < 1) return '방금 전'
    if (minutes < 60) return `${minutes}분 전`
    if (minutes < 1440) return `${Math.floor(minutes / 60)}시간 전`

    return new Date(createdAt).toLocaleDateString('ko-KR')
}

export default function NotificationPopover() {
    const navigate = useNavigate()
    const hasSession = useAuthStore(
        state => Boolean(state.tokens?.accessToken),
    )

    const [isOpen, setIsOpen] = useState(false)
    const [notifications, setNotifications] = useState<SiteNotification[]>([])
    const [unreadCount, setUnreadCount] = useState(0)
    const [isLoading, setIsLoading] = useState(false)
    const [isUpdating, setIsUpdating] = useState(false)
    const [errorMessage, setErrorMessage] = useState('')

    const containerRef = useRef<HTMLDivElement>(null)
    const buttonRef = useRef<HTMLButtonElement>(null)
    const panelId = useId()

    // 읽지 않은 알림 개수 조회
    const refreshUnreadCount = useCallback(async () => {
        if (!hasSession) return

        try {
            const result = await getSiteNotifications(0, 1, true)
            setUnreadCount(result.totalElements)
        } catch {
            // 일시적인 조회 실패는 다음 주기에 재시도
        }
    }, [hasSession])

    // 알림 목록 조회
    const loadNotifications = useCallback(async () => {
        try {
            const result = await getSiteNotifications(0, 20, false)
            setNotifications(result.content)
            setErrorMessage('')
        } catch {
            setErrorMessage('알림을 불러오지 못했습니다.')
        } finally {
            setIsLoading(false)
        }
    }, [])

    // 로그인 중에는 30초마다 신규 알림 확인
    useEffect(() => {
        if (!hasSession) {
            setUnreadCount(0)
            setNotifications([])
            setIsOpen(false)
            return
        }

        void refreshUnreadCount()

        const interval = window.setInterval(() => {
            void refreshUnreadCount()
        }, 30000)

        return () => window.clearInterval(interval)
    }, [hasSession, refreshUnreadCount])

    // 팝업이 열려 있을 때 목록 갱신
    useEffect(() => {
        if (!isOpen || !hasSession) return

        setIsLoading(true)
        void loadNotifications()

        const interval = window.setInterval(() => {
            void loadNotifications()
        }, 30000)

        return () => window.clearInterval(interval)
    }, [isOpen, hasSession, loadNotifications])

    // 팝업 외부 클릭 및 ESC 처리
    useEffect(() => {
        if (!isOpen) return

        function handlePointerDown(event: PointerEvent) {
            if (
                event.target instanceof Node &&
                !containerRef.current?.contains(event.target)
            ) {
                setIsOpen(false)
            }
        }

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === 'Escape') {
                setIsOpen(false)
                buttonRef.current?.focus()
            }
        }

        document.addEventListener('pointerdown', handlePointerDown)
        document.addEventListener('keydown', handleKeyDown)

        return () => {
            document.removeEventListener('pointerdown', handlePointerDown)
            document.removeEventListener('keydown', handleKeyDown)
        }
    }, [isOpen])

    // 알림 아이콘 클릭
    function handleToggle() {
        if (!hasSession) {
            navigate('/login')
            return
        }

        setIsOpen(open => !open)
    }

    // 알림 한 건 읽음 처리
    async function handleMarkAsRead(notification: SiteNotification) {
        if (notification.isRead || isUpdating) return

        setIsUpdating(true)
        setErrorMessage('')

        try {
            const updated = await markSiteNotificationAsRead(notification.id)

            setNotifications(previous =>
                previous.map(item =>
                    item.id === updated.id ? updated : item,
                ),
            )

            setUnreadCount(count => Math.max(0, count - 1))
            void refreshUnreadCount()
        } catch {
            setErrorMessage('알림 읽음 처리에 실패했습니다.')
        } finally {
            setIsUpdating(false)
        }
    }

    // 모든 읽지 않은 알림 처리
    async function handleMarkAllAsRead() {
        if (isUpdating || unreadCount === 0) return

        setIsUpdating(true)
        setErrorMessage('')

        try {
            // 전체 미읽음 목록을 먼저 가져옴
            const unreadNotifications: SiteNotification[] = []
            let pageNumber = 0

            while (true) {
                const page = await getSiteNotifications(pageNumber, 100, true)
                unreadNotifications.push(...page.content)

                if (page.last) break
                pageNumber += 1
            }

            // 기존 단건 PATCH API 재사용
            for (const notification of unreadNotifications) {
                await markSiteNotificationAsRead(notification.id)
            }

            await loadNotifications()
            await refreshUnreadCount()
        } catch {
            setErrorMessage(
                '일부 알림을 읽음 처리하지 못했습니다. 다시 시도해 주세요.',
            )
            await loadNotifications()
            await refreshUnreadCount()
        } finally {
            setIsUpdating(false)
        }
    }

    return (
        <div ref={containerRef} className="relative flex items-center">
            <button
                ref={buttonRef}
                type="button"
                aria-label="알림"
                aria-haspopup="dialog"
                aria-expanded={isOpen}
                aria-controls={isOpen ? panelId : undefined}
                onClick={handleToggle}
                className="relative flex h-7 w-7 items-center justify-center"
            >
                <img src={bellIcon} alt="" className="h-[24px] w-[24px] max-w-none object-contain"/>

                {/* 미읽음 알림이 있을 때만 표시 */}
                {hasSession && unreadCount > 0 && (
                    <span
                        aria-hidden="true"
                        className="absolute right-0 top-0 h-2 w-2 rounded-full bg-[#F4510B]"
                    />
                )}
            </button>

            {/* 알림 팝업 */}
            {isOpen && (
                <section
                    id={panelId}
                    role="dialog"
                    aria-label="알림 목록"
                    className="absolute right-0 top-full z-50 mt-3 w-[390px] max-w-[calc(100vw-24px)] overflow-hidden rounded-xl border border-[#EEEEEE] bg-white text-[#111] shadow-[0_6px_24px_rgba(0,0,0,0.12)]"
                >
                    {/* 팝업 상단 */}
                    <div className="flex h-[52px] items-center justify-between px-4">
                        <div className="flex items-center gap-2">
                            <h2 className="text-base font-semibold">알림</h2>

                            {unreadCount > 0 && (
                                <span className="rounded-full bg-[#F0EAFF] px-2 py-0.5 text-xs font-semibold text-[#5140CE]">
                                    {unreadCount}
                                </span>
                            )}
                        </div>

                        <div className="flex items-center gap-3">
                            <button
                                type="button"
                                onClick={() => void handleMarkAllAsRead()}
                                disabled={unreadCount === 0 || isUpdating}
                                className="text-xs text-[#777] disabled:opacity-40"
                            >
                                모두 읽음
                            </button>

                            <button
                                type="button"
                                aria-label="알림창 닫기"
                                onClick={() => setIsOpen(false)}
                                className="text-lg text-[#777]"
                            >
                                ×
                            </button>
                        </div>
                    </div>

                    {/* 알림 목록 */}
                    <div className="max-h-[420px] overflow-y-auto border-t border-[#F1F1F1]">
                        {isLoading && (
                            <p className="p-6 text-center text-sm text-[#777]">
                                알림을 불러오는 중입니다.
                            </p>
                        )}

                        {!isLoading && notifications.length === 0 && (
                            <p className="p-8 text-center text-sm text-[#888]">
                                새로운 알림이 없습니다.
                            </p>
                        )}

                        {!isLoading &&
                            notifications.map(notification => (
                                <button
                                    key={notification.id}
                                    type="button"
                                    onClick={() => void handleMarkAsRead(notification)}
                                    disabled={notification.isRead || isUpdating}
                                    className={`block w-full border-b border-[#F3F3F3] px-4 py-4 text-left transition-colors last:border-b-0 ${
                                        notification.isRead
                                            ? 'bg-white'
                                            : 'bg-[#F9F7FF] hover:bg-[#F2EEFF]'
                                    }`}
                                >
                                    <div className="flex items-center justify-between gap-2">
                                        <div className="flex min-w-0 items-center gap-1.5">
                                            {!notification.isRead && (
                                                <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-[#F4510B]" />
                                            )}

                                            <span
                                                className={`truncate text-xs font-medium ${
                                                    notification.type === 'SALE_OPENING'
                                                        ? 'text-[#F4510B]'
                                                        : 'text-[#555]'
                                                }`}
                                            >
                                                {notification.title}
                                            </span>
                                        </div>

                                        <time className="shrink-0 text-[11px] text-[#999]">
                                            {formatNotificationTime(notification.createdAt)}
                                        </time>
                                    </div>

                                    <p className="mt-2 text-sm font-semibold">
                                        {notification.performanceTitle && (
                                            <p className="mb-1 text-[14px] font-semibold text-[#2A2A37]">
                                                {notification.performanceTitle}
                                            </p>
                                        )}
                                    </p>

                                    {!notification.isRead && (
                                        <p className="mt-2 text-xs text-[#888]">
                                            클릭하여 읽음 처리
                                        </p>
                                    )}
                                </button>
                            ))}
                    </div>

                    {errorMessage && (
                        <p role="alert" className="px-4 py-3 text-xs text-red-600">
                            {errorMessage}
                        </p>
                    )}
                </section>
            )}
        </div>
    )
}
