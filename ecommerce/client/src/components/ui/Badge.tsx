import { cn } from '@/utils/cn'

type BadgeVariant = 'default' | 'success' | 'warning' | 'danger' | 'info' | 'admin' | 'user'

interface BadgeProps {
  children:   React.ReactNode
  variant?:   BadgeVariant
  className?: string
}

const variantClasses: Record<BadgeVariant, string> = {
  default:
    'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200',
  success:
    'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400',
  warning:
    'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-400',
  danger:
    'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400',
  info:
    'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-400',
  admin:
    'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-400',
  user:
    'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-400',
}

export function Badge({ children, variant = 'default', className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold',
        variantClasses[variant],
        className,
      )}
    >
      {children}
    </span>
  )
}

/** Maps a Spring Security role string to a Badge variant */
export function RoleBadge({ role }: { role: string }) {
  const label   = role.replace('ROLE_', '')
  const variant = label === 'ADMIN' ? 'admin' : 'user'
  return <Badge variant={variant}>{label}</Badge>
}
