import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
    getMyProfile,
    updateMyProfile,
    type MemberProfile,
} from '../../api/memberApi'
import MyPageSidebar from './MyPageSidebar'
import axios from 'axios'


// 가입일 표시 형식 : YYYY-MM-DD
function formatDate(value: string) {
    return new Date(value).toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    }).replace(/\. /g, '-').replace('.', '')
}

// 안내 메시지 반환 - API 오류
function getProfileErrorMessage(error: unknown): string {
    if (axios.isAxiosError(error)) {
        if (error.response?.status === 401) {
            return '로그인 시간이 만료되었거나 인증이 유효하지 않습니다. 다시 로그인해 주세요.'
        }

        if (error.response && error.response.status >= 500) {
            return '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
        }

        if (!error.response) {
            return '서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.'
        }
    }

    return '요청을 처리하지 못했습니다. 다시 시도해 주세요.'
}

export default function MemberProfilePage() {
    const navigate = useNavigate()

    const [profile, setProfile] = useState<MemberProfile | null>(null)
    const [name, setName] = useState('')
    const [isEditing, setIsEditing] = useState(false)
    const [isLoading, setIsLoading] = useState(true)
    const [isSaving, setIsSaving] = useState(false)
    const [message, setMessage] = useState('')
    const [error, setError] = useState('')
    const [isAuthError, setIsAuthError] = useState(false)

    useEffect(() => {
        let active = true

        async function loadProfile() {
            try {
                const data = await getMyProfile()

                if (!active) return

                setProfile(data)
                setName(data.name)
            } catch (error) {
                if (active) {
                    setError(getProfileErrorMessage(error))
                    setIsAuthError(
                        axios.isAxiosError(error) && error.response?.status === 401
                    )
                }
            } finally {
                if (active) setIsLoading(false)
            }
        }

        void loadProfile()

        return () => {
            active = false
        }
    }, [])

    // 이름 변경 시작
    function handleEdit() {
        setIsEditing(true)
        setMessage('')
        setError('')
    }

    // 변경사항 저장
    async function handleSave() {
        if (!profile) return

        if (!isEditing) {
            navigate('/mypage')
            return
        }

        const trimmedName = name.trim()

        if (!trimmedName) {
            setError('이름을 입력해 주세요.')
            return
        }

        if (trimmedName.length > 50) {
            setError('이름은 50자 이하로 입력해 주세요.')
            return
        }

        setIsSaving(true)
        setError('')
        setMessage('')

        try {
            const updated = await updateMyProfile(trimmedName)
            setProfile(updated)
            setName(updated.name)
            setIsEditing(false)
            setMessage('회원정보가 수정되었습니다.')
        } catch (error) {
            // 회원정보 수정을 실패한 경우
            setError(getProfileErrorMessage(error))
        } finally {
            setIsSaving(false)
        }
    }

    // 수정 취소 또는 마이페이지 복귀
    function handleCancel() {
        if (isEditing && profile) {
            setName(profile.name)
            setIsEditing(false)
            setError('')
            setMessage('')
        } else {
            navigate('/mypage')
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
                    <h2 className="mb-6 text-[22px] font-semibold">내 정보 조회</h2>

                    {isLoading ? (
                        <p className="text-[14px] text-[#777]">회원정보를 불러오는 중...</p>
                    ) : profile ? (
                        <>
                            <div className="rounded-md bg-[#f7f7f7] px-8 py-7">
                                <div className="flex items-center border-b border-[#ddd] py-5">
                                    <label htmlFor="member-name" className="w-28 shrink-0 text-[15px] font-medium">
                                        이름
                                    </label>

                                    {isEditing ? (
                                        <input
                                            id="member-name"
                                            value={name}
                                            maxLength={50}
                                            onChange={(event) => setName(event.target.value)}
                                            className="min-w-0 flex-1 rounded border border-[#bbb] bg-white px-3 py-2 text-[15px]"
                                        />
                                    ) : (
                                        <span id="member-name" className="flex-1 text-[15px]">
                                            {profile.name}
                                        </span>
                                    )}

                                    {!isEditing && (
                                        <button
                                            type="button"
                                            onClick={handleEdit}
                                            className="ml-4 text-[14px] font-medium underline"
                                        >
                                            변경
                                        </button>
                                    )}
                                </div>

                                <div className="flex items-center border-b border-[#ddd] py-5">
                                    <span className="w-28 shrink-0 text-[15px] font-medium">
                                        이메일
                                    </span>
                                    <span className="break-all text-[15px]">{profile.email}</span>
                                </div>


                                <div className="flex items-center py-5">
                                    <span className="w-28 shrink-0 text-[15px] font-medium">
                                        가입일
                                    </span>
                                    <span className="text-[15px]">
                                        {formatDate(profile.createdAt)}
                                    </span>
                                </div>
                            </div>

                            {/* 처리 결과 메시지 */}
                            {message && (
                                <p role="status" className="mt-4 text-[14px] text-green-700">
                                    {message}
                                </p>
                            )}
                            {error && (
                                <p role="alert" className="mt-4 text-[14px] text-red-600">
                                    {error}
                                </p>
                            )}

                            <div className="mt-7 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={handleCancel}
                                    disabled={isSaving}
                                    className="rounded border border-[#ccc] px-8 py-3 text-[14px] disabled:opacity-50"
                                >
                                    취소
                                </button>
                                <button
                                    type="button"
                                    onClick={() => void handleSave()}
                                    disabled={isSaving}
                                    className="rounded bg-[#333] px-8 py-3 text-[14px] text-white disabled:opacity-50"
                                >
                                    {isSaving ? '저장 중...' : '저장'}
                                </button>
                            </div>
                        </>
                    ) : (
                        <div>
                            <p role="alert" className="text-[14px] text-red-600">
                                {error || '회원정보를 표시할 수 없습니다.'}
                            </p>

                            <div className="mt-5 flex gap-4">
                                {isAuthError && (
                                    <Link
                                        to="/login"
                                        className="rounded bg-[#333] px-5 py-3 text-[14px] text-white"
                                    >
                                        다시 로그인
                                    </Link>
                                )}

                                <Link
                                    to="/mypage"
                                    className="rounded border border-[#ccc] px-5 py-3 text-[14px]"
                                >
                                    마이페이지로 돌아가기
                                </Link>
                            </div>
                        </div>
                    )}
                </section>
            </div>
        </div>
    )
}
