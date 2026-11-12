import {useState} from 'react'
import type {ChangeEvent, FocusEvent, FormEvent, ReactNode} from 'react'
import {GENRES} from '../../constants/genres.ts'

export interface FormState {
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

export const INITIAL_FORM: FormState = {
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
        errors.posterUrl = '포스터 주소는 https:// 로 시작해야 합니다.'
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

function Field({label, required, error, children}: {
    label: string
    required?: boolean
    error?: string
    children: ReactNode
}) {
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

// 등록 화면과 수정 화면이 함께 쓰는 입력 폼.
// 입력값과 검증만 담당하고, 서버 호출은 쓰는 쪽에서 한다.
export default function PerformanceForm({
                                            initial, submitLabel, submitting, onSubmit, onCancel, children,
                                        }: {
    initial: FormState
    submitLabel: string
    submitting: boolean
    onSubmit: (form: FormState) => void
    onCancel: () => void
    children?: ReactNode     // 버튼 위에 끼워 넣을 안내·에러·성공 박스
}) {
    const [form, setForm] = useState<FormState>(initial)
    const [touched, setTouched] = useState<Touched>({})

    // 렌더링할 때마다 계산. 따로 state로 저장하지 않아도 된다
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

    const handleSubmit = (e: FormEvent) => {
        e.preventDefault()
        if (!isValid || submitting) return
        onSubmit(form)
    }

    const common = {onChange: handleChange, onBlur: handleBlur}

    return (
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

            {children}

            <div className="flex gap-3">
                <button type="button" onClick={onCancel}
                        className="h-12 shrink-0 rounded-lg border border-gray-300 px-6 font-semibold text-gray-700 hover:bg-gray-50">
                    목록으로
                </button>
                <button type="submit" disabled={!isValid || submitting}
                        className="h-12 flex-1 rounded-lg bg-indigo-700 font-semibold text-white
                                   disabled:cursor-not-allowed disabled:bg-gray-200 disabled:text-gray-400">
                    {submitting ? '처리 중...' : submitLabel}
                </button>
            </div>
            <p className="text-xs text-gray-500">내용을 모두 작성하시고 버튼을 눌러주세요.</p>
        </form>
    )
}