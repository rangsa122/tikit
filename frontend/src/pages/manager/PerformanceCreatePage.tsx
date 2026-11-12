import {useState} from 'react'
import {useLocation, useNavigate} from 'react-router'
import {createPerformance} from '../../api/managerPerformanceApi.ts'
import {toMessage} from '../../api/apiError.ts'
import type {KopisPerformanceDetail} from '../../api/kopisApi.ts'
import PerformanceForm, {INITIAL_FORM} from '../../components/manager/PerformanceForm.tsx'
import type {FormState} from '../../components/manager/PerformanceForm.tsx'

function fromKopis(detail: KopisPerformanceDetail): FormState {
    return {
        title: detail.title ?? '',
        genreCode: detail.genreCode ?? '',
        venueName: detail.venueName ?? '',
        organizerName: detail.organizerName ?? '',
        description: detail.description ?? '',
        posterUrl: detail.posterUrl ?? '',
        startDate: detail.startDate ?? '',
        endDate: detail.endDate ?? '',
        bookingLimitPerMember: '4',
    }
}

export default function PerformanceCreatePage() {
    const location = useLocation()
    const navigate = useNavigate()
    const kopis = (location.state as { kopis?: KopisPerformanceDetail } | null)?.kopis ?? null

    const [serverError, setServerError] = useState('')
    const [createdId, setCreatedId] = useState<number | null>(null)
    const [submitting, setSubmitting] = useState(false)

    async function handleSubmit(form: FormState) {
        setServerError('')
        setCreatedId(null)
        setSubmitting(true)
        try {
            const created = await createPerformance({
                source: kopis ? 'KOPIS' : 'MANUAL',
                kopisId: kopis?.kopisId,
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
            setCreatedId(created.performanceId)
        } catch (e) {
            setServerError(toMessage(e, '공연을 등록하지 못했습니다'))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="mx-auto max-w-[720px] px-6 py-10">
            <h1 className="text-2xl font-bold text-gray-900">
                {kopis ? 'KOPIS 공연 등록' : '공연 직접 등록'}
            </h1>
            <p className="mt-1 text-sm text-gray-500">
                공연 기본 정보를 입력해 등록합니다. 회차와 가격은 등록한 뒤 판매 설정에서 작성해주세요.
            </p>

            {kopis && (
                <div className="mt-4 rounded-lg border border-indigo-200 bg-indigo-50 px-4 py-3 text-sm text-indigo-900">
                    KOPIS에서 가져온 정보입니다 (공연 ID: {kopis.kopisId}). 필수항목들을 채운 후 등록해주세요.
                    {!kopis.genreCode && (
                        <span className="mt-1 block font-semibold">
                            KOPIS 장르 “{kopis.genreName ?? '-'}”는 우리 장르와 연결되지 않아 비어 있습니다. 직접 선택해 주세요.
                        </span>
                    )}
                </div>
            )}

            <PerformanceForm
                initial={kopis ? fromKopis(kopis) : INITIAL_FORM}
                submitLabel="공연 등록"
                submitting={submitting}
                onSubmit={handleSubmit}
                onCancel={() => navigate('/manager/performances')}
            >
                <div className="rounded-lg bg-indigo-50 px-4 py-3 text-sm text-indigo-900">
                    등록한 공연은 비공개 상태로 저장 됩니다. 판매 설정을 마친 뒤 공개해주세요.
                </div>

                {serverError && (
                    <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                        {serverError}
                    </div>
                )}

                {createdId !== null && (
                    <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                        <p>공연이 등록되었습니다. (공연 번호: {createdId})</p>
                        <div className="mt-3 flex gap-2">
                            <button type="button"
                                    onClick={() => navigate(`/manager/performances/${createdId}/sales-configuration`)}
                                    className="h-9 rounded-lg bg-indigo-700 px-4 text-sm font-semibold text-white">
                                판매 설정하러 가기
                            </button>
                            <button type="button"
                                    onClick={() => navigate('/manager/performances')}
                                    className="h-9 rounded-lg border border-gray-300 bg-white px-4 text-sm font-semibold text-gray-700">
                                목록으로
                            </button>
                        </div>
                    </div>
                )}
            </PerformanceForm>
        </div>
    )
}