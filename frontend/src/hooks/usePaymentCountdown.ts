import {useEffect, useState} from "react"

const TIK_MS = 1000
const EMPTY_LABEL = '--:--'

export interface PaymentCountdown {
    remainingSeconds : number
    isExpired : boolean
    label : string
}

/**
 * 서버 시계와 내 pc 시계의 차이 (ms) 계산
 *
 * API 응답을 받은 직후 한 번만 호출 (화면 그리는 중에 호출하지 않음)
 * 서버가 내 pc보다 3초 빠르면 +3000
 */
export function getClockOffsetMs(serverTime: string): number {
    const serverTimeMs = Date.parse(serverTime)

    if (Number.isNaN(serverTimeMs)) {
        return 0
    }

    return serverTimeMs - Date.now()
}

/**
 * 결제 제한시간 타이머
 *
 * 남은 시간 = expiresAt - (내 pc 현재 시각 + 시계 차이)
 * 화면 표시용이며, 기한 지남 여부의 최종 판단은 서버가 함
 */
export function usePaymentCountdown(
    expiresAt: string | undefined,
    clockOffsetMs: number,
) : PaymentCountdown {
    const [now, setNow] = useState(() => Date.now())

    useEffect(() => {
        const timerId = window.setInterval(() => setNow(Date.now()), TIK_MS)

        return () => window.clearInterval(timerId)
    }, [])

    const expiresAtMs = expiresAt ? Date.parse(expiresAt) : Number.NaN

    if (Number.isNaN(expiresAtMs)) {
        return {remainingSeconds: 0, isExpired: false, label: EMPTY_LABEL}
    }

    const remainingMs = expiresAtMs - (now + clockOffsetMs)
    const remainingSeconds = Math.max(0, Math.ceil(remainingMs / 1000))

    return {
        remainingSeconds,
        isExpired: remainingMs <= 0,
        label: formatMinutesSeconds(remainingSeconds),
    }
}

function formatMinutesSeconds(totalSeconds: number): string {
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60

    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}