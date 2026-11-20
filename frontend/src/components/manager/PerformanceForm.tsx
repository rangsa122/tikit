import {useState} from 'react'
import type {ChangeEvent, FocusEvent, FormEvent, ReactNode} from 'react'
import {useGenres} from '../../hooks/useGenres.ts'
import {uploadImage} from '../../api/managerPerformanceApi.ts'
import {toMessage} from '../../api/apiError.ts'

export interface FormState {
    title: string
    genreCode: string
    venueName: string
    organizerName: string
    description: string
    posterUrl: string
    bannerUrl: string
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
    bannerUrl: '',
    startDate: '',
    endDate: '',
    bookingLimitPerMember: '4',
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

    // 이미지 주소는 두 종류다.
    // KOPIS에서 받아온 외부 주소(https://)와 우리 서버에 업로드한 주소(/uploads/)
    const imageUrlValid = (url: string) =>
        url.startsWith('https://') || url.startsWith('/uploads/')

    if (form.posterUrl && !imageUrlValid(form.posterUrl)) {
        errors.posterUrl = '포스터 이미지를 다시 업로드해 주세요.'
    }

    if (form.bannerUrl && !imageUrlValid(form.bannerUrl)) {
        errors.bannerUrl = '배너 이미지를 다시 업로드해 주세요.'
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

function Field({
                   label,
                   required,
                   error,
                   children,
               }: {
    label: string
    required?: boolean
    error?: string
    children: ReactNode
}) {
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

type ImageField = 'posterUrl' | 'bannerUrl'

// 포스터와 배너가 같은 모양의 입력을 쓴다. 미리보기 비율만 다르다.
function ImageUploadField({
                              label,
                              hint,
                              previewClass,
                              value,
                              uploading,
                              disabled,
                              error,
                              onSelect,
                              onRemove,
                          }: {
    label: string
    hint: string
    previewClass: string
    value: string
    uploading: boolean
    disabled: boolean
    error?: string
    onSelect: (e: ChangeEvent<HTMLInputElement>) => void
    onRemove: () => void
}) {
    return (
        <Field label={label} error={error}>
            <div className="flex items-start gap-4">
                {value ? (
                    <img
                        src={value}
                        alt={`${label} 미리보기`}
                        className={`${previewClass} shrink-0 rounded-lg border border-gray-200 object-cover`}
                    />
                ) : (
                    <div
                        className={`${previewClass} flex shrink-0 items-center justify-center rounded-lg border border-dashed border-gray-300 text-xs text-gray-400`}
                    >
                        미리보기
                    </div>
                )}

                <div className="flex min-w-0 flex-col gap-2">
                    <input
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        disabled={uploading || disabled}
                        onChange={onSelect}
                        className="text-sm text-gray-700 file:mr-3 file:rounded-lg file:border file:border-gray-300 file:bg-white file:px-4 file:py-2 file:text-sm file:font-semibold file:text-gray-700 disabled:opacity-50"
                    />

                    <p className="text-xs text-gray-500">
                        {hint}
                    </p>

                    {uploading && (
                        <p className="text-xs text-gray-500">
                            업로드 중...
                        </p>
                    )}

                    {value && !uploading && (
                        <button
                            type="button"
                            onClick={onRemove}
                            className="self-start text-xs font-semibold text-gray-500 underline"
                        >
                            이미지 삭제
                        </button>
                    )}
                </div>
            </div>
        </Field>
    )
}

// 등록 화면과 수정 화면이 함께 사용하는 입력 폼.
// 입력값과 유효성 검증을 담당하고, 서버 호출은 부모 컴포넌트에서 처리한다.
export default function PerformanceForm({
                                            initial,
                                            submitLabel,
                                            submitting,
                                            onSubmit,
                                            onCancel,
                                            children,
                                        }: {
    initial: FormState
    submitLabel: string
    submitting: boolean
    onSubmit: (form: FormState) => void
    onCancel: () => void
    children?: ReactNode
}) {
    const [form, setForm] = useState<FormState>(initial)
    const [touched, setTouched] = useState<Touched>({})
    const [uploading, setUploading] = useState({posterUrl: false, bannerUrl: false})
    const [uploadError, setUploadError] = useState({posterUrl: '', bannerUrl: ''})

    // 서버에서 장르 목록 조회
    const {
        genres,
        loading: genresLoading,
        error: genresError,
    } = useGenres()

    // 기본 입력값 검증
    const errors = validate(form)

    // 서버에 등록된 장르 코드인지 확인
    const genreValid = genres.some(
        (genre) => genre.code === form.genreCode,
    )

    if (
        !genresLoading &&
        !genresError &&
        form.genreCode &&
        !genreValid
    ) {
        errors.genreCode =
            '서버에 등록되지 않은 장르입니다. 다시 선택해 주세요.'
    }

    // 장르 API가 정상적으로 로딩되고 유효한 장르를 선택해야 제출 가능
    // 포스터 업로드가 끝나기 전에는 제출을 막는다
    const isValid =
        Object.keys(errors).length === 0 &&
        !genresLoading &&
        !genresError &&
        genreValid &&
        !uploading.posterUrl &&
        !uploading.bannerUrl

    // 사용자가 한 번 건드린 입력칸에만 오류 표시
    const shownError = (name: keyof FormState) =>
        touched[name] ? errors[name] : undefined

    const inputClass = (name: keyof FormState) =>
        'h-12 w-full rounded-lg border px-4 text-sm outline-none ' +
        (shownError(name)
            ? 'border-red-600 focus:border-red-600'
            : 'border-gray-300 focus:border-indigo-700')

    const handleChange = (
        e: ChangeEvent<
            HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
        >,
    ) => {
        const {name, value} = e.target

        setForm((prev) => ({
            ...prev,
            [name]: value,
        }))
    }

    const handleBlur = (
        e: FocusEvent<
            HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
        >,
    ) => {
        const name = e.target.name as keyof FormState

        setTouched((prev) => ({
            ...prev,
            [name]: true,
        }))
    }

    // 파일을 고르는 즉시 업로드하고, 돌려받은 주소를 폼에 담는다.
    // 포스터와 배너가 같은 업로드 API를 쓰므로 한 함수로 처리한다.
    const handleImageChange = async (
        field: ImageField,
        e: ChangeEvent<HTMLInputElement>,
    ) => {
        const input = e.target
        const file = input.files?.[0]

        if (!file) return

        setUploadError((prev) => ({...prev, [field]: ''}))
        setUploading((prev) => ({...prev, [field]: true}))

        try {
            const imageUrl = await uploadImage(file)

            setForm((prev) => ({...prev, [field]: imageUrl}))
            setTouched((prev) => ({...prev, [field]: true}))
        } catch (error: unknown) {
            setUploadError((prev) => ({
                ...prev,
                [field]: toMessage(error, '이미지를 업로드하지 못했습니다'),
            }))
        } finally {
            setUploading((prev) => ({...prev, [field]: false}))
            // 같은 파일을 다시 고를 수 있도록 비워 둔다
            input.value = ''
        }
    }

    const handleImageRemove = (field: ImageField) => {
        setUploadError((prev) => ({...prev, [field]: ''}))
        setForm((prev) => ({...prev, [field]: ''}))
    }

    const handleSubmit = (e: FormEvent) => {
        e.preventDefault()

        if (!isValid || submitting) return

        onSubmit(form)
    }

    const common = {
        onChange: handleChange,
        onBlur: handleBlur,
    }

    return (
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
                            현재 선택된 장르가 서버 목록에 없습니다.
                            다시 선택해 주세요.
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

            <ImageUploadField
                label="포스터 이미지"
                hint="JPG, PNG, WEBP · 5MB 이하 · 세로 3:4 권장 (예: 600×800)"
                previewClass="h-[160px] w-[120px]"
                value={form.posterUrl}
                uploading={uploading.posterUrl}
                disabled={submitting}
                error={uploadError.posterUrl || shownError('posterUrl')}
                onSelect={(e) => void handleImageChange('posterUrl', e)}
                onRemove={() => handleImageRemove('posterUrl')}
            />

            <ImageUploadField
                label="배너 이미지 (선택)"
                hint="등록하면 홈 배너에 노출됩니다 · 가로 1320×415 권장 · 5MB 이하"
                previewClass="h-[62px] w-[198px]"
                value={form.bannerUrl}
                uploading={uploading.bannerUrl}
                disabled={submitting}
                error={uploadError.bannerUrl || shownError('bannerUrl')}
                onSelect={(e) => void handleImageChange('bannerUrl', e)}
                onRemove={() => handleImageRemove('bannerUrl')}
            />

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

            {children}

            <div className="flex gap-3">
                <button
                    type="button"
                    onClick={onCancel}
                    className="h-12 shrink-0 rounded-lg border border-gray-300 px-6 font-semibold text-gray-700 hover:bg-gray-50"
                >
                    목록으로
                </button>

                <button
                    type="submit"
                    disabled={!isValid || submitting}
                    className="h-12 flex-1 rounded-lg bg-indigo-700 font-semibold text-white disabled:cursor-not-allowed disabled:bg-gray-200 disabled:text-gray-400"
                >
                    {submitting ? '처리 중...' : submitLabel}
                </button>
            </div>

            <p className="text-xs text-gray-500">
                내용을 모두 작성하시고 버튼을 눌러주세요.
            </p>
        </form>
    )
}