
import type { RouteObject } from 'react-router'
import MyPage from '../pages/mypage/MyPage'
import MemberProfilePage from '../pages/mypage/MemberProfilePage'
import NotificationSettingsPage from '../pages/mypage/NotificationSettingsPage'
import FavoritePerformancesPage from '../pages/mypage/FavoritePerformancesPage'

// 마이페이지 관련 경로 관리
const mypageRoutes: RouteObject[] = [
  { path: '/mypage', element: <MyPage /> },
  { path: '/mypage/profile', element: <MemberProfilePage /> },
  { path: '/mypage/notifications', element: <NotificationSettingsPage /> },
  { path: '/mypage/favorites', element: <FavoritePerformancesPage /> },
]

export default mypageRoutes
