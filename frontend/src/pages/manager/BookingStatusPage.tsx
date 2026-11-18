import {getManagerPerformance, type ManagerPerformance} from "../../api/managerPerformanceApi.ts";
import {useEffect, useState} from "react";
import {getBookingStatus, type PerformanceBookingStatus} from "../../api/bookingStatusApi.ts";
import {toMessage} from "../../api/apiError.ts";
import {useNavigate, useParams} from "react-router";

function formatNumber(value: number): string{
    return value.toLocaleString('ko-KR')
}

function formatMoney(value: number): string{
    return `${value.toLocaleString('ko-KR')}원`
}

function formatDateTime(value: string): string{
    const date = new Date(value)
    const pad = (n: number) => String(n).padStart(2,'0')

    return (
        `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())} `+
            `${pad(date.getHours())}:${pad(date.getMinutes())}`
    )
}

//판매율
function sellRate(soldSeats:number, totalSeats:number):number{
    if(totalSeats<=0) return 0
    return Math.round((soldSeats/totalSeats)*1000) / 10
}

//잔여좌석
function remaining(total:number, sold: number, held: number): number{
    return total - sold - held
}

function StatCard({label, value, hint}:{
    label: string
    value: string
    hint?: string
}){
    return (
        <div className="rounded-xl border border-gray-200 bg-white px-4 py-3">
            <p className="text-xs text-gray-500">{label}</p>
            <p className="mt-1 text-xl font-bold text-gray-900">{value}</p>
            {hint && <p className="mt-0.5 text-xs text-gray-400">{hint}</p>}
        </div>
    )
}

export default function BookingStatusPage(){
    const {performanceId} = useParams()
    const navigate = useNavigate()

    const [performance, setPerformance] = useState<ManagerPerformance | null>(null)
    const [status, setStatus] = useState<PerformanceBookingStatus | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    useEffect(() => {
        if (!performanceId) return

        let ignore = false   // 늦게 도착한 응답이 화면을 덮어쓰지 않게 한다
        const id = Number(performanceId)

        const load = async () => {
            setLoading(true)
            setError('')
            try {
                const [loadedPerformance, loadedStatus] = await Promise.all([
                    getManagerPerformance(id),
                    getBookingStatus(id),
                ])
                if (ignore) return
                setPerformance(loadedPerformance)
                setStatus(loadedStatus)
            } catch (err) {
                if (!ignore) setError(toMessage(err, '예매 현황을 불러오지 못했습니다'))
            } finally {
                if (!ignore) setLoading(false)
            }
        }

        void load()
        return () => {
            ignore = true
        }
    }, [performanceId])

    const summary = status?.summary

    return(
        <div className="mx-auto max-w-[1000px] px-6 py-10">
            <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                    <h1 className="truncate text-2xl font-bold text-gray-900">
                        {performance ? performance.title : '예매 현황'}
                    </h1>
                    <p className="mt-1 text-sm text-gray-500">
                        회차별 판매 현황과 매출을 확인합니다
                    </p>
                </div>

                <button
                    onClick={() => navigate('/manager/performances')}
                    className="h-10 shrink-0 rounded-lg border border-gray-300 px-4 text-sm font-semibold text-gray-700 hover:bg-gray-50"
                    >
                    목록으로
                </button>
            </div>

            {loading && (
                <p className="mt-6 rounded-2xl border border-dashed border-gray-300 p-10 text-center text-sm text-gray-500">
                    불러오는 중...
                </p>
            )}
            {!loading && error && (
                <div className="mt-6 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                    {error}
                </div>
            )}

            {!loading && !error && summary && status && (
                <>
                    <div className="mt-6 grid grid-cols-2 gap-3 md:grid-cols-4">
                        <StatCard
                            label="총 좌석"
                            value={formatNumber(summary.totalSeats)}
                        />
                        <StatCard
                            label="판매 좌석"
                            value={formatNumber(summary.soldSeats)}
                            hint={`판매율 ${sellRate(summary.soldSeats, summary.totalSeats)}%`}
                        />
                        <StatCard
                            label="선점 중"
                            value={formatNumber(summary.heldSeats)}
                            hint="결제 대기"
                        />
                        <StatCard
                            label="잔여 좌석"
                            value={formatNumber(
                                remaining(
                                    summary.totalSeats,
                                    summary.soldSeats,
                                    summary.heldSeats,
                                ),
                            )}
                        />
                        <StatCard
                            label="예매 건수"
                            value={formatNumber(summary.bookingCount)}
                            hint="좌석 수가 아닌 주문 수"
                        />
                        <StatCard
                            label="취소 건수"
                            value={formatNumber(summary.canceledCount)}
                        />
                        <StatCard
                            label="매출"
                            value={formatMoney(summary.revenue)}
                            hint="취소 제외"
                        />
                    </div>

                    <div className="mt-4 rounded-xl border border-gray-200 bg-white px-4 py-4">
                        <div className="flex items-center justify-between text-sm">
                            <span className="font-semibold text-gray-900">전체 판매율</span>
                            <span className="text-gray-600">
                                {formatNumber(summary.soldSeats)} / {formatNumber(summary.totalSeats)}
                                {' · '}
                                {sellRate(summary.soldSeats, summary.totalSeats)}%
                            </span>
                        </div>
                        <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-gray-100">
                            <div
                                className="h-full rounded-full bg-indigo-600"
                                style={{
                                    width: `${sellRate(summary.soldSeats, summary.totalSeats)}%`,
                                }}
                            />
                        </div>
                    </div>

                    <h2 className="mt-8 text-lg font-bold text-gray-900">회차별 현황</h2>

                    {status.sessions.length === 0 ? (
                        <p className="mt-3 rounded-2xl border border-dashed border-gray-300 p-10 text-center text-sm text-gray-500">
                            등록된 회차가 없습니다. 판매 설정에서 회차를 추가해 주세요.
                        </p>
                    ) : (
                        <div className="mt-3 overflow-x-auto rounded-2xl border border-gray-200 bg-white">
                            <table className="w-full min-w-[760px] text-sm">
                                <thead>
                                <tr className="border-b border-gray-100 bg-gray-50 text-xs text-gray-500">
                                    <th className="px-4 py-3 text-left font-medium">회차</th>
                                    <th className="px-4 py-3 text-right font-medium">총 좌석</th>
                                    <th className="px-4 py-3 text-right font-medium">판매</th>
                                    <th className="px-4 py-3 text-right font-medium">선점</th>
                                    <th className="px-4 py-3 text-right font-medium">잔여</th>
                                    <th className="px-4 py-3 text-right font-medium">판매율</th>
                                    <th className="px-4 py-3 text-right font-medium">예매</th>
                                    <th className="px-4 py-3 text-right font-medium">취소</th>
                                    <th className="px-4 py-3 text-right font-medium">매출</th>
                                </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                {status.sessions.map((session) => (
                                    <tr key={session.sessionId}>
                                        <td className="px-4 py-3 whitespace-nowrap text-gray-900">
                                            {formatDateTime(session.startsAt)}
                                        </td>
                                        <td className="px-4 py-3 text-right text-gray-600">
                                            {formatNumber(session.totalSeats)}
                                        </td>
                                        <td className="px-4 py-3 text-right font-semibold text-gray-900">
                                            {formatNumber(session.soldSeats)}
                                        </td>
                                        <td className="px-4 py-3 text-right text-gray-600">
                                            {formatNumber(session.heldSeats)}
                                        </td>
                                        <td className="px-4 py-3 text-right text-gray-600">
                                            {formatNumber(
                                                remaining(
                                                    session.totalSeats,
                                                    session.soldSeats,
                                                    session.heldSeats,
                                                ),
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-right text-gray-600">
                                            {sellRate(session.soldSeats, session.totalSeats)}%
                                        </td>
                                        <td className="px-4 py-3 text-right text-gray-600">
                                            {formatNumber(session.bookingCount)}
                                        </td>
                                        <td className="px-4 py-3 text-right text-gray-600">
                                            {formatNumber(session.canceledCount)}
                                        </td>
                                        <td className="px-4 py-3 text-right whitespace-nowrap text-gray-900">
                                            {formatMoney(session.revenue)}
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </>
            )}
        </div>
    )
}