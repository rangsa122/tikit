import {useState} from 'react'

export interface PerformanceSession {
  id: string
  date: string
  time: string
}

interface SessionSelectorProps {
  sessions: PerformanceSession[]
  onSessionChange?: (session: PerformanceSession | null) => void
}

export default function SessionSelector({
  sessions,
  onSessionChange,
}: SessionSelectorProps) {
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(
    null,
  )

  const dates = [...new Set(sessions.map(session => session.date))]

  const availableSessions = sessions.filter(
    session => session.date === selectedDate,
  )

  const selectedSession =
    sessions.find(session => session.id === selectedSessionId) ?? null

  function handleDateSelect(date: string) {
    setSelectedDate(date)
    setSelectedSessionId(null)
    onSessionChange?.(null)
  }

  function handleSessionSelect(session: PerformanceSession) {
    setSelectedSessionId(session.id)
    onSessionChange?.(session)
  }

  return (
    <section className="mt-10 rounded-xl border border-gray-200 p-6">
      <h2 className="text-xl font-bold">공연 날짜 및 회차 선택</h2>

      <div className="mt-6">
        <h3 className="mb-3 font-semibold">관람 날짜</h3>

        {dates.length === 0 ? (
          <p className="text-sm text-gray-500">
            현재 선택 가능한 공연 일정이 없습니다.
          </p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {dates.map(date => (
              <button
                key={date}
                type="button"
                aria-pressed={selectedDate === date}
                onClick={() => handleDateSelect(date)}
                className={`rounded-lg border px-4 py-2 text-sm transition-colors ${
                  selectedDate === date
                    ? 'border-black bg-black text-white'
                    : 'border-gray-300 bg-white text-gray-700 hover:border-black'
                }`}
              >
                {date}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="mt-8">
        <h3 className="mb-3 font-semibold">공연 회차</h3>

        {!selectedDate ? (
          <p className="text-sm text-gray-500">
            먼저 관람 날짜를 선택해 주세요.
          </p>
        ) : availableSessions.length === 0 ? (
          <p className="text-sm text-gray-500">
            선택한 날짜에 공연 회차가 없습니다.
          </p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {availableSessions.map((session, index) => (
              <button
                key={session.id}
                type="button"
                aria-pressed={selectedSessionId === session.id}
                onClick={() => handleSessionSelect(session)}
                className={`rounded-lg border px-4 py-2 text-sm transition-colors ${
                  selectedSessionId === session.id
                    ? 'border-black bg-black text-white'
                    : 'border-gray-300 bg-white text-gray-700 hover:border-black'
                }`}
              >
                {index + 1}회차 · {session.time}
              </button>
            ))}
          </div>
        )}
      </div>

      {selectedSession && (
        <div
          role="status"
          className="mt-8 rounded-lg bg-gray-100 p-4 text-sm"
        >
          <p className="font-semibold">선택한 공연 일정</p>
          <p className="mt-2">
            {selectedSession.date} · {selectedSession.time}
          </p>
        </div>
      )}
    </section>
  )
}