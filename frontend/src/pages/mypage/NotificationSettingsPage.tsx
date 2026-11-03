import { Link } from 'react-router'
import MyPageSidebar from './MyPageSidebar'

// 알림 설정 화면
export default function NotificationSettingsPage() {
    return (
        <div className="mx-auto w-full max-w-[1320px] px-5 py-10">
            <h1 className="border-b border-[#333] pb-5 text-[28px] font-semibold">
                <Link to="/mypage">
                    마이페이지
                </Link>
            </h1>

            <div className="mt-8 flex flex-col gap-10 md:flex-row">
                <MyPageSidebar />

                <main className="min-w-0 flex-1">
                    <h2 className="mb-3 text-[22px] font-semibold">
                        알림 설정
                    </h2>
                    <p className="mb-8 text-[14px] text-[#777]">
                        공연별 예매 시작 알림과 전체 알림 수신 여부를 관리할 수 있습니다.
                    </p>

                    <section className="rounded-md border border-[#ddd] p-6">
                        <div className="flex items-center justify-between gap-4">
                            <div>
                                <h3 className="text-[17px] font-semibold">
                                    전체 알림 수신
                                </h3>
                                <p className="mt-2 text-[13px] text-[#777]">
                                    신청한 공연의 예매 시작 알림을 수신합니다.
                                </p>
                            </div>

                        {/* TODO: 알림 API 연동 */}
                            <button
                                type="button"
                                role="switch"
                                aria-checked={false}
                                aria-label="전체 알림 수신"
                                disabled
                                className="flex h-7 w-12 shrink-0 items-center rounded-full bg-[#ccc] px-1 opacity-60"
                            >
                                <span className="h-5 w-5 rounded-full bg-white" />
                            </button>
                        </div>

                        <p className="mt-4 text-[12px] text-[#999]">
                            현재 수신 상태는 API 연동 후 표시됩니다.
                        </p>
                    </section>

                    <section className="mt-10">
                        <h3 className="mb-2 text-[20px] font-semibold">
                            공연별 알림 설정
                        </h3>
                        <p className="mb-6 text-[13px] text-[#777]">
                            공연별 예매 시작 알림 신청 내역을 확인하고 관리할 수 있습니다.
                        </p>

                        {/* TODO: 필터 기능 구현 */}
                        <div className="mb-5 flex flex-wrap gap-2">
                            {['전체', '알림 신청', '알림 해제'].map((filter) => (
                                <button
                                    key={filter}
                                    type="button"
                                    disabled
                                    className="rounded border border-[#ddd] px-4 py-2 text-[13px] text-[#777] opacity-60"
                                >
                                    {filter}
                                </button>
                            ))}
                        </div>

                        {/* TODO: 공연 목록 API 연동 + 화면 표시 */}
                        <div className="rounded-md border border-[#ddd] px-6 py-14 text-center">
                            <p className="text-[14px] text-[#777]">
                                공연별 알림 신청 내역은 API 연동 후 표시됩니다.
                            </p>
                        </div>
                    </section>

                    <section className="mt-10 rounded-md bg-[#f7f7f7] p-6">
                        <h3 className="mb-3 text-[15px] font-semibold">
                            알림 안내
                        </h3>
                        <p className="text-[13px] leading-7 text-[#666]">
                            예매 시작 알림은 사이트 내부 알림으로 제공됩니다.
                            <br />
                            전체 알림 수신을 OFF로 변경해도 공연별 알림 신청 기록은 유지됩니다.
                        </p>
                    </section>
                </main>
            </div>
        </div>
    )
}
