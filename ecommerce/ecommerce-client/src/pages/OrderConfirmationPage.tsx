import { useSearchParams, Link } from 'react-router-dom'
import { CheckCircle, Package, ArrowRight } from 'lucide-react'

export default function OrderConfirmationPage() {
  const [params] = useSearchParams()
  const orderNumber = params.get('orderNumber') ?? '—'
  const orderId     = params.get('orderId')

  return (
    <div className="max-w-[600px] mx-auto px-4 py-16 text-center">
      {/* Success animation */}
      <div className="w-24 h-24 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center mx-auto mb-6">
        <CheckCircle size={48} className="text-green-500" />
      </div>

      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">
        Order Placed Successfully!
      </h1>
      <p className="text-gray-500 dark:text-gray-400 mb-6">
        Thank you for your purchase. Your order has been received and is being processed.
      </p>

      {/* Order number badge */}
      <div className="inline-block bg-gray-100 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-4 mb-8">
        <p className="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1">Order Number</p>
        <p className="text-xl font-mono font-bold text-[#FF9900]">{orderNumber}</p>
      </div>

      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 mb-8 text-left space-y-3">
        <div className="flex items-start gap-3">
          <Package size={18} className="text-[#FF9900] mt-0.5 shrink-0" />
          <div>
            <p className="font-semibold text-gray-900 dark:text-gray-100 text-sm">What happens next?</p>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
              Our team will confirm your order and begin processing. You can track the status in My Orders.
            </p>
          </div>
        </div>
        <div className="flex items-start gap-3 pl-7">
          <div className="text-sm text-gray-600 dark:text-gray-300 space-y-1">
            <p>✅ Order confirmed → Processing → Shipped → Delivered</p>
            <p>📧 You'll receive updates at your registered email</p>
            <p>🔄 Free returns within 30 days</p>
          </div>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row gap-3 justify-center">
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
