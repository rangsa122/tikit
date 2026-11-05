import type {QueueEntry} from '../api/queueApi'
import type {SessionOpening} from '../hooks/useQueueEntry'

/**
 * 백엔드 없이 대기열 화면을 확인할 때 쓰는 가짜 데이터
 *
 * 예매 오픈 8초 전에서 시작 → 오픈 후 줄을 서면 37번부터 몇 명씩 줄다가 0이 되면 입장 허용
 */

const ADMISSION_MINUTES = 10
const OPENS_IN_MS = 8000
let mockPosition = 37
let mockWaitingCount = 9_000

function now() {
    return new Date().toISOString()
}

function waitingEntry(sessionId: string): QueueEntry {
    return {
        queueEntryId: 'mock-entry',
        sessionId: Number(sessionId),
        status: 'WAITING',
        position: mockPosition,
        waitingCount: mockWaitingCount,
        admissionId: null,
        admissionExpiresAt: null,
        pollAfterSeconds: 1,
        serverTime: now(),
    }
}

function admittedEntry(sessionId: string): QueueEntry {
    return {
        queueEntryId: 'mock-entry',
        sessionId: Number(sessionId),
        status: 'ADMITTED',
        position: 0,
        waitingCount: mockWaitingCount,
        admissionId: 'mock-admission',
        admissionExpiresAt: new Date(Date.now() + ADMISSION_MINUTES * 60_000).toISOString(),
        pollAfterSeconds: 5,
        serverTime: now(),
    }
}

export async function mockGetSessionOpening(): Promise<Omit<SessionOpening, 'clockOffsetMs'>> {
    return {salesOpenAt: new Date(Date.now() + OPENS_IN_MS).toISOString(), serverTime: now()}
}

export async function mockJoinQueue(sessionId: string): Promise<QueueEntry> {
    mockPosition = 37
    mockWaitingCount = 9_000
    return waitingEntry(sessionId)
}

export async function mockGetQueueEntry(sessionId: string): Promise<QueueEntry> {
    const moved = 3 + Math.floor(Math.random() * 6)
    mockPosition = Math.max(0, mockPosition - moved)
    mockWaitingCount = mockWaitingCount - moved + 40
    return mockPosition === 0 ? admittedEntry(sessionId) : waitingEntry(sessionId)
}
