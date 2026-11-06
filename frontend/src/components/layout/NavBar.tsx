
import {useLayoutEffect, useRef, useState} from 'react'
import {Link, NavLink, useLocation, useNavigate, useSearchParams} from 'react-router'
import type {FormEvent} from 'react'
import searchIcon from '../../assets/navigation/search.svg'
import heartIcon from '../../assets/navigation/heart.svg'
import shoppingBagIcon from '../../assets/navigation/shopping-bag.svg'
import AccountPopover from './AccountPopover'
import {categories} from '../../constants/categories'

function PerformanceSearch({initialKeyword}: {initialKeyword: string}) {
  const navigate = useNavigate()
  const [searchKeyword, setSearchKeyword] = useState(initialKeyword)

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const keyword = searchKeyword.trim()
    navigate(keyword ? `/?q=${encodeURIComponent(keyword)}` : '/')
  }

  return (
    <form
      role="search"
      onSubmit={handleSearch}
      className="flex h-10 w-[280px] min-w-0 items-center gap-3 rounded-full border border-[#edededd9] px-3 max-sm:flex-1"
    >
      <input
        type="search"
        aria-label="공연 검색"
        value={searchKeyword}
        onChange={event => setSearchKeyword(event.target.value)}
        placeholder="최현우 30주년 기념 마술쇼"
        className="h-full min-w-0 flex-1 bg-transparent text-sm leading-[normal] placeholder:text-[#8e8e8e] focus:outline-none!"
      />
      <button
        type="submit"
        aria-label="검색"
        className="flex shrink-0 items-center justify-center"
      >
        <img src={searchIcon} alt="" />
      </button>
    </form>
  )
}

export default function NavBar() {
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const keyword = searchParams.get('q') ?? ''
  const categoryList = useRef<HTMLDivElement>(null)
  const categoryUnderline = useRef<HTMLSpanElement>(null)

  useLayoutEffect(() => {
    const list = categoryList.current
    const underline = categoryUnderline.current
    if (!list || !underline) return

    let entranceAnimation: Animation | undefined

    function updateUnderline() {
      if (!list || !underline) return

      const label = list.querySelector<HTMLSpanElement>(
        'a[aria-current="page"] span',
      )
      if (!label) {
        underline.style.opacity = '0'
        return
      }

      const labelBounds = label.getBoundingClientRect()
      const listBounds = list.getBoundingClientRect()
      const isEntering = underline.style.opacity !== '1'
      const translation = `translateX(${labelBounds.left - listBounds.left}px)`

      if (isEntering) underline.style.transitionProperty = 'none'

      underline.style.width = `${labelBounds.width}px`
      underline.style.transform = translation
      underline.style.opacity = '1'

      if (isEntering) {
        underline.getBoundingClientRect()
        underline.style.transitionProperty = ''

        if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
          entranceAnimation = underline.animate(
            [
              {transform: `${translation} scaleX(0)`, opacity: 0},
              {transform: `${translation} scaleX(1)`, opacity: 1},
            ],
            {duration: 250, easing: 'cubic-bezier(0, 0, 0.2, 1)'},
          )
        }
      }
    }

    updateUnderline()
    const observer = new ResizeObserver(updateUnderline)
    observer.observe(list)
    list.querySelectorAll('a span').forEach(label => observer.observe(label))

    return () => {
      observer.disconnect()
      entranceAnimation?.cancel()
    }
  }, [location.pathname])

  return (
    <header className="w-full bg-white">
      <div className="mx-auto flex min-h-16 max-w-[1320px] flex-wrap items-center justify-between gap-y-4 pl-5 pt-6 max-sm:pr-5">
        <Link
          to="/"
          aria-label="TIKIT 홈"
          className="flex h-10 items-center text-[32px] leading-[normal] font-semibold"
        >
          TIKIT
        </Link>

        <div className="flex h-10 w-[400px] max-w-full items-center justify-between gap-4 max-sm:w-full">
          <PerformanceSearch key={keyword} initialKeyword={keyword} />

          <div className="flex h-full shrink-0 items-center gap-3 px-1">
            <button
              type="button"
              aria-label="관심 공연"
              className="flex h-6 w-[18.236px] items-center justify-center"
            >
              <img
                src={heartIcon}
                alt=""
                className="max-w-none shrink-0"
              />
            </button>

            <AccountPopover key={location.key} />

            <button
              type="button"
              aria-label="예매 내역"
              className="flex items-center justify-center"
            >
              <img src={shoppingBagIcon} alt="" />
            </button>
          </div>
        </div>
      </div>

      <nav
        aria-label="공연 장르"
        className="mx-auto h-12 max-w-[1320px] overflow-x-auto px-5"
      >
        <div
          ref={categoryList}
          className="relative flex h-full w-max min-w-full gap-[10px]"
        >
          {categories.map(category => (
            <NavLink
              key={category.id}
              to={`/categories/${category.id}`}
              className="flex shrink-0 items-center px-3 text-lg leading-[normal] font-medium whitespace-nowrap first:pl-1"
            >
              <span>{category.label}</span>
            </NavLink>
          ))}

          <span
            ref={categoryUnderline}
            aria-hidden="true"
            className="pointer-events-none absolute bottom-2 left-0 h-0.5 bg-[#111] opacity-0 transition-[transform,width,opacity] duration-250 ease-out motion-reduce:transition-none"
          />
        </div>
      </nav>
    </header>
  )
}
