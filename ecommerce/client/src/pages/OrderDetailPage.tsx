import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, MapPin, Package, XCircle, RotateCcw, Truck } from 'lucide-react'
import toast from 'react-hot-toast'
import { getOrderById, getOrderHistory, cancelOrder, requestReturn } from '@/api/orderApi'
import { OrderStatusTimeline } from '@/components/order/OrderStatusTimeline'
import { Spinner } from '@/components/ui/Spinner'
import { formatDate, formatDateTime, formatINR } from '@/utils/formatDate'
import type { OrderStatus } from '@/types'

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

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const orderId = Number(id)
  const qc = useQueryClient()

  const { data: order, isLoading, isError } = useQuery({
    queryKey: ['order', orderId],
    queryFn:  () => getOrderById(orderId),
    enabled:  !!orderId,
    staleTime: 30_000,
  })

  const { data: history = [] } = useQuery({
    queryKey: ['order-history', orderId],
    queryFn:  () => getOrderHistory(orderId),
    enabled:  !!orderId,
    staleTime: 30_000,
  })

  const { mutate: doCancel, isPending: cancelling } = useMutation({
    mutationFn: () => cancelOrder(orderId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['order', orderId] }); toast.success('Order cancelled') },
    onError: () => toast.error('Failed to cancel order'),
  })

  const { mutate: doReturn, isPending: returning } = useMutation({
    mutationFn: () => requestReturn(orderId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['order', orderId] }); toast.success('Return requested') },
    onError: () => toast.error('Failed to request return'),
  })

  if (isLoading) {
    return <div className="flex justify-center py-24"><Spinner size="lg" /></div>
  }

  if (isError || !order) {
    return (
      <div className="max-w-[800px] mx-auto px-4 py-16 text-center">
        <Package size={48} className="mx-auto text-gray-300 mb-4" />
        <p className="text-gray-500">Order not found.</p>
        <Link to="/orders" className="text-[#FF9900] hover:underline mt-4 inline-block">← Back to orders</Link>
      </div>
    )
  }

  const canCancel = ['PENDING', 'CONFIRMED', 'PROCESSING'].includes(order.status)
  const canReturn = order.status === 'DELIVERED'

  return (
    <div className="max-w-[900px] mx-auto px-4 py-8 space-y-6">
      {/* Back */}
      <Link to="/orders" className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-900 dark:hover:text-gray-100">
        <ArrowLeft size={14} /> Back to Orders
      </Link>

      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100">
            Order #{order.orderNumber}
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
            Placed on {formatDate(order.createdAt)}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className={`px-3 py-1 rounded-full text-sm font-semibold ${STATUS_STYLE[order.status]}`}>
            {order.status.replace(/_/g, ' ')}
          </span>
          {canCancel && (
            <button
              onClick={() => doCancel()}
              disabled={cancelling}
              className="flex items-center gap-1.5 text-sm text-red-600 hover:text-red-800 border border-red-300 dark:border-red-700 px-3 py-1 rounded-full disabled:opacity-50"
            >
              <XCircle size={14} /> Cancel Order
            </button>
          )}
          {canReturn && (
            <button
              onClick={() => doReturn()}
              disabled={returning}
              className="flex items-center gap-1.5 text-sm text-purple-600 hover:text-purple-800 border border-purple-300 dark:border-purple-700 px-3 py-1 rounded-full disabled:opacity-50"
            >
              <RotateCcw size={14} /> Request Return
            </button>
          )}
        </div>
      </div>

      {/* Tracking timeline */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
        <h2 className="font-semibold text-gray-900 dark:text-gray-100 mb-4">Order Tracking</h2>
        <OrderStatusTimeline
          status={order.status}
          timestamps={{
            createdAt:   order.createdAt,
            confirmedAt: order.confirmedAt,
            shippedAt:   order.shippedAt,
            deliveredAt: order.deliveredAt,
            cancelledAt: order.cancelledAt,
          }}
        />
        {order.trackingNumber && (
          <div className="mt-4 flex items-center gap-2 p-3 bg-cyan-50 dark:bg-cyan-900/20 rounded-lg text-sm">
            <Truck size={16} className="text-cyan-600 shrink-0" />
            <span className="text-gray-700 dark:text-gray-300">
              Tracking: <span className="font-mono font-semibold">{order.trackingNumber}</span> via {order.courierName}
            </span>
          </div>
        )}
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        {/* Shipping Address */}
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <h2 className="font-semibold text-gray-900 dark:text-gray-100 mb-3 flex items-center gap-2">
            <MapPin size={16} className="text-[#FF9900]" /> Delivery Address
          </h2>
          <div className="text-sm text-gray-600 dark:text-gray-300 space-y-0.5">
            <p className="font-semibold text-gray-900 dark:text-gray-100">{order.shippingAddress.fullName}</p>
            <p>{order.shippingAddress.street}</p>
            <p>{order.shippingAddress.city}, {order.shippingAddress.state} {order.shippingAddress.zip}</p>
            <p>{order.shippingAddress.country}</p>
            <p className="mt-1">📞 {order.shippingAddress.phone}</p>
          </div>
        </div>

        {/* Payment info */}
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <h2 className="font-semibold text-gray-900 dark:text-gray-100 mb-3">Payment Info</h2>
          <dl className="text-sm space-y-1.5">
            <div className="flex justify-between">
              <dt className="text-gray-500">Method</dt>
              <dd className="font-medium text-gray-900 dark:text-gray-100">{order.paymentMethod.replace(/_/g, ' ')}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Status</dt>
              <dd className={`font-semibold ${order.paymentStatus === 'PAID' ? 'text-green-600' : order.paymentStatus === 'FAILED' ? 'text-red-600' : 'text-yellow-600'}`}>
                {order.paymentStatus}
              </dd>
            </div>
            {order.paymentReference && (
              <div className="flex justify-between">
                <dt className="text-gray-500">Reference</dt>
                <dd className="font-mono text-xs text-gray-700 dark:text-gray-300">{order.paymentReference}</dd>
              </div>
            )}
          </dl>
        </div>
      </div>

      {/* Order Items */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
        <h2 className="font-semibold text-gray-900 dark:text-gray-100 px-5 py-4 border-b border-gray-100 dark:border-gray-700">
          Order Items ({order.items.length})
        </h2>
        <div className="divide-y divide-gray-100 dark:divide-gray-700">
          {order.items.map((item) => (
            <div key={item.id} className="flex items-center gap-4 px-5 py-4">
              <div className="w-14 h-14 rounded-lg bg-gray-100 dark:bg-gray-700 flex items-center justify-center shrink-0">
                <span className="text-2xl text-gray-300">{item.productName.charAt(0)}</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-medium text-gray-900 dark:text-gray-100 line-clamp-1">{item.productName}</p>
                <p className="text-xs text-gray-400 font-mono">SKU: {item.productSku}</p>
              </div>
              <div className="text-right shrink-0">
                <p className="text-sm text-gray-500">×{item.quantity}</p>
                <p className="font-semibold text-gray-900 dark:text-gray-100">{formatINR(item.totalPrice)}</p>
                <p className="text-xs text-gray-400">{formatINR(item.unitPrice)} each</p>
              </div>
            </div>
          ))}
        </div>

        {/* Price breakdown */}
        <div className="border-t border-gray-100 dark:border-gray-700 px-5 py-4">
          <dl className="space-y-1.5 text-sm max-w-xs ml-auto">
            <div className="flex justify-between">
              <dt className="text-gray-500">Subtotal</dt>
              <dd className="font-medium text-gray-900 dark:text-gray-100">{formatINR(order.subtotal)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Tax (GST)</dt>
              <dd className="font-medium text-gray-900 dark:text-gray-100">{formatINR(order.taxAmount)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Shipping</dt>
              <dd className={`font-medium ${order.shippingAmount === 0 ? 'text-green-600' : 'text-gray-900 dark:text-gray-100'}`}>
                {order.shippingAmount === 0 ? 'FREE' : formatINR(order.shippingAmount)}
              </dd>
            </div>
            {order.discountAmount > 0 && (
              <div className="flex justify-between text-green-600">
                <dt>Discount</dt>
                <dd>-{formatINR(order.discountAmount)}</dd>
              </div>
            )}
            <hr className="border-gray-200 dark:border-gray-600" />
            <div className="flex justify-between text-base font-bold text-gray-900 dark:text-gray-100">
              <dt>Total</dt>
              <dd>{formatINR(order.totalAmount)}</dd>
            </div>
          </dl>
        </div>
      </div>

      {/* Status History */}
      {history.length > 0 && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
          <h2 className="font-semibold text-gray-900 dark:text-gray-100 mb-3">Status History</h2>
          <div className="space-y-3">
            {history.map((h) => (
              <div key={h.id} className="flex items-start gap-3 text-sm">
                <div className="w-2 h-2 rounded-full bg-[#FF9900] mt-1.5 shrink-0" />
                <div className="flex-1">
                  <p className="text-gray-800 dark:text-gray-200">
                    <span className="font-medium">{h.fromStatus?.replace(/_/g, ' ') ?? '—'}</span>
                    {' → '}
                    <span className="font-semibold">{h.toStatus.replace(/_/g, ' ')}</span>
                  </p>
                  {h.reason && <p className="text-gray-500 text-xs">{h.reason}</p>}
                  <p className="text-xs text-gray-400">{formatDateTime(h.changedAt)} by {h.changedBy}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
