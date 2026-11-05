import {useCallback, useEffect, useState} from 'react'
import type {ReactNode} from 'react'
import {useLocation, useNavigate, useParams} from 'react-router'
import {subscribeOpening} from '../../api/queueApi'
import type {QueueApiError, QueueEntry} from '../../api/queueApi'
import timerIcon from '../../assets/queue/timer.svg'
import {usePaymentCountdown} from '../../hooks/usePaymentCountdown'
import {useQueueEntry, useSessionOpening} from '../../hooks/useQueueEntry'
import type {SessionOpening} from '../../hooks/useQueueEntry'
import {formatDateTime} from '../../utils/bookingFormat'

// 백엔드 입장 권한 유지 시간(tikit.queue.admission-ttl)과 맞춤
const ADMISSION_MINUTES = 5
// 입장 순서가 되면 이 초만큼 보여 준 뒤 좌석 선택으로 이동
const ENTER_DELAY_SECONDS = 3

/**
 * 공연 상세에서 넘겨주는 값 (없으면 공연 정보 칸을 숨김)
 */
export interface QueuePageState {
    performanceId?: string
    performanceTitle?: string
    // ISO 시각 (2026-11-07T14:00:00+09:00)
    sessionStartsAt?: string
    venue?: string
    posterUrl?: string
}

/**
 * 예매 대기열 화면 (/sessions/:sessionId/queue)
 *
 * 피그마 "예매 오픈 카운트다운", "대기열 대기 중", "입장 직전 (좌석 선택으로 이동)"
 * 오픈 전이면 카운트다운 → 오픈 후 예매하기를 누르면 대기열 → 순서가 되면 좌석 선택으로 자동 이동
 */
export default function QueuePage() {
    const {sessionId} = useParams()
    const pageState = (useLocation().state ?? {}) as QueuePageState
    const opening = useSessionOpening(pageState.performanceId, sessionId)
    const [joinRequested, setJoinRequested] = useState(false)

    // 공연 ID가 없거나 오픈 시각을 모르면 바로 대기열 (판매 전이면 서버가 막음)
    const openingKnown = opening.isSuccess && opening.data !== null
    const waitingForOpening = opening.isLoading
    const showCountdown = openingKnown && !joinRequested && !isOpenAtLoad(opening.data!)
    const queueEnabled = !waitingForOpening && !showCountdown

    return (
        <div className="flex min-h-svh flex-col bg-[#f6f7f9]">
            <HeaderMinimal />
            <main className="flex flex-1 items-center justify-center px-4 py-10">
                <section className="flex w-full max-w-[520px] flex-col items-center gap-7 rounded-[20px] border border-[#e4e6eb] bg-white p-6 sm:p-10">
                    {showCountdown
                        ? <CountdownView opening={opening.data!} pageState={pageState} onJoin={() => setJoinRequested(true)} />
                        : <QueueView sessionId={sessionId} enabled={queueEnabled} pageState={pageState} />}
                </section>
            </main>
        </div>
    )
}

function isOpenAtLoad(opening: SessionOpening) {
    return Date.parse(opening.salesOpenAt) <= Date.parse(opening.serverTime)
}

// 예매 오픈 카운트다운
function CountdownView({opening, pageState, onJoin}: {
    opening: SessionOpening
    pageState: QueuePageState
    onJoin: () => void
}) {
    const countdown = usePaymentCountdown(opening.salesOpenAt, opening.clockOffsetMs)
    const [subscribe, setSubscribe] = useState<'idle' | 'pending' | 'done' | 'failed'>('idle')
    const hours = Math.floor(countdown.remainingSeconds / 3600)
    const minutes = Math.floor((countdown.remainingSeconds % 3600) / 60)
    const seconds = countdown.remainingSeconds % 60
    const sub = [pageState.sessionStartsAt && formatDateTime(pageState.sessionStartsAt), pageState.venue]
        .filter(Boolean)
        .join(', ')

    async function handleSubscribe() {
        if (!pageState.performanceId) {
            return
        }
        setSubscribe('pending')
        try {
            await subscribeOpening(pageState.performanceId)
            setSubscribe('done')
        } catch {
            setSubscribe('failed')
        }
    }

    return (
        <>
            <ShowInfo pageState={pageState} sub={sub} />
            <Heading
                title="예매 오픈까지"
                description={`${formatDateTime(opening.salesOpenAt)} 오픈 (서버 시간 기준)`}
            />

            <div className="flex items-start justify-center gap-2.5" role="timer" aria-label="예매 오픈까지 남은 시간">
                <CountdownUnit value={hours} label="시간" />
                <CountdownSeparator />
                <CountdownUnit value={minutes} label="분" />
                <CountdownSeparator />
                <CountdownUnit value={seconds} label="초" />
            </div>

            <div className="flex w-full flex-col gap-3">
                <PrimaryButton disabled={!countdown.isExpired} onClick={onJoin}>
                    {countdown.isExpired ? '예매하기' : '오픈 후 예매하기가 활성화돼요'}
                </PrimaryButton>
                {pageState.performanceId && (
                    <SecondaryButton disabled={subscribe === 'pending' || subscribe === 'done'} onClick={handleSubscribe}>
                        {subscribe === 'done' ? '예매 시작 알림을 신청했어요' : '예매 시작 알림 신청'}
                    </SecondaryButton>
                )}
                {subscribe === 'failed' && (
                    <p role="alert" className="text-center text-[13px] text-[#b93815]">알림 신청에 실패했어요. 다시 시도해 주세요.</p>
                )}
            </div>

            <Notes>
                <li>오픈 시각이 되면 버튼이 자동으로 활성화되고, 누르면 대기열에 입장해요.</li>
                <li>오픈 직후에는 접속이 몰려 대기 순번이 부여될 수 있어요.</li>
            </Notes>
        </>
    )
}

function QueueView({sessionId, enabled, pageState}: {
    sessionId: string | undefined
    enabled: boolean
    pageState: QueuePageState
}) {
    const navigate = useNavigate()
    const queue = useQueueEntry(sessionId, enabled)
    const backPath = pageState.performanceId ? `/performances/${pageState.performanceId}` : '/'
    const seatPath = `/sessions/${sessionId}/seats`
    const sub = pageState.sessionStartsAt ? `${formatDateTime(pageState.sessionStartsAt)} 회차` : undefined

    // 입장 화면의 3초 타이머가 다시 그릴 때마다 초기화되지 않도록 같은 함수 유지
    const enterSeats = useCallback(
        () => navigate(seatPath, {replace: true, state: pageState}),
        [navigate, seatPath, pageState],
    )

    async function handleLeave() {
        await queue.leave()
        navigate(backPath)
    }

    const {entry, error} = queue

    if (error) {
        return (
            <>
                <ShowInfo pageState={pageState} sub={sub} />
                <ErrorView error={error} onRetry={queue.rejoin} onBack={() => navigate(backPath)} onLogin={() => navigate('/login')} />
            </>
        )
    }

    if (!entry) {
        return (
            <>
                <ShowInfo pageState={pageState} sub={sub} />
                <Heading title="대기열에 들어가는 중이에요" description="잠시만 기다려 주세요." />
            </>
        )
    }

    if (entry.status === 'WAITING') {
        return (
            <>
                <ShowInfo pageState={pageState} sub={sub} />
                <WaitingView entry={entry} startPosition={queue.startPosition} etaSeconds={queue.etaSeconds}
                             isReconnecting={queue.isReconnecting} onLeave={handleLeave} />
            </>
        )
    }

    if (entry.status === 'ADMITTED') {
        return (
            <>
                <ShowInfo pageState={pageState} sub={sub} />
                <AdmittedView entry={entry} clockOffsetMs={queue.clockOffsetMs}
                              onEnter={enterSeats}
                              onExpired={queue.rejoin} />
            </>
        )
    }

    return (
        <>
            <ShowInfo pageState={pageState} sub={sub} />
            <FinishedView entry={entry} onRejoin={queue.rejoin} onBack={() => navigate(backPath)}
                          onBookings={() => navigate('/mypage')} />
        </>
    )
}

// 대기열 대기 중
function WaitingView({entry, startPosition, etaSeconds, isReconnecting, onLeave}: {
    entry: QueueEntry
    startPosition: number | null
    etaSeconds: number | null
    isReconnecting: boolean
    onLeave: () => void
}) {
    const position = entry.position ?? 1
    const ahead = Math.max(0, position - 1)
    const behind = Math.max(0, entry.waitingCount - position)
    // 처음 받은 순번에서 얼마나 줄었는지 (처음부터 1번이면 이미 맨 앞)
    const start = Math.max(startPosition ?? position, position)
    const progress = start <= 1 ? 100 : Math.round(((start - position) / (start - 1)) * 100)

    return (
        <>
            <Heading
                title="예매 대기 중이에요"
                description={isReconnecting
                    ? '연결이 불안정해서 다시 연결하고 있어요. 순번은 그대로예요.'
                    : '순서가 되면 좌석 선택 화면으로 자동 이동해요.'}
            />

            <div className="flex w-full flex-col items-center gap-1" aria-live="polite">
                <p className="text-center text-sm leading-[1.4] font-medium text-[#5b616e]">내 대기 순번</p>
                <p className="flex items-baseline gap-1 font-bold whitespace-nowrap">
                    <span className="text-[56px] leading-none text-[#4338ca] tabular-nums">{position.toLocaleString('ko-KR')}</span>
                    <span className="text-[22px] text-[#111318]">번</span>
                </p>
            </div>

            <div className="flex w-full flex-col gap-2">
                <div
                    role="progressbar"
                    aria-label="대기 진행률"
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={progress}
                    className="h-2 w-full overflow-hidden rounded-[4px] bg-[#e4e6eb]"
                >
                    <div
                        className="h-2 rounded-[4px] bg-[#4338ca] transition-[width] duration-700 ease-out"
                        style={{width: `${progress}%`}}
                    />
                </div>
                <div className="flex justify-between text-xs whitespace-nowrap text-[#5b616e]">
                    <span>대기 시작</span>
                    <span>입장</span>
                </div>
            </div>

            <div className="flex w-full gap-3">
                <Stat label="내 앞 대기 인원" value={`${ahead.toLocaleString('ko-KR')}명`} />
                <Stat label="예상 대기 시간" value={formatEta(etaSeconds)} />
                <Stat label="내 뒤 대기 인원" value={`${behind.toLocaleString('ko-KR')}명`} />
            </div>

            <Notes>
                <li>새로고침해도 순번은 그대로예요. 창을 닫고 2분이 지나면 순번이 사라져요.</li>
                <li>입장 후 좌석 선택은 {ADMISSION_MINUTES}분 안에 마쳐야 해요.</li>
                <li>한 계정으로 여러 창에서 대기하면 하나만 유지돼요.</li>
            </Notes>

            <SecondaryButton onClick={onLeave}>대기 취소하고 나가기</SecondaryButton>
        </>
    )
}

// 입장 직전 (좌석 선택으로 이동)
function AdmittedView({entry, clockOffsetMs, onEnter, onExpired}: {
    entry: QueueEntry
    clockOffsetMs: number
    onEnter: () => void
    onExpired: () => void
}) {
    const [count, setCount] = useState(ENTER_DELAY_SECONDS)
    const admission = usePaymentCountdown(entry.admissionExpiresAt ?? undefined, clockOffsetMs)
    const holdMinutes = Math.max(1, Math.ceil(admission.remainingSeconds / 60))

    useEffect(() => {
        if (count <= 0) {
            onEnter()
            return
        }
        const timerId = window.setTimeout(() => setCount(value => value - 1), 1000)
        return () => window.clearTimeout(timerId)
    }, [count, onEnter])

    if (admission.isExpired) {
        return (
            <>
                <Heading title="입장 시간이 지났어요" description="다시 줄을 서면 맨 뒤 순서부터 시작해요." />
                <PrimaryButton onClick={onExpired}>다시 줄 서기</PrimaryButton>
            </>
        )
    }

    return (
        <>
            <div className="flex size-[140px] items-center justify-center rounded-full border-[6px] border-[#4338ca] bg-[#eef0ff]">
                <span className="text-[64px] font-bold text-[#4338ca] tabular-nums" aria-live="polite">{Math.max(1, count)}</span>
            </div>
            <Heading title="입장 순서가 되었어요" description={`${Math.max(1, count)}초 후 좌석 선택 화면으로 이동해요.`} />

            <div className="flex w-full items-center gap-2.5 rounded-xl bg-[#fff6ed] px-4 py-3.5">
                <img src={timerIcon} alt="" width={20} height={20} className="shrink-0" />
                <p className="flex-1 text-sm leading-[1.5] font-medium text-[#b93815]">
                    입장하면 좌석 선택 제한시간 {holdMinutes}분이 시작돼요.
                </p>
            </div>

            <PrimaryButton onClick={onEnter}>지금 바로 이동</PrimaryButton>
        </>
    )
}

// 피그마에 없는 상태 (끝난 항목). 대기 화면과 같은 조각으로 보여 줌
function FinishedView({entry, onRejoin, onBack, onBookings}: {
    entry: QueueEntry
    onRejoin: () => void
    onBack: () => void
    onBookings: () => void
}) {
    if (entry.status === 'CONSUMED') {
        return (
            <>
                <Heading title="이미 좌석을 선택했어요" description="이 입장으로 만든 예매가 있어요. 결제는 예매 내역에서 이어서 할 수 있어요." />
                <div className="flex w-full flex-col gap-3">
                    <PrimaryButton onClick={onBookings}>예매 내역 보기</PrimaryButton>
                    <SecondaryButton onClick={onBack}>공연으로 돌아가기</SecondaryButton>
                </div>
            </>
        )
    }

    const left = entry.status === 'LEFT'
    return (
        <>
            <Heading
                title={left ? '대기를 취소했어요' : '대기 시간이 지났어요'}
                description={left
                    ? '다시 줄을 서면 맨 뒤 순서부터 시작해요.'
                    : '입장 시간이 지났거나 오래 연결이 끊겨서 순번이 사라졌어요.'}
            />
            <div className="flex w-full flex-col gap-3">
                <PrimaryButton onClick={onRejoin}>다시 줄 서기</PrimaryButton>
                <SecondaryButton onClick={onBack}>공연으로 돌아가기</SecondaryButton>
            </div>
        </>
    )
}

// 피그마에 없는 상태 (오류). 대기 화면과 같은 조각으로 보여 줌
function ErrorView({error, onRetry, onBack, onLogin}: {
    error: QueueApiError
    onRetry: () => void
    onBack: () => void
    onLogin: () => void
}) {
    const view = getErrorView(error)

    return (
        <>
            <Heading title={view.title} description={view.description} />
            <div className="flex w-full flex-col gap-3">
                {view.action === 'login' && <PrimaryButton onClick={onLogin}>로그인하기</PrimaryButton>}
                {view.action === 'retry' && <PrimaryButton onClick={onRetry}>다시 시도</PrimaryButton>}
                <SecondaryButton onClick={onBack}>공연으로 돌아가기</SecondaryButton>
            </div>
        </>
    )
}

function getErrorView(error: QueueApiError): {title: string; description: string; action?: 'login' | 'retry'} {
    switch (error.code) {
        case 'SALE_NOT_OPEN':
            return {title: '지금은 예매 시간이 아니에요', description: '판매 시작 전이거나 판매가 끝난 회차예요.'}
        case 'SALE_STOPPED':
            return {title: '판매가 잠시 중단됐어요', description: '판매가 다시 열리면 예매할 수 있어요.'}
        case 'RESOURCE_NOT_FOUND':
            return {title: '회차를 찾을 수 없어요', description: '공연 정보에서 회차를 다시 선택해 주세요.'}
        case 'ACCESS_DENIED':
            return {title: '일반 회원만 예매할 수 있어요', description: '관리자 계정으로는 대기열에 들어갈 수 없어요.'}
        case 'AUTHENTICATION_REQUIRED':
            return {title: '로그인이 필요해요', description: '로그인한 뒤 다시 예매해 주세요.', action: 'login'}
        default:
            if (error.status === 401) {
                return {title: '로그인이 필요해요', description: '로그인한 뒤 다시 예매해 주세요.', action: 'login'}
            }
            return {title: '대기열에 들어가지 못했어요', description: error.message, action: 'retry'}
    }
}

// 남은 대기 시간 → 약 3분
function formatEta(seconds: number | null): string {
    if (seconds === null) {
        return '계산 중'
    }
    if (seconds < 60) {
        return '1분 이내'
    }
    const minutes = Math.ceil(seconds / 60)
    if (minutes < 60) {
        return `약 ${minutes}분`
    }
    return `약 ${Math.floor(minutes / 60)}시간 ${minutes % 60}분`
}

// 피그마 Header/Minimal (대기열과 카운트다운 화면 상단의 간소화 헤더)
function HeaderMinimal() {
    return (
        <header className="h-16 shrink-0 border-b border-[#e4e6eb] bg-white">
            <div className="mx-auto flex h-full max-w-[1280px] items-center justify-between gap-4 px-4">
                <p className="text-2xl font-bold text-[#111318]">TIKIT</p>
                <p className="truncate text-[13px] text-[#5b616e]">공정한 예매를 위해 대기열을 운영하고 있어요</p>
            </div>
        </header>
    )
}

function ShowInfo({pageState, sub}: {pageState: QueuePageState; sub?: string}) {
    if (!pageState.performanceTitle) {
        return null
    }

    return (
        <>
            <div className="flex w-full items-center gap-3.5">
                {pageState.posterUrl
                    ? <img src={pageState.posterUrl} alt="" className="h-16 w-12 shrink-0 rounded-lg object-cover" />
                    : <div aria-hidden="true" className="h-16 w-12 shrink-0 rounded-lg border border-dashed border-[#c9cdd4] bg-[#f2f4f7]" />}
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <p className="text-[15px] leading-[1.4] font-bold text-[#111318]">{pageState.performanceTitle}</p>
                    {sub && <p className="text-[13px] text-[#5b616e]">{sub}</p>}
                </div>
            </div>
            <div className="h-px w-full bg-[#e4e6eb]" />
        </>
    )
}

function Heading({title, description}: {title: string; description: string}) {
    return (
        <div className="flex w-full flex-col items-center gap-1.5 text-center leading-[1.4]">
            <h1 className="text-xl font-bold text-[#111318]">{title}</h1>
            <p className="text-sm text-[#5b616e]">{description}</p>
        </div>
    )
}

function CountdownUnit({value, label}: {value: number; label: string}) {
    return (
        <div className="flex flex-col items-center gap-2">
            <div className="flex size-[72px] items-center justify-center rounded-2xl bg-[#111318] sm:size-[104px]">
                <span className="text-[34px] font-bold text-white tabular-nums sm:text-5xl">{String(value).padStart(2, '0')}</span>
            </div>
            <span className="text-[13px] text-[#5b616e]">{label}</span>
        </div>
    )
}

function CountdownSeparator() {
    return (
        <div aria-hidden="true" className="flex h-[72px] w-4 items-center justify-center sm:h-[104px]">
            <span className="text-[40px] font-bold text-[#c9cdd4]">:</span>
        </div>
    )
}

function Stat({label, value}: {label: string; value: string}) {
    return (
        <div className="flex min-w-0 flex-1 flex-col items-center gap-1 rounded-xl bg-[#f6f7f9] px-2 py-3.5 whitespace-nowrap">
            <span className="text-xs text-[#5b616e]">{label}</span>
            <span className="text-base font-bold text-[#111318]">{value}</span>
        </div>
    )
}

function Notes({children}: {children: ReactNode}) {
    return (
        <ul className="flex w-full list-none flex-col gap-1.5 rounded-[10px] bg-[#f6f7f9] px-4 py-3.5 text-[13px] leading-[1.55] text-[#3a3f48] [&>li]:before:mr-2 [&>li]:before:content-['•']">
            {children}
        </ul>
    )
}

function PrimaryButton({disabled = false, onClick, children}: {disabled?: boolean; onClick: () => void; children: ReactNode}) {
    return (
        <button
            type="button"
            disabled={disabled}
            onClick={onClick}
            className="flex h-[54px] w-full items-center justify-center rounded-xl bg-[#4338ca] text-[17px] font-bold text-white transition-colors hover:bg-[#3730a3] disabled:cursor-not-allowed disabled:bg-[#e4e6eb] disabled:text-[#5b616e]"
        >
            {children}
        </button>
    )
}

function SecondaryButton({disabled = false, onClick, children}: {disabled?: boolean; onClick: () => void; children: ReactNode}) {
    return (
        <button
            type="button"
            disabled={disabled}
            onClick={onClick}
            className="flex h-11 w-full items-center justify-center rounded-xl border border-[#e4e6eb] bg-white text-[15px] text-[#111318] transition-colors hover:bg-[#f6f7f9] disabled:cursor-not-allowed disabled:text-[#5b616e] disabled:hover:bg-white"
        >
            {children}
        </button>
    )
}
