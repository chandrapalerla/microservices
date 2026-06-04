import { useSearchParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle, Package, ArrowRight, Clock, AlertCircle, RefreshCw } from 'lucide-react'
import { getPaymentByOrderId, retryPayment } from '@/api/paymentApi'
import type { Payment } from '@/types'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'

function PaymentStatusBadge({ status }: { status: Payment['status'] }) {
  const map: Record<Payment['status'], { label: string; cls: string; icon: React.ReactNode }> = {
    PENDING:      { label: 'Payment Pending',    cls: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400', icon: <Clock size={14} /> },
    PROCESSING:   { label: 'Processing Payment', cls: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',   icon: <RefreshCw size={14} className="animate-spin" /> },
    COMPLETED:    { label: 'Payment Successful', cls: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400', icon: <CheckCircle size={14} /> },
    FAILED:       { label: 'Payment Failed',     cls: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',       icon: <AlertCircle size={14} /> },
    REFUNDED:     { label: 'Refunded',           cls: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400', icon: <CheckCircle size={14} /> },
    REFUND_FAILED:{ label: 'Refund Failed',      cls: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',       icon: <AlertCircle size={14} /> },
  }
  const { label, cls, icon } = map[status] ?? map.PENDING
  return (
    <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-medium ${cls}`}>
      {icon} {label}
    </span>
  )
}

export default function OrderConfirmationPage() {
  const [params] = useSearchParams()
  const orderNumber = params.get('orderNumber') ?? '—'
  const orderId     = params.get('orderId') ? Number(params.get('orderId')) : null
  const queryClient = useQueryClient()

  // Poll payment status every 4 seconds until terminal state
  const { data: payment } = useQuery({
    queryKey: ['payment', orderId],
    queryFn:  () => getPaymentByOrderId(orderId!),
    enabled:  orderId !== null,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (status === 'COMPLETED' || status === 'FAILED' || status === 'REFUNDED') return false
      return 4_000
    },
    retry: 3,
  })

  const { mutate: retry, isPending: isRetrying } = useMutation({
    mutationFn: () => retryPayment(orderId!),
    onSuccess: (updated) => {
      queryClient.setQueryData(['payment', orderId], updated)
      toast.success('Payment retry initiated')
    },
    onError: () => toast.error('Retry failed — please try again'),
  })

  const isPending    = !payment || payment.status === 'PENDING' || payment.status === 'PROCESSING'
  const isFailed     = payment?.status === 'FAILED'
  const isSuccessful = payment?.status === 'COMPLETED'

  return (
    <div className="max-w-[600px] mx-auto px-4 py-16 text-center">
      {/* Icon */}
      <div className={`w-24 h-24 rounded-full flex items-center justify-center mx-auto mb-6
        ${isFailed
          ? 'bg-red-100 dark:bg-red-900/30'
          : 'bg-green-100 dark:bg-green-900/30'}`}>
        {isFailed
          ? <AlertCircle size={48} className="text-red-500" />
          : <CheckCircle  size={48} className="text-green-500" />}
      </div>

      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">
        {isFailed ? 'Payment Failed' : 'Order Placed Successfully!'}
      </h1>
      <p className="text-gray-500 dark:text-gray-400 mb-4">
        {isFailed
          ? 'Your order is reserved but payment could not be processed.'
          : 'Thank you for your purchase. Your order has been received.'}
      </p>

      {/* Order number */}
      <div className="inline-block bg-gray-100 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-4 mb-6">
        <p className="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1">Order Number</p>
        <p className="text-xl font-mono font-bold text-[#FF9900]">{orderNumber}</p>
      </div>

      {/* Payment status card */}
      {orderId && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 mb-6 text-left">
          <p className="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">Payment Status</p>

          {isPending && !payment && (
            <div className="flex items-center gap-2 text-sm text-gray-500">
              <RefreshCw size={14} className="animate-spin" /> Checking payment status…
            </div>
          )}

          {payment && (
            <div className="space-y-3">
              <PaymentStatusBadge status={payment.status} />

              <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm mt-2">
                <span className="text-gray-500">Method</span>
                <span className="font-medium text-gray-800 dark:text-gray-200">
                  {payment.paymentMethod.replace(/_/g, ' ')}
                </span>
                <span className="text-gray-500">Amount</span>
                <span className="font-medium text-gray-800 dark:text-gray-200">
                  ₹{payment.amount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                </span>
                {payment.gatewayProvider && (
                  <>
                    <span className="text-gray-500">Gateway</span>
                    <span className="font-medium text-gray-800 dark:text-gray-200">{payment.gatewayProvider}</span>
                  </>
                )}
                {payment.gatewayTxnId && (
                  <>
                    <span className="text-gray-500">Txn ID</span>
                    <span className="font-mono text-xs text-gray-600 dark:text-gray-400 truncate">{payment.gatewayTxnId}</span>
                  </>
                )}
              </div>

              {isFailed && payment.failureReason && (
                <div className="mt-2 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 px-3 py-2 text-xs text-red-600 dark:text-red-400">
                  {payment.failureReason}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* What happens next */}
      {!isFailed && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 mb-8 text-left">
          <div className="flex items-start gap-3">
            <Package size={18} className="text-[#FF9900] mt-0.5 shrink-0" />
            <div>
              <p className="font-semibold text-gray-900 dark:text-gray-100 text-sm">What happens next?</p>
              <div className="text-sm text-gray-600 dark:text-gray-300 space-y-1 mt-2">
                {isSuccessful
                  ? <><p>✅ Payment confirmed → Processing → Shipped → Delivered</p>
                     <p>📧 Updates sent to your registered email</p>
                     <p>🔄 Free returns within 30 days</p></>
                  : <><p>⏳ Your payment is being processed by {payment?.paymentMethod?.replace(/_/g,' ') ?? 'your bank'}</p>
                     <p>📧 You will be notified once confirmed</p>
                     <p>🔄 Order ships after payment is confirmed</p></>}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="flex flex-col sm:flex-row gap-3 justify-center">
        {isFailed && orderId && (
          <button
            onClick={() => retry()}
            disabled={isRetrying}
            className="bg-red-600 hover:bg-red-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-full transition-colors flex items-center justify-center gap-2"
          >
            {isRetrying ? <RefreshCw size={16} className="animate-spin" /> : <RefreshCw size={16} />}
            Retry Payment
          </button>
        )}
        {orderId && (
          <Link
            to={`/orders/${orderId}`}
            className="bg-[#FF9900] hover:bg-[#F3A847] text-[#131921] font-semibold px-6 py-2.5 rounded-full transition-colors flex items-center justify-center gap-2"
          >
            Track Order <ArrowRight size={16} />
          </Link>
        )}
        <Link
          to="/orders"
          className="border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 font-semibold px-6 py-2.5 rounded-full hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
        >
          My Orders
        </Link>
        <Link
          to="/"
          className="border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 font-semibold px-6 py-2.5 rounded-full hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
        >
          Continue Shopping
        </Link>
      </div>
    </div>
  )
}
