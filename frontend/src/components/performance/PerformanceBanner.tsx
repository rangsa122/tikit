import {useEffect, useRef, useState} from 'react'
import type {PointerEvent} from 'react'
import {Link} from 'react-router'
import {useBanners} from '../../hooks/useBanners.ts'
import type {Banner} from '../../api/bannerApi.ts'

interface PerformanceBannerProps {
  variant?: 'home' | 'category'
}

interface BannerSlide {
  key: string
  label: string
  performanceId?: string
  imageUrl?: string
}

// 카테고리 배너와 홈 배너 로딩 중에 쓰는 빈 슬라이드
const placeholderSlides: BannerSlide[] = Array.from({length: 5}, (_, index) => ({
  key: `placeholder-${index + 1}`,
  label: `추천 공연 배너 ${index + 1}`,
}))

const transitionDuration = 450

export default function PerformanceBanner({variant = 'home'}: PerformanceBannerProps) {
  const isCategory = variant === 'category'

  // 카테고리 배너는 비율이 달라 공연 배너 이미지를 쓰지 않는다.
  const bannersQuery = useBanners(!isCategory)

  let banners: BannerSlide[] = placeholderSlides
  if (!isCategory && !bannersQuery.isLoading) {
    banners = (bannersQuery.data ?? []).map((banner: Banner) => ({
      key: banner.performanceId,
      label: banner.title,
      performanceId: banner.performanceId,
      imageUrl: banner.bannerUrl,
    }))
  }

  // 배너로 지정된 공연이 하나도 없으면 영역 자체를 숨긴다.
  if (banners.length === 0) {
    return null
  }

  // 배너 개수가 바뀌면(로딩 완료) 캐러셀을 새로 그려 위치를 초기화한다.
  // useEffect로 위치를 맞추면 렌더가 연쇄적으로 일어나므로 key로 처리한다.
  return (
      <BannerCarousel
          key={banners.length}
          banners={banners}
          isCategory={isCategory}
      />
  )
}

function BannerCarousel({
                          banners,
                          isCategory,
                        }: {
  banners: BannerSlide[]
  isCategory: boolean
}) {
  const count = banners.length
  const slides = [...banners, ...banners, ...banners]

  const [position, setPosition] = useState(count)
  const [moving, setMoving] = useState(false)
  const [isPlaying, setIsPlaying] = useState(() => !window.matchMedia('(prefers-reduced-motion: reduce)').matches)
  const [isHovered, setIsHovered] = useState(false)
  const [dragOffset, setDragOffset] = useState(0)
  const [dragStep, setDragStep] = useState(1)
  const movingRef = useRef(false)
  const playbackButton = useRef<HTMLButtonElement>(null)
  const pointerStart = useRef<{id: number; x: number; y: number; step: number} | null>(null)
  const gap = isCategory ? 18 : 20
  const visualPosition = position - dragOffset / dragStep
  const centeredPosition = Math.round(visualPosition)
  const activeIndex = ((centeredPosition % count) + count) % count

  function move(direction: number) {
    if (movingRef.current) return
    movingRef.current = true
    setMoving(true)
    setPosition(value => value + direction)
  }

  useEffect(() => {
    if (!moving) return
    const timer = window.setTimeout(() => {
      setPosition(value => (value % count) + count)
      setMoving(false)
      movingRef.current = false
    }, transitionDuration)
    return () => window.clearTimeout(timer)
  }, [moving, count])

  useEffect(() => {
    if (!isPlaying || isHovered || moving || count < 2) return
    const timer = window.setInterval(() => {
      if (!document.hidden && !pointerStart.current) move(1)
    }, 4000)
    return () => window.clearInterval(timer)
  }, [isPlaying, isHovered, moving, count])

  function handlePointerDown(event: PointerEvent<HTMLDivElement>) {
    if (!event.isPrimary || event.button !== 0 || movingRef.current) return
    const step = event.currentTarget.getBoundingClientRect().width + gap
    setDragStep(step)
    pointerStart.current = {
      id: event.pointerId,
      x: event.clientX,
      y: event.clientY,
      step,
    }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function handlePointerMove(event: PointerEvent<HTMLDivElement>) {
    const start = pointerStart.current
    if (!start || start.id !== event.pointerId) return
    const distance = event.clientX - start.x
    if (Math.abs(distance) > Math.abs(event.clientY - start.y)) {
      const limit = start.step * (count - 1)
      setDragOffset(Math.max(-limit, Math.min(limit, distance)))
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
      const steps = Math.min(Math.max(count - 1, 1), Math.max(1, Math.round(Math.abs(distance) / start.step)))
      move(distance < 0 ? steps : -steps)
    }
  }

  let frameClass = 'aspect-[1321/415] min-h-[160px] max-w-[1321px]'
  if (isCategory) frameClass = 'aspect-[500/460] max-w-[500px]'

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
                      key={`${slide.key}-${index}`}
                      role="group"
                      aria-roledescription="슬라이드"
                      aria-label={`${(index % count) + 1} / ${count}: ${slide.label}`}
                      aria-hidden={index !== centeredPosition}
                      className={`h-full w-full shrink-0 overflow-hidden rounded-xl bg-[#999] ease-out motion-reduce:transition-none ${moving ? 'transition-transform duration-[450ms]' : ''}`}
                      style={{
                        transform: isCategory
                            ? `scale(${1 - 0.08 * Math.min(Math.abs(index - visualPosition), 1)})`
                            : undefined,
                      }}
                  >
                    {slide.performanceId && slide.imageUrl && (
                        <Link
                            to={`/performances/${slide.performanceId}`}
                            tabIndex={index === centeredPosition ? 0 : -1}
                            // 드래그로 넘긴 경우에는 상세로 이동하지 않게 막는다.
                            onClick={event => {
                              if (dragOffset !== 0) event.preventDefault()
                            }}
                            className="block h-full w-full"
                        >
                          <img
                              src={slide.imageUrl}
                              alt={slide.label}
                              draggable={false}
                              className="h-full w-full object-cover"
                          />
                        </Link>
                    )}
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
            {activeIndex + 1} / {count}
          </span>
          </div>
        </div>
      </section>
  )
}