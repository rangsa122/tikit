import {useCallback, useEffect, useRef, useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {getPerformanceSessions} from '../api/performanceApi'
import {getQueueEntry, joinQueue, leaveQueue, QueueApiError} from '../api/queueApi'
import type {QueueEntry} from '../api/queueApi'
import {mockGetQueueEntry, mockGetSessionOpening, mockJoinQueue} from '../mocks/queue'
import {clearAdmission, saveAdmission} from '../utils/admissionStorage'
import {getClockOffsetMs} from './usePaymentCountdown'

// 백엔드 없이 화면만 볼 때 false
const USE_REAL_API = true

// 순번 조회가 실패했을 때 다시 시도하기까지 기다릴 시간
const RETRY_DELAY_MS = 3000

export interface QueueState {
    entry: QueueEntry | null
    // 처음 받은 순번과 그때 시각 (진행 막대와 예상 대기 시간 계산용)
    startPosition: number | null
    startedAt: number | null
    // 지금까지 순번이 줄어든 속도로 계산한 남은 대기 시간 (초). 아직 계산할 수 없으면 null
    etaSeconds: number | null
    clockOffsetMs: number
    error: QueueApiError | null
    // 순번 조회가 잠깐 실패해서 다시 시도 중
    isReconnecting: boolean
}

const INITIAL_STATE: QueueState = {
    entry: null,
    startPosition: null,
    startedAt: null,
    etaSeconds: null,
    clockOffsetMs: 0,
    error: null,
    isReconnecting: false,
}

/**
 * 회차 대기열
 *
 * enabled가 되면 줄을 서고, 서버가 알려 준 pollAfterSeconds마다 순번을 다시 조회
 * 새로고침해도 서버가 같은 항목을 돌려주므로 순번이 유지됨
 * 입장 허용(ADMITTED)이 되면 입장 권한을 저장하고 조회를 멈춤
 *
 * 조회가 곧 "아직 기다리는 중" 신호이므로 탭이 숨어 있어도 조회를 멈추지 않음
 */
export function useQueueEntry(sessionId: string | undefined, enabled: boolean) {
    const [state, setState] = useState<QueueState>(INITIAL_STATE)
    const [attempt, setAttempt] = useState(0)
    const entryIdRef = useRef<string | null>(null)

    useEffect(() => {
        if (!sessionId || !enabled) {
            return
        }

        let cancelled = false
        let timerId: number | undefined

        const schedule = (delayMs: number) => {
            timerId = window.setTimeout(poll, delayMs)
        }

        const apply = (entry: QueueEntry) => {
            if (cancelled) {
                return
            }
            entryIdRef.current = entry.queueEntryId

            if (entry.status === 'ADMITTED' && entry.admissionId && entry.admissionExpiresAt) {
                saveAdmission(sessionId, {
                    admissionId: entry.admissionId,
                    queueEntryId: entry.queueEntryId,
                    expiresAt: entry.admissionExpiresAt,
                })
            }

            const now = Date.now()
            setState(prev => {
                const isFirstWaiting = prev.startPosition === null && entry.status === 'WAITING'
                const startPosition = isFirstWaiting ? entry.position : prev.startPosition
                const startedAt = isFirstWaiting ? now : prev.startedAt
                return {
                    entry,
                    startPosition,
                    startedAt,
                    etaSeconds: estimateSeconds(entry.position, startPosition, startedAt, now),
                    clockOffsetMs: getClockOffsetMs(entry.serverTime),
                    error: null,
                    isReconnecting: false,
                }
            })

            if (entry.status === 'WAITING') {
                schedule(Math.max(1, entry.pollAfterSeconds) * 1000)
            }
        }

        const fail = (error: unknown, canRetry: boolean) => {
            if (cancelled) {
                return
            }
            const queueError = error instanceof QueueApiError
                ? error
                : new QueueApiError('UNKNOWN_ERROR', '알 수 없는 오류가 발생했습니다.')

            // 네트워크나 서버 일시 장애면 순번을 지우지 않고 다시 시도
            if (canRetry && isTemporary(queueError)) {
                setState(prev => ({...prev, isReconnecting: true}))
                schedule(RETRY_DELAY_MS)
                return
            }
            setState(prev => ({...prev, error: queueError, isReconnecting: false}))
        }

        async function poll() {
            const entryId = entryIdRef.current
            if (!entryId) {
                return
            }
            try {
                apply(USE_REAL_API ? await getQueueEntry(entryId) : await mockGetQueueEntry(sessionId!))
            } catch (error) {
                fail(error, true)
            }
        }

        async function join() {
            try {
                apply(USE_REAL_API ? await joinQueue(sessionId!) : await mockJoinQueue(sessionId!))
            } catch (error) {
                fail(error, false)
            }
        }

        join()

        return () => {
            cancelled = true
            window.clearTimeout(timerId)
        }
    }, [sessionId, enabled, attempt])

    // 끝난 항목(EXPIRED, LEFT)이나 오류에서 다시 줄 서기
    const rejoin = useCallback(() => {
        entryIdRef.current = null
        setState(INITIAL_STATE)
        setAttempt(value => value + 1)
    }, [])

    // 대기 취소 또는 입장 종료. 실패해도 화면은 나감 (서버 항목은 조회가 끊기면 정리됨)
    const leave = useCallback(async () => {
        const entryId = entryIdRef.current
        if (sessionId) {
            clearAdmission(sessionId)
        }
        if (!entryId || !USE_REAL_API) {
            return
        }
        try {
            await leaveQueue(entryId)
        } catch {
            // 무시
        }
    }, [sessionId])

    return {...state, rejoin, leave}
}

export interface SessionOpening {
    salesOpenAt: string
    serverTime: string
    // 조회 직후 계산한 서버 시계와 내 PC 시계 차이
    clockOffsetMs: number
}

interface SessionListResponse {
    items?: Array<{sessionId: string | number; salesOpenAt?: string | null; serverTime?: string}>
}

/**
 * 회차의 예매 오픈 시각 (B파트 회차 조회 API)
 *
 * 오픈 전이면 카운트다운, 오픈 후면 바로 대기열에 들어감
 * 공연 ID를 모르거나 조회에 실패하면 null → 바로 대기열 (판매 전이면 서버가 SALE_NOT_OPEN으로 막음)
 */
export function useSessionOpening(performanceId: string | undefined, sessionId: string | undefined) {
    return useQuery({
        queryKey: ['performances', performanceId, 'sessions', sessionId, 'opening'],
        queryFn: async (): Promise<SessionOpening | null> => {
            if (!USE_REAL_API) {
                const mock = await mockGetSessionOpening()
                return {...mock, clockOffsetMs: getClockOffsetMs(mock.serverTime)}
            }
            const data = await getPerformanceSessions(performanceId!) as SessionListResponse | null
            const session = data?.items?.find(item => String(item.sessionId) === sessionId)
            if (!session?.salesOpenAt || !session.serverTime) {
                return null
            }
            return {
                salesOpenAt: session.salesOpenAt,
                serverTime: session.serverTime,
                clockOffsetMs: getClockOffsetMs(session.serverTime),
            }
        },
        enabled: Boolean(sessionId) && (Boolean(performanceId) || !USE_REAL_API),
        staleTime: Infinity,
        retry: false,
    })
}

// 처음 순번에서 줄어든 속도가 그대로 이어진다고 보고 남은 시간 계산 (5초 이상 지켜본 뒤부터)
function estimateSeconds(position: number | null, startPosition: number | null,
                         startedAt: number | null, now: number): number | null {
    if (position === null || startPosition === null || startedAt === null) {
        return null
    }
    const elapsedSeconds = (now - startedAt) / 1000
    const moved = startPosition - position
    if (elapsedSeconds < 5 || moved <= 0) {
        return null
    }
    return Math.round(position / (moved / elapsedSeconds))
}

function isTemporary(error: QueueApiError) {
    return error.code === 'NETWORK_ERROR'
        || error.code === 'SERVICE_UNAVAILABLE'
        || error.code === 'RATE_LIMITED'
        || (error.status !== undefined && error.status >= 500)
}
