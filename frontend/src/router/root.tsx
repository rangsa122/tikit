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
import AdminGuard from '../pages/admin/AdminGuard'
import AdminMembersPage from '../pages/admin/AdminMembersPage'
import PerformanceCreatePage from '../pages/manager/PerformanceCreatePage'
import PerformanceListPage from '../pages/manager/PerformanceListPage'
import RoleRoute from '../components/auth/RoleRoute'
import SalesConfigurationPage from '../pages/manager/SalesConfigurationPage'
import KopisSearchPage from '../pages/manager/KopisSearchPage'
import PerformanceEditPage from '../pages/manager/PerformanceEditPage'
import BookingStatusPage from '../pages/manager/BookingStatusPage'

const router = createBrowserRouter([
    {
        element: <MainLayout />,
        children: [
            {
                element: <AdminGuard />,
                children: [
                    {path: '/admin/members', element: <AdminMembersPage />},
                ],
            },
            {path: '/', element: <HomePage />},
            {path: '/categories/:categoryId', element: <CategoryPage />},
            {path: '/performances/:performanceId', element: <PerformanceDetailPage />},
            {path: '/login', element: <LoginPage />},
            {path: '/signup', element: <SignupPage />},
            {path: '/bookings/:bookingId/payment', element: <PaymentPage />},
            {path: '/bookings/:bookingId/payment/success', element: <PaymentSuccessPage />},
            {path: '/bookings/:bookingId/payment/fail', element: <PaymentFailPage />},
            {
                path: '/manager/performances',
                element: (
                    <RoleRoute roles={['MANAGER', 'ADMIN']}>
                        <PerformanceListPage />
                    </RoleRoute>
                ),
            },
            {
                path: '/manager/performances/new',
                element: (
                    <RoleRoute roles={['MANAGER']}>
                        <PerformanceCreatePage />
                    </RoleRoute>
                ),
            },
            {
                path: '/manager/performances/kopis',
                element: (
                    <RoleRoute roles={['MANAGER']}>
                        <KopisSearchPage />
                    </RoleRoute>
                ),
            },
            {
                path: '/manager/performances/:performanceId/sales-configuration',
                element: (
                    <RoleRoute roles={['MANAGER']}>
                        <SalesConfigurationPage />
                    </RoleRoute>
                ),
            },
            {
                path: '/manager/performances/:performanceId/edit',
                element: (
                    <RoleRoute roles={['MANAGER']}>
                        <PerformanceEditPage />
                    </RoleRoute>
                ),
            },
            {
                path: '/manager/performances/:performanceId/booking-status',
                element: (
                    <RoleRoute roles={['MANAGER']}>
                        <BookingStatusPage />
                    </RoleRoute>
                ),
            },
            ...mypageRoutes,
        ],
    },
    // 대기열과 좌석 선택은 전용 헤더를 사용
    {path: '/sessions/:sessionId/queue', element: <QueuePage />},
    {path: '/sessions/:sessionId/seats', element: <SeatSelectionPage />},
])

export default router