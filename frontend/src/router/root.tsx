
import {createBrowserRouter} from 'react-router'
import MainLayout from '../layouts/MainLayout'
import HomePage from '../pages/HomePage'
import LoginPage from '../pages/auth/LoginPage'
import SignupPage from '../pages/auth/SignupPage'
import CategoryPage from '../pages/CategoryPage'
import PerformanceDetailPage from '../pages/PerformanceDetailPage'
import PaymentPage from '../pages/payment/PaymentPage'
import PaymentSuccessPage from '../pages/payment/PaymentSuccessPage'
import PaymentFailPage from '../pages/payment/PaymentFailPage'
import mypageRoutes from './mypageRoutes'
import PerformanceCreatePage from "../pages/manager/PerformanceCreatePage.tsx";

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
      {path: '/manager/performances/new', element: <PerformanceCreatePage /> },
      ...mypageRoutes,
    ],
  },
])

export default router
