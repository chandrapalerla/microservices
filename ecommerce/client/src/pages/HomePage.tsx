import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Zap, Shield, Truck, RotateCcw } from 'lucide-react'
import { searchProducts, getCategories } from '@/api/productApi'
import { ProductCard } from '@/components/product/ProductCard'

function CategorySkeleton() {
  return (
    <div className="rounded-lg bg-white dark:bg-gray-800 p-4 animate-pulse">
      <div className="aspect-square bg-gray-200 dark:bg-gray-700 rounded-lg mb-3" />
      <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-2/3 mx-auto" />
    </div>
  )
}

export default function HomePage() {
  const navigate = useNavigate()

  const { data: categories = [], isLoading: catsLoading } = useQuery({
    queryKey: ['categories'],
    queryFn:  getCategories,
    staleTime: 300_000,
  })

  const { data: featuredPage, isLoading: featuredLoading } = useQuery({
    queryKey: ['featured-products'],
    queryFn:  () => searchProducts({ page: 0, size: 8, sort: 'id,desc' }),
    staleTime: 60_000,
  })

  const { data: dealsPage } = useQuery({
    queryKey: ['deal-products'],
    queryFn:  () => searchProducts({ page: 0, size: 8, sort: 'price,asc', inStockOnly: true }),
    staleTime: 60_000,
  })

  const featuredProducts = featuredPage?.content ?? []
  const dealProducts = (dealsPage?.content ?? []).filter(
    (p) => p.originalPrice && p.originalPrice > p.price,
  ).slice(0, 8)

  const CATEGORY_COLORS = [
    'from-blue-500 to-blue-600',
    'from-purple-500 to-purple-600',
    'from-green-500 to-green-600',
    'from-orange-500 to-orange-600',
    'from-pink-500 to-pink-600',
    'from-teal-500 to-teal-600',
    'from-red-500 to-red-600',
    'from-indigo-500 to-indigo-600',
  ]

  return (
    <div>
      {/* ── Hero Banner ──────────────────────────────────────────────────────── */}
      <div className="relative bg-gradient-to-br from-[#131921] via-[#232f3e] to-[#37475a] text-white overflow-hidden">
        <div className="absolute inset-0 opacity-10">
          {Array.from({ length: 20 }).map((_, i) => (
            <div
              key={i}
              className="absolute rounded-full bg-white"
              style={{
                width: `${Math.random() * 200 + 50}px`,
                height: `${Math.random() * 200 + 50}px`,
                top: `${Math.random() * 100}%`,
                left: `${Math.random() * 100}%`,
                opacity: Math.random() * 0.3,
              }}
            />
          ))}
        </div>
        <div className="relative max-w-[1200px] mx-auto px-6 py-16 md:py-24 text-center">
          <div className="inline-block bg-[#FF9900] text-[#131921] text-xs font-bold px-3 py-1 rounded-full mb-4 uppercase tracking-wide">
            New Arrivals
          </div>
          <h1 className="text-3xl md:text-5xl font-bold mb-4 leading-tight">
            Everything You Need,<br />
            <span className="text-[#FF9900]">Delivered Fast</span>
          </h1>
          <p className="text-gray-300 text-base md:text-lg mb-8 max-w-xl mx-auto">
            Discover millions of products across every category. Best prices, trusted sellers, fast delivery.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <button
              onClick={() => navigate('/shop')}
              className="bg-[#FF9900] hover:bg-[#F3A847] text-[#131921] font-bold px-8 py-3 rounded-full transition-colors flex items-center justify-center gap-2"
            >
              Shop Now <ArrowRight size={18} />
            </button>
            <button
              onClick={() => navigate('/shop?inStockOnly=true&sort=price,asc')}
              className="border-2 border-white/30 hover:border-white text-white font-semibold px-8 py-3 rounded-full transition-colors"
            >
              Today's Deals
            </button>
          </div>
        </div>
      </div>

      {/* ── Trust badges ─────────────────────────────────────────────────────── */}
      <div className="bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800">
        <div className="max-w-[1200px] mx-auto px-6 py-4 grid grid-cols-2 md:grid-cols-4 gap-4">
          {[
            { icon: Truck,      label: 'Free Delivery',  sub: 'On orders over ₹500' },
            { icon: RotateCcw,  label: 'Easy Returns',   sub: '30-day return policy' },
            { icon: Shield,     label: 'Secure Payment', sub: '100% safe & secure' },
            { icon: Zap,        label: 'Fast Delivery',  sub: '2-5 business days' },
          ].map(({ icon: Icon, label, sub }) => (
            <div key={label} className="flex items-center gap-3">
              <div className="bg-[#FF9900]/10 p-2.5 rounded-lg shrink-0">
                <Icon size={20} className="text-[#FF9900]" />
              </div>
              <div>
                <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">{label}</p>
                <p className="text-xs text-gray-500 dark:text-gray-400">{sub}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="max-w-[1200px] mx-auto px-4 py-8 space-y-12">

        {/* ── Shop by Category ─────────────────────────────────────────────── */}
        <section>
          <div className="flex items-center justify-between mb-5">
            <h2 className="text-xl font-bold text-gray-900 dark:text-gray-100">Shop by Category</h2>
            <Link to="/shop" className="text-sm text-[#FF9900] hover:underline flex items-center gap-1">
              See all <ArrowRight size={14} />
            </Link>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
            {catsLoading
              ? Array.from({ length: 6 }).map((_, i) => <CategorySkeleton key={i} />)
              : categories.slice(0, 12).map((cat, idx) => (
                <Link
                  key={cat.id}
                  to={`/shop?categoryId=${cat.id}`}
                  className="group rounded-xl bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 p-4 hover:shadow-md hover:border-[#FF9900]/40 transition-all text-center"
                >
                  <div className={`aspect-square rounded-lg bg-gradient-to-br ${CATEGORY_COLORS[idx % CATEGORY_COLORS.length]} flex items-center justify-center mb-3`}>
                    <span className="text-3xl font-bold text-white opacity-60">
                      {cat.name.charAt(0)}
                    </span>
                  </div>
                  <p className="text-sm font-semibold text-gray-800 dark:text-gray-200 group-hover:text-[#FF9900] transition-colors line-clamp-1">
                    {cat.name}
                  </p>
                  {cat.productCount > 0 && (
                    <p className="text-xs text-gray-400 mt-0.5">{cat.productCount} items</p>
                  )}
                </Link>
              ))
            }
          </div>
        </section>

        {/* ── Today's Deals ────────────────────────────────────────────────── */}
        {(dealProducts.length > 0) && (
          <section>
            <div className="flex items-center justify-between mb-5">
              <div className="flex items-center gap-2">
                <Zap size={20} className="text-[#FF9900]" />
                <h2 className="text-xl font-bold text-gray-900 dark:text-gray-100">Today's Deals</h2>
              </div>
              <Link to="/shop?sort=price,asc" className="text-sm text-[#FF9900] hover:underline flex items-center gap-1">
                See all deals <ArrowRight size={14} />
              </Link>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
              {dealProducts.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
          </section>
        )}

        {/* ── New Arrivals ─────────────────────────────────────────────────── */}
        <section>
          <div className="flex items-center justify-between mb-5">
            <h2 className="text-xl font-bold text-gray-900 dark:text-gray-100">New Arrivals</h2>
            <Link to="/shop?sort=id,desc" className="text-sm text-[#FF9900] hover:underline flex items-center gap-1">
              View all <ArrowRight size={14} />
            </Link>
          </div>
          {featuredLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
              {Array.from({ length: 8 }).map((_, i) => (
                <div key={i} className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 animate-pulse">
                  <div className="aspect-square bg-gray-200 dark:bg-gray-700 rounded-t-xl" />
                  <div className="p-4 space-y-2">
                    <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded w-2/3" />
                    <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded" />
                    <div className="h-8 bg-gray-200 dark:bg-gray-700 rounded mt-2" />
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
              {featuredProducts.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
          )}
        </section>

        {/* ── Promo banner ─────────────────────────────────────────────────── */}
        <section className="rounded-2xl bg-gradient-to-r from-[#FF9900] to-[#F3A847] p-8 text-[#131921] flex flex-col md:flex-row items-center justify-between gap-6">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide opacity-70 mb-1">Limited Time Offer</p>
            <h3 className="text-2xl md:text-3xl font-bold mb-2">Free Shipping on All Orders</h3>
            <p className="text-sm opacity-80">On orders above ₹500 — No promo code needed</p>
          </div>
          <button
            onClick={() => navigate('/shop')}
            className="bg-[#131921] text-white font-semibold px-8 py-3 rounded-full hover:bg-[#232f3e] transition-colors shrink-0 flex items-center gap-2"
          >
            Shop Now <ArrowRight size={16} />
          </button>
        </section>

      </div>
    </div>
  )
}
