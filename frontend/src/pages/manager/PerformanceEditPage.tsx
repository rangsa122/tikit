import {useEffect, useState} from 'react'
import {useNavigate, useParams} from 'react-router'
import {getManagerPerformance, updatePerformance} from '../../api/managerPerformanceApi.ts'
import type {ManagerPerformance} from '../../api/managerPerformanceApi.ts'
import {toMessage} from '../../api/apiError.ts'
import PerformanceForm from '../../components/manager/PerformanceForm.tsx'
import type {FormState} from '../../components/manager/PerformanceForm.tsx'

// 서버에서 받은 공연을 폼 초기값으로 바꾼다
function toFormState(performance: ManagerPerformance): FormState {
    return {
        title: performance.title,
        genreCode: performance.genreCode,
        venueName: performance.venueName,
        organizerName: performance.organizerName ?? '',
        description: performance.description,
        posterUrl: performance.posterUrl ?? '',
        startDate: performance.startDate,
        endDate: performance.endDate,
        bookingLimitPerMember: String(performance.bookingLimitPerMember),
    }
}

export default function PerformanceEditPage() {
    const {performanceId} = useParams()
    const id = Number(performanceId)
    const navigate = useNavigate()

    const [performance, setPerformance] = useState<ManagerPerformance | null>(null)
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState('')
    const [serverError, setServerError] = useState('')
    const [saved, setSaved] = useState(false)
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        let ignore = false

        async function load() {
            setLoading(true)
            setLoadError('')
            try {
                const result = await getManagerPerformance(id)
                if (!ignore) setPerformance(result)
            } catch (e) {
                if (!ignore) setLoadError(toMessage(e, '공연을 불러오지 못했습니다'))
            } finally {
                if (!ignore) setLoading(false)
            }
        }

        void load()
        return () => {
            ignore = true
        }
    }, [id])

    async function handleSubmit(form: FormState) {
        if (!performance) return

        setServerError('')
        setSaved(false)
        setSubmitting(true)
        try {
            const updated = await updatePerformance(id, {
                expectedVersion: performance.version,
                title: form.title.trim(),
                genreCode: form.genreCode,
                venueName: form.venueName.trim(),
                organizerName: form.organizerName.trim() || undefined,
                description: form.description.trim(),
                posterUrl: form.posterUrl.trim() || undefined,
                startDate: form.startDate,
                endDate: form.endDate,
                bookingLimitPerMember: Number(form.bookingLimitPerMember),
            })
            // 저장하면 version이 올라간다. 새 값을 들고 있어야 연속 저장이 된다
            setPerformance(updated)
            setSaved(true)
        } catch (e) {
            setServerError(toMessage(e, '공연을 수정하지 못했습니다'))
        } finally {
            setSubmitting(false)
        }
    }

    if (loading) return <div className="p-8 text-gray-500">불러오는 중…</div>
    if (loadError) return <div className="p-8 text-red-600">{loadError}</div>
    if (!performance) return null

    // 게시된 공연은 서버가 PERFORMANCE_EDIT_LOCKED로 거절한다
    if (performance.publicationStatus !== 'DRAFT') {
        return (
            <div className="mx-auto max-w-[720px] px-6 py-10">
                <h1 className="text-2xl font-bold text-gray-900">공연 수정</h1>
                <div className="mt-6 rounded-2xl border border-gray-200 bg-white p-10 text-center">
                    <p className="text-sm text-gray-600">
                        이미 게시된 공연은 수정할 수 없습니다.
                    </p>
                    <button onClick={() => navigate('/manager/performances')}
                            className="mt-4 h-10 rounded-lg border border-gray-300 px-5 text-sm font-semibold text-gray-700">
                        목록으로
                    </button>
                </div>
            </div>
        )
    }

    return (
        <div className="mx-auto max-w-[720px] px-6 py-10">
            <h1 className="text-2xl font-bold text-gray-900">공연 수정</h1>
            <p className="mt-1 text-sm text-gray-500">
                {performance.title}
                <span className="ml-2 text-gray-400">version {performance.version}</span>
            </p>

            <PerformanceForm
                initial={toFormState(performance)}
                submitLabel="수정 저장"
                submitting={submitting}
                onSubmit={handleSubmit}
                onCancel={() => navigate('/manager/performances')}
            >
                {serverError && (
                    <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                        {serverError}
                    </div>
                )}

                {saved && (
                    <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                        저장했습니다.
                    </div>
                )}
            </PerformanceForm>
        </div>
    )
}