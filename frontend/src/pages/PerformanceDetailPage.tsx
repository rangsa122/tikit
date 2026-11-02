
import {Link, useParams} from 'react-router'
import {mockPerformances} from '../mocks/performances'
import SessionSelector from '../components/performance/SessionSelector'
import type {PerformanceSession} from '../components/performance/SessionSelector'
import {
  usePerformanceDetail,
  usePerformanceSessions,
} from '../hooks/usePerformanceDetail'

// 백엔드 API 연동 전까지 사용하는 임시 회차 데이터
const mockSessions: PerformanceSession[] = [
  {id: 'session-1', date: '2026-10-30', time: '14:00'},
  {id: 'session-2', date: '2026-10-30', time: '19:00'},
  {id: 'session-3', date: '2026-10-31', time: '15:00'},
  {id: 'session-4', date: '2026-11-01', time: '14:00'},
  {id: 'session-5', date: '2026-11-01', time: '18:00'},
]

// 실제 API가 준비되면 true로 변경
const USE_REAL_API = false

export default function PerformanceDetailPage() {
  const {performanceId} = useParams()

  const detailQuery = usePerformanceDetail(
    USE_REAL_API ? performanceId : undefined,
  )

  const sessionsQuery = usePerformanceSessions(
    USE_REAL_API ? performanceId : undefined,
  )

  const mockPerformance = mockPerformances.find(
    item => item.id === performanceId,
  )

  // 백엔드 응답 DTO가 확정되면 실제 데이터 변환 로직 추가
  const performance = mockPerformance
  const sessions = mockSessions

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

  return (
    <main className="mx-auto max-w-[1320px] px-5 py-12">
      <Link
        to={`/categories/${performance.categoryId}`}
        className="text-sm text-gray-500 hover:underline"
      >
        ← 공연 목록으로
      </Link>

      <div className="mt-8 grid gap-10 md:grid-cols-[320px_1fr]">
        <div
          aria-hidden="true"
          className="aspect-[3/4] rounded-xl bg-[#999]"
        />

        <section>
          <h1 className="text-3xl font-bold">
            {performance.title}
          </h1>

          <dl className="mt-8 grid grid-cols-[80px_1fr] gap-y-5 text-base">
            <dt className="font-semibold">공연 장소</dt>
            <dd>{performance.venue}</dd>

            <dt className="font-semibold">공연 기간</dt>
            <dd>{performance.period}</dd>
          </dl>
        </section>
      </div>

      <SessionSelector
        key={performance.id}
        sessions={sessions}
      />

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
