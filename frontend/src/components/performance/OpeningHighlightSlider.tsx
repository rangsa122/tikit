import {useEffect, useRef, useState} from 'react'
import {Link} from 'react-router'
import type {Performance} from '../../types/performance'

interface OpeningHighlightSliderProps {
    performances: Performance[]
}

/** 오픈 임박 공연 큰 카드 슬라이드 */
export default function OpeningHighlightSlider({
                                                   performances,
                                               }: OpeningHighlightSliderProps) {
    const listRef = useRef<HTMLUListElement>(null)
    const [canPrev, setCanPrev] = useState(false)
    const [canNext, setCanNext] = useState(false)

    function updateButtons() {
        const list = listRef.current

        if (!list) return

        setCanPrev(list.scrollLeft > 0)
        setCanNext(list.scrollLeft + list.clientWidth < list.scrollWidth - 1)
    }

    useEffect(() => {
        updateButtons()

        window.addEventListener('resize', updateButtons)

        return () => window.removeEventListener('resize', updateButtons)
    }, [performances])

    function scrollByPage(direction: 1 | -1) {
        const list = listRef.current

        if (!list) return

        list.scrollBy({left: direction * list.clientWidth, behavior: 'smooth'})
    }

    if (performances.length === 0) {
        return null
    }

    return (
        <section
            aria-label="오픈 임박 공연"
            className="relative"
        >
            <ul
                ref={listRef}
                onScroll={updateButtons}
                className="flex snap-x snap-mandatory gap-4 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
            >
                {performances.map(performance => (
                    <li
                        key={performance.id}
                        className="shrink-0 basis-[85%] snap-start md:basis-[calc((100%-16px)/2)] lg:basis-[calc((100%-32px)/3)]"
                    >
                        <Link
                            to={`/performances/${performance.id}`}
                            aria-label={`${performance.title} 공연 상세 보기`}
                            className="block rounded-2xl focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-black"
                        >
                            <div className="relative aspect-[10/9] w-full overflow-hidden rounded-2xl bg-[#e4e6eb]">
                                {performance.posterUrl && (
                                    <>
                                        <img
                                            src={performance.posterUrl}
                                            alt=""
                                            aria-hidden="true"
                                            className="absolute inset-0 h-full w-full scale-125 object-cover opacity-70 blur-2xl"
                                        />
                                        <img
                                            src={performance.posterUrl}
                                            alt={`${performance.title} 포스터`}
                                            loading="lazy"
                                            className="absolute top-1/2 left-1/2 h-[75%] w-auto -translate-x-1/2 -translate-y-1/2 rounded-lg object-cover shadow-lg"
                                        />
                                    </>
                                )}
                            </div>

                            <div className="flex flex-col gap-1.5 pt-4 pl-1 leading-[normal]">
                                <p className="text-lg leading-[normal] font-semibold text-[#4338CA]">
                                    {performance.openingLabel}
                                </p>
                                <h3 className="line-clamp-2 text-lg leading-[normal] font-semibold break-words text-[#111]">
                                    {performance.title}
                                </h3>
                            </div>
                        </Link>
                    </li>
                ))}
            </ul>

            {canPrev && (
                <button
                    type="button"
                    aria-label="이전 공연 보기"
                    onClick={() => scrollByPage(-1)}
                    className="absolute top-[calc(45%-22px)] -left-5 flex h-11 w-11 items-center justify-center rounded-xl border border-[#e4e6eb] bg-white text-xl shadow"
                >
                    ‹
                </button>
            )}

            {canNext && (
                <button
                    type="button"
                    aria-label="다음 공연 보기"
                    onClick={() => scrollByPage(1)}
                    className="absolute top-[calc(45%-22px)] -right-5 flex h-11 w-11 items-center justify-center rounded-xl border border-[#e4e6eb] bg-white text-xl shadow"
                >
                    ›
                </button>
            )}
        </section>
    )
}