import type {MouseEvent} from 'react'
import {Link} from 'react-router'

interface OpeningAlarmBannerProps {
    isLoggedIn: boolean
    onLoginRequired: () => void
}

/** 찜·오픈 알림 안내 배너 (로그인 안 했으면 이동 대신 로그인 안내) */
export default function OpeningAlarmBanner({
                                               isLoggedIn,
                                               onLoginRequired,
                                           }: OpeningAlarmBannerProps) {
    function handleClick(event: MouseEvent<HTMLAnchorElement>) {
        if (isLoggedIn) return

        event.preventDefault()
        onLoginRequired()
    }

    return (
        <Link
            to="/mypage/favorites"
            onClick={handleClick}
            className="flex items-center gap-4 rounded-xl border border-[#e4e6eb] bg-white px-6 py-4 hover:bg-[#fafafa]"
        >
            <svg
                viewBox="0 0 24 24"
                aria-hidden="true"
                className="h-7 w-7 shrink-0"
            >
                <path
                    d="M12 20.5s-7.5-4.6-9.5-9.2C1 7.7 3.2 4.5 6.6 4.5c2.1 0 3.6 1.1 5.4 3.2 1.8-2.1 3.3-3.2 5.4-3.2 3.4 0 5.6 3.2 4.1 6.8-2 4.6-9.5 9.2-9.5 9.2z"
                    fill="#ff3b4e"
                />
            </svg>

            <div className="min-w-0 flex-1 leading-[normal]">
                <p className="font-semibold text-[#111]">
                    찜하고 오픈 알림을 받아보세요
                </p>
                <p className="mt-1 text-sm text-[#5b616e]">
                    관심 공연의 오픈 소식을 알려드려요.
                </p>
            </div>

            <span
                aria-hidden="true"
                className="text-xl text-[#5b616e]"
            >
        ›
      </span>
        </Link>
    )
}