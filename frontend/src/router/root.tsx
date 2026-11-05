import {createBrowserRouter} from 'react-router'
import MainLayout from '../layouts/MainLayout'
import HomePage from '../pages/HomePage'
import LoginPage from '../pages/auth/LoginPage'
import SignupPage from '../pages/auth/SignupPage'
import MyPage from '../pages/mypage/MyPage'
import MemberProfilePage from '../pages/mypage/MemberProfilePage'
import CategoryPage from '../pages/CategoryPage'
import PerformanceDetailPage from '../pages/PerformanceDetailPage'
import PaymentPage from '../pages/payment/PaymentPage'
import QueuePage from '../pages/queue/QueuePage'

const router = createBrowserRouter([
  {
    element: <MainLayout />,
    children: [
      {path: '/', element: <HomePage />},
      {path: '/categories/:categoryId', element: <CategoryPage />},
      {path: '/performances/:performanceId', element: <PerformanceDetailPage />},
      {path: '/login', element: <LoginPage />},
      {path: '/signup', element: <SignupPage />},
      {path: '/mypage', element: <MyPage />},
      {path: '/mypage/profile', element: <MemberProfilePage />},
      {path: '/bookings/:bookingId/payment', element: <PaymentPage />},
    ],
  },
  // 대기열은 피그마 Header/Minimal만 쓰므로 사이트 메뉴 없이 따로 둠
  {path: '/sessions/:sessionId/queue', element: <QueuePage />},
])

export default router