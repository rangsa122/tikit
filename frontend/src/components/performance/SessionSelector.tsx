
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

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function getMonthKey(date: string) {
  return date.slice(0, 7)
}

export default function SessionSelector({
  sessions,
  onSessionChange,
}: SessionSelectorProps) {
  const dates = [...new Set(sessions.map(session => session.date))].sort()
  const availableDates = new Set(dates)

  const [visibleMonth, setVisibleMonth] = useState(
    getMonthKey(dates[0] ?? new Date().toISOString().slice(0, 10)),
  )
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null)

  const [year, month] = visibleMonth.split('-').map(Number)
  const firstWeekday = new Date(year, month - 1, 1).getDay()
  const daysInMonth = new Date(year, month, 0).getDate()

  const calendarDays = [
    ...Array.from({length: firstWeekday}, () => null),
    ...Array.from({length: daysInMonth}, (_, index) => index + 1),
  ]

  const availableSessions = sessions.filter(
    session => session.date === selectedDate,
  )

  const selectedSession =
    sessions.find(session => session.id === selectedSessionId) ?? null

  function changeMonth(offset: number) {
    const next = new Date(year, month - 1 + offset, 1)
    const nextMonth = `${next.getFullYear()}-${String(
      next.getMonth() + 1,
    ).padStart(2, '0')}`

    setVisibleMonth(nextMonth)
    setSelectedDate(null)
    setSelectedSessionId(null)
    onSessionChange?.(null)
  }

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
    <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm sm:p-6">
      <h2 className="text-xl font-bold">관람일 및 회차 선택</h2>
      <p className="mt-1 text-sm text-gray-500">
        관람하실 날짜와 시간을 선택해 주세요.
      </p>

      <div className="mt-6 flex items-center justify-between">
        <button
          type="button"
          aria-label="이전 달"
          onClick={() => changeMonth(-1)}
          className="rounded-lg border border-gray-200 px-3 py-2 hover:bg-gray-50"
        >
          ‹
        </button>

        <h3 className="text-lg font-bold">
          {year}년 {month}월
        </h3>

        <button
          type="button"
          aria-label="다음 달"
          onClick={() => changeMonth(1)}
          className="rounded-lg border border-gray-200 px-3 py-2 hover:bg-gray-50"
        >
          ›
        </button>
      </div>

      <div className="mt-5 grid grid-cols-7 gap-1 text-center">
        {WEEKDAYS.map((day, index) => (
          <div
            key={day}
            className={`py-2 text-xs font-semibold ${
              index === 0 ? 'text-red-500' : 'text-gray-500'
            }`}
          >
            {day}
          </div>
        ))}

        {calendarDays.map((day, index) => {
          if (day === null) {
            return <div key={`empty-${index}`} />
          }

          const date = `${year}-${String(month).padStart(2, '0')}-${String(
            day,
          ).padStart(2, '0')}`

          const isAvailable = availableDates.has(date)
          const isSelected = selectedDate === date

          return (
            <button
              key={date}
              type="button"
              disabled={!isAvailable}
              aria-pressed={isSelected}
              onClick={() => handleDateSelect(date)}
              className={`aspect-square rounded-lg text-sm transition-colors ${
                isSelected
                  ? 'bg-blue-600 font-bold text-white'
                  : isAvailable
                    ? 'bg-blue-50 font-semibold text-blue-700 hover:bg-blue-100'
                    : 'cursor-not-allowed text-gray-300'
              }`}
            >
              {day}
            </button>
          )
        })}
      </div>

      <div className="mt-6 flex items-center gap-4 border-b border-gray-100 pb-5 text-xs text-gray-500">
        <span className="flex items-center gap-2">
          <span className="h-3 w-3 rounded bg-blue-50 ring-1 ring-blue-200" />
          선택 가능
        </span>
        <span className="flex items-center gap-2">
          <span className="h-3 w-3 rounded bg-blue-600" />
          선택한 날짜
        </span>
      </div>

      <div className="mt-6">
        <h3 className="font-bold">공연 회차</h3>

        {!selectedDate ? (
          <p className="mt-3 text-sm text-gray-500">
            먼저 달력에서 관람 날짜를 선택해 주세요.
          </p>
        ) : (
          <div className="mt-3 flex flex-wrap gap-2">
            {availableSessions.map((session, index) => (
              <button
                key={session.id}
                type="button"
                aria-pressed={selectedSessionId === session.id}
                onClick={() => handleSessionSelect(session)}
                className={`rounded-lg border px-4 py-3 text-sm transition-colors ${
                  selectedSessionId === session.id
                    ? 'border-blue-600 bg-blue-600 font-semibold text-white'
                    : 'border-gray-200 text-gray-700 hover:border-blue-400'
                }`}
              >
                {index + 1}회차 · {session.time}
              </button>
            ))}
          </div>
        )}
      </div>

      {selectedSession && (
        <div role="status" className="mt-6 rounded-xl bg-blue-50 p-4 text-sm">
          <p className="font-semibold text-blue-900">선택한 공연 일정</p>
          <p className="mt-1 text-blue-700">
            {selectedSession.date} · {selectedSession.time}
          </p>
        </div>
      )}
    </section>
  )
}
