import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import { MapPin, CreditCard, CheckCircle, AlertCircle, ChevronDown, ChevronUp } from 'lucide-react'
import toast from 'react-hot-toast'
import { createOrder } from '@/api/orderApi'
import { getMyProfile } from '@/api/userApi'
import { useCart } from '@/context/CartContext'
import { formatINR } from '@/utils/formatDate'
import type { ShippingAddress, PaymentMethod } from '@/types'

const PAYMENT_METHODS: { value: PaymentMethod; label: string; icon: string }[] = [
  { value: 'CARD',              label: 'Credit / Debit Card',    icon: '💳' },
  { value: 'UPI',               label: 'UPI',                    icon: '📱' },
  { value: 'NET_BANKING',       label: 'Net Banking',            icon: '🏦' },
  { value: 'CASH_ON_DELIVERY',  label: 'Cash on Delivery',       icon: '💵' },
  { value: 'WALLET',            label: 'Wallet',                 icon: '👛' },
]

type FormErrors = Partial<Record<keyof ShippingAddress, string>>

function validateAddress(addr: ShippingAddress): FormErrors {
  const e: FormErrors = {}
  if (!addr.fullName.trim())  e.fullName = 'Full name is required'
  if (!addr.phone.trim())     e.phone    = 'Phone number is required'
  else if (!/^\d{10}$/.test(addr.phone.trim())) e.phone = 'Enter a valid 10-digit number'
  if (!addr.street.trim())    e.street   = 'Street address is required'
  if (!addr.city.trim())      e.city     = 'City is required'
  if (!addr.state.trim())     e.state    = 'State is required'
  if (!addr.zip.trim())       e.zip      = 'PIN code is required'
  else if (!/^\d{6}$/.test(addr.zip.trim())) e.zip = 'Enter a valid 6-digit PIN code'
  return e
}

function Field({
  label, name, value, onChange, error, type = 'text', placeholder,
}: {
  label: string
  name: string
  value: string
  onChange: (v: string) => void
  error?: string
  type?: string
  placeholder?: string
}) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{label}</label>
      <input
        type={type}
        name={name}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={`w-full rounded-lg border px-3 py-2 text-sm text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-[#FF9900]/50
          ${error ? 'border-red-400' : 'border-gray-300 dark:border-gray-600'}`}
      />
      {error && <p className="text-xs text-red-500 mt-1">{error}</p>}
    </div>
  )
}

export default function CheckoutPage() {
  const navigate = useNavigate()
  const { items, subtotal, clearCart } = useCart()

  const [addressOpen, setAddressOpen] = useState(true)
  const [paymentOpen, setPaymentOpen] = useState(false)
  const [errors, setErrors] = useState<FormErrors>({})

  const [address, setAddress] = useState<ShippingAddress>({
    fullName: '', phone: '', street: '', city: '', state: '', zip: '', country: 'India',
  })
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH_ON_DELIVERY')

  const { data: profile } = useQuery({
    queryKey: ['my-profile'],
    queryFn:  getMyProfile,
    staleTime: 300_000,
  })

  const shipping = subtotal >= 500 ? 0 : 50
  const tax      = Math.round(subtotal * 0.18)
  const total    = subtotal + shipping + tax

  const { mutate: placeOrder, isPending } = useMutation({
    mutationFn: () => {
      const uid = profile?.id
      if (!uid) throw new Error('User profile not loaded')
      return createOrder({
        userId: uid,
        items:  items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
        shippingAddress: address,
        paymentMethod,
      })
    },
    onSuccess: (order) => {
      clearCart()
      navigate(`/orders/confirmation?orderId=${order.id}&orderNumber=${order.orderNumber}`)
    },
    onError: (err: Error) => {
      toast.error(err.message || 'Failed to place order')
    },
  })

  function handleAddressNext() {
    const e = validateAddress(address)
    if (Object.keys(e).length > 0) { setErrors(e); return }
    setErrors({})
    setAddressOpen(false)
    setPaymentOpen(true)
  }

  function handlePlaceOrder() {
    const e = validateAddress(address)
    if (Object.keys(e).length > 0) { setErrors(e); setAddressOpen(true); setPaymentOpen(false); return }
    placeOrder()
  }

  const setField = (key: keyof ShippingAddress) => (val: string) => {
    setAddress((a) => ({ ...a, [key]: val }))
    setErrors((e) => ({ ...e, [key]: undefined }))
  }

  if (items.length === 0) {
    return (
      <div className="max-w-[800px] mx-auto px-4 py-16 text-center">
        <AlertCircle size={48} className="mx-auto text-gray-300 mb-4" />
        <p className="text-gray-500 mb-4">Your cart is empty.</p>
        <button onClick={() => navigate('/shop')} className="bg-[#FF9900] text-[#131921] font-semibold px-6 py-2 rounded-full">
          Start Shopping
        </button>
      </div>
    )
  }

  return (
    <div className="max-w-[1100px] mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-6">Checkout</h1>

      <div className="flex flex-col lg:flex-row gap-6">
        {/* ── Left: Address + Payment ─────────────────────────────────────── */}
        <div className="flex-1 space-y-4">

          {/* Step 1: Delivery address */}
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
            <button
              onClick={() => setAddressOpen((o) => !o)}
              className="w-full flex items-center justify-between px-5 py-4 hover:bg-gray-50 dark:hover:bg-gray-750"
            >
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-full bg-[#FF9900] flex items-center justify-center text-[#131921] font-bold text-sm">1</div>
                <span className="font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                  <MapPin size={16} /> Delivery Address
                </span>
              </div>
              {addressOpen ? <ChevronUp size={16} className="text-gray-400" /> : <ChevronDown size={16} className="text-gray-400" />}
            </button>

            {addressOpen && (
              <div className="px-5 pb-5 space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Field label="Full Name" name="fullName" value={address.fullName} onChange={setField('fullName')} error={errors.fullName} placeholder="John Doe" />
                  <Field label="Phone Number" name="phone" value={address.phone} onChange={setField('phone')} error={errors.phone} type="tel" placeholder="9876543210" />
                </div>
                <Field label="Street Address" name="street" value={address.street} onChange={setField('street')} error={errors.street} placeholder="123, Main Street, Area" />
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Field label="City" name="city" value={address.city} onChange={setField('city')} error={errors.city} placeholder="Mumbai" />
                  <Field label="State" name="state" value={address.state} onChange={setField('state')} error={errors.state} placeholder="Maharashtra" />
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Field label="PIN Code" name="zip" value={address.zip} onChange={setField('zip')} error={errors.zip} placeholder="400001" />
                  <Field label="Country" name="country" value={address.country} onChange={setField('country')} placeholder="India" />
                </div>
                <button
                  onClick={handleAddressNext}
                  className="bg-[#FF9900] hover:bg-[#F3A847] text-[#131921] font-semibold px-6 py-2 rounded-full text-sm transition-colors"
                >
                  Continue to Payment
                </button>
              </div>
            )}
          </div>

          {/* Step 2: Payment method */}
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
            <button
              onClick={() => setPaymentOpen((o) => !o)}
              className="w-full flex items-center justify-between px-5 py-4 hover:bg-gray-50 dark:hover:bg-gray-750"
            >
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-full bg-[#FF9900] flex items-center justify-center text-[#131921] font-bold text-sm">2</div>
                <span className="font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                  <CreditCard size={16} /> Payment Method
                </span>
              </div>
              {paymentOpen ? <ChevronUp size={16} className="text-gray-400" /> : <ChevronDown size={16} className="text-gray-400" />}
            </button>

            {paymentOpen && (
              <div className="px-5 pb-5 space-y-2">
                {PAYMENT_METHODS.map((pm) => (
                  <label
                    key={pm.value}
                    className={`flex items-center gap-3 p-3 rounded-lg border-2 cursor-pointer transition-colors
                      ${paymentMethod === pm.value
                        ? 'border-[#FF9900] bg-[#FF9900]/5'
                        : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                      }`}
                  >
                    <input
                      type="radio"
                      value={pm.value}
                      checked={paymentMethod === pm.value}
                      onChange={() => setPaymentMethod(pm.value)}
                      className="accent-[#FF9900]"
                    />
                    <span className="text-lg">{pm.icon}</span>
                    <span className="text-sm font-medium text-gray-800 dark:text-gray-200">{pm.label}</span>
                  </label>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* ── Right: Order Summary ─────────────────────────────────────────── */}
        <div className="lg:w-80 shrink-0">
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 sticky top-24">
            <h2 className="font-bold text-gray-900 dark:text-gray-100 text-lg mb-4">Order Summary</h2>

            {/* Items */}
            <div className="space-y-2 max-h-48 overflow-y-auto mb-4">
              {items.map((item) => (
                <div key={item.productId} className="flex items-center gap-2 text-sm">
                  <div className="w-10 h-10 rounded bg-gray-100 dark:bg-gray-700 flex-shrink-0 overflow-hidden">
                    {item.thumbnailUrl
                      ? <img src={item.thumbnailUrl} alt="" className="w-full h-full object-cover" />
                      : <div className="w-full h-full flex items-center justify-center text-gray-400 text-xs">{item.productName.charAt(0)}</div>
                    }
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="truncate text-gray-800 dark:text-gray-200">{item.productName}</p>
                    <p className="text-gray-400">×{item.quantity}</p>
                  </div>
                  <span className="font-medium text-gray-900 dark:text-gray-100 shrink-0">
                    {formatINR(item.price * item.quantity)}
                  </span>
                </div>
              ))}
            </div>

            <hr className="border-gray-100 dark:border-gray-700 mb-3" />

            <dl className="space-y-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-gray-500">Subtotal</dt>
                <dd className="font-medium text-gray-900 dark:text-gray-100">{formatINR(subtotal)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-gray-500">Shipping</dt>
                <dd className={`font-medium ${shipping === 0 ? 'text-green-600' : 'text-gray-900 dark:text-gray-100'}`}>
                  {shipping === 0 ? 'FREE' : formatINR(shipping)}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-gray-500">Tax (18% GST)</dt>
                <dd className="font-medium text-gray-900 dark:text-gray-100">{formatINR(tax)}</dd>
              </div>
              <hr className="border-gray-100 dark:border-gray-700" />
              <div className="flex justify-between text-base">
                <dt className="font-bold text-gray-900 dark:text-gray-100">Total</dt>
                <dd className="font-bold text-xl text-gray-900 dark:text-gray-100">{formatINR(total)}</dd>
              </div>
            </dl>

            <button
              onClick={handlePlaceOrder}
              disabled={isPending}
              className="mt-5 w-full bg-[#FF9900] hover:bg-[#F3A847] disabled:opacity-60 text-[#131921] font-bold py-3 rounded-full transition-colors flex items-center justify-center gap-2"
            >
              {isPending ? (
                <span className="flex items-center gap-2">
                  <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  Placing order…
                </span>
              ) : (
                <>
                  <CheckCircle size={16} /> Place Order
                </>
              )}
            </button>

            <p className="text-xs text-center text-gray-400 mt-3">🔒 Safe and secure checkout</p>
          </div>
        </div>
      </div>
    </div>
  )
}
