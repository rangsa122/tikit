
import {useId, useState} from 'react'
import type {ReactNode} from 'react'
import {Link} from 'react-router'
import type {Performance} from '../../types/performance'
import PerformanceCard from './PerformanceCard'
import PerformanceListState from './PerformanceListState'

interface PerformanceSectionProps {
  title: string
  performances: Performance[]
  variant?: 'opening' | 'standard'
  children?: ReactNode
  isLoading?: boolean
  isError?: boolean
  onRetry?: () => void
  moreLink?: string
}

export default function PerformanceSection({
  title,
  performances,
  variant,
  children,
  isLoading = false,
  isError = false,
  onRetry,
  moreLink,
}: PerformanceSectionProps) {
  const [expanded, setExpanded] = useState(false)

  const headingId = useId()
  const listId = useId()

  const visiblePerformances = expanded
    ? performances
    : performances.slice(0, 5)

  return (
    <section
      aria-labelledby={headingId}
      className="px-5 pt-8"
    >
      <div className="flex items-center justify-between gap-4 py-5 leading-[normal]">
        <h2
          id={headingId}
          className="text-2xl leading-[normal] font-bold"
        >
          {title}
        </h2>

        {moreLink && !isLoading && !isError && performances.length > 0 && (
            <Link
                to={moreLink}
                className="shrink-0 text-base leading-[normal]"
            >
              {'전체 보기 >'}
            </Link>
        )}

        {!moreLink && !isLoading && !isError && performances.length > 5 && (
          <button
            type="button"
            aria-expanded={expanded}
            aria-controls={listId}
            onClick={() => setExpanded(value => !value)}
            className="shrink-0 text-base leading-[normal]"
          >
            {expanded ? '접기 ∧' : '전체 보기 >'}
          </button>
        )}
      </div>

      {children}

      <div
        id={listId}
        className={children ? 'pt-7' : undefined}
      >
        {isError ? (
          <PerformanceListState
            state="error"
            onRetry={onRetry}
          />
        ) : isLoading ? (
          <PerformanceListState state="loading" />
        ) : visiblePerformances.length === 0 ? (
          <PerformanceListState state="empty" />
        ) : (
          <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-5">
            {visiblePerformances.map(performance => (
              <PerformanceCard
                key={performance.id}
                performance={performance}
                variant={variant}
              />
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
