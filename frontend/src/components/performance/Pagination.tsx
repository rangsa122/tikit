
interface PaginationProps {
  currentPage: number
  totalPages: number
  onPageChange: (page: number) => void
}

export default function Pagination({
  currentPage,
  totalPages,
  onPageChange,
}: PaginationProps) {
  if (totalPages <= 1) {
    return null
  }

  const pages = Array.from(
    {length: totalPages},
    (_, index) => index + 1,
  )

  return (
    <nav aria-label="페이지 이동" className="flex items-center justify-center gap-2 py-6">
      <button
        type="button"
        disabled={currentPage <= 1}
        onClick={() => onPageChange(currentPage - 1)}
        className="rounded-lg border px-3 py-2 disabled:cursor-not-allowed disabled:opacity-40"
      >
        이전
      </button>

      {pages.map(page => (
        <button
          key={page}
          type="button"
          aria-current={page === currentPage ? 'page' : undefined}
          onClick={() => onPageChange(page)}
          className={`rounded-lg px-3 py-2 ${
            page === currentPage
              ? 'bg-black text-white'
              : 'border hover:bg-gray-100'
          }`}
        >
          {page}
        </button>
      ))}

      <button
        type="button"
        disabled={currentPage >= totalPages}
        onClick={() => onPageChange(currentPage + 1)}
        className="rounded-lg border px-3 py-2 disabled:cursor-not-allowed disabled:opacity-40"
      >
        다음
      </button>
    </nav>
  )
}
