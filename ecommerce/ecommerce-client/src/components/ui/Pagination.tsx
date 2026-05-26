import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/utils/cn'
import { Button } from './Button'

interface PaginationProps {
  page:          number      // 0-indexed (Spring Data convention)
  totalPages:    number
  totalElements: number
  pageSize:      number
  onPageChange:  (page: number) => void
  onSizeChange?: (size: number) => void
  pageSizeOptions?: number[]
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  onSizeChange,
  pageSizeOptions = [10, 20, 50],
}: PaginationProps) {
  const start = page * pageSize + 1
  const end   = Math.min((page + 1) * pageSize, totalElements)

  /** Generate visible page numbers with ellipsis */
  function getPages(): (number | '…')[] {
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i)
    const pages: (number | '…')[] = [0]
    if (page > 2)         pages.push('…')
    for (let i = Math.max(1, page - 1); i <= Math.min(totalPages - 2, page + 1); i++) {
      pages.push(i)
    }
    if (page < totalPages - 3) pages.push('…')
    pages.push(totalPages - 1)
    return pages
  }

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-3 px-1 py-2 text-sm text-gray-600 dark:text-gray-400">
      {/* Row count */}
      <span>
        {totalElements === 0
          ? 'No results'
          : `Showing ${start}–${end} of ${totalElements} results`}
      </span>

      <div className="flex items-center gap-3">
        {/* Page-size selector */}
        {onSizeChange && (
          <div className="flex items-center gap-2">
            <span className="text-xs">Rows</span>
            <select
              value={pageSize}
              onChange={(e) => onSizeChange(Number(e.target.value))}
              className="rounded border border-gray-300 bg-white px-2 py-1 text-xs dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200"
            >
              {pageSizeOptions.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
        )}

        {/* Page buttons */}
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
            aria-label="Previous page"
            className="p-1.5"
          >
            <ChevronLeft size={16} />
          </Button>

          {getPages().map((p, idx) =>
            p === '…' ? (
              <span key={`ellipsis-${idx}`} className="px-1">…</span>
            ) : (
              <button
                key={p}
                onClick={() => onPageChange(p as number)}
                className={cn(
                  'h-8 w-8 rounded-lg text-xs font-medium transition-colors',
                  p === page
                    ? 'bg-indigo-600 text-white'
                    : 'hover:bg-gray-100 dark:hover:bg-gray-700',
                )}
              >
                {(p as number) + 1}
              </button>
            ),
          )}

          <Button
            variant="ghost"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => onPageChange(page + 1)}
            aria-label="Next page"
            className="p-1.5"
          >
            <ChevronRight size={16} />
          </Button>
        </div>
      </div>
    </div>
  )
}
