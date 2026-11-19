import {useEffect, useRef, useState} from 'react'
import {isAxiosError} from 'axios'
import OpeningNotificationModal from '../components/performance/OpeningNotificationModal'
import {subscribeOpeningSubscription} from '../api/notificationApi'
import {Link, useNavigate, useParams} from 'react-router'
import {categories} from '../constants/categories'
import SessionSelector from '../components/performance/SessionSelector'
import type {PerformanceSession as SelectorSession} from '../components/performance/SessionSelector'
import {
  usePerformanceDetail,
  usePerformanceSessions,
} from '../hooks/usePerformanceDetail'
import {useAuthStore} from '../stores/authStore'
import type {QueuePageState} from './queue/QueuePage'
import {
  addFavoritePerformance,
  getFavoritePerformances,
  removeFavoritePerformance,
} from '../api/favoritePerformanceApi'

const DETAIL_TABS = [
  {id: 'notice', label: '공지사항'},
  {id: 'casting', label: '캐스팅'},
  {id: 'detail', label: '상세정보'},
  {id: 'price', label: '가격정보'},
  {id: 'guide', label: '이용안내'},
] as const

type DetailTab = (typeof DETAIL_TABS)[number]['id']

function formatPeriod(startDate: string, endDate: string) {
  return `${startDate} ~ ${endDate}`
}

function formatPrice(price: number | null) {
  if (price === null) {
    return '가격 정보 준비 중'
  }

  return `${price.toLocaleString('ko-KR')}원부터`
}

function toSelectorSessions(
  items: {
    sessionId: string
    startsAt: string
  }[],
): SelectorSession[] {
  return items.map(item => {
    const date = new Date(item.startsAt)

    if (Number.isNaN(date.getTime())) {
      return {
        id: item.sessionId,
        date: item.startsAt.slice(0, 10),
        time: item.startsAt.slice(11, 16),
      }
    }

    const formatter = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Seoul',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })

    const parts = formatter.formatToParts(date)

    const getPart = (type: string) =>
      parts.find(part => part.type === type)?.value ?? ''

    return {
      id: item.sessionId,
      date: [
        getPart('year'),
        getPart('month'),
        getPart('day'),
      ].join('-'),
      time: `${getPart('hour')}:${getPart('minute')}`,
    }
  })
}

export default function PerformanceDetailPage() {
  const {performanceId} = useParams()
  const navigate = useNavigate()

  const accessToken = useAuthStore(
    state => state.tokens?.accessToken,
  )

  const parsedId =
    performanceId && /^[1-9]\d*$/.test(performanceId)
      ? Number(performanceId)
      : null

  const numericPerformanceId =
    parsedId !== null && Number.isSafeInteger(parsedId)
      ? parsedId
      : null

  const isValidPerformance = numericPerformanceId !== null

  const detailQuery = usePerformanceDetail(
    isValidPerformance ? performanceId : undefined,
  )

  const sessionsQuery = usePerformanceSessions(
    isValidPerformance ? performanceId : undefined,
  )

  const performance = detailQuery.data

  const [selectedSession, setSelectedSession] =
    useState<SelectorSession | null>(null)

  const [activeTab, setActiveTab] = useState<DetailTab>('detail')
  const [bookingNotice, setBookingNotice] = useState(false)
  const [failedPosterUrl, setFailedPosterUrl] =
    useState<string | null>(null)

  const [isFavorite, setIsFavorite] = useState(false)
  const [favoriteLoading, setFavoriteLoading] = useState(false)
  const [favoriteSaving, setFavoriteSaving] = useState(false)
  const [favoriteError, setFavoriteError] = useState<string | null>(null)
  const [favoriteNotice, setFavoriteNotice] = useState<string | null>(null)
  const [favoriteStateReady, setFavoriteStateReady] = useState(false)

  const [openingModalOpen, setOpeningModalOpen] = useState(false)
  const [openingSubmitting, setOpeningSubmitting] = useState(false)
  const [openingError, setOpeningError] = useState<string | null>(null)
  const openingRequest = useRef(false)
  const openingContext = useRef(0)

  useEffect(() => {
    openingContext.current += 1
    openingRequest.current = false
    setOpeningModalOpen(false)
    setOpeningSubmitting(false)
    setOpeningError(null)
    setFavoriteSaving(false)
    return () => { openingContext.current += 1 }
  }, [accessToken, numericPerformanceId])

  const handleOpeningClose = () => {
    if (openingRequest.current) return
    setOpeningModalOpen(false)
    setOpeningError(null)
  }

  const handleOpeningConfirm = async () => {
    if (!openingModalOpen || !accessToken || numericPerformanceId === null || openingRequest.current) return

    const context = openingContext.current
    openingRequest.current = true
    setOpeningSubmitting(true)
    setOpeningError(null)

    try {
      await subscribeOpeningSubscription(numericPerformanceId)
      if (context !== openingContext.current) return
      setFavoriteNotice('관심 공연 등록 및 예매 시작 알림 신청이 완료되었습니다.')
      setOpeningModalOpen(false)
    } catch (error) {
      if (context !== openingContext.current) return
      const message = isAxiosError<{message?: string}>(error)
        ? error.response?.data?.message
        : undefined
      setOpeningError(message || '알림 신청에 실패했습니다. 다시 시도해 주세요.')
    } finally {
      if (context === openingContext.current) {
        openingRequest.current = false
        setOpeningSubmitting(false)
      }
    }
  }

  useEffect(() => {
    setSelectedSession(null)
    setBookingNotice(false)
    setActiveTab('detail')
    setFailedPosterUrl(null)
  }, [performanceId])

  useEffect(() => {
    let cancelled = false

    setIsFavorite(false)
    setFavoriteError(null)
    setFavoriteNotice(null)
    setFavoriteStateReady(false)

    if (!accessToken || numericPerformanceId === null) {
      setFavoriteLoading(false)

      return () => {
        cancelled = true
      }
    }

    setFavoriteLoading(true)

    const checkFavoriteStatus = async () => {
      try {
        const targetId = String(numericPerformanceId)
        let currentPage = 0
        let found = false
        let totalPages = 1

        // 관심 공연이 여러 페이지에 있어도 정확히 확인합니다.
        while (currentPage < totalPages && !found && !cancelled) {
          const result = await getFavoritePerformances(currentPage, 100)

          if (cancelled) return

          found = result.items.some(
            item => item.performanceId === targetId,
          )

          totalPages = result.totalPages
          currentPage += 1
        }

        if (cancelled) return

        setIsFavorite(found)
        setFavoriteStateReady(true)
      } catch {
        if (cancelled) return

        setFavoriteError('관심 공연 상태를 확인하지 못했습니다.')
      } finally {
        if (!cancelled) {
          setFavoriteLoading(false)
        }
      }
    }

    void checkFavoriteStatus()

    return () => {
      cancelled = true
    }
  }, [accessToken, numericPerformanceId])

  const handleFavoriteClick = async () => {
    if (favoriteLoading || favoriteSaving) return

    setFavoriteError(null)
    setFavoriteNotice(null)

    if (!accessToken) {
      setFavoriteNotice(
        '로그인 후 관심 공연을 등록할 수 있습니다.',
      )
      return
    }

    if (numericPerformanceId === null) {
      setFavoriteError('유효하지 않은 공연입니다.')
      return
    }

    if (!favoriteStateReady) {
      setFavoriteError(
        '관심 공연 상태를 확인하지 못했습니다. 페이지를 새로고침해 주세요.',
      )
      return
    }

    const context = openingContext.current

    try {
      setFavoriteSaving(true)

      if (isFavorite) {
        await removeFavoritePerformance(numericPerformanceId)
        if (context !== openingContext.current) return

        setIsFavorite(false)
        setFavoriteNotice('관심 공연을 해제했습니다.')
      } else {
        await addFavoritePerformance(numericPerformanceId)
        if (context !== openingContext.current) return

        setIsFavorite(true)
        setFavoriteNotice('관심 공연에 등록했습니다.')
        setOpeningError(null)
        setOpeningModalOpen(true)
      }
    } catch (error) {
      if (context !== openingContext.current) return
      setFavoriteError(
        error instanceof Error
          ? error.message
          : '관심 공연 처리 중 오류가 발생했습니다.',
      )
    } finally {
      if (context === openingContext.current) setFavoriteSaving(false)
    }
  }

  if (!isValidPerformance) {
    return (
      <main className="mx-auto max-w-[1320px] px-5 py-20 text-center">
        <h1 className="text-2xl font-bold">
          유효하지 않은 공연입니다.
        </h1>

        <Link to="/" className="mt-6 inline-block underline">
          메인으로 이동
        </Link>
      </main>
    )
  }

  if (detailQuery.isLoading) {
    return (
      <main
        role="status"
        className="mx-auto max-w-[1320px] px-5 py-20 text-center"
      >
        공연 정보를 불러오는 중입니다...
      </main>
    )
  }

  if (detailQuery.isError) {
    return (
      <main className="mx-auto max-w-[1320px] px-5 py-20 text-center">
        <h1 className="text-2xl font-bold">
          공연 정보를 불러오지 못했습니다.
        </h1>

        <button
          type="button"
          onClick={() => void detailQuery.refetch()}
          className="mt-6 rounded-lg bg-blue-600 px-5 py-3 text-white"
        >
          다시 시도
        </button>
      </main>
    )
  }

  if (!performance) {
    return (
      <main className="mx-auto max-w-[1320px] px-5 py-20 text-center">
        <h1 className="text-2xl font-bold">
          공연 정보를 찾을 수 없습니다.
        </h1>

        <Link to="/" className="mt-6 inline-block underline">
          메인으로 이동
        </Link>
      </main>
    )
  }

  const sessions = toSelectorSessions(
    sessionsQuery.data?.items ?? [],
  )

  const category = categories.find(
    item => item.id === performance.genreCode,
  )

  const categoryLabel =
    category?.label ?? performance.genreCode

  const period = formatPeriod(
    performance.startDate,
    performance.endDate,
  )

  const hasPoster =
      Boolean(performance.posterUrl) &&
      performance.posterUrl !== failedPosterUrl

  // 예매하기: 로그인 확인 -> 선택한 회차의 대기열로 이동
  // (판매 시작 전이면 대기열 화면이 카운트다운을 보여 주고, 입장 순서가 되면 좌석 선택으로 넘어감)
  const handleBookingClick = () => {
    if (!selectedSession) return

    // 대기열 등록은 회원만 가능 -> 비로그인은 안내만 하고 이동하지 않음
    if (!accessToken) {
      setBookingNotice(true)
      return
    }

    // 대기열 화면 상단에 보여 줄 공연 정보 (회차 시작 시각은 원래 ISO 값을 그대로 넘김)
    const session = sessionsQuery.data?.items.find(
        item => item.sessionId === selectedSession.id,
    )

    const queueState: QueuePageState = {
      performanceId: performance.performanceId,
      performanceTitle: performance.title,
      sessionStartsAt: session?.startsAt,
      venue: performance.venueName,
      posterUrl: performance.posterUrl ?? undefined,
    }

    navigate(`/sessions/${selectedSession.id}/queue`, {state: queueState})
  }

  return (
    <main className="mx-auto max-w-[1320px] px-5 py-10">
      <OpeningNotificationModal
        isOpen={openingModalOpen}
        isSubmitting={openingSubmitting}
        error={openingError}
        onClose={handleOpeningClose}
        onConfirm={() => void handleOpeningConfirm()}
      />
      <Link
        to={category ? `/categories/${category.id}` : '/'}
        className="text-sm text-gray-500 hover:text-blue-600"
      >
        ← 공연 목록으로
      </Link>

      <div className="mt-6 border-b border-gray-200 pb-7">
        <p className="text-sm font-semibold text-blue-600">
          공연 상세정보
        </p>

        <div className="mt-2 flex flex-wrap items-center justify-between gap-4">
          <h1 className="text-3xl font-bold tracking-tight">
            {performance.title}
          </h1>

          <button
            type="button"
            onClick={() => void handleFavoriteClick()}
            disabled={
              favoriteLoading ||
              favoriteSaving ||
              (Boolean(accessToken) && !favoriteStateReady)
            }
            aria-pressed={isFavorite}
            aria-label={
              isFavorite ? '관심 공연 해제' : '관심 공연 등록'
            }
            className={`flex items-center gap-2 rounded-lg border px-5 py-3 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
              isFavorite
                ? 'border-red-300 bg-red-50 text-red-600'
                : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'
            }`}
          >
            <span aria-hidden="true" className="text-xl">
              {isFavorite ? '♥' : '♡'}
            </span>

            {favoriteLoading
              ? '확인 중...'
              : favoriteSaving
                ? '처리 중...'
                : isFavorite
                  ? '관심 공연 등록됨'
                  : '관심 공연 등록'}
          </button>
        </div>

        {favoriteNotice && (
          <p role="status" className="mt-3 text-sm text-blue-600">
            {favoriteNotice}
          </p>
        )}

        {favoriteError && (
          <p role="alert" className="mt-3 text-sm text-red-600">
            {favoriteError}
          </p>
        )}
      </div>

      <div className="mt-8 grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_380px]">
        <section className="min-w-0">
          <div className="grid gap-7 sm:grid-cols-[230px_minmax(0,1fr)]">
            {hasPoster ? (
              <img
                src={performance.posterUrl ?? undefined}
                alt={`${performance.title} 포스터`}
                onError={() =>
                  setFailedPosterUrl(performance.posterUrl)
                }
                className="aspect-[3/4] w-full rounded-xl object-cover shadow-sm"
              />
            ) : (
              <div
                aria-label="공연 포스터 이미지 준비 중"
                className="flex aspect-[3/4] items-center justify-center rounded-xl bg-gradient-to-br from-slate-700 to-slate-900 p-6 text-center text-sm font-semibold text-white shadow-sm"
              >
                {performance.title}
              </div>
            )}

            <div>
              <h2 className="text-xl font-bold">
                {performance.title}
              </h2>

              <dl className="mt-6 grid grid-cols-[76px_1fr] gap-x-3 gap-y-5 text-sm">
                <dt className="font-semibold text-gray-500">
                  장르
                </dt>
                <dd>{categoryLabel}</dd>

                <dt className="font-semibold text-gray-500">
                  공연 장소
                </dt>
                <dd className="font-medium">
                  {performance.venueName}
                </dd>

                <dt className="font-semibold text-gray-500">
                  공연 기간
                </dt>
                <dd>{period}</dd>

                <dt className="font-semibold text-gray-500">
                  예매 상태
                </dt>
                <dd>
                  <span className="rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700">
                    {performance.salesStatus === 'ENABLED'
                      ? '판매 가능'
                      : performance.salesStatus === 'STOPPED'
                        ? '판매 중지'
                        : performance.salesStatus}
                  </span>
                </dd>

                <dt className="font-semibold text-gray-500">
                  가격
                </dt>
                <dd>{formatPrice(performance.minPrice)}</dd>
              </dl>

              <div className="mt-7 rounded-xl bg-gray-50 p-4 text-sm text-gray-600">
                공연 및 예매 정보를 확인해 주세요.
              </div>
            </div>
          </div>

          <section className="mt-10 overflow-hidden rounded-xl border border-gray-200">
            <div
              role="tablist"
              aria-label="공연 상세정보"
              className="flex overflow-x-auto border-b border-gray-200"
            >
              {DETAIL_TABS.map(tab => (
                <button
                  key={tab.id}
                  type="button"
                  role="tab"
                  id={`tab-${tab.id}`}
                  aria-selected={activeTab === tab.id}
                  aria-controls="performance-tab-panel"
                  onClick={() => setActiveTab(tab.id)}
                  className={`shrink-0 border-b-2 px-5 py-4 text-sm font-semibold ${
                    activeTab === tab.id
                      ? 'border-blue-600 text-blue-600'
                      : 'border-transparent text-gray-500 hover:text-gray-900'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            <div
              id="performance-tab-panel"
              role="tabpanel"
              aria-labelledby={`tab-${activeTab}`}
              className="min-h-48 p-6"
            >
              {activeTab === 'notice' && (
                <>
                  <h3 className="text-lg font-bold">
                    공지사항
                  </h3>

                  <p className="mt-4 text-sm leading-7 text-gray-600">
                    공연 관련 공지사항은 추후 등록될 예정입니다.
                  </p>
                </>
              )}

              {activeTab === 'casting' && (
                <>
                  <h3 className="text-lg font-bold">
                    캐스팅
                  </h3>

                  <p className="mt-4 text-sm leading-7 text-gray-600">
                    캐스팅 정보는 추후 제공될 예정입니다.
                  </p>
                </>
              )}

              {activeTab === 'detail' && (
                <>
                  <h3 className="text-lg font-bold">
                    공연 상세정보
                  </h3>

                  <p className="mt-4 whitespace-pre-line text-sm leading-7 text-gray-600">
                    {performance.description ||
                      '등록된 공연 상세정보가 없습니다.'}
                  </p>

                  <div className="mt-6 rounded-lg bg-gray-50 p-5 text-sm leading-7 text-gray-600">
                    <p>공연 장소: {performance.venueName}</p>
                    <p>공연 기간: {period}</p>
                  </div>
                </>
              )}

              {activeTab === 'price' && (
                <>
                  <h3 className="text-lg font-bold">
                    가격정보
                  </h3>

                  <p className="mt-4 text-sm leading-7 text-gray-600">
                    최저 가격: {formatPrice(performance.minPrice)}
                  </p>
                </>
              )}

              {activeTab === 'guide' && (
                <>
                  <h3 className="text-lg font-bold">
                    이용안내
                  </h3>

                  <p className="mt-4 whitespace-pre-line text-sm leading-7 text-gray-600">
                    {performance.cancellationPolicy ||
                      '예매 및 관람 안내는 추후 제공될 예정입니다.'}
                  </p>
                </>
              )}
            </div>
          </section>
        </section>

        <aside className="lg:sticky lg:top-6">
          {sessionsQuery.isLoading ? (
            <div
              role="status"
              className="rounded-xl border border-gray-200 p-6 text-sm text-gray-500"
            >
              공연 회차를 불러오는 중입니다...
            </div>
          ) : sessionsQuery.isError ? (
            <div
              role="alert"
              className="rounded-xl border border-red-200 p-6 text-sm text-red-600"
            >
              <p>공연 회차를 불러오지 못했습니다.</p>

              <button
                type="button"
                onClick={() => void sessionsQuery.refetch()}
                className="mt-3 underline"
              >
                다시 시도
              </button>
            </div>
          ) : sessions.length === 0 ? (
            <div
              role="status"
              className="rounded-xl border border-gray-200 p-6 text-sm text-gray-500"
            >
              등록된 공연 회차가 없습니다.
            </div>
          ) : (
            <SessionSelector
              key={performance.performanceId}
              sessions={sessions}
              onSessionChange={session => {
                setSelectedSession(session)
                setBookingNotice(false)
              }}
            />
          )}

          <div className="mt-4 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between text-sm">
              <span className="text-gray-500">
                선택한 관람 일정
              </span>

              <span className="font-semibold">
                {selectedSession
                  ? `${selectedSession.date} ${selectedSession.time}`
                  : '미선택'}
              </span>
            </div>

            <button
              type="button"
              disabled={
                !selectedSession ||
                sessionsQuery.isLoading ||
                sessionsQuery.isError ||
                performance.salesStatus !== 'ENABLED'
              }
              onClick={handleBookingClick}
              className="mt-5 w-full rounded-xl bg-blue-600 px-5 py-4 font-bold text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300"
            >
              예매하기
            </button>

            {bookingNotice && (
              <p
                role="status"
                className="mt-3 text-sm text-gray-600"
              >
                로그인 후 예매할 수 있습니다.{' '}
                <Link to="/login" className="font-semibold text-blue-600 underline">
                  로그인하기
                </Link>
              </p>
            )}
          </div>
        </aside>
      </div>
    </main>
  )
}
