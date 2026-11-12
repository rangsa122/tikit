import {Link, Navigate, Outlet, useLocation} from 'react-router'
import {useAuthStore} from '../../stores/authStore'
import {getAuthClaims} from '../../utils/authClaims'

export default function AdminGuard() {
  const {tokens, isInitialized} = useAuthStore()
  const location = useLocation()
  if (!isInitialized) return <p role="status" className="p-10">인증 정보를 확인하고 있습니다.</p>
  if (!tokens) return <Navigate to="/login" replace state={{from: location.pathname}} />
  if (getAuthClaims(tokens.accessToken)?.role !== 'ADMIN') return (
    <div className="mx-auto max-w-6xl px-5 py-16"><h1 className="text-2xl font-semibold">관리자 전용 화면입니다.</h1><Link to="/" className="mt-6 inline-block underline">홈으로 이동</Link></div>
  )
  return <Outlet />
}
