import { cn } from '@/utils/cn'

type SpinnerSize = 'sm' | 'md' | 'lg' | 'xl'

interface SpinnerProps {
  size?:      SpinnerSize
  className?: string
  label?:     string   // accessible label
}

const sizeClasses: Record<SpinnerSize, string> = {
  sm:  'h-4 w-4 border-2',
  md:  'h-6 w-6 border-2',
  lg:  'h-8 w-8 border-[3px]',
  xl:  'h-12 w-12 border-4',
}

export function Spinner({ size = 'md', className, label = 'Loading…' }: SpinnerProps) {
  return (
    <span role="status" aria-label={label} className={cn('inline-block', className)}>
      <span
        className={cn(
          'block animate-spin rounded-full',
          'border-gray-300 border-t-indigo-600',
          'dark:border-gray-600 dark:border-t-indigo-400',
          sizeClasses[size],
        )}
      />
    </span>
  )
}

/** Full-page centered spinner used during lazy-load / auth bootstrap. */
export function PageSpinner() {
  return (
    <div className="flex h-screen items-center justify-center bg-gray-50 dark:bg-gray-950">
      <Spinner size="xl" />
    </div>
  )
}
