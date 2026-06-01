/**
 * ProductCard — Amazon-style product card for the shop grid.
 */
import { ShoppingCart, Check, Tag } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useCart } from '@/context/CartContext'
import { Button } from '@/components/ui/Button'
import type { ProductSummary } from '@/types'
import { formatINR } from '@/utils/formatDate'

interface ProductCardProps {
  product: ProductSummary
}

export function ProductCard({ product }: ProductCardProps) {
  const { addItem, hasItem, getQty } = useCart()
  const inCart = hasItem(product.id)
  const qty    = getQty(product.id)
  const isOOS  = product.stockStatus === 'OUT_OF_STOCK'

  function handleAdd(e: React.MouseEvent) {
    e.preventDefault()  // don't navigate via Link
    if (!isOOS) addItem(product)
  }

  return (
    <Link
      to={`/shop/${product.id}`}
      className="group flex flex-col rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden hover:shadow-lg hover:border-indigo-300 dark:hover:border-indigo-600 transition-all duration-200"
    >
      {/* Image */}
      <div className="relative aspect-square bg-gray-100 dark:bg-gray-700 overflow-hidden">
        {product.thumbnailUrl ? (
          <img
            src={product.thumbnailUrl}
            alt={product.name}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            onError={(e) => { (e.target as HTMLImageElement).src = '' }}
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <div className="text-gray-300 dark:text-gray-600 text-5xl font-light">
              {product.name.charAt(0).toUpperCase()}
            </div>
          </div>
        )}

        {/* Discount badge */}
        {product.discountPercent != null && product.discountPercent > 0 && (
          <div className="absolute top-2 left-2">
            <span className="bg-red-500 text-white text-xs font-bold px-2 py-0.5 rounded-full flex items-center gap-1">
              <Tag size={10} />
              {product.discountPercent}% off
            </span>
          </div>
        )}

        {/* Out-of-stock overlay */}
        {isOOS && (
          <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
            <span className="bg-white/90 text-gray-800 text-xs font-semibold px-3 py-1 rounded-full">
              Out of Stock
            </span>
          </div>
        )}
      </div>

      {/* Content */}
      <div className="flex flex-col flex-1 p-4 gap-2">
        {/* Category + brand */}
        <div className="flex items-center gap-1.5 flex-wrap">
          <span className="text-xs text-indigo-600 dark:text-indigo-400 font-medium truncate">
            {product.categoryName}
          </span>
          {product.brand && (
            <>
              <span className="text-gray-300 dark:text-gray-600">·</span>
              <span className="text-xs text-gray-400 dark:text-gray-500 truncate">{product.brand}</span>
            </>
          )}
        </div>

        {/* Name */}
        <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 line-clamp-2 leading-snug">
          {product.name}
        </h3>

        {/* Price */}
        <div className="flex items-baseline gap-2 mt-auto">
          <span className="text-lg font-bold text-gray-900 dark:text-gray-100">
            {formatINR(product.price)}
          </span>
          {product.originalPrice && product.originalPrice > product.price && (
            <span className="text-sm text-gray-400 line-through">
              {formatINR(product.originalPrice)}
            </span>
          )}
        </div>

        {/* Stock status */}
        {product.stockStatus === 'LOW_STOCK' && (
          <p className="text-xs text-amber-600 dark:text-amber-400 font-medium">
            Only {product.stockQuantity} left!
          </p>
        )}

        {/* Add to Cart */}
        <Button
          variant={inCart ? 'secondary' : 'primary'}
          size="sm"
          className="mt-1 w-full"
          disabled={isOOS}
          onClick={handleAdd}
          leftIcon={inCart ? <Check size={14} /> : <ShoppingCart size={14} />}
        >
          {inCart ? `In cart (${qty})` : isOOS ? 'Out of Stock' : 'Add to Cart'}
        </Button>
      </div>
    </Link>
  )
}
