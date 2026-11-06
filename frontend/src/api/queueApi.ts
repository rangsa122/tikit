import axios from 'axios'
import {apiClient} from './apiClient.ts'

interface ApiResponse<T> {
    code: string
    message: string
    data: T | null
}

export type QueueEntryStatus = 'WAITING' | 'ADMITTED' | 'CONSUMED' | 'LEFT' | 'EXPIRED'

/**
 * 대기 항목 (백엔드 QueueEntryResponseDTO와 같은 모양)
 *
 * position: 대기 중 1부터, 입장 허용 시 0, 끝난 항목이면 null
 * waitingCount: 이 회차에서 대기 중인 전체 인원 (내 뒤 대기 인원 = waitingCount - position)
 * admissionId: ADMITTED일 때만 있음 → 예매 생성 때 X-Admission-Id 헤더로 보냄
 */
export interface QueueEntry {
    queueEntryId: string
    sessionId: number
    status: QueueEntryStatus
    position: number | null
    waitingCount: number
    admissionId: string | null
    admissionExpiresAt: string | null
    pollAfterSeconds: number
    serverTime: string
}

/**
 * API가 실패 응답을 줬을 때 (화면은 code로 분기)
 */
export class QueueApiError extends Error {
    readonly code: string
    readonly status: number | undefined

    constructor(code: string, message: string, status?: number) {
        super(message)
        this.code = code
        this.status = status
    }
}

/**
 * 회차 대기열 참여 (이미 대기 중이거나 입장한 항목이 있으면 그 항목)
 * POST /api/v1/sessions/{sessionId}/queue-entries
 */
export async function joinQueue(sessionId: string): Promise<QueueEntry> {
    return request(() => apiClient.post<ApiResponse<QueueEntry>>(
        `/api/v1/sessions/${encodeURIComponent(sessionId)}/queue-entries`,
    ))
}

/**
 * 대기 순번과 입장 상태 조회
 * GET /api/v1/queue-entries/{queueEntryId}
 */
export async function getQueueEntry(queueEntryId: string): Promise<QueueEntry> {
    return request(() => apiClient.get<ApiResponse<QueueEntry>>(
        `/api/v1/queue-entries/${encodeURIComponent(queueEntryId)}`,
    ))
}

/**
 * 대기 취소 또는 입장 종료 (204, 본문 없음)
 * DELETE /api/v1/queue-entries/{queueEntryId}
 */
export async function leaveQueue(queueEntryId: string): Promise<void> {
    try {
        await apiClient.delete(`/api/v1/queue-entries/${encodeURIComponent(queueEntryId)}`)
    } catch (error) {
        throw toQueueApiError(error)
    }
}

/**
 * 예매 시작 알림 신청 (C파트 API, 이미 신청했어도 같은 결과)
 * PUT /api/v1/me/opening-subscriptions/{performanceId}
 */
export async function subscribeOpening(performanceId: string): Promise<void> {
    try {
        await apiClient.put(`/api/v1/me/opening-subscriptions/${encodeURIComponent(performanceId)}`)
    } catch (error) {
        throw toQueueApiError(error)
    }
}

async function request(call: () => Promise<{data: ApiResponse<QueueEntry>}>): Promise<QueueEntry> {
    let body: ApiResponse<QueueEntry>
    try {
        body = (await call()).data
    } catch (error) {
        throw toQueueApiError(error)
    }

    if (body.code !== 'SUCCESS' || !body.data) {
        throw new QueueApiError(body.code, body.message || '대기열 정보를 불러오지 못했습니다.')
    }
    return body.data
}

function toQueueApiError(error: unknown): QueueApiError {
    if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
        const body = error.response?.data
        return new QueueApiError(
            body?.code ?? (error.response ? 'UNKNOWN_ERROR' : 'NETWORK_ERROR'),
            body?.message ?? '대기열 서버에 연결하지 못했습니다.',
            error.response?.status,
        )
    }
    return new QueueApiError('UNKNOWN_ERROR', '알 수 없는 오류가 발생했습니다.')
}
