import {useEffect, useState} from 'react'
import {useParams} from 'react-router'
import {
    getSalesConfiguration,
    getSeatTemplates,
    saveSalesConfiguration,
    toLocalInput,
    toMessage,
    toOffsetDateTime,
    type SalesConfiguration,
    type SeatTemplate,
    type SeatTemplateSeat,
} from '../../api/salesConfigurationApi.ts'
import {
    getManagerPerformance,
    type ManagerPerformance,
} from '../../api/managerPerformanceApi.ts'

// 화면에서 다루는 회차. 입력값은 전부 문자열로 들고 있다가 저장할 때 변환한다
interface SessionForm {
    key: string                      // 화면에서 구분하는 용도. 서버에 안 보냄
    sessionId: number | null         // 기존 회차면 ID, 새 회차면 null
    startsAt: string                 // datetime-local 값 "2026-12-01T19:00"
    salesOpenAt: string
    salesCloseAt: string
    cancellationDeadline: string
    seatTemplateId: number | null
    prices: Record<string, string>   // 등급 -> 가격 입력값
}

let keyCounter = 0
const nextKey = () => `s${keyCounter++}`

function emptySession(): SessionForm {
    return {
        key: nextKey(),
        sessionId: null,
        startsAt: '',
        salesOpenAt: '',
        salesCloseAt: '',
        cancellationDeadline: '',
        seatTemplateId: null,
        prices: {},
    }
}

// 좌석 선택 화면과 같은 색·순서·통로를 쓴다
const AISLE_AFTER_SEATS = [4, 16]
const GRADE_ORDER = ['VIP', 'R', 'A']
const GRADE_COLOR: Record<string, string> = {
    VIP: 'bg-[#4c1d95]',
    R: 'bg-[#2563eb]',
    A: 'bg-[#7dd3fc]',
}
const DEFAULT_GRADE_COLOR = 'bg-gray-300'

function gradesOf(template: SeatTemplate): string[] {
    const grades = [...new Set(template.seats.map((s) => s.grade))]
    const rank = (g: string) => {
        const index = GRADE_ORDER.indexOf(g)
        return index === -1 ? GRADE_ORDER.length : index
    }
    return grades.sort((a, b) => rank(a) - rank(b) || a.localeCompare(b))
}

// 서버 검증(SalesConfigurationValidator)과 같은 규칙을 화면에서 먼저 본다.
// 서버는 어디가 틀렸는지 알려주지 않고 INVALID_SALES_CONFIGURATION 하나만 주기 때문이다.
function validate(
    bookingLimit: string,
    sessions: SessionForm[],
    templates: SeatTemplate[],
    performance: ManagerPerformance,
): string[] {
    const errors: string[] = []

    const limit = Number(bookingLimit)
    if (!Number.isInteger(limit) || limit < 1 || limit > 10) {
        errors.push('회원당 예매 제한은 1~10 사이여야 합니다.')
    }
    if (sessions.length < 1) errors.push('회차를 하나 이상 등록해 주세요.')
    if (sessions.length > 50) errors.push('회차는 최대 50개까지 등록할 수 있습니다.')

    const seenStarts = new Set<string>()

    sessions.forEach((session, index) => {
        const label = `${index + 1}회차`
        const {startsAt, salesOpenAt, salesCloseAt, cancellationDeadline} = session

        if (!startsAt || !salesOpenAt || !salesCloseAt || !cancellationDeadline) {
            errors.push(`${label}: 시각을 모두 입력해 주세요.`)
            return
        }

        if (seenStarts.has(startsAt)) {
            errors.push(`${label}: 공연 시작 시각이 다른 회차와 겹칩니다.`)
        }
        seenStarts.add(startsAt)

        const date = startsAt.slice(0, 10)
        if (date < performance.startDate || date > performance.endDate) {
            errors.push(
                `${label}: 공연 기간(${performance.startDate} ~ ${performance.endDate}) 안이어야 합니다.`,
            )
        }
        if (salesOpenAt >= salesCloseAt) {
            errors.push(`${label}: 판매 오픈은 판매 마감보다 빨라야 합니다.`)
        }
        if (salesCloseAt > startsAt) {
            errors.push(`${label}: 판매 마감은 공연 시작보다 늦을 수 없습니다.`)
        }
        if (cancellationDeadline > startsAt) {
            errors.push(`${label}: 취소 기한은 공연 시작보다 늦을 수 없습니다.`)
        }

        const template = templates.find((t) => t.templateId === session.seatTemplateId)
        if (!template) {
            errors.push(`${label}: 좌석 템플릿을 선택해 주세요.`)
            return
        }
        for (const grade of gradesOf(template)) {
            const raw = session.prices[grade]
            const price = Number(raw)
            if (raw === undefined || raw === '' ||
                !Number.isInteger(price) || price < 0 || price > 100_000_000) {
                errors.push(`${label}: ${grade} 가격을 0 ~ 100,000,000 사이로 입력해 주세요.`)
            }
        }
    })

    return errors
}

export default function SalesConfigurationPage() {
    const {performanceId} = useParams()
    const id = Number(performanceId)

    const [templates, setTemplates] = useState<SeatTemplate[]>([])
    const [performance, setPerformance] = useState<ManagerPerformance | null>(null)
    const [version, setVersion] = useState<number | null>(null)
    const [bookingLimit, setBookingLimit] = useState('4')
    const [sessions, setSessions] = useState<SessionForm[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [errors, setErrors] = useState<string[]>([])
    const [saving, setSaving] = useState(false)
    const [saved, setSaved] = useState(false)

    useEffect(() => {
        let ignore = false

        async function load() {
            setLoading(true)
            setError(null)
            try {
                const [templateList, config, performanceDetail] = await Promise.all([
                    getSeatTemplates(),
                    getSalesConfiguration(id),
                    getManagerPerformance(id),
                ])
                if (ignore) return
                setTemplates(templateList)
                setVersion(config.version)
                setBookingLimit(String(config.bookingLimitPerMember))
                setSessions(toForms(config))
                setPerformance(performanceDetail)
            } catch (e) {
                if (ignore) return
                setError(toMessage(e, '불러오지 못했습니다'))
            } finally {
                if (!ignore) setLoading(false)
            }
        }

        load()
        return () => {
            ignore = true
        }
    }, [id])

    // 회차 하나의 값을 바꾼다
    function updateSession(key: string, patch: Partial<SessionForm>) {
        setSessions((prev) =>
            prev.map((s) => (s.key === key ? {...s, ...patch} : s)),
        )
    }

    function updatePrice(key: string, grade: string, value: string) {
        setSessions((prev) =>
            prev.map((s) =>
                s.key === key ? {...s, prices: {...s.prices, [grade]: value}} : s,
            ),
        )
    }

    function addSession() {
        setSessions((prev) => [...prev, emptySession()])
    }

    function removeSession(key: string) {
        setSessions((prev) => prev.filter((s) => s.key !== key))
    }

    async function handleSave() {
        if (!performance || version === null) return

        const problems = validate(bookingLimit, sessions, templates, performance)
        setErrors(problems)
        setSaved(false)
        if (problems.length > 0) return

        setSaving(true)
        try {
            const result = await saveSalesConfiguration(id, {
                expectedVersion: version,
                bookingLimitPerMember: Number(bookingLimit),
                sessions: sessions.map((s) => {
                    const template = templates.find((t) => t.templateId === s.seatTemplateId)
                    const grades = template ? gradesOf(template) : []
                    return {
                        sessionId: s.sessionId,
                        startsAt: toOffsetDateTime(s.startsAt),
                        salesOpenAt: toOffsetDateTime(s.salesOpenAt),
                        salesCloseAt: toOffsetDateTime(s.salesCloseAt),
                        cancellationDeadline: toOffsetDateTime(s.cancellationDeadline),
                        seatTemplateId: Number(s.seatTemplateId),
                        gradePrices: grades.map((grade) => ({
                            grade,
                            price: Number(s.prices[grade]),
                        })),
                    }
                }),
            })

            // 저장하면 서버가 version을 올리고 새 회차에 ID를 붙여준다.
            // 그 값을 받아두지 않으면 다음 저장이 409가 나거나 회차가 중복 생성된다
            setVersion(result.version)
            setBookingLimit(String(result.bookingLimitPerMember))
            setSessions(toForms(result))
            setSaved(true)
        } catch (e) {
            setErrors([toMessage(e, '저장하지 못했습니다')])
        } finally {
            setSaving(false)
        }
    }

    if (loading) return <div className="p-8 text-gray-500">불러오는 중…</div>
    if (error) return <div className="p-8 text-red-600">{error}</div>

    return (
        <div className="mx-auto max-w-4xl p-8">
            <h1 className="text-2xl font-bold">판매 설정</h1>
            <p className="mt-1 text-sm text-gray-500">
                {performance?.title} · {performance?.startDate} ~ {performance?.endDate}
                <span className="ml-2 text-gray-400">version {version}</span>
            </p>

            <section className="mt-8">
                <label className="block text-sm font-semibold">회원당 예매 제한</label>
                <div className="mt-2 flex items-center gap-2">
                    <input
                        type="number" min={1} max={10}
                        value={bookingLimit}
                        onChange={(e) => setBookingLimit(e.target.value)}
                        className="w-24 rounded border px-3 py-2"
                    />
                    <span className="text-sm text-gray-500">매 (1~10)</span>
                </div>
            </section>

            <section className="mt-10">
                <div className="flex items-center justify-between">
                    <h2 className="text-lg font-semibold">회차 ({sessions.length}개)</h2>
                    <button
                        onClick={addSession}
                        className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white"
                    >
                        회차 추가
                    </button>
                </div>

                {sessions.length === 0 && (
                    <p className="mt-4 rounded-lg border border-dashed p-8 text-center text-sm text-gray-500">
                        등록된 회차가 없습니다. 회차를 추가해 주세요.
                    </p>
                )}

                <div className="mt-4 space-y-4">
                    {sessions.map((session, index) => (
                        <SessionCard
                            key={session.key}
                            index={index}
                            session={session}
                            templates={templates}
                            onChange={(patch) => updateSession(session.key, patch)}
                            onPriceChange={(grade, value) => updatePrice(session.key, grade, value)}
                            onRemove={() => removeSession(session.key)}
                        />
                    ))}
                </div>
            </section>

            <section className="mt-8">
                {errors.length > 0 && (
                    <ul className="mb-4 space-y-1 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                        {errors.map((message) => (
                            <li key={message}>· {message}</li>
                        ))}
                    </ul>
                )}

                {saved && (
                    <p className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700">
                        저장했습니다.
                    </p>
                )}

                <button
                    onClick={handleSave}
                    disabled={saving}
                    className="rounded bg-indigo-600 px-5 py-2.5 font-semibold text-white disabled:bg-gray-300"
                >
                    {saving ? '저장 중…' : '저장'}
                </button>
            </section>
        </div>
    )
}

function SessionCard({
                         index, session, templates, onChange, onPriceChange, onRemove,
                     }: {
    index: number
    session: SessionForm
    templates: SeatTemplate[]
    onChange: (patch: Partial<SessionForm>) => void
    onPriceChange: (grade: string, value: string) => void
    onRemove: () => void
}) {
    const template = templates.find((t) => t.templateId === session.seatTemplateId)
    const grades = template ? gradesOf(template) : []

    return (
        <div className="rounded-lg border p-5">
            <div className="flex items-center justify-between">
                <span className="font-semibold">
                    {index + 1}회차
                    {session.sessionId === null && (
                        <span className="ml-2 text-xs font-normal text-indigo-600">신규</span>
                    )}
                </span>
                <button onClick={onRemove} className="text-sm text-red-600">
                    삭제
                </button>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-4">
                <TimeField label="공연 시작"
                           value={session.startsAt}
                           onChange={(v) => onChange({startsAt: v})}/>
                <TimeField label="판매 오픈"
                           value={session.salesOpenAt}
                           onChange={(v) => onChange({salesOpenAt: v})}/>
                <TimeField label="판매 마감"
                           value={session.salesCloseAt}
                           onChange={(v) => onChange({salesCloseAt: v})}/>
                <TimeField label="취소 기한"
                           value={session.cancellationDeadline}
                           onChange={(v) => onChange({cancellationDeadline: v})}/>
            </div>

            <div className="mt-4">
                <label className="block text-sm font-semibold">좌석 템플릿</label>
                <select
                    value={session.seatTemplateId ?? ''}
                    onChange={(e) =>
                        onChange({seatTemplateId: e.target.value ? Number(e.target.value) : null})
                    }
                    className="mt-2 w-full rounded border px-3 py-2"
                >
                    <option value="">선택해 주세요</option>
                    {templates.map((t) => (
                        <option key={t.templateId} value={t.templateId}>
                            {t.name} ({t.seats.length}석)
                        </option>
                    ))}
                </select>
            </div>

            {template && (
                <div className="mt-4 flex flex-wrap items-start gap-10">
                    <div>
                        <label className="block text-sm font-semibold">등급별 가격</label>
                        <div className="mt-3 space-y-2">
                            {grades.map((grade) => (
                                <div key={grade} className="flex items-center gap-2">
                                    <span className={`size-3 shrink-0 rounded-[2px] ${GRADE_COLOR[grade] ?? DEFAULT_GRADE_COLOR}`}/>
                                    <span className="w-10 text-sm font-medium">{grade}</span>
                                    <input
                                        type="number" min={0}
                                        value={session.prices[grade] ?? ''}
                                        onChange={(e) => onPriceChange(grade, e.target.value)}
                                        className="w-32 rounded border px-3 py-2"
                                        placeholder="0"
                                    />
                                    <span className="text-sm text-gray-500">원</span>
                                </div>
                            ))}
                        </div>
                    </div>
                    <div>
                        <label className="block text-sm font-semibold">
                            좌석 배치 ({template.seats.length}석)
                        </label>
                        <div className="mt-3">
                            <TemplatePreview template={template}/>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}

function TimeField({label, value, onChange}: {
    label: string
    value: string
    onChange: (value: string) => void
}) {
    return (
        <div>
            <label className="block text-sm font-semibold">{label}</label>
            <input
                type="datetime-local"
                value={value}
                onChange={(e) => onChange(e.target.value)}
                className="mt-2 w-full rounded border px-3 py-2"
            />
        </div>
    )
}

function TemplatePreview({template}: { template: SeatTemplate }) {
    const rows = groupRows(template)

    return (
        <div className="inline-block rounded-lg border border-gray-200 p-3">
            <div className="mb-3 rounded bg-gray-100 py-1 text-center text-[10px] tracking-widest text-gray-500">
                STAGE
            </div>
            {rows.map((row, index) => (
                <div
                    key={row.label}
                    className={`flex items-center gap-1.5 ${
                        index > 0 && rows[index - 1].grade !== row.grade ? 'mt-3' : 'mt-1'
                    }`}
                >
                    <span className="w-3 text-[9px] text-gray-400">{row.label}</span>
                    <div className="flex items-center">
                        {row.seats.map((seat, i) => (
                            <span key={seat.seatCode} className="flex items-center">
                                <span
                                    title={`${seat.grade} ${seat.label}`}
                                    className={`size-2 rounded-[2px] ${GRADE_COLOR[seat.grade] ?? DEFAULT_GRADE_COLOR}`}
                                />
                                {i < row.seats.length - 1 && (
                                    <span className={AISLE_AFTER_SEATS.includes(seat.number) ? 'w-2' : 'w-0.5'}/>
                                )}
                            </span>
                        ))}
                    </div>
                    <span className="w-3 text-[9px] text-gray-400">{row.label}</span>
                </div>
            ))}
        </div>
    )
}

// 좌석을 열 단위로 묶는다. y가 작은 열이 무대에 가깝다
function groupRows(template: SeatTemplate) {
    const byRow = new Map<string, SeatTemplateSeat[]>()
    for (const seat of template.seats) {
        const list = byRow.get(seat.row) ?? []
        list.push(seat)
        byRow.set(seat.row, list)
    }
    return [...byRow.entries()]
        .map(([label, seats]) => {
            const sorted = [...seats].sort((a, b) => a.number - b.number)
            return {label, y: sorted[0].y, grade: sorted[0].grade, seats: sorted}
        })
        .sort((a, b) => a.y - b.y)
}

// 서버 응답을 화면이 다루는 형태로 바꾼다
function toForms(config: SalesConfiguration): SessionForm[] {
    return config.sessions.map((s) => ({
        key: nextKey(),
        sessionId: s.sessionId,
        startsAt: toLocalInput(s.startsAt),
        salesOpenAt: toLocalInput(s.salesOpenAt),
        salesCloseAt: toLocalInput(s.salesCloseAt),
        cancellationDeadline: toLocalInput(s.cancellationDeadline),
        seatTemplateId: s.seatTemplateId,
        prices: Object.fromEntries(s.gradePrices.map((p) => [p.grade, String(p.price)])),
    }))
}