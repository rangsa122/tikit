
import {useState} from 'react'
import {Link, useNavigate, useParams} from 'react-router'
import type {QueuePageState} from './queue/QueuePage'
import {mockPerformances} from '../mocks/performances'
import SessionSelector from '../components/performance/SessionSelector'
import type {PerformanceSession} from '../components/performance/SessionSelector'
import {
  usePerformanceDetail,
  usePerformanceSessions,
} from '../hooks/usePerformanceDetail'

const USE_REAL_API = false

const CATEGORY_LABELS: Record<string, string> = {
  theater: '연극',
  musical: '뮤지컬',
  concert: '콘서트',
  classic: '클래식',
  exhibition: '전시',
  sports: '스포츠',
}

const DETAIL_TABS = [
  {id: 'notice', label: '공지사항'},
  {id: 'casting', label: '캐스팅'},
  {id: 'detail', label: '상세정보'},
  {id: 'price', label: '가격정보'},
  {id: 'guide', label: '이용안내'},
] as const

type DetailTab = (typeof DETAIL_TABS)[number]['id']

function createMockSessions(period: string): PerformanceSession[] {
  const dates = period.match(/\d{2}\.\d{2}\.\d{2}/g)

  if (!dates || dates.length !== 2) {
    return []
  }

  const [startYear, startMonth, startDay] = dates[0].split('.').map(Number)
  const [endYear, endMonth, endDay] = dates[1].split('.').map(Number)

  const start = new Date(2000 + startYear, startMonth - 1, startDay)
  const end = new Date(2000 + endYear, endMonth - 1, endDay)

  if (start > end) {
    return []
  }

  const sessions: PerformanceSession[] = []

  for (
    const current = new Date(start);
    current <= end;
    current.setDate(current.getDate() + 1)
  ) {
    const date = [
      current.getFullYear(),
      String(current.getMonth() + 1).padStart(2, '0'),
      String(current.getDate()).padStart(2, '0'),
    ].join('-')

    for (const time of ['14:00', '19:00']) {
      sessions.push({
        id: `${date}-${time}`,
        date,
        time,
      })
    }
  }

  return sessions
}

export default function PerformanceDetailPage() {
  const {performanceId} = useParams()
  const navigate = useNavigate()

  const [selectedSession, setSelectedSession] =
    useState<PerformanceSession | null>(null)
  const [activeTab, setActiveTab] = useState<DetailTab>('detail')
  const [bookingNotice, setBookingNotice] = useState(false)

  const detailQuery = usePerformanceDetail(
    USE_REAL_API ? performanceId : undefined,
  )

  const sessionsQuery = usePerformanceSessions(
    USE_REAL_API ? performanceId : undefined,
  )

  const performance = mockPerformances.find(
    item => item.id === performanceId,
  )

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

  const sessions = createMockSessions(performance.period)
  const categoryLabel =
    CATEGORY_LABELS[performance.categoryId] ?? performance.categoryId

  return (
    <main className="mx-auto max-w-[1320px] px-5 py-10">
      <Link
        to={`/categories/${performance.categoryId}`}
        className="text-sm text-gray-500 hover:text-blue-600"
      >
        ← 공연 목록으로
      </Link>

      <div className="mt-6 border-b border-gray-200 pb-7">
        <p className="text-sm font-semibold text-blue-600">
          공연 상세정보
        </p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight">
          {performance.title}
        </h1>
      </div>

      <div className="mt-8 grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_380px]">
        <section className="min-w-0">
          <div className="grid gap-7 sm:grid-cols-[230px_minmax(0,1fr)]">
            <div
              aria-label="공연 포스터 이미지 준비 중"
              className="flex aspect-[3/4] items-center justify-center rounded-xl bg-gradient-to-br from-slate-700 to-slate-900 p-6 text-center text-sm font-semibold text-white shadow-sm"
            >
              {performance.title}
            </div>

            <div>
              <h2 className="text-xl font-bold">{performance.title}</h2>

              <dl className="mt-6 grid grid-cols-[76px_1fr] gap-x-3 gap-y-5 text-sm">
                <dt className="font-semibold text-gray-500">장르</dt>
                <dd>{categoryLabel}</dd>

                <dt className="font-semibold text-gray-500">공연 장소</dt>
                <dd className="font-medium">{performance.venue}</dd>

                <dt className="font-semibold text-gray-500">공연 기간</dt>
                <dd>{performance.period}</dd>

                <dt className="font-semibold text-gray-500">예매 상태</dt>
                <dd>
                  <span className="rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700">
                    테스트 일정
                  </span>
                </dd>
              </dl>

              <div className="mt-7 rounded-xl bg-gray-50 p-4 text-sm text-gray-600">
                현재는 테스트용 공연 일정이 표시됩니다.
                실제 예매 기능은 API 연동 후 제공됩니다.
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
                  <h3 className="text-lg font-bold">공지사항</h3>
                  <p className="mt-4 text-sm leading-7 text-gray-600">
                    공연 관련 공지사항은 추후 등록될 예정입니다.
                  </p>
                </>
              )}

              {activeTab === 'casting' && (
                <>
                  <h3 className="text-lg font-bold">캐스팅</h3>
                  <p className="mt-4 text-sm leading-7 text-gray-600">
                    캐스팅 정보는 추후 제공될 예정입니다.
                  </p>
                </>
              )}

              {activeTab === 'detail' && (
                <>
                  <h3 className="text-lg font-bold">공연 상세정보</h3>
                  <p className="mt-4 text-sm leading-7 text-gray-600">
                    {performance.title} 공연의 상세 안내 페이지입니다.
                    공연 소개와 상세 이미지는 추후 등록될 예정입니다.
                  </p>

                  <div className="mt-6 rounded-lg bg-gray-50 p-5 text-sm leading-7 text-gray-600">
                    <p>공연 장소: {performance.venue}</p>
                    <p>공연 기간: {performance.period}</p>
                  </div>
                </>
              )}

              {activeTab === 'price' && (
                <>
                  <h3 className="text-lg font-bold">가격정보</h3>
                  <p className="mt-4 text-sm leading-7 text-gray-600">
                    좌석 등급별 가격은 실제 공연 데이터 연동 후 제공됩니다.
                  </p>
                </>
              )}

              {activeTab === 'guide' && (
                <>
                  <h3 className="text-lg font-bold">이용안내</h3>
                  <p className="mt-4 text-sm leading-7 text-gray-600">
                    예매 및 관람 안내는 추후 제공될 예정입니다.
                  </p>
                </>
              )}
            </div>
          </section>
        </section>

        <aside className="lg:sticky lg:top-6">
          <SessionSelector
            key={performance.id}
            sessions={sessions}
            onSessionChange={session => {
              setSelectedSession(session)
              setBookingNotice(false)
            }}
          />

          <div className="mt-4 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between text-sm">
              <span className="text-gray-500">선택한 관람 일정</span>
              <span className="font-semibold">
                {selectedSession
                  ? `${selectedSession.date} ${selectedSession.time}`
                  : '미선택'}
              </span>
            </div>

            <button
              type="button"
              disabled={!selectedSession}
              onClick={() => {
                // 목업 회차는 실제 회차 ID가 아니라서 대기열로 보내지 않음
                if (!USE_REAL_API || !selectedSession) {
                  setBookingNotice(true)
                  return
                }
                const queueState: QueuePageState = {
                  performanceId,
                  performanceTitle: performance.title,
                  sessionStartsAt: `${selectedSession.date}T${selectedSession.time}:00+09:00`,
                  venue: performance.venue,
                }
                navigate(`/sessions/${selectedSession.id}/queue`, {state: queueState})
              }}
              className="mt-5 w-full rounded-xl bg-blue-600 px-5 py-4 font-bold text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300"
            >
              예매하기
            </button>

            {bookingNotice && (
              <p role="status" className="mt-3 text-sm text-gray-600">
                현재는 예매 화면을 준비 중입니다.
                실제 예약은 진행되지 않습니다.
              </p>
            )}
          </div>
        </aside>
      </div>

      {USE_REAL_API && (detailQuery.isLoading || sessionsQuery.isLoading) && (
        <p className="mt-6 text-sm text-gray-500">
          공연 정보를 불러오는 중입니다.
        </p>
      )}

      {USE_REAL_API && (detailQuery.isError || sessionsQuery.isError) && (
        <p role="alert" className="mt-6 text-sm text-red-600">
          공연 정보를 불러오지 못했습니다.
        </p>
      )}
    </main>
  )
}
