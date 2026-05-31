/**
 * Lightweight className utility — merges conditional class strings without
 * pulling in a full clsx/twMerge dependency.
 *
 * Usage:
 *   cn('base', isActive && 'text-blue-500', isDark ? 'bg-gray-900' : 'bg-white')
 */
export function cn(...classes: (string | boolean | undefined | null)[]): string {
  return classes.filter(Boolean).join(' ')
}
