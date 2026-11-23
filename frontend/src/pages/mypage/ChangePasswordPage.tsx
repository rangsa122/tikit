import {useState} from 'react'
import type {FormEvent} from 'react'
import {Link, useNavigate} from 'react-router'
import axios from 'axios'
import MyPageSidebar from './MyPageSidebar'
import {changeMyPassword} from '../../api/memberApi'
import {useAuthStore} from '../../stores/authStore.ts'
import eyeIcon from '../../assets/auth/eye.svg'

// 비밀번호 변경 오류 메시지
function getPasswordErrorMessage(error: unknown): string {
    if (axios.isAxiosError<{code?: string}>(error)) {
        console.log('비밀번호 변경 오류 상태:', error.response?.status)
        console.log('비밀번호 변경 오류 응답:', error.response?.data)
        if (error.response?.data?.code === 'PASSWORD_MISMATCH') {
            return '현재 비밀번호가 일치하지 않습니다.'
        }

        if (error.response?.status === 403) {
            return '현재 비밀번호 또는 변경 권한을 확인해 주세요.'
        }

        if (error.response?.status === 400) {
            return '비밀번호 입력값을 확인해 주세요.'
        }

        if (error.response && error.response.status >= 500) {
            return '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
        }

        if (!error.response) {
            return '서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.'
        }
    }

    return '비밀번호를 변경하지 못했습니다. 다시 시도해 주세요.'
}

export default function ChangePasswordPage() {
    const navigate = useNavigate()

    const [currentPassword, setCurrentPassword] = useState('')
    const [newPassword, setNewPassword] = useState('')
    const [confirmPassword, setConfirmPassword] = useState('')

    const [isSaving, setIsSaving] = useState(false)
    const [error, setError] = useState('')
    const [message, setMessage] = useState('')

    const [showCurrentPassword, setShowCurrentPassword] = useState(false)
    const [showNewPassword, setShowNewPassword] = useState(false)
    const [showConfirmPassword, setShowConfirmPassword] = useState(false)

    // 비밀번호 변경 요청
    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        if (isSaving) return

        setError('')
        setMessage('')

        // 필수 입력 확인
        if (!currentPassword || !newPassword || !confirmPassword) {
            setError('모든 항목을 입력해 주세요.')
            return
        }

        // 새 비밀번호 길이 검증
        if (newPassword.length < 10 || newPassword.length > 128) {
            setError('새 비밀번호는 10자 이상 128자 이하로 입력해 주세요.')
            return
        }

        // 새 비밀번호 일치 여부 확인
        if (newPassword !== confirmPassword) {
            setError('새 비밀번호가 일치하지 않습니다.')
            return
        }

        // 기존 비밀번호 재사용 방지
        if (currentPassword === newPassword) {
            setError('현재 비밀번호와 다른 비밀번호를 입력해 주세요.')
            return
        }

        setIsSaving(true)

        try {
            // 비밀번호 변경 API 호출
            await changeMyPassword(currentPassword, newPassword)

            // 입력된 비밀번호 초기화
            setCurrentPassword('')
            setNewPassword('')
            setConfirmPassword('')

            // 변경 완료 화면 표시
            setMessage('비밀번호가 성공적으로 변경되었습니다.')
        } catch (error) {
            setError(getPasswordErrorMessage(error))
        } finally {
            setIsSaving(false)
        }
    }

    // 변경 완료 후 로그인 페이지 이동
    function handleGoToLogin() {
        // 프론트엔드 인증 상태 삭제
        useAuthStore.getState().clearTokens()

        // 로그인 페이지로 이동
        navigate('/login', {replace: true})
    }

    return (
        <div className="mx-auto w-full max-w-[1320px] px-5 py-10">
            <h1 className="border-b border-[#333] pb-5 text-[28px] font-semibold">
                <Link to="/mypage" className="cursor-pointer">
                    마이페이지
                </Link>
            </h1>

            <div className="mt-8 flex flex-col gap-10 md:flex-row">
                <MyPageSidebar />

                <section className="min-w-0 flex-1">
                    <h2 className="mb-6 text-[22px] font-semibold">
                        비밀번호 변경
                    </h2>

                    {message ? (
                        // 비밀번호 변경 완료 화면
                        <div
                            role="status"
                            className="rounded-md bg-[#f7f7f7] px-8 py-14 text-center"
                        >
                            <div className="mb-5 text-[42px] text-green-600">
                                ✓
                            </div>

                            <h3 className="mb-4 text-[22px] font-semibold">
                                비밀번호 변경 완료
                            </h3>

                            <p className="text-[15px] text-[#555]">
                                {message}
                            </p>

                            <p className="mt-2 text-[14px] text-[#777]">
                                새로운 비밀번호로 다시 로그인해 주세요.
                            </p>

                            <button
                                type="button"
                                onClick={handleGoToLogin}
                                className="mt-8 rounded bg-[#333] px-10 py-3 text-[14px] text-white"
                            >
                                로그인하러 가기
                            </button>
                        </div>
                    ) : (
                        <>
                            <p className="mb-6 text-[14px] text-[#777]">
                                계정 보안을 위해 현재 비밀번호를 확인하고
                                새로운 비밀번호로 변경해 주세요.
                            </p>

                            <form onSubmit={handleSubmit}>
                                <div className="rounded-md bg-[#f7f7f7] px-8 py-7">

                                    {/* 현재 비밀번호 */}
                                    <div className="flex flex-col gap-3 py-5 sm:flex-row sm:items-center">
                                        <label
                                            htmlFor="current-password"
                                            className="w-28 shrink-0 text-[15px] font-medium"
                                        >
                                            현재 비밀번호
                                        </label>

                                        <div className="flex min-w-0 flex-1 items-center rounded border border-[#bbb] bg-white pr-1 pl-3">
                                            <input
                                                id="current-password"
                                                type={showCurrentPassword ? 'text' : 'password'}
                                                autoComplete="current-password"
                                                value={currentPassword}
                                                onChange={event => setCurrentPassword(event.target.value)}
                                                placeholder="현재 비밀번호 입력"
                                                disabled={isSaving}
                                                className="min-w-0 flex-1 bg-transparent py-2 text-[15px] focus:outline-none!"
                                            />

                                            <button
                                                type="button"
                                                onClick={() => setShowCurrentPassword(prev => !prev)}
                                                aria-label={showCurrentPassword ? '현재 비밀번호 숨기기' : '현재 비밀번호 보기'}
                                                aria-pressed={showCurrentPassword}
                                                className="flex size-9 shrink-0 items-center justify-center rounded"
                                            >
                                                <img src={eyeIcon} alt="" width={20} height={20}/>
                                            </button>
                                        </div>

                                    </div>

                                    {/* 새 비밀번호 */}
                                    <div className="flex flex-col gap-3 py-5 sm:flex-row sm:items-center">
                                        <label
                                            htmlFor="new-password"
                                            className="w-28 shrink-0 text-[15px] font-medium"
                                        >
                                            새 비밀번호
                                        </label>

                                        <div className="min-w-0 flex-1">
                                            <div className="flex w-full items-center rounded border border-[#bbb] bg-white pr-1 pl-3">
                                                <input
                                                    id="new-password"
                                                    type={showNewPassword ? 'text' : 'password'}
                                                    autoComplete="new-password"
                                                    value={newPassword}
                                                    onChange={event => setNewPassword(event.target.value)}
                                                    placeholder="새 비밀번호 입력"
                                                    minLength={10}
                                                    maxLength={128}
                                                    disabled={isSaving}
                                                    className="min-w-0 flex-1 bg-transparent py-2 text-[15px] focus:outline-none!"
                                                />

                                                <button
                                                    type="button"
                                                    onClick={() => setShowNewPassword(prev => !prev)}
                                                    aria-label={showNewPassword ? '새 비밀번호 숨기기' : '새 비밀번호 보기'}
                                                    aria-pressed={showNewPassword}
                                                    className="flex size-9 shrink-0 items-center justify-center rounded"
                                                >
                                                    <img src={eyeIcon} alt="" width={20} height={20}/>
                                                </button>
                                            </div>


                                            <p className="mt-2 text-[13px] text-[#777]">
                                                10자 이상 128자 이하로 입력해 주세요.
                                            </p>
                                        </div>
                                    </div>

                                    {/* 새 비밀번호 확인 */}
                                    <div className="flex flex-col gap-3 py-5 sm:flex-row sm:items-center">
                                        <label
                                            htmlFor="confirm-password"
                                            className="w-28 shrink-0 text-[15px] font-medium"
                                        >
                                            새 비밀번호 확인
                                        </label>

                                        <div className="flex min-w-0 flex-1 items-center rounded border border-[#bbb] bg-white pr-1 pl-3">
                                            <input
                                                id="confirm-password"
                                                type={showConfirmPassword ? 'text' : 'password'}
                                                autoComplete="new-password"
                                                value={confirmPassword}
                                                onChange={event => setConfirmPassword(event.target.value)}
                                                placeholder="새 비밀번호 다시 입력"
                                                disabled={isSaving}
                                                className="min-w-0 flex-1 bg-transparent py-2 text-[15px] focus:outline-none!"
                                            />

                                            <button
                                                type="button"
                                                onClick={() => setShowConfirmPassword(prev => !prev)}
                                                aria-label={showConfirmPassword ? '새 비밀번호 확인 숨기기' : '새 비밀번호 확인 보기'}
                                                aria-pressed={showConfirmPassword}
                                                className="flex size-9 shrink-0 items-center justify-center rounded"
                                            >
                                                <img src={eyeIcon} alt="" width={20} height={20}/>
                                            </button>
                                        </div>

                                    </div>
                                </div>

                                {/* 오류 메시지 */}
                                {error && (
                                    <p
                                        role="alert"
                                        className="mt-4 text-[14px] text-red-600"
                                    >
                                        {error}
                                    </p>
                                )}

                                {/* 하단 버튼 */}
                                <div className="mt-7 flex justify-end gap-3">
                                    <button
                                        type="button"
                                        onClick={() => navigate('/mypage/profile')}
                                        disabled={isSaving}
                                        className="rounded border border-[#ccc] px-8 py-3 text-[14px] disabled:opacity-50"
                                    >
                                        취소
                                    </button>

                                    <button
                                        type="submit"
                                        disabled={isSaving}
                                        className="rounded bg-[#333] px-8 py-3 text-[14px] text-white disabled:opacity-50"
                                    >
                                        {isSaving ? '변경 중...' : '비밀번호 변경'}
                                    </button>
                                </div>
                            </form>
                        </>
                    )}
                </section>
            </div>
        </div>
    )
}
