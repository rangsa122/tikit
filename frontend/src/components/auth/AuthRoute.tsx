
import type {ReactNode} from 'react'
import {Navigate, useLocation} from 'react-router'
import {useAuthStore} from '../../stores/authStore'

interface Props {
  children: ReactNode
}

export default function AuthRoute({children}: Props) {
  const tokens = useAuthStore((state) => state.tokens)
  const isInitialized = useAuthStore((state) => state.isInitialized)
  const location = useLocation()

  // 새로고침 후 로그인 정보 복구가 끝날 때까지 대기
  if (!isInitialized) {
    return (
      <p className="p-10 text-center text-sm text-gray-500">
        확인 중...
      </p>
    )
  }

  // 비로그인 상태라면 로그인 페이지로 이동
  if (!tokens?.accessToken) {
    return (
      <Navigate
        to="/login"
        state={{from: location.pathname}}
        replace
      />
    )
  }

  return <>{children}</>
}
