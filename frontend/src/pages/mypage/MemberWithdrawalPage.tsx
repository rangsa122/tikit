import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { getWithdrawalEligibility, withdrawMyAccount } from '../../api/memberApi'
import MyPageSidebar from './MyPageSidebar'
import axios from 'axios'
import { useAuthStore } from '../../stores/authStore'

// 회원 탈퇴 화면
export default function MemberWithdrawalPage() {
    const [password, setPassword] = useState('')
    const [isConfirmed, setIsConfirmed] = useState(false)
    const navigate = useNavigate()
    const [isSubmitting, setIsSubmitting] = useState(false)
    const [submitError, setSubmitError] = useState('')

    // 탈퇴 가능 여부 조회 상태
    const [eligible, setEligible] = useState<boolean | null>(null)
    const [isLoading, setIsLoading] = useState(true)
    const [error, setError] = useState('')

    // 페이지 진입 시 탈퇴 가능 여부 조회
    useEffect(() => {
        let active = true

        async function loadEligibility() {
            try {
                const data = await getWithdrawalEligibility()

                if (active) {
                    setEligible(data.eligible)
                }
            } catch {
                if (active) {
                    setError('탈퇴 가능 여부를 확인하지 못했습니다.')
                }
            } finally {
                if (active) {
                    setIsLoading(false)
                }
            }
        }

        void loadEligibility()

        return () => {
            active = false
        }
    }, [])

    // 비밀번호 확인 후 회원 탈퇴
    async function handleWithdraw() {
        if (eligible !== true || !password || !isConfirmed || isSubmitting) {
            return
        }

        // 최종 탈퇴 확인
        const confirmed = window.confirm(
            '정말 회원 탈퇴를 진행하시겠습니까?'
        )

        if (!confirmed) return

        setIsSubmitting(true)
        setSubmitError('')

        try {
            await withdrawMyAccount(password)

            // 탈퇴 성공 후 Access Token 삭제
            useAuthStore.getState().clearTokens()

            // 로그인 화면으로 이동
            navigate('/login', { replace: true })
        } catch (error) {
            if (axios.isAxiosError(error)) {
                const status = error.response?.status

                if (status === 403) {
                    setSubmitError('비밀번호가 올바르지 않거나 접근 권한이 없습니다.')
                } else if (status === 409) {
                    setSubmitError('유효한 예매 또는 처리 중인 결제·환불 내역이 있어 탈퇴할 수 없습니다.')
                } else if (status === 401) {
                    setSubmitError('로그인이 만료되었습니다. 다시 로그인해 주세요.')
                } else {
                    setSubmitError('회원 탈퇴 요청을 처리하지 못했습니다.')
                }
            } else {
                setSubmitError('회원 탈퇴 중 오류가 발생했습니다.')
            }
        } finally {
            setIsSubmitting(false)
        }
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
                        회원 탈퇴
                    </h2>

                    <div className="rounded-md bg-[#f7f7f7] px-8 py-7">
                        <h3 className="text-[17px] font-semibold">
                            회원 탈퇴 안내
                        </h3>

                        <p className="mt-4 text-[14px] leading-7 text-[#555]">
                            회원 탈퇴 시 계정 이용이 제한됩니다.
                            유효한 좌석 선점, 관람 전 확정 예매,
                            처리 중인 결제 또는 환불 내역이 있다면
                            탈퇴할 수 없습니다.
                        </p>

                        <div className="mt-6 border-t border-[#ddd] pt-6">
                            <p className="text-[15px] font-medium">탈퇴 가능 여부</p>
                            {isLoading ? (
                                <p className="mt-2 text-[14px] text-[#777]">
                                    탈퇴 가능 여부를 확인하는 중...
                                </p>
                            ) : error ? (
                                <p role="alert" className="mt-2 text-[14px] text-red-600">
                                    {error}
                                </p>
                            ) : eligible ? (
                                <p className="mt-2 text-[14px] text-green-700">
                                    현재 회원 탈퇴가 가능합니다.
                                </p>
                            ) : (
                                <p className="mt-2 text-[14px] text-red-600">
                                    유효한 예매 또는 처리 중인 결제·환불 내역이 있어
                                    회원 탈퇴가 제한됩니다.
                                </p>
                            )}
                        </div>

                        <div className="mt-6 border-t border-[#ddd] pt-6">
                            <label htmlFor="withdrawal-password" className="block text-[15px] font-medium">
                                비밀번호 확인
                            </label>

                            <input
                                id="withdrawal-password"
                                type="password"
                                autoComplete="current-password"
                                value={password}
                                onChange={(event) =>
                                    setPassword(event.target.value)
                                }
                                placeholder="현재 비밀번호를 입력해 주세요."
                                className="mt-3 w-full rounded border border-[#bbb] bg-white px-3 py-3 text-[14px]"
                            />
                        </div>

                        <label className="mt-6 flex items-start gap-3 text-[14px] text-[#555]">
                            <input
                                type="checkbox"
                                checked={isConfirmed}
                                onChange={(event) =>
                                    setIsConfirmed(event.target.checked)
                                }
                                className="mt-1"
                            />
                            <span>
                                회원 탈퇴 안내 사항을 확인했으며 탈퇴 진행에 동의합니다.
                            </span>
                        </label>
                    </div>

                    {submitError && (<p role="alert" className="mt-4 text-[14px] text-red-600">{submitError}</p>)}

                    <div className="mt-7 flex justify-end gap-3">
                        <Link to="/mypage" className="rounded border border-[#ccc] px-8 py-3 text-[14px]">
                            취소
                        </Link>

                        <button
                            type="button"
                            onClick={() => void handleWithdraw()}
                            disabled={eligible !== true || !password.trim() || !isConfirmed || isSubmitting}
                            className="rounded bg-[#333] px-8 py-3 text-[14px] text-white disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {isSubmitting ? '탈퇴 처리 중...' : '회원 탈퇴'}
                        </button>
                    </div>
                </section>
            </div>
        </div>
    )
}
