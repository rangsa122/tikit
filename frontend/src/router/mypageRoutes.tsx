
import type { RouteObject } from 'react-router'
import MyPage from '../pages/mypage/MyPage'
import MemberProfilePage from '../pages/mypage/MemberProfilePage'
import NotificationSettingsPage from '../pages/mypage/NotificationSettingsPage'
import FavoritePerformancesPage from '../pages/mypage/FavoritePerformancesPage'
import BookingHistoryPage from '../pages/mypage/BookingHistoryPage'
import BookingDetailPage from '../pages/mypage/BookingDetailPage'
import CancellationHistoryPage from '../pages/mypage/CancellationHistoryPage'
import MemberWithdrawalPage from '../pages/mypage/MemberWithdrawalPage'
import AuthRoute from '../components/auth/AuthRoute'

// 마이페이지 관련 경로 관리
const mypageRoutes: RouteObject[] = [
  { path: '/mypage', element: <MyPage /> },
  { path: '/mypage/profile', element: <MemberProfilePage /> },
  {
    path: '/mypage/favorites',
    element: (
      <AuthRoute>
        <FavoritePerformancesPage />
      </AuthRoute>
    ),
  },
  { path: '/mypage/bookings', element: <BookingHistoryPage /> },
  { path: '/mypage/bookings/:bookingId', element: <BookingDetailPage /> },
  { path: '/mypage/cancellations', element: <CancellationHistoryPage /> },
  { path: '/mypage/withdrawal', element: <MemberWithdrawalPage /> },
]

export default mypageRoutes
