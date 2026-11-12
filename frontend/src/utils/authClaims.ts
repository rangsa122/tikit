// 화면 표시용 정보입니다. 요청 권한 검증은 서버에서 수행합니다.
export function getAuthClaims(token?: string) {
  try {
    const payload = token?.split('.')[1]
    if (!payload) return null
    const value: unknown = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
    if (!value || typeof value !== 'object') return null
    const claims = value as Record<string, unknown>
    return {id: String(claims.sub ?? ''), role: String(claims.roleName ?? '')}
  } catch {
    return null
  }
}
