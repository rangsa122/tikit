import {useEffect, useRef, useState} from 'react'
import type {KeyboardEvent, SubmitEvent} from 'react'
import {askRagQuestion} from '../api/ragQuestionApi'
import type {RagQuestionResponse} from '../api/ragQuestionApi'

type ChatTurn = {
  id: number
  question: string
} & (
  | {status: 'pending'}
  | {status: 'failed'}
  | {status: 'answered'; response: RagQuestionResponse}
)

const ASSISTANT_BUBBLE = 'w-fit max-w-[88%] rounded-2xl rounded-tl-sm border border-slate-200 bg-white px-4 py-3 text-sm leading-relaxed text-slate-800 sm:max-w-[80%]'

function ChatTurnReply({turn, busy, onRetry}: {
  turn: ChatTurn
  busy: boolean
  onRetry: () => void
}) {
  if (turn.status === 'pending') {
    return <p role="status" className="text-slate-600">답변을 찾고 있어요…</p>
  }

  if (turn.status === 'failed') {
    return (
      <>
        <p>지금은 답변을 가져오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
        <button
          type="button"
          onClick={onRetry}
          disabled={busy}
          className="mt-2 rounded-md border border-indigo-200 px-3 py-1.5 font-semibold text-indigo-700 hover:bg-indigo-50 disabled:opacity-50"
        >
          다시 보내기
        </button>
      </>
    )
  }

  if (turn.response.sources.length === 0) {
    return <p>이 질문에 대한 답을 찾지 못했어요. 예매, 취소, 환불 중 어떤 점이 궁금한지 조금 더 자세히 알려주세요.</p>
  }

  return <p className="wrap-break-word whitespace-pre-wrap">{turn.response.answer}</p>
}

function RagConversation() {
  const [question, setQuestion] = useState('')
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const nextTurnId = useRef(0)
  const activeRequest = useRef<AbortController | null>(null)
  const conversation = useRef<HTMLDivElement>(null)
  const latestTurn = turns.at(-1)
  const isSubmitting = turns.some(turn => turn.status === 'pending')
  const trimmedQuestion = question.trim()

  useEffect(() => () => activeRequest.current?.abort(), [])

  useEffect(() => {
    const log = conversation.current
    if (latestTurn && log) log.scrollTop = log.scrollHeight
  }, [latestTurn])

  async function sendQuestion(question: string, retryId?: number) {
    if (activeRequest.current) return

    const request = new AbortController()
    activeRequest.current = request
    const id = retryId ?? ++nextTurnId.current
    const pending: ChatTurn = {id, question, status: 'pending'}
    setTurns(current => retryId !== undefined
      ? current.map(previous => previous.id === id ? pending : previous)
      : [...current, pending])

    try {
      const response = await askRagQuestion(question, request.signal)
      setTurns(current => current.map(previous =>
        previous.id === id ? {id, question, status: 'answered', response} : previous,
      ))
    } catch {
      if (request.signal.aborted) return
      setTurns(current => current.map(previous =>
        previous.id === id ? {id, question, status: 'failed'} : previous,
      ))
    } finally {
      activeRequest.current = null
    }
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!trimmedQuestion || activeRequest.current) return

    setQuestion('')
    void sendQuestion(trimmedQuestion)
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return

    event.preventDefault()
    event.currentTarget.form?.requestSubmit()
  }

  return (
    <div className="flex h-[min(660px,calc(100dvh-11rem))] min-h-[26rem] flex-col bg-white">
      <div
        ref={conversation}
        role="log"
        aria-label="AI 안내 대화"
        tabIndex={0}
        className="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-y-contain bg-slate-50 p-4 focus-visible:outline-offset-[-3px] sm:p-6"
      >
        <div className={ASSISTANT_BUBBLE}>
          <p className="mb-1 text-xs font-semibold text-indigo-700">AI 안내</p>
          <p>안녕하세요! 예매, 취소, 환불에 대해 궁금한 점을 물어보세요.</p>
        </div>
        {turns.map(turn => (
          <div key={turn.id} className="space-y-3">
            <div className="ml-auto w-fit max-w-[88%] rounded-2xl rounded-tr-sm bg-indigo-700 px-4 py-3 text-sm leading-relaxed text-white sm:max-w-[80%]">
              <span className="sr-only">내 질문</span>
              <p className="wrap-break-word whitespace-pre-wrap">{turn.question}</p>
            </div>
            <div className={ASSISTANT_BUBBLE}>
              <p className="mb-1 text-xs font-semibold text-indigo-700">AI 안내</p>
              <ChatTurnReply turn={turn} busy={isSubmitting} onRetry={() => void sendQuestion(turn.question, turn.id)} />
            </div>
          </div>
        ))}
      </div>

      <form onSubmit={handleSubmit} className="border-t border-slate-200 p-3 sm:p-4">
        <div className="flex items-end gap-2">
          <label htmlFor="rag-question" className="sr-only">질문 입력</label>
          <textarea
            id="rag-question"
            name="question"
            rows={2}
            enterKeyHint="send"
            aria-describedby="rag-composer-help"
            placeholder="메시지를 입력하세요"
            value={question}
            onChange={event => setQuestion(event.target.value)}
            onKeyDown={handleComposerKeyDown}
            className="min-w-0 flex-1 resize-none rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-base leading-snug text-slate-900 placeholder:text-slate-500"
          />
          <button
            type="submit"
            disabled={!trimmedQuestion || isSubmitting}
            className="h-11 shrink-0 rounded-xl bg-indigo-700 px-4 text-sm font-semibold whitespace-nowrap text-white hover:bg-indigo-800 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-600"
          >
            보내기
          </button>
        </div>
        <p id="rag-composer-help" className="mt-2 hidden text-xs text-slate-500 sm:block">
          Enter로 전송 · Shift+Enter로 줄바꿈
        </p>
      </form>
    </div>
  )
}

export default function RagQuestionPage() {
  return (
    <section aria-labelledby="rag-title" className="mx-auto w-full max-w-[800px] px-4 pt-5 pb-8 sm:px-6 sm:pt-8">
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <header className="border-b border-slate-200 px-4 py-3.5 sm:px-6">
          <h1 id="rag-title" className="text-lg font-bold text-slate-900">AI 안내</h1>
        </header>
        <RagConversation />
      </div>
    </section>
  )
}
