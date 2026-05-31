import { Link, useNavigate } from 'react-router-dom'
import { Minus, Plus, Trash2, ShoppingCart, ArrowRight, Tag } from 'lucide-react'
import { useCart } from '@/context/CartContext'
import { useAuth } from '@/context/AuthContext'
import { formatINR } from '@/utils/formatDate'

function CartItemRow({
  item,
  onUpdate,
  onRemove,
}: {
  item: { productId: number; productName: string; sku: string; price: number; quantity: number; thumbnailUrl?: string; maxStock: number }
  onUpdate: (qty: number) => void
  onRemove: () => void
}) {
  return (
    <div className="flex gap-4 py-5 border-b border-gray-100 dark:border-gray-700 last:border-0">
      {/* Thumbnail */}
      <div className="w-24 h-24 shrink-0 rounded-lg bg-gray-100 dark:bg-gray-700 overflow-hidden">
        {item.thumbnailUrl ? (
          <img src={item.thumbnailUrl} alt={item.productName} className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-3xl font-light text-gray-300">
            {item.productName.charAt(0)}
          </div>
        )}
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <Link
          to={`/shop/${item.productId}`}
          className="font-semibold text-gray-900 dark:text-gray-100 hover:text-[#FF9900] line-clamp-2 text-sm"
        >
          {item.productName}
        </Link>
        <p className="text-xs text-gray-400 font-mono mt-0.5">SKU: {item.sku}</p>

        <div className="flex items-center justify-between mt-3 flex-wrap gap-2">
          {/* Qty controls */}
          <div className="flex items-center border border-gray-300 dark:border-gray-600 rounded-lg overflow-hidden">
            <button
              onClick={() => onUpdate(item.quantity - 1)}
              disabled={item.quantity <= 1}
              className="px-3 py-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40 transition-colors"
            >
              <Minus size={13} />
            </button>
            <span className="px-3 py-1.5 text-sm font-semibold border-x border-gray-300 dark:border-gray-600 min-w-[2.5rem] text-center">
              {item.quantity}
            </span>
            <button
              onClick={() => onUpdate(item.quantity + 1)}
              disabled={item.quantity >= item.maxStock}
              className="px-3 py-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40 transition-colors"
            >
              <Plus size={13} />
            </button>
          </div>

          {/* Price + remove */}
          <div className="flex items-center gap-4">
            <span className="font-bold text-gray-900 dark:text-gray-100">
              {formatINR(item.price * item.quantity)}
            </span>
            <button
              onClick={onRemove}
              className="text-red-500 hover:text-red-700 p-1"
              title="Remove"
            >
              <Trash2 size={16} />
            </button>
          </div>
        </div>

        {item.quantity >= item.maxStock && (
          <p className="text-xs text-amber-600 dark:text-amber-400 mt-1">Max stock reached</p>
        )}
      </div>
    </div>
  )
}

export default function CartPage() {
  const { items, count, subtotal, updateQty, removeItem, clearCart } = useCart()
  const { user } = useAuth()
  const navigate = useNavigate()

  const shipping = subtotal >= 500 ? 0 : 50
  const tax = Math.round(subtotal * 0.18)
  const total = subtotal + shipping + tax

  if (items.length === 0) {
    return (
      <div className="max-w-[1200px] mx-auto px-4 py-16 flex flex-col items-center gap-6 text-center">
        <div className="w-24 h-24 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
          <ShoppingCart size={40} className="text-gray-300 dark:text-gray-600" />
        </div>
        <div>
          <h2 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">Your cart is empty</h2>
          <p className="text-gray-500 dark:text-gray-400">Add items to get started</p>
        </div>
        <Link
          to="/shop"
          className="bg-[#FF9900] hover:bg-[#F3A847] text-[#131921] font-semibold px-8 py-3 rounded-full transition-colors flex items-center gap-2"
        >
          Continue Shopping <ArrowRight size={16} />
        </Link>
      </div>
    )
  }

  return (
    <div className="max-w-[1200px] mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-6">
        Shopping Cart <span className="text-gray-400 text-lg font-normal">({count} {count === 1 ? 'item' : 'items'})</span>
      </h1>

      <div className="flex flex-col lg:flex-row gap-6">
        {/* ── Cart Items ──────────────────────────────────────────────────────── */}
        <div className="flex-1">
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 px-6">
            {items.map((item) => (
              <CartItemRow
                key={item.productId}
                item={item}
                onUpdate={(qty) => updateQty(item.productId, qty)}
                onRemove={() => removeItem(item.productId)}
              />
            ))}
          </div>

          <div className="mt-4 flex justify-between items-center">
            <button
              onClick={clearCart}
              className="text-sm text-red-500 hover:text-red-700 flex items-center gap-1"
            >
              <Trash2 size={14} /> Clear cart
            </button>
            <Link to="/shop" className="text-sm text-[#FF9900] hover:underline">
              Continue shopping
            </Link>
          </div>
        </div>

        {/* ── Order Summary ───────────────────────────────────────────────────── */}
        <div className="lg:w-80 shrink-0">
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 sticky top-24">
            <h2 className="font-bold text-gray-900 dark:text-gray-100 text-lg mb-4">Order Summary</h2>

            <dl className="space-y-2.5 text-sm">
              <div className="flex justify-between">
                <dt className="text-gray-500 dark:text-gray-400">Subtotal ({count} items)</dt>
                <dd className="font-medium text-gray-900 dark:text-gray-100">{formatINR(subtotal)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-gray-500 dark:text-gray-400">Shipping</dt>
                <dd className={`font-medium ${shipping === 0 ? 'text-green-600' : 'text-gray-900 dark:text-gray-100'}`}>
                  {shipping === 0 ? 'FREE' : formatINR(shipping)}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-gray-500 dark:text-gray-400">Tax (18% GST)</dt>
                <dd className="font-medium text-gray-900 dark:text-gray-100">{formatINR(tax)}</dd>
              </div>
              {shipping > 0 && (
                <p className="text-xs text-green-600 flex items-center gap-1">
                  <Tag size={12} /> Add {formatINR(500 - subtotal)} more for free shipping
                </p>
              )}
              <hr className="border-gray-200 dark:border-gray-700" />
              <div className="flex justify-between text-base">
                <dt className="font-bold text-gray-900 dark:text-gray-100">Total</dt>
                <dd className="font-bold text-gray-900 dark:text-gray-100">{formatINR(total)}</dd>
              </div>
            </dl>

            <button
              onClick={() => user ? navigate('/checkout') : navigate('/login')}
              className="mt-5 w-full bg-[#FF9900] hover:bg-[#F3A847] text-[#131921] font-bold py-3 rounded-full transition-colors flex items-center justify-center gap-2"
            >
              {user ? 'Proceed to Checkout' : 'Sign in to Checkout'} <ArrowRight size={16} />
            </button>

            <div className="mt-4 flex items-center justify-center gap-4 text-xs text-gray-400">
              <span>🔒 Secure checkout</span>
              <span>·</span>
              <span>Free returns</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
