import {useEffect, useRef, useState} from 'react'
import {Link} from 'react-router'
import {usePerformances} from '../../hooks/usePerformances'
import type {PointerEvent} from 'react'

interface PerformanceBannerProps {
  variant?: 'home' | 'category'
}

const transitionDuration = 450

export default function PerformanceBanner({variant = 'home'}: PerformanceBannerProps) {
  const today = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())

  // 공개된 공연 중 종료되지 않은 공연 조회
  const bannerQuery = usePerformances({
    page: 0,
    size: 100,
    fromDate: today,
    sort: 'startDate',
  })

  // 판매 활성 공연 중 최대 5개 선택
  const banners = (bannerQuery.data?.items ?? [])
      .filter(item => item.salesStatus === 'ENABLED')
      .slice(0, 5)

  const slides = [...banners, ...banners, ...banners]
  const bannerCount = banners.length

  const [position, setPosition] = useState(0)
  const [failedPosterUrls, setFailedPosterUrls] = useState<string[]>([])
  const [moving, setMoving] = useState(false)
  const [isPlaying, setIsPlaying] = useState(() => !window.matchMedia('(prefers-reduced-motion: reduce)').matches)
  const [isHovered, setIsHovered] = useState(false)
  const [dragOffset, setDragOffset] = useState(0)
  const [dragStep, setDragStep] = useState(1)
  const movingRef = useRef(false)
  const draggedRef = useRef(false)
  const playbackButton = useRef<HTMLButtonElement>(null)
  const pointerStart = useRef<{id: number; x: number; y: number; step: number} | null>(null)
  const isCategory = variant === 'category'
  const gap = isCategory ? 18 : 20
  const visualPosition = position - dragOffset / dragStep
  const centeredPosition = Math.round(visualPosition)
  const activeIndex = bannerCount > 0
      ? ((centeredPosition % bannerCount) + bannerCount) % bannerCount
      : 0

// 공연 데이터가 변경되면 슬라이드 위치 초기화
  useEffect(() => {
    movingRef.current = false
    setMoving(false)
    setPosition(bannerCount)
  }, [bannerCount])

  function move(direction: number) {
    if (movingRef.current || bannerCount <= 1) return
    movingRef.current = true
    setMoving(true)
    setPosition(value => value + direction)
  }

  useEffect(() => {
    if (!moving) return
    const timer = window.setTimeout(() => {
      setPosition(value => (value % banners.length) + banners.length)
      setMoving(false)
      movingRef.current = false
    }, transitionDuration)
    return () => window.clearTimeout(timer)
  }, [moving, bannerCount])

  useEffect(() => {
    if (!isPlaying || isHovered || moving || bannerCount <= 1) return
    const timer = window.setInterval(() => {
      if (!document.hidden && !pointerStart.current) move(1)
    }, 4000)
    return () => window.clearInterval(timer)
  }, [isPlaying, isHovered, moving, bannerCount])

  function handlePointerDown(event: PointerEvent<HTMLDivElement>) {
    if (!event.isPrimary || event.button !== 0 || movingRef.current) return

    draggedRef.current = false

    const step = event.currentTarget.getBoundingClientRect().width + gap
    setDragStep(step)

    pointerStart.current = {
      id: event.pointerId,
      x: event.clientX,
      y: event.clientY,
      step,
    }
  }

  function handlePointerMove(event: PointerEvent<HTMLDivElement>) {
    const start = pointerStart.current
    if (!start || start.id !== event.pointerId) return

    const distanceX = event.clientX - start.x
    const distanceY = event.clientY - start.y

    // 가로로 8px 이상 이동하면 드래그로 판단
    if (Math.abs(distanceX) > 8 && Math.abs(distanceX) > Math.abs(distanceY)) {
      draggedRef.current = true

      // 실제 드래그가 시작된 경우에만 포인터 캡처
      if (!event.currentTarget.hasPointerCapture(event.pointerId)) {
        event.currentTarget.setPointerCapture(event.pointerId)
      }
    }

    if (Math.abs(distanceX) > Math.abs(distanceY)) {
      const limit = start.step * (bannerCount - 1)

      setDragOffset(
          Math.max(-limit, Math.min(limit, distanceX))
      )
    }
  }

  function handlePointerUp(event: PointerEvent<HTMLDivElement>) {
    const start = pointerStart.current
    if (!start || start.id !== event.pointerId) return
    const distance = event.clientX - start.x
    const isSwipe = Math.abs(distance) >= 50 && Math.abs(distance) > Math.abs(event.clientY - start.y)
    pointerStart.current = null
    setDragOffset(0)
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
    if (isSwipe) {
      const count = Math.min(banners.length - 1, Math.max(1, Math.round(Math.abs(distance) / start.step)))
      move(distance < 0 ? count : -count)
    }
  }

  let frameClass = 'aspect-[1321/415] min-h-[160px] max-w-[1321px]'
  if (isCategory) frameClass = 'aspect-[500/460] max-w-[500px]'

  // 공연 조회 상태 처리
  if (bannerQuery.isPending) {
    return (
        <div className="mx-auto max-w-[1321px] px-5 pt-6">
          <div className="flex h-[300px] items-center justify-center rounded-xl bg-[#f7f7f7] text-[#777]">
            추천 공연을 불러오는 중...
          </div>
        </div>
    )
  }

  if (bannerQuery.isError) {
    return (
        <div className="mx-auto max-w-[1321px] px-5 pt-6">
          <div className="flex h-[300px] flex-col items-center justify-center gap-4 rounded-xl bg-[#f7f7f7]">
            <p>추천 공연을 불러오지 못했습니다.</p>
            <button
                type="button"
                onClick={() => void bannerQuery.refetch()}
                className="rounded border border-[#ccc] px-4 py-2"
            >
              다시 시도
            </button>
          </div>
        </div>
    )
  }

  if (bannerCount === 0) {
    return (
        <div className="mx-auto max-w-[1321px] px-5 pt-6">
          <div className="flex h-[300px] items-center justify-center rounded-xl bg-[#f7f7f7] text-[#777]">
            현재 추천할 공연이 없습니다.
          </div>
        </div>
    )
  }

  return (
    <section
      aria-label={isCategory ? '카테고리 추천 공연' : '추천 공연'}
      aria-roledescription="캐러셀"
      className="overflow-hidden pt-6"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onFocusCapture={event => {
        if (event.target !== playbackButton.current) setIsPlaying(false)
      }}
      onKeyDown={event => {
        if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
          event.preventDefault()
          move(event.key === 'ArrowLeft' ? -1 : 1)
        }
      }}
    >
      <div className={`relative mx-auto w-[calc(100%-40px)] ${frameClass}`}>
        <div
            className="h-full touch-pan-y cursor-grab select-none active:cursor-grabbing"
            onClickCapture={event => {
              // 드래그 후 발생하는 클릭은 상세 페이지 이동 방지
              if (draggedRef.current) {
                event.preventDefault()
                event.stopPropagation()
                draggedRef.current = false
              }
            }}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerCancel={() => {
            pointerStart.current = null
            setDragOffset(0)
          }}
          onLostPointerCapture={() => {
            pointerStart.current = null
            setDragOffset(0)
          }}
        >
          <div
            className={`flex h-full ease-out motion-reduce:transition-none ${moving ? 'transition-transform duration-[450ms]' : ''}`}
            style={{gap, transform: `translateX(calc(${-position * 100}% - ${position * gap}px + ${dragOffset}px))`}}
          >
            {slides.map((slide, index) => (
                <div
                    key={`${slide.performanceId}-${index}`}
                    role="group"
                    aria-roledescription="슬라이드"
                    aria-label={`${(index % bannerCount) + 1} / ${bannerCount}: ${slide.title}`}
                    aria-hidden={index !== centeredPosition}
                    className={`h-full w-full shrink-0 overflow-hidden rounded-xl ease-out motion-reduce:transition-none ${moving ? 'transition-transform duration-[450ms]' : ''}`}
                    style={{
                      transform: isCategory
                          ? `scale(${1 - 0.08 * Math.min(Math.abs(index - visualPosition), 1)})`
                          : undefined,
                    }}
                >
                  <Link
                      to={`/performances/${slide.performanceId}`}
                      tabIndex={index === centeredPosition ? 0 : -1}
                      onDragStart={event => event.preventDefault()}
                      className="relative block h-full w-full overflow-hidden bg-[#222]"
                  >
                    {slide.posterUrl && !failedPosterUrls.includes(slide.posterUrl) ? (
                        <>
                          {/* 흐릿한 배경 이미지 */}
                          <img
                              src={slide.posterUrl}
                              alt=""
                              aria-hidden="true"
                              draggable={false}
                              className="pointer-events-none absolute inset-0 h-full w-full scale-110 object-cover blur-3xl brightness-[0.4]"
                          />

                          {/* 전체 포스터 */}
                          <img
                              src={slide.posterUrl}
                              alt={`${slide.title} 포스터`}
                              draggable={false}
                              onError={() => {
                                const url = slide.posterUrl

                                if (url) {
                                  setFailedPosterUrls(current =>
                                      current.includes(url) ? current : [...current, url]
                                  )
                                }
                              }}
                              className="pointer-events-none absolute inset-0 h-full w-full object-contain"
                          />
                        </>
                    ) : (
                        <div className="flex h-full w-full items-center justify-center text-white/70">
                          포스터 이미지 없음
                        </div>
                    )}

                    {/* 공연명 */}
                    <div className="pointer-events-none absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 to-transparent px-8 py-7">
                      <h2 className="text-xl font-semibold text-white">
                        {slide.title}
                      </h2>
                    </div>
                  </Link>
                </div>
            ))}
          </div>
        </div>
        {!isCategory && <button
          type="button"
          aria-label="이전 배너"
          onClick={() => move(-1)}
          className="absolute top-1/2 left-4 flex size-12 -translate-y-1/2 items-center justify-center rounded-2xl bg-white/95 text-3xl text-[#111] shadow-sm max-sm:left-2 max-sm:size-9 max-sm:text-2xl"
        >
          <span aria-hidden="true">‹</span>
        </button>}
        {!isCategory && <button
          type="button"
          aria-label="다음 배너"
          onClick={() => move(1)}
          className="absolute top-1/2 right-4 flex size-12 -translate-y-1/2 items-center justify-center rounded-2xl bg-white/95 text-3xl text-[#111] shadow-sm max-sm:right-2 max-sm:size-9 max-sm:text-2xl"
        >
          <span aria-hidden="true">›</span>
        </button>}
        <div className="absolute right-5 bottom-5 flex items-center gap-2 max-sm:right-3 max-sm:bottom-3">
          <button
            ref={playbackButton}
            type="button"
            aria-label={isPlaying ? '자동 재생 일시정지' : '자동 재생 시작'}
            onClick={() => setIsPlaying(value => !value)}
            className="flex size-9 items-center justify-center rounded-full bg-black/60 text-xs text-white"
          >
            <span aria-hidden="true">{isPlaying ? 'Ⅱ' : '▶'}</span>
          </button>
          <span aria-label="현재 배너" aria-live={isPlaying ? 'off' : 'polite'} className="rounded-full bg-black/60 px-4 py-2 text-sm leading-[normal] text-white tabular-nums">
            {activeIndex + 1} / {banners.length}
          </span>
        </div>
      </div>
    </section>
  )
}
