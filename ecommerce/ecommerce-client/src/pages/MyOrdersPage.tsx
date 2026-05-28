import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Package, ArrowRight, XCircle, RotateCcw, RefreshCw } from 'lucide-react'
import toast from 'react-hot-toast'
import { getMyOrders, cancelOrder, requestReturn } from '@/api/orderApi'
import { Spinner } from '@/components/ui/Spinner'
import { Pagination } from '@/components/ui/Pagination'
import { formatDate, formatINR } from '@/utils/formatDate'
import type { Order, OrderStatus } from '@/types'

const STATUS_STYLE: Record<OrderStatus, string> = {
  PENDING:           'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
  PAYMENT_FAILED:    'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  CONFIRMED:         'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  PROCESSING:        'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400',
  SHIPPED:           'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/30 dark:text-cyan-400',
  OUT_FOR_DELIVERY:  'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-400',
  DELIVERED:         'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  RETURN_REQUESTED:  'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  RETURNED:          'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  REFUNDED:          'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300',
  CANCELLED:         'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
}

function OrderCard({ order, onCancel, onReturn, isMutating }: {
  order: Order
  onCancel: (id: number) => void
  onReturn: (id: number) => void
  isMutating: boolean
}) {
  const canCancel  = ['PENDING', 'CONFIRMED', 'PROCESSING'].includes(order.status)
  const canReturn  = order.status === 'DELIVERED'
  const firstItems = order.items.slice(0, 2)
  const moreCount  = order.items.length - 2

  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden hover:shadow-md transition-shadow">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 bg-gray-50 dark:bg-gray-900/50 border-b border-gray-100 dark:border-gray-700">
        <div className="flex flex-wrap gap-4 text-xs text-gray-500 dark:text-gray-400">
          <span>ORDER PLACED <strong className="text-gray-700 dark:text-gray-200">{formatDate(order.createdAt)}</strong></span>
          <span>TOTAL <strong className="text-gray-700 dark:text-gray-200">{formatINR(order.totalAmount)}</strong></span>
          <span>SHIP TO <strong className="text-gray-700 dark:text-gray-200">{order.shippingAddress.fullName}</strong></span>
        </div>
        <span className="font-mono text-xs text-gray-400">#{order.orderNumber}</span>
      </div>

      {/* Body */}
      <div className="px-5 py-4 flex flex-col sm:flex-row gap-4">
        {/* Items thumbnails */}
        <div className="flex gap-2">
          {firstItems.map((item) => (
            <div key={item.id} className="w-16 h-16 rounded-lg bg-gray-100 dark:bg-gray-700 flex items-center justify-center overflow-hidden shrink-0">
              <span className="text-2xl font-light text-gray-300 dark:text-gray-600">
                {item.productName.charAt(0)}
              </span>
            </div>
          ))}
          {moreCount > 0 && (
            <div className="w-16 h-16 rounded-lg bg-gray-100 dark:bg-gray-700 flex items-center justify-center text-sm text-gray-500">
              +{moreCount}
            </div>
          )}
        </div>

        {/* Status + actions */}
        <div className="flex-1 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${STATUS_STYLE[order.status]}`}>
              {order.status.replace(/_/g, ' ')}
            </span>
            <p className="text-sm text-gray-600 dark:text-gray-300 mt-1">
              {order.items.length} {order.items.length === 1 ? 'item' : 'items'}
            </p>
            {order.trackingNumber && (
              <p className="text-xs text-gray-400 mt-0.5">
                Tracking: <span className="font-mono">{order.trackingNumber}</span> ({order.courierName})
              </p>
            )}
          </div>

          <div className="flex flex-wrap gap-2">
            <Link
              to={`/orders/${order.id}`}
              className="flex items-center gap-1.5 text-sm font-medium text-[#FF9900] hover:underline"
            >
              View Details <ArrowRight size={14} />
            </Link>
            {canCancel && (
              <button
                onClick={() => onCancel(order.id)}
                disabled={isMutating}
                className="flex items-center gap-1 text-sm text-red-600 hover:text-red-800 disabled:opacity-50"
              >
                <XCircle size={14} /> Cancel
              </button>
            )}
            {canReturn && (
              <button
                onClick={() => onReturn(order.id)}
                disabled={isMutating}
                className="flex items-center gap-1 text-sm text-purple-600 hover:text-purple-800 disabled:opacity-50"
              >
                <RotateCcw size={14} /> Return
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default function MyOrdersPage() {
  const [page, setPage] = useState(0)
  const qc = useQueryClient()

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['my-orders', page],
    queryFn:  () => getMyOrders({ page, size: 10, sort: 'createdAt,desc' }),
    staleTime: 30_000,
    placeholderData: (prev) => prev,
  })

  const { mutate: doCancel, isPending: cancelling } = useMutation({
    mutationFn: (id: number) => cancelOrder(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['my-orders'] }); toast.success('Order cancelled') },
    onError: () => toast.error('Failed to cancel order'),
  })

  const { mutate: doReturn, isPending: returning } = useMutation({
    mutationFn: (id: number) => requestReturn(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['my-orders'] }); toast.success('Return requested') },
    onError: () => toast.error('Failed to request return'),
  })

  return (
    <div className="max-w-[1000px] mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Your Orders</h1>
        <button
          onClick={() => refetch()}
          disabled={isFetching}
          className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
        >
          <RefreshCw size={14} className={isFetching ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      ) : isError ? (
        <div className="text-center py-16 text-red-500">Failed to load orders.</div>
      ) : data?.content.length === 0 ? (
        <div className="text-center py-16">
          <Package size={48} className="mx-auto text-gray-200 dark:text-gray-700 mb-4" />
          <h2 className="text-xl font-semibold text-gray-700 dark:text-gray-300 mb-2">No orders yet</h2>
          <p className="text-gray-400 mb-6">When you place orders, they'll appear here.</p>
          <Link
            to="/shop"
            className="bg-[#FF9900] hover:bg-[#F3A847] text-[#131921] font-semibold px-8 py-2.5 rounded-full transition-colors"
          >
            Start Shopping
          </Link>
        </div>
      ) : (
        <>
          <div className="space-y-4">
            {data!.content.map((order) => (
              <OrderCard
                key={order.id}
                order={order}
                onCancel={(id) => doCancel(id)}
                onReturn={(id) => doReturn(id)}
                isMutating={cancelling || returning}
              />
            ))}
          </div>

          {data!.totalPages > 1 && (
            <div className="mt-6">
              <Pagination
                page={data!.number}
                totalPages={data!.totalPages}
                totalElements={data!.totalElements}
                pageSize={data!.size}
                onPageChange={(p) => { setPage(p); window.scrollTo({ top: 0, behavior: 'smooth' }) }}
              />
            </div>
          )}
        </>
      )}
    </div>
  )
}
