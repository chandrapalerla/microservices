/**
 * ProductDetailPage — full product detail with add-to-cart.
 */
import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ShoppingCart, ArrowLeft, Minus, Plus, Tag, Package, Check } from 'lucide-react'
import { getProductById } from '@/api/productApi'
import { useCart } from '@/context/CartContext'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { formatINR } from '@/utils/formatDate'

export default function ProductDetailPage() {
  const { id }     = useParams<{ id: string }>()
  const navigate   = useNavigate()
  const [qty, setQty] = useState(1)
  const { addItem, hasItem, getQty } = useCart()

  const { data: product, isLoading, isError } = useQuery({
    queryKey: ['product', id],
    queryFn:  () => getProductById(Number(id)),
    enabled:  !!id,
    staleTime: 60_000,
  })

  if (isLoading) {
    return (
      <div className="flex justify-center items-center min-h-64">
        <Spinner size="lg" />
      </div>
    )
  }

  if (isError || !product) {
    return (
      <div className="text-center py-24">
        <Package size={48} className="mx-auto text-gray-300 dark:text-gray-600 mb-4" />
        <p className="text-gray-500">Product not found.</p>
        <Button variant="outline" size="sm" className="mt-4" onClick={() => navigate('/shop')}>
          Back to Shop
        </Button>
      </div>
    )
  }

  const inCart    = hasItem(product.id)
  const cartQty   = getQty(product.id)
  const isOOS     = product.stockStatus === 'OUT_OF_STOCK'
  const maxQty    = Math.max(1, product.stockQuantity - cartQty)
  const hasDiscount = product.originalPrice && product.originalPrice > product.price

  function handleAddToCart() {
    addItem(product, qty)
    setQty(1)
  }

  function handleBuyNow() {
    addItem(product, qty)
    navigate('/cart')
  }

  return (
    <div className="max-w-5xl space-y-6">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-gray-400">
        <Link to="/shop" className="hover:text-indigo-600 flex items-center gap-1">
          <ArrowLeft size={14} /> Shop
        </Link>
        <span>/</span>
        <span className="text-indigo-600 dark:text-indigo-400">{product.category.name}</span>
        <span>/</span>
        <span className="text-gray-700 dark:text-gray-300 truncate max-w-xs">{product.name}</span>
      </nav>

      {/* Main card */}
      <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
        <div className="grid md:grid-cols-2 gap-0">
          {/* Image */}
          <div className="bg-gray-50 dark:bg-gray-700 flex items-center justify-center min-h-72 md:min-h-96 relative">
            {product.thumbnailUrl ? (
              <img
                src={product.thumbnailUrl}
                alt={product.name}
                className="max-h-80 object-contain p-8"
              />
            ) : (
              <div className="text-8xl font-light text-gray-300 dark:text-gray-600">
                {product.name.charAt(0)}
              </div>
            )}
            {hasDiscount && (
              <div className="absolute top-4 left-4 bg-red-500 text-white text-sm font-bold px-3 py-1 rounded-full flex items-center gap-1">
                <Tag size={12} /> {product.discountPercent}% off
              </div>
            )}
          </div>

          {/* Details */}
          <div className="p-6 flex flex-col gap-4">
            {/* Brand + Category */}
            <div className="flex gap-2 flex-wrap">
              {product.brand && (
                <span className="text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 px-2 py-0.5 rounded-full">
                  {product.brand}
                </span>
              )}
              <Link
                to={`/shop?categoryId=${product.category.id}`}
                className="text-xs bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 px-2 py-0.5 rounded-full hover:bg-indigo-100 dark:hover:bg-indigo-900/50"
              >
                {product.category.name}
              </Link>
            </div>

            {/* Name */}
            <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100 leading-snug">
              {product.name}
            </h1>

            {/* SKU */}
            <p className="text-xs text-gray-400 font-mono">SKU: {product.sku}</p>

            {/* Price */}
            <div className="flex items-end gap-3">
              <span className="text-3xl font-bold text-gray-900 dark:text-gray-100">
                {formatINR(product.price)}
              </span>
              {hasDiscount && (
                <span className="text-lg text-gray-400 line-through mb-0.5">
                  {formatINR(product.originalPrice!)}
                </span>
              )}
            </div>

            {/* Stock status */}
            <div className="flex items-center gap-2">
              <span className={`w-2 h-2 rounded-full ${isOOS ? 'bg-red-500' : product.stockStatus === 'LOW_STOCK' ? 'bg-amber-500' : 'bg-green-500'}`} />
              <span className={`text-sm font-medium ${isOOS ? 'text-red-600' : product.stockStatus === 'LOW_STOCK' ? 'text-amber-600 dark:text-amber-400' : 'text-green-600 dark:text-green-400'}`}>
                {isOOS ? 'Out of Stock' : product.stockStatus === 'LOW_STOCK' ? `Only ${product.stockQuantity} left` : 'In Stock'}
              </span>
            </div>

            {/* Quantity selector */}
            {!isOOS && (
              <div className="flex items-center gap-3">
                <span className="text-sm text-gray-600 dark:text-gray-400">Qty:</span>
                <div className="flex items-center border border-gray-300 dark:border-gray-600 rounded-lg overflow-hidden">
                  <button
                    onClick={() => setQty((q) => Math.max(1, q - 1))}
                    disabled={qty <= 1}
                    className="px-3 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40 transition-colors"
                  >
                    <Minus size={14} />
                  </button>
                  <span className="px-4 py-2 text-sm font-semibold border-x border-gray-300 dark:border-gray-600 min-w-[3rem] text-center">
                    {qty}
                  </span>
                  <button
                    onClick={() => setQty((q) => Math.min(q + 1, maxQty))}
                    disabled={qty >= maxQty}
                    className="px-3 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40 transition-colors"
                  >
                    <Plus size={14} />
                  </button>
                </div>
                {inCart && (
                  <span className="text-xs text-green-600 dark:text-green-400 flex items-center gap-1">
                    <Check size={12} /> {cartQty} in cart
                  </span>
                )}
              </div>
            )}

            {/* Action buttons */}
            <div className="flex flex-col sm:flex-row gap-3 mt-2">
              <Button
                onClick={handleAddToCart}
                disabled={isOOS}
                className="flex-1"
                leftIcon={<ShoppingCart size={16} />}
              >
                {inCart ? 'Add More' : 'Add to Cart'}
              </Button>
              <Button
                variant="secondary"
                onClick={handleBuyNow}
                disabled={isOOS}
                className="flex-1"
              >
                Buy Now
              </Button>
            </div>
          </div>
        </div>

        {/* Description */}
        {product.description && (
          <div className="border-t border-gray-200 dark:border-gray-700 p-6">
            <h2 className="font-semibold text-gray-900 dark:text-gray-100 mb-2">Description</h2>
            <p className="text-sm text-gray-600 dark:text-gray-300 leading-relaxed whitespace-pre-line">
              {product.description}
            </p>
          </div>
        )}

        {/* Specs */}
        <div className="border-t border-gray-200 dark:border-gray-700 p-6">
          <h2 className="font-semibold text-gray-900 dark:text-gray-100 mb-3">Product Details</h2>
          <dl className="grid grid-cols-2 sm:grid-cols-3 gap-x-6 gap-y-2 text-sm">
            {product.brand && (
              <><dt className="text-gray-500">Brand</dt><dd className="text-gray-900 dark:text-gray-100">{product.brand}</dd></>
            )}
            <dt className="text-gray-500">Category</dt>
            <dd className="text-gray-900 dark:text-gray-100">{product.category.name}</dd>
            <dt className="text-gray-500">SKU</dt>
            <dd className="font-mono text-gray-900 dark:text-gray-100">{product.sku}</dd>
            {product.weight != null && (
              <><dt className="text-gray-500">Weight</dt><dd className="text-gray-900 dark:text-gray-100">{product.weight} kg</dd></>
            )}
          </dl>
        </div>
      </div>
    </div>
  )
}
