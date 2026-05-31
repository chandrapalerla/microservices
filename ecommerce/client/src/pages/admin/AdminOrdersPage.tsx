/**
 * Admin Orders management page — view and advance order status.
 *
 * Features:
 *  - Server-side pagination with status filter
 *  - Status badge with colour map
 *  - Per-row action dropdown for valid status transitions
 *  - Ship modal requiring trackingNumber + courierName
 *  - React Query cache management
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Package,
  Truck,
  CheckCheck,
  XCircle,
  RefreshCw,
  ChevronDown,
  AlertCircle,
} from 'lucide-react'
import toast from 'react-hot-toast'

import {
  getAllOrders,
  confirmOrder,
  processOrder,
  shipOrder,
  deliverOrder,
  cancelOrder,
} from '@/api/orderApi'
import type { ShipOrderDto } from '@/api/orderApi'
import type { Order, OrderStatus } from '@/types'
import { DEFAULT_PAGE_SIZE } from '@/constants'

import { Button }           from '@/components/ui/Button'
import { Input }            from '@/components/ui/Input'
import { Spinner }          from '@/components/ui/Spinner'
import { Pagination }       from '@/components/ui/Pagination'
import { Modal }            from '@/components/ui/Modal'
import { Badge }            from '@/components/ui/Badge'
import { SkeletonTableRow }  from '@/components/ui/SkeletonRow'

// ── Status config ──────────────────────────────────────────────────────────────

const ALL_STATUSES: OrderStatus[] = [
  'PENDING',
  'PAYMENT_FAILED',
  'CONFIRMED',
  'PROCESSING',
  'SHIPPED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'RETURN_REQUESTED',
  'RETURNED',
  'REFUNDED',
  'CANCELLED',
]

type BadgeVariant = 'default' | 'success' | 'warning' | 'danger' | 'info' | 'admin' | 'user'

const STATUS_BADGE: Record<OrderStatus, BadgeVariant> = {
  PENDING:           'warning',
  PAYMENT_FAILED:    'danger',
  CONFIRMED:         'info',
  PROCESSING:        'user',      // indigo
  SHIPPED:           'info',      // cyan-ish via class override
  OUT_FOR_DELIVERY:  'default',   // teal via class override
  DELIVERED:         'success',
  RETURN_REQUESTED:  'warning',   // orange via class override
  RETURNED:          'admin',     // purple
  REFUNDED:          'default',
  CANCELLED:         'danger',
}

// Fine-grained Tailwind class overrides for statuses Badge doesn't natively map
const STATUS_CLASS: Partial<Record<OrderStatus, string>> = {
  PROCESSING:       'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-400',
  SHIPPED:          'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/40 dark:text-cyan-400',
  OUT_FOR_DELIVERY: 'bg-teal-100 text-teal-700 dark:bg-teal-900/40 dark:text-teal-400',
  RETURN_REQUESTED: 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-400',
  REFUNDED:         'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300',
}

function StatusBadge({ status }: { status: OrderStatus }) {
  return (
    <Badge variant={STATUS_BADGE[status]} className={STATUS_CLASS[status]}>
      {status.replace(/_/g, ' ')}
    </Badge>
  )
}

// ── Terminal status check ──────────────────────────────────────────────────────

const TERMINAL_STATUSES: Set<OrderStatus> = new Set([
  'DELIVERED',
  'RETURNED',
  'REFUNDED',
  'CANCELLED',
  'PAYMENT_FAILED',
])

function isTerminal(status: OrderStatus): boolean {
  return TERMINAL_STATUSES.has(status)
}

// ── Ship modal form ────────────────────────────────────────────────────────────

interface ShipFormErrors {
  trackingNumber?: string
  courierName?:    string
}

function validateShipForm(dto: ShipOrderDto): ShipFormErrors {
  const errors: ShipFormErrors = {}
  if (!dto.trackingNumber.trim()) errors.trackingNumber = 'Tracking number is required'
  if (!dto.courierName.trim())    errors.courierName    = 'Courier name is required'
  return errors
}

interface ShipFormProps {
  onSubmit:    (dto: ShipOrderDto) => void
  onCancel:    () => void
  isLoading:   boolean
}

function ShipForm({ onSubmit, onCancel, isLoading }: ShipFormProps) {
  const [trackingNumber, setTrackingNumber] = useState('')
  const [courierName,    setCourierName]    = useState('')
  const [errors,         setErrors]         = useState<ShipFormErrors>({})

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const dto: ShipOrderDto = {
      trackingNumber: trackingNumber.trim(),
      courierName:    courierName.trim(),
    }
    const errs = validateShipForm(dto)
    if (Object.keys(errs).length > 0) { setErrors(errs); return }
    onSubmit(dto)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Input
        label="Tracking Number"
        value={trackingNumber}
        onChange={(e) => { setTrackingNumber(e.target.value); setErrors((p) => ({ ...p, trackingNumber: undefined })) }}
        error={errors.trackingNumber}
        placeholder="1Z999AA10123456784"
        autoFocus
      />
      <Input
        label="Courier Name"
        value={courierName}
        onChange={(e) => { setCourierName(e.target.value); setErrors((p) => ({ ...p, courierName: undefined })) }}
        error={errors.courierName}
        placeholder="FedEx"
      />
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="outline" type="button" onClick={onCancel} disabled={isLoading}>
          Cancel
        </Button>
        <Button type="submit" isLoading={isLoading} leftIcon={<Truck size={14} />}>
          Mark as Shipped
        </Button>
      </div>
    </form>
  )
}

// ── Row action dropdown ────────────────────────────────────────────────────────

interface RowActionsProps {
  order:         Order
  onConfirm:     (id: number) => void
  onProcess:     (id: number) => void
  onShip:        (order: Order) => void
  onDeliver:     (id: number) => void
  onCancel:      (id: number) => void
  isMutating:    boolean
}

function RowActions({
  order,
  onConfirm,
  onProcess,
  onShip,
  onDeliver,
  onCancel,
  isMutating,
}: RowActionsProps) {
  const [open, setOpen] = useState(false)

  if (isTerminal(order.status)) return null

  return (
    <div className="relative inline-block text-left">
      <Button
        variant="outline"
        size="sm"
        onClick={() => setOpen((o) => !o)}
        disabled={isMutating}
        rightIcon={<ChevronDown size={12} />}
      >
        Actions
      </Button>

      {open && (
        <>
          {/* Backdrop to close on outside click */}
          <div
            className="fixed inset-0 z-10"
            onClick={() => setOpen(false)}
            aria-hidden="true"
          />
          <div className="absolute right-0 z-20 mt-1 w-44 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-lg py-1">
            {order.status === 'PENDING' && (
              <button
                className="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center gap-2 text-blue-600 dark:text-blue-400"
                onClick={() => { setOpen(false); onConfirm(order.id) }}
              >
                <CheckCheck size={14} />
                Confirm Order
              </button>
            )}
            {order.status === 'CONFIRMED' && (
              <button
                className="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center gap-2 text-indigo-600 dark:text-indigo-400"
                onClick={() => { setOpen(false); onProcess(order.id) }}
              >
                <Package size={14} />
                Process Order
              </button>
            )}
            {order.status === 'PROCESSING' && (
              <button
                className="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center gap-2 text-cyan-600 dark:text-cyan-400"
                onClick={() => { setOpen(false); onShip(order) }}
              >
                <Truck size={14} />
                Ship Order
              </button>
            )}
            {order.status === 'SHIPPED' && (
              <button
                className="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center gap-2 text-green-600 dark:text-green-400"
                onClick={() => { setOpen(false); onDeliver(order.id) }}
              >
                <CheckCheck size={14} />
                Mark Delivered
              </button>
            )}
            {/* Cancel — available for any non-terminal status */}
            <button
              className="w-full px-4 py-2 text-left text-sm hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center gap-2 text-red-600 dark:text-red-400"
              onClick={() => { setOpen(false); onCancel(order.id) }}
            >
              <XCircle size={14} />
              Cancel Order
            </button>
          </div>
        </>
      )}
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function AdminOrdersPage() {
  const qc = useQueryClient()

  // ── State ──────────────────────────────────────────────────────────────────
  const [page,         setPage]         = useState(0)
  const [size,         setSize]         = useState(DEFAULT_PAGE_SIZE)
  const [statusFilter, setStatusFilter] = useState<OrderStatus | ''>('')

  // Ship modal
  const [shipOrder_, setShipOrder_] = useState<Order | null>(null)

  // ── Query ──────────────────────────────────────────────────────────────────
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey:  ['admin-orders', page, size, statusFilter],
    queryFn:   () =>
      getAllOrders({
        page,
        size,
        sort: 'createdAt,desc',
        ...(statusFilter ? { status: statusFilter } : {}),
      }),
    staleTime: 15_000,
    placeholderData: (prev) => prev,
  })

  // ── Mutations ──────────────────────────────────────────────────────────────
  function invalidate() {
    qc.invalidateQueries({ queryKey: ['admin-orders'] })
  }

  const { mutate: doConfirm, isPending: confirming } = useMutation({
    mutationFn: (id: number) => confirmOrder(id),
    onSuccess: () => { invalidate(); toast.success('Order confirmed') },
    onError:   () => toast.error('Failed to confirm order'),
  })

  const { mutate: doProcess, isPending: processing } = useMutation({
    mutationFn: (id: number) => processOrder(id),
    onSuccess: () => { invalidate(); toast.success('Order moved to Processing') },
    onError:   () => toast.error('Failed to process order'),
  })

  const { mutate: doShip, isPending: shipping } = useMutation({
    mutationFn: ({ id, dto }: { id: number; dto: ShipOrderDto }) => shipOrder(id, dto),
    onSuccess: () => { invalidate(); setShipOrder_(null); toast.success('Order shipped') },
    onError:   () => toast.error('Failed to ship order'),
  })

  const { mutate: doDeliver, isPending: delivering } = useMutation({
    mutationFn: (id: number) => deliverOrder(id),
    onSuccess: () => { invalidate(); toast.success('Order marked as delivered') },
    onError:   () => toast.error('Failed to update order'),
  })

  const { mutate: doCancel, isPending: cancelling } = useMutation({
    mutationFn: (id: number) => cancelOrder(id),
    onSuccess: () => { invalidate(); toast.success('Order cancelled') },
    onError:   () => toast.error('Failed to cancel order'),
  })

  const isMutating = confirming || processing || shipping || delivering || cancelling

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-4 max-w-7xl">
      {/* Toolbar */}
      <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-center justify-between">
        <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Orders</h1>
        <div className="flex flex-wrap gap-2 items-center">
          {/* Status filter */}
          <select
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value as OrderStatus | '')
              setPage(0)
            }}
            className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-900 shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 dark:bg-gray-800 dark:text-gray-100 dark:border-gray-600"
          >
            <option value="">All Statuses</option>
            {ALL_STATUSES.map((s) => (
              <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
            ))}
          </select>

          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            isLoading={isFetching && !isLoading}
            leftIcon={<RefreshCw size={14} />}
          >
            Refresh
          </Button>
        </div>
      </div>

      {/* Table card */}
      <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
        {isError && (
          <div className="flex items-center gap-2 px-4 py-3 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm border-b border-red-200 dark:border-red-800">
            <AlertCircle size={14} />
            Failed to load orders: {String((error as Error)?.message ?? error)}
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50">
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide">
                  Order #
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide">
                  Customer
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide">
                  Status
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide">
                  Payment
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-28">
                  Total
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-32">
                  Date
                </th>
                <th className="px-4 py-3 text-right font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-36">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
              {isLoading &&
                Array.from({ length: size }).map((_, i) => (
                  <SkeletonTableRow key={i} cols={7} />
                ))}

              {!isLoading &&
                (data?.content ?? []).map((order) => (
                  <tr
                    key={order.id}
                    className="hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors"
                  >
                    <td className="px-4 py-3 font-mono text-xs font-medium text-indigo-600 dark:text-indigo-400">
                      {order.orderNumber}
                    </td>
                    <td className="px-4 py-3 text-gray-600 dark:text-gray-300 truncate max-w-[180px]">
                      {order.userEmail}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={order.status} />
                    </td>
                    <td className="px-4 py-3 text-gray-600 dark:text-gray-300">
                      {order.paymentStatus}
                    </td>
                    <td className="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
                      ₹{order.totalAmount.toLocaleString('en-IN')}
                    </td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400 text-xs">
                      {new Date(order.createdAt).toLocaleDateString('en-IN', {
                        day: '2-digit', month: 'short', year: 'numeric',
                      })}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <RowActions
                        order={order}
                        onConfirm={(id) => doConfirm(id)}
                        onProcess={(id) => doProcess(id)}
                        onShip={(o)   => setShipOrder_(o)}
                        onDeliver={(id) => doDeliver(id)}
                        onCancel={(id) => doCancel(id)}
                        isMutating={isMutating}
                      />
                    </td>
                  </tr>
                ))}

              {!isLoading && (data?.content ?? []).length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-12 text-center">
                    <div className="flex flex-col items-center gap-2 text-gray-400 dark:text-gray-500">
                      {isFetching ? (
                        <Spinner size="md" />
                      ) : (
                        <>
                          <Package size={24} />
                          <p className="text-sm">
                            {statusFilter
                              ? `No orders with status "${statusFilter.replace(/_/g, ' ')}"`
                              : 'No orders found'}
                          </p>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {data && !isLoading && (
          <div className="border-t border-gray-100 dark:border-gray-700 px-4 py-3">
            <Pagination
              page={data.number}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              pageSize={data.size}
              onPageChange={(p) => { setPage(p); window.scrollTo({ top: 0, behavior: 'smooth' }) }}
              onSizeChange={(s) => { setSize(s); setPage(0) }}
            />
          </div>
        )}
      </div>

      {/* ── Ship modal ── */}
      <Modal
        open={shipOrder_ !== null}
        onClose={() => setShipOrder_(null)}
        title={`Ship Order ${shipOrder_?.orderNumber ?? ''}`}
        size="md"
      >
        {shipOrder_ && (
          <ShipForm
            onSubmit={(dto) => doShip({ id: shipOrder_.id, dto })}
            onCancel={() => setShipOrder_(null)}
            isLoading={shipping}
          />
        )}
      </Modal>
    </div>
  )
}
