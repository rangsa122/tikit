
import {createBrowserRouter} from 'react-router'
import MainLayout from '../layouts/MainLayout'
import HomePage from '../pages/HomePage'
import LoginPage from '../pages/auth/LoginPage'
import SignupPage from '../pages/auth/SignupPage'
import CategoryPage from '../pages/CategoryPage'
import PerformanceDetailPage from '../pages/PerformanceDetailPage'
import PaymentPage from '../pages/payment/PaymentPage'
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
            {path: '/bookings/:bookingId/payment', element: <PaymentPage />},
            ...mypageRoutes,
        ],
    },
])

export default router
