
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
import PerformanceCreatePage from "../pages/manager/PerformanceCreatePage.tsx";
import PerformanceListPage from "../pages/manager/PerformanceListPage.tsx";
import RoleRoute from "../components/auth/RoleRoute.tsx";
import KopisSearchPage from "../pages/manager/KopisSearchPage.tsx";

const router = createBrowserRouter([
  {
    element: <MainLayout />,
    children: [
      {path: '/', element: <HomePage />},
      {path: '/categories/:categoryId', element: <CategoryPage />},
      {path: '/performances/:performanceId', element: <PerformanceDetailPage />},
      {path: '/login', element: <LoginPage />},
      {path: '/signup', element: <SignupPage />},
      {path: '/bookings/:bookingId/payment', element: <PaymentPage />},
      {path: '/bookings/:bookingId/payment/success', element: <PaymentSuccessPage />},
      {path: '/bookings/:bookingId/payment/fail', element: <PaymentFailPage />},
      {path: '/manager/performances', element: (
            <RoleRoute roles={['MANAGER', 'ADMIN']}><PerformanceListPage /></RoleRoute>
        )},
      {path: '/manager/performances/new', element: (
            <RoleRoute roles={['MANAGER']}><PerformanceCreatePage /></RoleRoute>
        )},
      {path: '/manager/performances/kopis', element: (
            <RoleRoute roles={['MANAGER']}><KopisSearchPage /></RoleRoute>
        )},

      ...mypageRoutes,
    ],
  },
  // 대기열과 좌석 선택은 피그마에서 전용 헤더를 쓰므로 사이트 메뉴 없이 따로 둠
  {path: '/sessions/:sessionId/queue', element: <QueuePage />},
  {path: '/sessions/:sessionId/seats', element: <SeatSelectionPage />},
])

export default router
