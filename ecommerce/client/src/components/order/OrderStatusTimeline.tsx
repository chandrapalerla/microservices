import { Check, Clock } from 'lucide-react'
import type { OrderStatus } from '@/types'
import { formatDateTime } from '@/utils/formatDate'

const STEPS: { status: OrderStatus; label: string }[] = [
  { status: 'PENDING',          label: 'Order Placed' },
  { status: 'CONFIRMED',        label: 'Confirmed' },
  { status: 'PROCESSING',       label: 'Processing' },
  { status: 'SHIPPED',          label: 'Shipped' },
  { status: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { status: 'DELIVERED',        label: 'Delivered' },
]

const CANCELLED_STATUSES = new Set<OrderStatus>([
  'CANCELLED', 'PAYMENT_FAILED', 'RETURN_REQUESTED', 'RETURNED', 'REFUNDED',
])

function stepIndex(status: OrderStatus): number {
  const idx = STEPS.findIndex((s) => s.status === status)
  return idx === -1 ? (CANCELLED_STATUSES.has(status) ? -1 : 0) : idx
}

interface Props {
  status: OrderStatus
  timestamps?: {
    createdAt?: string
    confirmedAt?: string
    shippedAt?: string
    deliveredAt?: string
    cancelledAt?: string
  }
}

export function OrderStatusTimeline({ status, timestamps }: Props) {
  const isCancelled = CANCELLED_STATUSES.has(status)
  const current = stepIndex(status)

  if (isCancelled) {
    return (
      <div className="flex items-center gap-3 p-4 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
        <div className="w-8 h-8 rounded-full bg-red-500 flex items-center justify-center shrink-0">
          <Clock size={16} className="text-white" />
        </div>
        <div>
          <p className="font-semibold text-red-700 dark:text-red-400">
            Order {status.replace(/_/g, ' ')}
          </p>
          {timestamps?.cancelledAt && (
            <p className="text-xs text-red-500">{formatDateTime(timestamps.cancelledAt)}</p>
          )}
        </div>
      </div>
    )
  }

  const stepTimestamps: (string | undefined)[] = [
    timestamps?.createdAt,
    timestamps?.confirmedAt,
    undefined,
    timestamps?.shippedAt,
    undefined,
    timestamps?.deliveredAt,
  ]

  return (
    <div className="flex items-start gap-0">
      {STEPS.map((step, idx) => {
        const done = idx <= current
        const active = idx === current
        return (
          <div key={step.status} className="flex-1 flex flex-col items-center">
            {/* Connector + circle row */}
            <div className="flex items-center w-full">
              {/* Left line */}
              <div className={`flex-1 h-1 ${idx === 0 ? 'invisible' : done ? 'bg-[#FF9900]' : 'bg-gray-200 dark:bg-gray-700'}`} />
              {/* Circle */}
              <div
                className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 border-2 transition-colors
                  ${done
                    ? 'bg-[#FF9900] border-[#FF9900]'
                    : 'bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600'
                  } ${active ? 'ring-2 ring-[#FF9900]/30' : ''}`}
              >
                {done ? (
                  <Check size={14} className="text-white" strokeWidth={3} />
                ) : (
                  <span className="text-xs text-gray-400">{idx + 1}</span>
                )}
              </div>
              {/* Right line */}
              <div className={`flex-1 h-1 ${idx === STEPS.length - 1 ? 'invisible' : done && idx < current ? 'bg-[#FF9900]' : 'bg-gray-200 dark:bg-gray-700'}`} />
            </div>
            {/* Label */}
            <div className="mt-2 text-center px-1">
              <p className={`text-xs font-medium ${active ? 'text-[#FF9900]' : done ? 'text-gray-700 dark:text-gray-200' : 'text-gray-400 dark:text-gray-500'}`}>
                {step.label}
              </p>
              {stepTimestamps[idx] && (
                <p className="text-xs text-gray-400 mt-0.5 hidden sm:block">
                  {formatDateTime(stepTimestamps[idx]!)}
                </p>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}
