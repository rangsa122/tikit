
import { Link, useLocation } from 'react-router'

// MyPage 왼쪽 사이드바

// 나의 예매 정보
const bookingMenus = [
    '예매 내역',
    '취소/환불 내역',
    '관심 공연',
]

// 계정 관리
const accountMenus = [
    { label: '내 정보 조회', path: '/mypage/profile' },
    { label: '비밀번호 변경', path: null },
    { label: '알림 설정', path: '/mypage/notifications' },
    { label: '로그아웃', path: null },
]

export default function MyPageSidebar() {
    const location = useLocation()

    return (
        <aside className="w-full shrink-0 md:w-[190px]">
            <div>
                <h2 className="mb-5 text-[15px] font-semibold">
                    나의 예매 정보
                </h2>

                <nav className="flex flex-col gap-4">
                    {bookingMenus.map((menu) => {
                        if (menu === '예매 내역') {
                            const isActive =
                                location.pathname === '/mypage/bookings' ||
                                location.pathname.startsWith('/mypage/bookings/')

                            return (
                                <Link
                                    key={menu}
                                    to="/mypage/bookings"
                                    aria-current={isActive ? 'page' : undefined}
                                    className={
                                        isActive
                                            ? 'rounded bg-[#eeeeee] px-2 py-2 text-[14px] text-[#111]'
                                            : 'text-[14px] text-[#555] hover:text-black'
                                    }
                                >
                                    {menu}
                                </Link>
                            )
                        }

                        if (menu === '관심 공연') {
                            const isActive = location.pathname === '/mypage/favorites'

                            return (
                                <Link
                                    key={menu}
                                    to="/mypage/favorites"
                                    aria-current={isActive ? 'page' : undefined}
                                    className={
                                        isActive
                                            ? 'rounded bg-[#eeeeee] px-2 py-2 text-[14px] text-[#111]'
                                            : 'text-[14px] text-[#555] hover:text-black'
                                    }
                                >
                                    {menu}
                                </Link>
                            )
                        }

                        if (menu === '취소/환불 내역') {
                            const isActive = location.pathname === '/mypage/cancellations'

                            return (
                                <Link
                                    key={menu}
                                    to="/mypage/cancellations"
                                    aria-current={isActive ? 'page' : undefined}
                                    className={
                                        isActive
                                            ? 'rounded bg-[#eeeeee] px-2 py-2 text-[14px] text-[#111]'
                                            : 'text-[14px] text-[#555] hover:text-black'
                                    }
                                >
                                    {menu}
                                </Link>
                            )
                        }

                        return null
                    })}
                </nav>
            </div>

            <div className="my-8 border-t border-[#ddd]" />

            <div>
                <h2 className="mb-5 text-[15px] font-semibold">
                    계정 관리
                </h2>

                <nav className="flex flex-col gap-4">
                    {accountMenus.map((menu) => {
                        // 아직 구현되지 않은 메뉴는 텍스트만 표시
                        if (!menu.path) {
                            return (
                                <span
                                    key={menu.label}
                                    className="text-[14px] text-[#555]"
                                >
                                    {menu.label}
                                </span>
                            )
                        }

                        // 현재 페이지 메뉴 강조
                        const isActive = location.pathname === menu.path

                        return (
                            <Link
                                key={menu.label}
                                to={menu.path}
                                aria-current={isActive ? 'page' : undefined}
                                className={
                                    isActive
                                        ? 'rounded bg-[#eeeeee] px-2 py-2 text-[14px] text-[#111]'
                                        : 'text-[14px] text-[#555] hover:text-black'
                                }
                            >
                                {menu.label}
                            </Link>
                        )
                    })}
                </nav>
            </div>
        </aside>
    )
}
