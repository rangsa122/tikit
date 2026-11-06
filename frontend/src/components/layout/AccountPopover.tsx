import {useEffect, useId, useRef, useState} from 'react'
import {Link, useNavigate} from 'react-router'
import {isAxiosError} from 'axios'
import {logout} from '../../api/authApi'
import {useAuthStore} from '../../stores/authStore'
import myPageIcon from '../../assets/navigation/my-page.svg'
import walletIcon from '../../assets/account/wallet.svg'
import profileIcon from '../../assets/account/profile.svg'

export default function AccountPopover() {
  const hasSession = useAuthStore(state => Boolean(state.tokens?.accessToken))
  const [isOpen, setIsOpen] = useState(false)
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const [message, setMessage] = useState('')
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLElement>(null)
  const panelId = useId()
  const navigate = useNavigate()

  useEffect(() => {
    if (!isOpen) return

    panelRef.current?.querySelector<HTMLElement>('a, button:not(:disabled)')?.focus()

    function handlePointerDown(event: PointerEvent) {
      if (event.target instanceof Node && !containerRef.current?.contains(event.target)) {
        setIsOpen(false)
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsOpen(false)
        triggerRef.current?.focus()
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  async function handleLogout() {
    if (isLoggingOut) return
    setIsLoggingOut(true)
    setMessage('')

    try {
      await logout()
      setIsOpen(false)
      triggerRef.current?.focus()
    } catch (error) {
      if (isAxiosError<{message: string}>(error)) {
        setMessage(error.response?.data?.message ?? '서버에 연결할 수 없습니다.')
      } else {
        setMessage('로그아웃 처리 중 오류가 발생했습니다.')
      }
    } finally {
      setIsLoggingOut(false)
    }
  }

  function handleAccountPage() {
    setIsOpen(false)

    if (hasSession) {
      navigate('/mypage')
    } else {
      navigate('/login')
    }
  }

  let logoutLabel = '로그아웃'
  if (isLoggingOut) logoutLabel = '로그아웃 중...'

  return (
    <div
      ref={containerRef}
      className="relative flex items-center"
      onBlur={event => {
        if (!event.currentTarget.contains(event.relatedTarget)) setIsOpen(false)
      }}
    >
      <button
        ref={triggerRef}
        type="button"
        aria-label="회원 메뉴"
        aria-haspopup="dialog"
        aria-expanded={isOpen}
        aria-controls={panelId}
        className="flex items-center justify-center"
        onClick={() => {
          setMessage('')
          setIsOpen(open => !open)
        }}
      >
        <img src={myPageIcon} alt="" />
      </button>
      {isOpen && (
        <section
          ref={panelRef}
          id={panelId}
          role="dialog"
          aria-label="계정"
          className="absolute top-full right-0 z-50 mt-3 w-[360px] max-w-[calc(100vw-80px)] bg-white px-6 pt-6 pb-8 text-[#111] shadow-[0_4px_20px_rgba(0,0,0,0.12)]"
        >
          <div className="px-3">
            <h2 className="text-xl leading-[normal] font-normal">계정</h2>
            <div className="flex flex-col gap-3 py-[18px]">
              {hasSession && (
                <button
                  type="button"
                  disabled={isLoggingOut}
                  onClick={handleLogout}
                  className="flex h-[42px] w-full items-center justify-center bg-[#999] text-lg leading-[normal] font-medium text-white disabled:opacity-50"
                >
                  {logoutLabel}
                </button>
              )}
              {!hasSession && (
                <>
                  <Link to="/login" onClick={() => setIsOpen(false)} className="flex h-[42px] items-center justify-center bg-[#999] text-lg leading-[normal] font-medium text-white">
                    로그인
                  </Link>
                  <Link to="/signup" onClick={() => setIsOpen(false)} className="flex h-[42px] items-center justify-center bg-[#111] text-lg leading-[normal] font-medium text-white">
                    회원가입
                  </Link>
                </>
              )}
            </div>
            <div className="flex items-center justify-between px-[46px] text-base leading-[normal] font-medium max-sm:px-4">
              <button type="button" onClick={handleAccountPage} disabled={hasSession} title="주문" className="flex items-center gap-1 whitespace-nowrap">
                <img src={walletIcon} alt="" className="max-w-none shrink-0" />
                주문
              </button>
              <button type="button" onClick={handleAccountPage} title="프로필" className="flex items-center gap-1 whitespace-nowrap">
                <img src={profileIcon} alt="" className="max-w-none shrink-0" />
                프로필
              </button>
            </div>
            {message && <p role="alert" className="mt-3 text-sm text-red-600">{message}</p>}
          </div>
        </section>
      )}
    </div>
  )
}
