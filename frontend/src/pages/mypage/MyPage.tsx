import {Link} from 'react-router'
import {useEffect, useState} from 'react'
import {getMyProfile, type MemberProfile} from '../../api/memberApi'
import { getMyBookings, type Booking } from '../../api/bookingApi'
import MyPageSidebar from './MyPageSidebar'
import axios from 'axios'

const favorites = [
  {id: 1, title: '관심 공연 1'},
  {id: 2, title: '관심 공연 2'},
  {id: 3, title: '관심 공연 3'},
]

// 마이페이지 메인 화면
export default function MyPage() {
  const [profile, setProfile] = useState<MemberProfile | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [isAuthError, setIsAuthError] = useState(false)

  const [bookings, setBookings] = useState<Booking[]>([])
  const [bookingCount, setBookingCount] = useState<number | null>(null)
  const [isBookingsLoading, setIsBookingsLoading] = useState(true)
  const [bookingError, setBookingError] = useState('')

  // 회원정보 조회
  useEffect(() => {
    let active = true

    async function loadProfile() {
      try {
        const profileData = await getMyProfile()

        if (!active) return

        setProfile(profileData)
        setError('')
        setIsAuthError(false)
      } catch (error) {
        if (!active) return

        // 인증 오류 여부 확인
        if (axios.isAxiosError(error)) {
          if (error.response?.status === 401) {
            setError('로그인 시간이 만료되었거나 인증이 유효하지 않습니다.')
            setIsAuthError(true)
          } else if (error.response && error.response.status >= 500) {
            setError('서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.')
          } else if (!error.response) {
            setError('서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.')
          } else {
            setError('회원정보를 불러오지 못했습니다.')
          }
        } else {
          setError('회원정보를 불러오지 못했습니다.')
        }
      } finally {
        if (active) setIsLoading(false)
      }
    }

    void loadProfile()

    return () => {
      active = false
    }
  }, [])

  // 최근 예매 확정 3건 조회
  useEffect(() => {
    let active = true

    async function loadBookings() {
      try {
        const data = await getMyBookings(0, 3, 'CONFIRMED')

        if (!active) return

        setBookings(data.items)
        setBookingCount(data.totalElements)
        setBookingError('')
      } catch {
        if (!active) return

        setBookingError('예매 내역을 불러오지 못했습니다.')
      } finally {
        if (active) setIsBookingsLoading(false)
      }
    }

    void loadBookings()

    return () => {
      active = false
    }
  }, [])


  return (
    <div className="mx-auto w-full max-w-[1320px] px-5 py-10">
      <h1 className="border-b border-[#333] pb-5 text-[28px] font-semibold">
        마이페이지
      </h1>

      <div className="mt-8 flex flex-col gap-10 md:flex-row">
        <MyPageSidebar />

        <div className="min-w-0 flex-1">
          <section className="flex flex-col justify-between gap-6 rounded-md border border-[#ddd] bg-[#fafafa] p-7 sm:flex-row sm:items-center">
            <div>
              {isLoading ? (
                <p className="text-[14px] text-[#777]">
                  회원정보를 불러오는 중...
                </p>
              ) : error ? (
                <div>
                  <p role="alert" className="text-[14px] text-red-600">
                    {error}
                  </p>
                  {isAuthError && (
                    <Link
                      to="/login"
                      className="mt-3 inline-block rounded bg-[#333] px-4 py-2 text-[14px] text-white"
                    >
                      다시 로그인
                    </Link>
                  )}
                </div>
              ) : profile ? (
                <>
                  <h2 className="text-[22px] font-semibold">
                    {profile.name}님
                  </h2>
                  <p className="mt-2 text-[14px] text-[#777]">
                    {profile.email}
                  </p>
                </>
              ) : (
                <p className="text-[14px] text-[#777]">
                  회원정보를 표시할 수 없습니다.
                </p>
              )}
            </div>

            <div className="flex gap-10">
              <div className="text-center">
                <p className="text-[13px] text-[#666]">예매</p>
                <p className="mt-2 text-[20px] font-semibold">
                  {bookingCount ?? '—'}{' '}
                  <span className="text-[13px] font-normal">건</span>
                </p>
              </div>
              <div className="text-center">
                <p className="text-[13px] text-[#666]">관심 공연</p>
                <p className="mt-2 text-[20px] font-semibold">
                  3 <span className="text-[13px] font-normal">건</span>
                </p>
              </div>
            </div>
          </section>

          {/* 예매 내역 */}
          <section className="mt-12">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-[20px] font-semibold">
                예매 내역
              </h2>

              <Link
                  to="/mypage/bookings"
                  className="text-[13px] text-[#777] hover:text-[#4338CA]"
              >
                전체 보기 ›
              </Link>
            </div>

            <div className="bg-[#f5f5f5] px-5 py-3 text-[13px] text-[#666]">
              최근 확정 예매 내역
            </div>

            {isBookingsLoading ? (
                <p className="py-8 text-center text-[14px] text-[#777]">
                  예매 내역을 불러오는 중...
                </p>
            ) : bookingError ? (
                <p role="alert" className="py-8 text-center text-[14px] text-red-600">
                  {bookingError}
                </p>
            ) : bookings.length === 0 ? (
                <p className="py-10 text-center text-[14px] text-[#777]">
                  확정된 예매 내역이 없습니다.
                </p>
            ) : (
                bookings.map((booking) => (
                    <Link
                        key={booking.bookingId}
                        to={`/mypage/bookings/${booking.bookingId}`}
                        className="flex items-center justify-between gap-4 border-b border-[#eee] px-5 py-5 hover:bg-[#fafafa]"
                    >
                      <div className="min-w-0">
                        <p className="text-[15px] font-medium text-[#222]">
                          {booking.performanceTitle}
                        </p>

                        <p className="mt-2 text-[13px] text-[#888]">
                          {new Date(booking.startsAt).toLocaleString('ko-KR', {
                            timeZone: 'Asia/Seoul',
                            year: 'numeric',
                            month: '2-digit',
                            day: '2-digit',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </p>
                      </div>

                      <span className="shrink-0 rounded bg-[#eeecff] px-3 py-1 text-[12px] font-medium text-[#4338CA]">
                        예매 확정
                      </span>
                    </Link>
                ))
            )}
          </section>

          {/* 관심 공연 */}
          <section className="mt-12">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-[20px] font-semibold">관심 공연</h2>
              <Link
                to="/mypage/favorites"
                className="text-[13px] text-[#777] hover:text-black"
              >
                전체 보기 ›
              </Link>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              {favorites.map((favorite) => (
                <div key={favorite.id}>
                  {/* TODO: 추후 실제 공연 포스터로 교체 */}
                  <div className="flex aspect-[3/4] items-center justify-center rounded-md bg-[#f1f1f1] text-[13px] text-[#999]">
                    공연 이미지
                  </div>
                  <p className="mt-3 text-[14px] font-medium">
                    {favorite.title}
                  </p>
                </div>
              ))}
            </div>
          </section>

          {/* 계정 관리 */}
          <section className="mt-12">
            <h2 className="mb-4 text-[20px] font-semibold">
              계정 관리
            </h2>

            <div className="grid grid-cols-1 border border-[#ddd] sm:grid-cols-3">
              <Link
                to="/mypage/profile"
                className="flex items-center justify-between border-b border-[#ddd] px-5 py-6 text-[14px] sm:border-r sm:border-b-0"
              >
                내 정보 조회
                <span>›</span>
              </Link>

              <div className="flex items-center justify-between border-b border-[#ddd] px-5 py-6 text-[14px] sm:border-r sm:border-b-0">
                비밀번호 변경
                <span>›</span>
              </div>

              <div className="flex items-center justify-between px-5 py-6 text-[14px]">
                알림 설정
                <span>›</span>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
