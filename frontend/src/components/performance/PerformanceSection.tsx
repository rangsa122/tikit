import {useId, useState} from 'react'
import type {ReactNode} from 'react'
import type {Performance} from '../../mocks/performances'
import PerformanceCard from './PerformanceCard'

interface PerformanceSectionProps {
  title: string
  performances: Performance[]
  variant?: 'opening' | 'standard'
  children?: ReactNode
}

export default function PerformanceSection({title, performances, variant, children}: PerformanceSectionProps) {
  const [expanded, setExpanded] = useState(false)
  const headingId = useId()
  const listId = useId()
  const visiblePerformances = expanded ? performances : performances.slice(0, 5)

  return (
    <section aria-labelledby={headingId} className="px-5 pt-8">
      <div className="flex items-center justify-between gap-4 py-5 leading-[normal]">
        <h2 id={headingId} className="text-2xl leading-[normal] font-bold">{title}</h2>
        {performances.length > 5 && (
          <button type="button" aria-expanded={expanded} aria-controls={listId}
            onClick={() => setExpanded(value => !value)} className="shrink-0 text-base leading-[normal]">
            {expanded ? '접기 ∧' : '전체 보기 >'}
          </button>
        )}
      </div>
      {children}
      <div id={listId} className={children ? 'pt-7' : undefined}>
        {visiblePerformances.length > 0 ? (
          <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-5">
            {visiblePerformances.map(performance => (
              <PerformanceCard key={performance.id} performance={performance} variant={variant} />
            ))}
          </div>
        ) : (
          <p role="status" className="py-16 text-center text-[#716f6f]">등록된 공연이 없습니다.</p>
        )}
      </div>
    </section>
  )
}
