import type {ReactNode} from 'react'
import {Navigate} from 'react-router'
import {useAuthStore} from '../../stores/authStore.ts'
import {getRoleFromToken} from '../../utils/token.ts'

interface Props {
    roles: string[]
    children: ReactNode
}

export default function RoleRoute({roles, children}: Props) {
    const tokens = useAuthStore((state) => state.tokens)
    const isInitialized = useAuthStore((state) => state.isInitialized)

    // 새로고침 직후 토큰을 복구하는 중이면 기다린다
    if (!isInitialized) {
        return <p className="p-10 text-center text-sm text-gray-500">확인 중...</p>
    }

    if (!tokens?.accessToken) {
        return <Navigate to="/login" replace/>
    }

    const role = getRoleFromToken(tokens.accessToken)
    if (!role || !roles.includes(role)) {
        return <Navigate to="/" replace/>
    }

    return <>{children}</>
}