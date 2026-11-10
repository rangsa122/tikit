import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router'
import axios from 'axios'
import {getManagerPerformances} from '../../api/managerPerformanceApi.ts'
import type {ManagerPerformance, PageResponse} from '../../api/managerPerformanceApi.ts'

const PAGE_SIZE = 10

type StatusFilter = 'ALL' | 'DRAFT' | 'PUBLISHED'

const FILTERS: { value: StatusFilter; label: string }[] = [
    {value: 'ALL', label: '전체'},
    {value: 'DRAFT', label: '작성 중'},
    {value: 'PUBLISHED', label: '게시됨'},
]

type Tone = 'gray' | 'green' | 'red' | 'indigo'

const TONE_CLASS: Record<Tone, string> = {
    gray: 'bg-gray-100 text-gray-600',
    green: 'bg-emerald-50 text-emerald-700',
    red: 'bg-red-50 text-red-600',
    indigo: 'bg-indigo-50 text-indigo-700',
}

function Badge({text, tone}: { text: string; tone: Tone }) {
    return (
        <span className={`rounded px-2 py-0.5 text-xs font-medium ${TONE_CLASS[tone]}`}>
            {text}
        </span>
    )
}

// "2026-12-01" → "2026.12.01"
function formatDate(value: string) {
    return value.split('-').join('.')
}

export default function PerformanceListPage() {
    const navigate = useNavigate()

    const [data, setData] = useState<PageResponse<ManagerPerformance> | null>(null)
    const [status, setStatus] = useState<StatusFilter>('ALL')
    const [page, setPage] = useState(0)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    useEffect(() => {
        let ignore = false   // 늦게 도착한 응답이 화면을 덮어쓰지 않게 한다

        const load = async () => {
            setLoading(true)
            setError('')
            try {
                const result = await getManagerPerformances({
                    page,
                    size: PAGE_SIZE,
                    publicationStatus: status === 'ALL' ? undefined : status,
                })
                if (!ignore) setData(result)
            } catch (err) {
                if (ignore) return
                if (axios.isAxiosError<{ code: string; message: string }>(err)) {
                    const httpStatus = err.response?.status
                    if (httpStatus === 401) setError('로그인이 필요해요.')
                    else if (httpStatus === 403) setError('공연 관리 권한이 없어요.')
                    else setError(err.response?.data?.message ?? '공연 목록을 불러오지 못했습니다.')
                } else {
                    setError(err instanceof Error ? err.message : '공연 목록을 불러오지 못했습니다.')
                }
            } finally {
                if (!ignore) setLoading(false)
            }
        }

        void load()
        return () => {
            ignore = true
        }
    }, [page, status])

    const changeStatus = (next: StatusFilter) => {
        setStatus(next)
        setPage(0)   // 조건이 바뀌면 첫 페이지부터
    }

    const goCreate = () => navigate('/manager/performances/new')

    return (
        <div className="mx-auto max-w-[960px] px-6 py-10">
            <div className="flex items-end justify-between">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">공연 관리</h1>
                    <p className="mt-1 text-sm text-gray-500">
                        등록한 공연을 확인하고 판매 설정과 게시를 관리해요.
                    </p>
                </div>
                <button onClick={goCreate}
                        className="h-11 shrink-0 rounded-lg bg-indigo-700 px-5 font-semibold text-white">
                    공연 등록
                </button>
            </div>

            <div className="mt-6 flex gap-2">
                {FILTERS.map((filter) => (
                    <button key={filter.value} onClick={() => changeStatus(filter.value)}
                            className={'h-9 rounded-full border px-4 text-sm ' + (
                                status === filter.value
                                    ? 'border-indigo-700 bg-indigo-50 font-semibold text-indigo-700'
                                    : 'border-gray-300 text-gray-600')}>
                        {filter.label}
                    </button>
                ))}
            </div>

            <div className="mt-4 overflow-hidden rounded-2xl border border-gray-200 bg-white">
                {loading && (
                    <p className="px-6 py-16 text-center text-sm text-gray-500">불러오는 중...</p>
                )}

                {!loading && error && (
                    <p className="px-6 py-16 text-center text-sm text-red-600">{error}</p>
                )}

                {!loading && !error && data && data.items.length === 0 && (
                    <div className="px-6 py-16 text-center">
                        <p className="text-sm text-gray-500">등록된 공연이 없어요.</p>
                        <button onClick={goCreate} className="mt-3 text-sm font-semibold text-indigo-700">
                            첫 공연 등록하기
                        </button>
                    </div>
                )}

                {!loading && !error && data && data.items.length > 0 && (
                    <ul className="divide-y divide-gray-100">
                        {data.items.map((performance) => (
                            <li key={performance.performanceId}
                                className="flex items-center gap-4 px-6 py-4">
                                <div className="min-w-0 flex-1">
                                    <p className="truncate font-semibold text-gray-900">
                                        {performance.title}
                                    </p>
                                    <p className="mt-1 truncate text-sm text-gray-500">
                                        {performance.genreName} · {performance.venueName} ·{' '}
                                        {formatDate(performance.startDate)} ~ {formatDate(performance.endDate)}
                                    </p>
                                </div>
                                <div className="flex shrink-0 gap-1.5">
                                    {performance.publicationStatus === 'PUBLISHED'
                                        ? <Badge text="게시됨" tone="green"/>
                                        : <Badge text="작성 중" tone="gray"/>}
                                    {performance.visibility === 'PUBLIC' && <Badge text="공개" tone="indigo"/>}
                                    {performance.visibility === 'HIDDEN' && <Badge text="숨김" tone="red"/>}
                                    {performance.visibility === 'PRIVATE' && <Badge text="비공개" tone="gray"/>}
                                    {performance.salesStatus === 'ENABLED'
                                        ? <Badge text="판매 중" tone="green"/>
                                        : <Badge text="판매 중지" tone="red"/>}
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </div>

            {!loading && !error && data && data.totalPages > 1 && (
                <div className="mt-6 flex items-center justify-center gap-4">
                    <button disabled={page === 0} onClick={() => setPage(page - 1)}
                            className="h-9 rounded-lg border border-gray-300 px-4 text-sm disabled:opacity-40">
                        이전
                    </button>
                    <span className="text-sm text-gray-600">{data.page + 1} / {data.totalPages}</span>
                    <button disabled={data.page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}
                            className="h-9 rounded-lg border border-gray-300 px-4 text-sm disabled:opacity-40">
                        다음
                    </button>
                </div>
            )}

            {!loading && !error && data && data.totalElements > 0 && (
                <p className="mt-3 text-center text-xs text-gray-500">전체 {data.totalElements}개</p>
            )}
        </div>
    )
}