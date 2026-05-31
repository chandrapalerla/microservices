import type { OrderStatus } from '@/types'
import { cn } from '@/utils/cn'

const config: Record<OrderStatus, { label: string; cls: string }> = {
  PENDING:           { label: 'Pending',           cls: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-400' },
  PAYMENT_FAILED:    { label: 'Payment Failed',     cls: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400' },
  CONFIRMED:         { label: 'Confirmed',          cls: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-400' },
  PROCESSING:        { label: 'Processing',         cls: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-400' },
  SHIPPED:           { label: 'Shipped',            cls: 'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/40 dark:text-cyan-400' },
  OUT_FOR_DELIVERY:  { label: 'Out for Delivery',   cls: 'bg-teal-100 text-teal-700 dark:bg-teal-900/40 dark:text-teal-400' },
  DELIVERED:         { label: 'Delivered',          cls: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400' },
  RETURN_REQUESTED:  { label: 'Return Requested',   cls: 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-400' },
  RETURNED:          { label: 'Returned',           cls: 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-400' },
  REFUNDED:          { label: 'Refunded',           cls: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300' },
  CANCELLED:         { label: 'Cancelled',          cls: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400' },
}

interface Props {
  status: OrderStatus
  size?:  'sm' | 'md'
}

export function OrderStatusBadge({ status, size = 'md' }: Props) {
  const { label, cls } = config[status] ?? { label: status, cls: 'bg-gray-100 text-gray-600' }
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full font-semibold',
        size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-0.5 text-xs',
        cls,
      )}
    >
      {label}
    </span>
  )
}
