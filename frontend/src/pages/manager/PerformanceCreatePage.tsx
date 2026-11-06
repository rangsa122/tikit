import {useState} from 'react'
import type {ChangeEvent, FocusEvent, FormEvent, ReactNode} from 'react'
import axios from 'axios'
import {createPerformance} from '../../api/managerPerformanceApi.ts'

const GENRES = [
    {code: 'THEATER', name: '연극'},
    {code: 'DANCE', name: '무용'},
    {code: 'POPULAR_DANCE', name: '대중무용'},
    {code: 'CLASSICAL', name: '클래식'},
    {code: 'KOREAN_TRADITIONAL', name: '국악'},
    {code: 'POPULAR_MUSIC', name: '대중음악'},
    {code: 'COMPLEX', name: '복합'},
    {code: 'CIRCUS_MAGIC', name: '서커스/마술'},
    {code: 'MUSICAL', name: '뮤지컬'},
]

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

function validate(form: FormState): FormErrors {
    const errors: FormErrors = {}

    if (!form.title.trim()) errors.title = '공연명을 입력해 주세요.'
    else if (form.title.length > 200) errors.title = '공연명은 200자 이하로 입력해 주세요.'

    if (!form.genreCode) errors.genreCode = '장르를 선택해 주세요.'

    if (!form.venueName.trim()) errors.venueName = '공연장을 입력해 주세요.'
    else if (form.venueName.length > 200) errors.venueName = '공연장은 200자 이하로 입력해 주세요.'

    if (form.organizerName.length > 100) errors.organizerName = '주최자는 100자 이하로 입력해 주세요.'

    if (!form.description.trim()) errors.description = '공연 설명을 입력해 주세요.'
    else if (form.description.length > 10000) errors.description = '공연 설명은 10000자 이하로 입력해 주세요.'

    if (form.posterUrl && !form.posterUrl.startsWith('https://')) {
        errors.posterUrl = '포스터 주소는 https:// 로 시작해야 해요.'
    }

    if (!form.startDate) errors.startDate = '시작일을 입력해 주세요.'
    if (!form.endDate) errors.endDate = '종료일을 입력해 주세요.'
    else if (form.startDate && form.startDate > form.endDate) {
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
                {required && <span className="ml-0.5 text-red-600">*</span>}
            </label>
            {children}
            {error && <p className="mt-1.5 text-xs text-red-600">{error}</p>}
        </div>
    )
}

export default function PerformanceCreatePage() {
    const [form, setForm] = useState<FormState>(INITIAL_FORM)
    const [touched, setTouched] = useState<Touched>({})
    const [serverError, setServerError] = useState('')
    const [success, setSuccess] = useState('')
    const [submitting, setSubmitting] = useState(false)

    // 렌더링할 때마다 계산. 따로 state로 저장하지 않아도 됌.
    const errors = validate(form)
    const isValid = Object.keys(errors).length === 0

    // 한 번 건드린 입력칸에만 오류를 보여준다
    const shownError = (name: keyof FormState) => (touched[name] ? errors[name] : undefined)

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
        setSubmitting(true)
        try {
            const created = await createPerformance({
                source: 'MANUAL',
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
            setSuccess(`공연이 등록되었습니다. (공연 번호: ${created.performanceId})`)
            setForm(INITIAL_FORM)
            setTouched({})
        } catch (err) {
            if (axios.isAxiosError<{ code: string; message: string }>(err)) {
                const status = err.response?.status
                if (status === 401) setServerError('MANAGER로그인이 필요합니다.')
                else if (status === 403) setServerError('공연 등록 권한이 없습니다.')
                else setServerError(err.response?.data?.message ?? '공연을 등록하지 못했습니다.')
            } else {
                setServerError(err instanceof Error ? err.message : '공연을 등록하지 못했습니다.')
            }
        } finally {
            setSubmitting(false)
        }
    }


    const common = {onChange: handleChange, onBlur: handleBlur}

    return (
        <div className="mx-auto max-w-[720px] px-6 py-10">
            <h1 className="text-2xl font-bold text-gray-900">공연 직접 등록</h1>
            <p className="mt-1 text-sm text-gray-500">
                공연 기본 정보를 입력해 등록합니다. 회차와 가격은 등록한 뒤 판매 설정에서 작성해주세요.
            </p>

            <form onSubmit={handleSubmit}
                  className="mt-6 space-y-5 rounded-2xl border border-gray-200 bg-white p-8">
                <Field label="공연명" required error={shownError('title')}>
                    <input name="title" value={form.title} {...common} className={inputClass('title')}/>
                </Field>

                <Field label="장르" required error={shownError('genreCode')}>
                    <select name="genreCode" value={form.genreCode} {...common} className={inputClass('genreCode')}>
                        <option value="">선택해 주세요</option>
                        {GENRES.map((g) => (
                            <option key={g.code} value={g.code}>{g.name}</option>
                        ))}
                    </select>
                </Field>

                <Field label="공연장" required error={shownError('venueName')}>
                    <input name="venueName" value={form.venueName} {...common} className={inputClass('venueName')}/>
                </Field>

                <Field label="주최자" error={shownError('organizerName')}>
                    <input name="organizerName" value={form.organizerName} {...common}
                           className={inputClass('organizerName')}/>
                </Field>

                <Field label="공연 설명" required error={shownError('description')}>
                    <textarea name="description" rows={5} value={form.description} {...common}
                              className={inputClass('description').replace('h-12', 'py-3')}/>
                </Field>

                <Field label="포스터 주소 (https://)" error={shownError('posterUrl')}>
                    <input name="posterUrl" value={form.posterUrl} {...common} className={inputClass('posterUrl')}/>
                </Field>

                <div className="grid grid-cols-2 gap-4">
                    <Field label="시작일" required error={shownError('startDate')}>
                        <input type="date" name="startDate" value={form.startDate} {...common}
                               className={inputClass('startDate')}/>
                    </Field>
                    <Field label="종료일" required error={shownError('endDate')}>
                        <input type="date" name="endDate" value={form.endDate} {...common}
                               className={inputClass('endDate')}/>
                    </Field>
                </div>

                <Field label="1인당 예매 한도 (1~10)" error={shownError('bookingLimitPerMember')}>
                    <input type="number" name="bookingLimitPerMember" value={form.bookingLimitPerMember} {...common}
                           className={inputClass('bookingLimitPerMember')}/>
                </Field>

                <div className="rounded-lg bg-indigo-50 px-4 py-3 text-sm text-indigo-900">
                    등록한 공연은 비공개 상태로 저장 됩니다. 판매 설정을 마친 뒤 공개해주세요.
                </div>

                {serverError && (
                    <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                        {serverError}
                    </div>
                )}
                {success && (
                    <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                        {success}
                    </div>
                )}

                <button type="submit" disabled={!isValid || submitting}
                        className="h-12 w-full rounded-lg bg-indigo-700 font-semibold text-white
                                   disabled:cursor-not-allowed disabled:bg-gray-200 disabled:text-gray-400">
                    {submitting ? '등록 중...' : '공연 등록'}
                </button>
                <p className="text-xs text-gray-500">내용을 모두 작성하시고 등록버튼을 눌러주세요.</p>
            </form>
        </div>
    )
}