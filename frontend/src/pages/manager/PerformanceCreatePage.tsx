
import {useState} from 'react'
import type {ChangeEvent, FocusEvent, FormEvent, ReactNode} from 'react'
import axios from 'axios'
import {createPerformance} from '../../api/managerPerformanceApi.ts'
import type {KopisPerformanceDetail} from '../../api/kopisApi.ts'
import {useGenres} from '../../hooks/useGenres.ts'
import {useLocation, useNavigate} from 'react-router'

interface FormState {
    title: string
    genreCode: string
    venueName: string
    organizerName: string
    description: string
    posterUrl: string
    startDate: string
    endDate: string
    bookingLimitPerMember: string
}

type FormErrors = Partial<Record<keyof FormState, string>>
type Touched = Partial<Record<keyof FormState, boolean>>

const INITIAL_FORM: FormState = {
    title: '',
    genreCode: '',
    venueName: '',
    organizerName: '',
    description: '',
    posterUrl: '',
    startDate: '',
    endDate: '',
    bookingLimitPerMember: '4',
}

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

function validate(form: FormState): FormErrors {
    const errors: FormErrors = {}

    if (!form.title.trim()) {
        errors.title = '공연명을 입력해 주세요.'
    } else if (form.title.length > 200) {
        errors.title = '공연명은 200자 이하로 입력해 주세요.'
    }

    if (!form.genreCode) {
        errors.genreCode = '장르를 선택해 주세요.'
    }

    if (!form.venueName.trim()) {
        errors.venueName = '공연장을 입력해 주세요.'
    } else if (form.venueName.length > 200) {
        errors.venueName = '공연장은 200자 이하로 입력해 주세요.'
    }

    if (form.organizerName.length > 100) {
        errors.organizerName = '주최자는 100자 이하로 입력해 주세요.'
    }

    if (!form.description.trim()) {
        errors.description = '공연 설명을 입력해 주세요.'
    } else if (form.description.length > 10000) {
        errors.description = '공연 설명은 10000자 이하로 입력해 주세요.'
    }

    if (form.posterUrl && !form.posterUrl.startsWith('https://')) {
        errors.posterUrl = '포스터 주소는 https:// 로 시작해야 합니다.'
    }

    if (!form.startDate) {
        errors.startDate = '시작일을 입력해 주세요.'
    }

    if (!form.endDate) {
        errors.endDate = '종료일을 입력해 주세요.'
    } else if (form.startDate && form.startDate > form.endDate) {
        errors.endDate = '종료일은 시작일 이후여야 합니다.'
    }

    const limit = Number(form.bookingLimitPerMember)

    if (!Number.isInteger(limit) || limit < 1 || limit > 10) {
        errors.bookingLimitPerMember = '1장에서 10장 사이로 정할 수 있습니다.'
    }

    return errors
}

interface FieldProps {
    label: string
    required?: boolean
    error?: string
    children: ReactNode
}

function Field({label, required, error, children}: FieldProps) {
    return (
        <div>
            <label className="mb-2 block text-sm font-semibold text-gray-900">
                {label}
                {required && (
                    <span className="ml-0.5 text-red-600">*</span>
                )}
            </label>

            {children}

            {error && (
                <p className="mt-1.5 text-xs text-red-600">
                    {error}
                </p>
            )}
        </div>
    )
}

export default function PerformanceCreatePage() {
    const location = useLocation()
    const navigate = useNavigate()

    const kopis = (
        location.state as {kopis?: KopisPerformanceDetail} | null
    )?.kopis ?? null

    // 서버 장르 목록 조회
    const {
        genres,
        loading: genresLoading,
        error: genresError,
    } = useGenres()

    const [form, setForm] = useState<FormState>(
        kopis ? fromKopis(kopis) : INITIAL_FORM,
    )
    const [touched, setTouched] = useState<Touched>({})
    const [serverError, setServerError] = useState('')
    const [success, setSuccess] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [createdId, setCreatedId] = useState<number | null>(null)

    const errors = validate(form)

    // 서버에서 제공한 장르 코드인지 확인
    const genreValid = genres.some(
        (genre) => genre.code === form.genreCode,
    )

    if (
        !genresLoading &&
        !genresError &&
        form.genreCode &&
        !genreValid
    ) {
        errors.genreCode = '서버에 등록되지 않은 장르입니다. 다시 선택해 주세요.'
    }

    const isValid =
        Object.keys(errors).length === 0 &&
        !genresLoading &&
        !genresError &&
        genreValid

    const shownError = (name: keyof FormState) =>
        touched[name] ? errors[name] : undefined

    const inputClass = (name: keyof FormState) =>
        'h-12 w-full rounded-lg border px-4 text-sm outline-none ' +
        (shownError(name)
            ? 'border-red-600 focus:border-red-600'
            : 'border-gray-300 focus:border-indigo-700')

    const handleChange = (
        e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
    ) => {
        const {name, value} = e.target
        setForm((prev) => ({...prev, [name]: value}))
    }

    const handleBlur = (
        e: FocusEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
    ) => {
        const name = e.target.name as keyof FormState
        setTouched((prev) => ({...prev, [name]: true}))
    }

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault()

        if (!isValid || submitting) return

        setServerError('')
        setSuccess('')
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

            setSuccess(
                `공연이 등록되었습니다. (공연 번호: ${created.performanceId})`,
            )
            setCreatedId(created.performanceId)

            if (!kopis) {
                setForm(INITIAL_FORM)
                setTouched({})
            }
        } catch (err) {
            if (
                axios.isAxiosError<{code: string; message: string}>(err)
            ) {
                const status = err.response?.status

                if (status === 401) {
                    setServerError('MANAGER 로그인이 필요합니다.')
                } else if (status === 403) {
                    setServerError('공연 등록 권한이 없습니다.')
                } else {
                    setServerError(
                        err.response?.data?.message ??
                        '공연을 등록하지 못했습니다.',
                    )
                }
            } else {
                setServerError(
                    err instanceof Error
                        ? err.message
                        : '공연을 등록하지 못했습니다.',
                )
            }
        } finally {
            setSubmitting(false)
        }
    }

    const common = {
        onChange: handleChange,
        onBlur: handleBlur,
    }

    return (
        <div className="mx-auto max-w-[720px] px-6 py-10">
            <h1 className="text-2xl font-bold text-gray-900">
                {kopis ? 'KOPIS 공연 등록' : '공연 직접 등록'}
            </h1>

            <p className="mt-1 text-sm text-gray-500">
                공연 기본 정보를 입력해 등록합니다.
                회차와 가격은 등록한 뒤 판매 설정에서 작성해주세요.
            </p>

            {kopis && (
                <div className="mt-4 rounded-lg border border-indigo-200 bg-indigo-50 px-4 py-3 text-sm text-indigo-900">
                    KOPIS에서 가져온 정보입니다
                    (공연 ID: {kopis.kopisId}).
                    필수항목들을 채운 후 등록해주세요.

                    {!kopis.genreCode && (
                        <span className="mt-1 block font-semibold">
                            KOPIS 장르 “{kopis.genreName ?? '-'}”는
                            우리 장르와 연결되지 않아 비어 있습니다.
                            직접 선택해 주세요.
                        </span>
                    )}
                </div>
            )}

            <form
                onSubmit={handleSubmit}
                className="mt-6 space-y-5 rounded-2xl border border-gray-200 bg-white p-8"
            >
                <Field
                    label="공연명"
                    required
                    error={shownError('title')}
                >
                    <input
                        name="title"
                        value={form.title}
                        {...common}
                        className={inputClass('title')}
                    />
                </Field>

                <Field
                    label="장르"
                    required
                    error={shownError('genreCode')}
                >
                    <select
                        name="genreCode"
                        value={genreValid ? form.genreCode : ''}
                        {...common}
                        disabled={genresLoading || Boolean(genresError)}
                        className={
                            inputClass('genreCode') +
                            ' disabled:bg-gray-100'
                        }
                    >
                        <option value="">
                            {genresLoading
                                ? '장르 불러오는 중...'
                                : '선택해 주세요'}
                        </option>

                        {genres.map((genre) => (
                            <option
                                key={genre.code}
                                value={genre.code}
                            >
                                {genre.name}
                            </option>
                        ))}
                    </select>

                    {genresError && (
                        <p className="mt-1.5 text-xs text-red-600">
                            {genresError}
                        </p>
                    )}

                    {!genresLoading &&
                        !genresError &&
                        genres.length === 0 && (
                            <p className="mt-1.5 text-xs text-gray-500">
                                등록 가능한 장르가 없습니다.
                            </p>
                        )}

                    {!genresLoading &&
                        !genresError &&
                        form.genreCode &&
                        !genreValid && (
                            <p className="mt-1.5 text-xs text-red-600">
                                KOPIS에서 전달된 장르가 서버 목록에 없습니다.
                                장르를 다시 선택해 주세요.
                            </p>
                        )}
                </Field>

                <Field
                    label="공연장"
                    required
                    error={shownError('venueName')}
                >
                    <input
                        name="venueName"
                        value={form.venueName}
                        {...common}
                        className={inputClass('venueName')}
                    />
                </Field>

                <Field
                    label="주최자"
                    error={shownError('organizerName')}
                >
                    <input
                        name="organizerName"
                        value={form.organizerName}
                        {...common}
                        className={inputClass('organizerName')}
                    />
                </Field>

                <Field
                    label="공연 설명"
                    required
                    error={shownError('description')}
                >
                    <textarea
                        name="description"
                        rows={5}
                        value={form.description}
                        {...common}
                        className={
                            inputClass('description').replace('h-12', 'py-3')
                        }
                    />
                </Field>

                <Field
                    label="포스터 주소 (https://)"
                    error={shownError('posterUrl')}
                >
                    <input
                        name="posterUrl"
                        value={form.posterUrl}
                        {...common}
                        className={inputClass('posterUrl')}
                    />
                </Field>

                <div className="grid grid-cols-2 gap-4">
                    <Field
                        label="시작일"
                        required
                        error={shownError('startDate')}
                    >
                        <input
                            type="date"
                            name="startDate"
                            value={form.startDate}
                            {...common}
                            className={inputClass('startDate')}
                        />
                    </Field>

                    <Field
                        label="종료일"
                        required
                        error={shownError('endDate')}
                    >
                        <input
                            type="date"
                            name="endDate"
                            value={form.endDate}
                            {...common}
                            className={inputClass('endDate')}
                        />
                    </Field>
                </div>

                <Field
                    label="1인당 예매 한도 (1~10)"
                    error={shownError('bookingLimitPerMember')}
                >
                    <input
                        type="number"
                        name="bookingLimitPerMember"
                        value={form.bookingLimitPerMember}
                        {...common}
                        className={inputClass('bookingLimitPerMember')}
                    />
                </Field>

                <div className="rounded-lg bg-indigo-50 px-4 py-3 text-sm text-indigo-900">
                    등록한 공연은 비공개 상태로 저장됩니다.
                    판매 설정을 마친 뒤 공개해주세요.
                </div>

                {serverError && (
                    <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                        {serverError}
                    </div>
                )}

                {success && (
                    <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                        <p>{success}</p>

                        <div className="mt-3 flex gap-2">
                            {createdId !== null && (
                                <button
                                    type="button"
                                    onClick={() =>
                                        navigate(
                                            `/manager/performances/${createdId}/sales-configuration`,
                                        )
                                    }
                                    className="h-9 rounded-lg bg-indigo-700 px-4 text-sm font-semibold text-white"
                                >
                                    판매 설정하러 가기
                                </button>
                            )}

                            <button
                                type="button"
                                onClick={() =>
                                    navigate('/manager/performances')
                                }
                                className="h-9 rounded-lg border border-gray-300 bg-white px-4 text-sm font-semibold text-gray-700"
                            >
                                목록으로
                            </button>
                        </div>
                    </div>
                )}

                <div className="flex gap-3">
                    <button
                        type="button"
                        onClick={() =>
                            navigate('/manager/performances')
                        }
                        className="h-12 shrink-0 rounded-lg border border-gray-300 px-6 font-semibold text-gray-700 hover:bg-gray-50"
                    >
                        목록으로
                    </button>

                    <button
                        type="submit"
                        disabled={!isValid || submitting}
                        className="h-12 flex-1 rounded-lg bg-indigo-700 font-semibold text-white disabled:cursor-not-allowed disabled:bg-gray-200 disabled:text-gray-400"
                    >
                        {submitting ? '등록 중...' : '공연 등록'}
                    </button>
                </div>

                <p className="text-xs text-gray-500">
                    내용을 모두 작성하시고 등록 버튼을 눌러주세요.
                </p>
            </form>
        </div>
    )
}
