
import {createBrowserRouter} from 'react-router'
import MainLayout from '../layouts/MainLayout'
import HomePage from '../pages/HomePage'
import LoginPage from '../pages/auth/LoginPage'
import SignupPage from '../pages/auth/SignupPage'
import SeatSelectionPage from '../pages/SeatSelectionPage'
import CategoryPage from '../pages/CategoryPage'
import PerformanceDetailPage from '../pages/PerformanceDetailPage'
import PaymentPage from '../pages/payment/PaymentPage'
import QueuePage from '../pages/queue/QueuePage'
import PaymentSuccessPage from '../pages/payment/PaymentSuccessPage'
import PaymentFailPage from '../pages/payment/PaymentFailPage'
import mypageRoutes from './mypageRoutes'

const router = createBrowserRouter([
  {
    element: <MainLayout />,
    children: [
      {path: '/', element: <HomePage />},
      {path: '/categories/:categoryId', element: <CategoryPage />},
      {path: '/performances/:performanceId', element: <PerformanceDetailPage />},
      {path: '/login', element: <LoginPage />},
      {path: '/signup', element: <SignupPage />},
      {path: '/sessions/:sessionId/seats', element: <SeatSelectionPage />},
      {path: '/bookings/:bookingId/payment', element: <PaymentPage />},
      {path: '/bookings/:bookingId/payment/success', element: <PaymentSuccessPage />},
      {path: '/bookings/:bookingId/payment/fail', element: <PaymentFailPage />},
      ...mypageRoutes,
    ],
  },
  // 대기열은 피그마 Header/Minimal만 쓰므로 사이트 메뉴 없이 따로 둠
  {path: '/sessions/:sessionId/queue', element: <QueuePage />},
])

export default router
