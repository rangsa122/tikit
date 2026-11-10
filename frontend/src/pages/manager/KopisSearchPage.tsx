import {useState} from 'react'
import {useNavigate} from 'react-router'
import {
    getKopisPerformance,
    searchKopisPerformances,
    type KopisPerformanceItem,
} from '../../api/kopisApi.ts'
import {toMessage} from '../../api/apiError.ts'
import {GENRES} from '../../constants/genres.ts'

const PAGE_SIZE = 20
const MAX_RANGE_DAYS = 31

// "2026-11-10"
function toDateInput(date: Date): string {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}

function daysBetween(start: string, end: string): number {
    return Math.round((new Date(end).getTime() - new Date(start).getTime()) / 86_400_000)
}

function formatDate(value: string | null): string {
    return value ? value.split('-').join('.') : '-'
}

// KOPIS 포스터는 http로 오지만 https로도 열린다
function toHttps(url: string | null): string | undefined {
    if (!url) return undefined
    return url.startsWith('http://') ? `https://${url.slice('http://'.length)}` : url
}

export default function KopisSearchPage() {
    const navigate = useNavigate()

    const now = new Date()
    const [startDate, setStartDate] = useState(toDateInput(now))
    const [endDate, setEndDate] = useState(
        toDateInput(new Date(now.getTime() + 30 * 86_400_000)),
    )
    const [title, setTitle] = useState('')
    const [genreCode, setGenreCode] = useState('')

    const [items, setItems] = useState<KopisPerformanceItem[]>([])
    const [page, setPage] = useState(0)
    const [searched, setSearched] = useState(false)
    const [searching, setSearching] = useState(false)
    const [selecting, setSelecting] = useState<string | null>(null)
    const [error, setError] = useState('')

    // KOPIS 제약을 요청 전에 거른다
    function rangeProblem(): string {
        if (!startDate || !endDate) return '검색 기간을 입력해 주세요.'
        if (endDate < startDate) return '종료일은 시작일 이후여야 합니다.'
        if (daysBetween(startDate, endDate) > MAX_RANGE_DAYS) {
            return `KOPIS는 검색 기간을 ${MAX_RANGE_DAYS}일까지만 지원합니다.`
        }
        return ''
    }

    async function runSearch(nextPage: number) {
        const problem = rangeProblem()
        if (problem) {
            setError(problem)
            return
        }

        setSearching(true)
        setError('')
        try {
            const result = await searchKopisPerformances({
                startDate,
                endDate,
                title: title.trim() || undefined,
                genreCode: genreCode || undefined,
                page: nextPage,
                size: PAGE_SIZE,
            })
            setItems(result.items)
            setPage(result.page)
            setSearched(true)
        } catch (e) {
            setError(toMessage(e, 'KOPIS 공연을 검색하지 못했습니다'))
        } finally {
            setSearching(false)
        }
    }

    // 상세를 받아 등록 폼으로 넘긴다. 운영자가 거기서 고쳐서 등록한다
    async function handleSelect(item: KopisPerformanceItem) {
        setSelecting(item.kopisId)
        setError('')
        try {
            const detail = await getKopisPerformance(item.kopisId)
            navigate('/manager/performances/new', {state: {kopis: detail}})
        } catch (e) {
            setError(toMessage(e, '공연 정보를 불러오지 못했습니다'))
        } finally {
            setSelecting(null)
        }
    }

    return (
        <div className="mx-auto max-w-[900px] px-6 py-10">
        <h1 className="text-2xl font-bold text-gray-900">KOPIS에서 공연 등록</h1>
    <p className="mt-1 text-sm text-gray-500">
        공연예술통합전산망에서 공연을 찾아 등록합니다. 선택하면 등록 폼이 채워진 채로 열려요.
    </p>

    <div className="mt-6 rounded-2xl border border-gray-200 bg-white p-6">
    <div className="grid grid-cols-2 gap-4">
    <div>
        <label className="mb-2 block text-sm font-semibold">시작일</label>
        <input type="date" value={startDate}
    onChange={(e) => setStartDate(e.target.value)}
    className="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm"/>
    </div>
    <div>
    <label className="mb-2 block text-sm font-semibold">종료일</label>
        <input type="date" value={endDate}
    onChange={(e) => setEndDate(e.target.value)}
    className="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm"/>
    </div>
    <div>
    <label className="mb-2 block text-sm font-semibold">공연명</label>
        <input value={title} placeholder="선택 입력"
    onChange={(e) => setTitle(e.target.value)}
    className="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm"/>
    </div>
    <div>
    <label className="mb-2 block text-sm font-semibold">장르</label>
        <select value={genreCode}
    onChange={(e) => setGenreCode(e.target.value)}
    className="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm">
    <option value="">전체</option>
    {GENRES.map((g) => (
        <option key={g.code} value={g.code}>{g.name}</option>
    ))}
    </select>
    </div>
    </div>

    <button
    onClick={() => runSearch(0)}
    disabled={searching}
    className="mt-5 h-11 w-full rounded-lg bg-indigo-700 font-semibold text-white disabled:bg-gray-200 disabled:text-gray-400"
        >
        {searching ? '검색 중…' : '검색'}
        </button>

        <p className="mt-2 text-xs text-gray-500">
        KOPIS는 검색 기간을 최대 {MAX_RANGE_DAYS}일까지 지원합니다.
    </p>
    </div>

    {error && (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
            </div>
    )}

    {searched && items.length === 0 && !searching && (
        <p className="mt-6 rounded-2xl border border-dashed border-gray-300 p-10 text-center text-sm text-gray-500">
            검색 결과가 없습니다. 기간이나 공연명을 바꿔 보세요.
    </p>
    )}

    {items.length > 0 && (
        <ul className="mt-6 divide-y divide-gray-100 rounded-2xl border border-gray-200 bg-white">
            {items.map((item) => (
                    <li key={item.kopisId} className="flex items-center gap-4 p-4">
                    {toHttps(item.posterUrl) ? (
        <img src={toHttps(item.posterUrl)} alt=""
        className="h-24 w-16 shrink-0 rounded object-cover"/>
    ) : (
        <div className="flex h-24 w-16 shrink-0 items-center justify-center rounded bg-gray-100 text-[10px] text-gray-400">
            없음
            </div>
    )}

        <div className="min-w-0 flex-1">
        <p className="truncate font-semibold text-gray-900">{item.title}</p>
            <p className="mt-1 truncate text-sm text-gray-500">
        {item.venueName ?? '-'} · {formatDate(item.startDate)} ~ {formatDate(item.endDate)}
        </p>
        <p className="mt-1 text-xs text-gray-400">
        {item.genreName ?? '-'} · {item.area ?? '-'} · {item.state ?? '-'}
        </p>
        </div>

        {item.alreadyRegistered ? (
            <span className="shrink-0 rounded bg-gray-100 px-3 py-1.5 text-xs font-medium text-gray-500">
                등록됨
                </span>
        ) : (
            <button
                onClick={() => handleSelect(item)}
            disabled={selecting !== null}
            className="h-9 shrink-0 rounded-lg border border-gray-300 px-4 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-40"
            >
            {selecting === item.kopisId ? '불러오는 중…' : '선택'}
            </button>
        )}
        </li>
    ))}
        </ul>
    )}

    {items.length > 0 && (
        <div className="mt-6 flex items-center justify-center gap-4">
        <button disabled={page === 0 || searching} onClick={() => runSearch(page - 1)}
        className="h-9 rounded-lg border border-gray-300 px-4 text-sm disabled:opacity-40">
            이전
            </button>
            <span className="text-sm text-gray-600">{page + 1}</span>
        <button disabled={items.length < PAGE_SIZE || searching} onClick={() => runSearch(page + 1)}
        className="h-9 rounded-lg border border-gray-300 px-4 text-sm disabled:opacity-40">
            다음
            </button>
            </div>
    )}
    </div>
)
}