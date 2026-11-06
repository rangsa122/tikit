/**
 * 대기열에서 받은 입장 권한 보관 (회차별)
 *
 * 좌석 선택 화면(A-10)이 예매 생성 요청에 X-Admission-Id 헤더로 실어 보냄
 * 탭을 닫으면 사라지도록 sessionStorage 사용
 */

export interface StoredAdmission {
    admissionId: string
    queueEntryId: string
    expiresAt: string
}

const KEY_PREFIX = 'tikit:admission:'

export function saveAdmission(sessionId: string, admission: StoredAdmission) {
    try {
        sessionStorage.setItem(KEY_PREFIX + sessionId, JSON.stringify(admission))
    } catch {
        // 저장소를 못 쓰는 환경이면 다시 줄을 서서 받으면 됨
    }
}

export function getAdmission(sessionId: string): StoredAdmission | null {
    try {
        const raw = sessionStorage.getItem(KEY_PREFIX + sessionId)
        if (!raw) {
            return null
        }
        const admission = JSON.parse(raw) as StoredAdmission
        // 화면 표시용 판단. 최종 판단은 서버가 함
        return Date.parse(admission.expiresAt) > Date.now() ? admission : null
    } catch {
        return null
    }
}

export function clearAdmission(sessionId: string) {
    try {
        sessionStorage.removeItem(KEY_PREFIX + sessionId)
    } catch {
        // 무시
    }
}
