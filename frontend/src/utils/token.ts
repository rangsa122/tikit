interface TokenPayload {
    roleName?: string
}

/** 액세스 토큰에서 역할을 꺼낸다. 못 읽으면 null. */
export function getRoleFromToken(token: string | null | undefined): string | null {
    if (!token) return null

    try {
        const payload = token.split('.')[1]
        if (!payload) return null

        // JWT는 base64url이라 일반 base64로 바꾸고 길이를 맞춘다
        const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
        const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)

        // 멀티바이트 문자를 제대로 읽기 위한 변환
        const json = decodeURIComponent(
            atob(padded)
                .split('')
                .map((char) => '%' + char.charCodeAt(0).toString(16).padStart(2, '0'))
                .join(''),
        )

        return (JSON.parse(json) as TokenPayload).roleName ?? null
    } catch {
        return null
    }
}