/**
 * ShopPage — public product catalogue.
 * Left sidebar: category + price + brand + stock filters.
 * Right grid: products with sort and pagination.
 */
import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { Search, SlidersHorizontal, X, ChevronDown, ChevronUp } from 'lucide-react'
import { searchProducts, getCategories } from '@/api/productApi'
import { useDebounce } from '@/hooks/useDebounce'
import { ProductCard } from '@/components/product/ProductCard'
import { Pagination } from '@/components/ui/Pagination'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import type { ProductSearchParams } from '@/types'

const SORT_OPTIONS = [
  { label: 'Newest',       value: 'id,desc' },
  { label: 'Price: Low → High', value: 'price,asc' },
  { label: 'Price: High → Low', value: 'price,desc' },
  { label: 'Name A–Z',     value: 'name,asc' },
]

export default function ShopPage() {
  const [urlParams] = useSearchParams()

  const [search,      setSearch]      = useState(urlParams.get('name') ?? '')
  const [categoryId,  setCategoryId]  = useState<number | undefined>(
    urlParams.get('categoryId') ? Number(urlParams.get('categoryId')) : undefined,
  )
  const [minPrice,    setMinPrice]    = useState('')
  const [maxPrice,    setMaxPrice]    = useState('')
  const [brand,       setBrand]       = useState('')
  const [inStockOnly, setInStockOnly] = useState(urlParams.get('inStockOnly') === 'true')
  const [sort,        setSort]        = useState(urlParams.get('sort') ?? 'id,desc')
  const [page,        setPage]        = useState(0)
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false)
  const [priceOpen,   setPriceOpen]   = useState(true)

  const dSearch = useDebounce(search, 350)
  const dBrand  = useDebounce(brand,  350)

  // Reset page when filters change
  useEffect(() => { setPage(0) }, [dSearch, categoryId, minPrice, maxPrice, dBrand, inStockOnly])

  const params: ProductSearchParams = {
    ...(dSearch    ? { name: dSearch } : {}),
    ...(categoryId ? { categoryId }    : {}),
    ...(dBrand     ? { brand: dBrand } : {}),
    ...(minPrice && !isNaN(+minPrice) ? { minPrice: +minPrice } : {}),
    ...(maxPrice && !isNaN(+maxPrice) ? { maxPrice: +maxPrice } : {}),
    ...(inStockOnly ? { inStockOnly: true } : {}),
    page, size: 16, sort,
  }

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['shop', params],
    queryFn:  () => searchProducts(params),
    staleTime: 60_000,
    placeholderData: (prev) => prev,
  })

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn:  getCategories,
    staleTime: 300_000,
  })

  const hasFilters = !!(dSearch || categoryId || minPrice || maxPrice || dBrand || inStockOnly)

  function clearFilters() {
    setSearch(''); setCategoryId(undefined); setMinPrice(''); setMaxPrice('')
    setBrand(''); setInStockOnly(false); setPage(0)
  }

  // ── Filter panel content ──────────────────────────────────────────────────

  const FilterPanel = () => (
    <div className="space-y-5">
      {hasFilters && (
        <button onClick={clearFilters} className="flex items-center gap-1.5 text-xs text-red-500 hover:text-red-700">
          <X size={12} /> Clear all filters
        </button>
      )}

      {/* Search */}
      <div>
        <label className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2 block">
          Search
        </label>
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Product name…"
          leftAddon={<Search size={12} />}
        />
      </div>

      {/* Category */}
      <div>
        <label className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2 block">
          Category
        </label>
        <div className="space-y-1">
          <button
            onClick={() => setCategoryId(undefined)}
            className={`w-full text-left text-sm px-2 py-1 rounded ${!categoryId ? 'text-indigo-600 font-medium' : 'text-gray-600 dark:text-gray-300 hover:text-indigo-600'}`}
          >
            All Categories
          </button>
          {categories.map((cat) => (
            <button
              key={cat.id}
              onClick={() => setCategoryId(cat.id === categoryId ? undefined : cat.id)}
              className={`w-full text-left text-sm px-2 py-1 rounded flex justify-between items-center ${categoryId === cat.id ? 'text-indigo-600 font-medium' : 'text-gray-600 dark:text-gray-300 hover:text-indigo-600'}`}
            >
              <span>{cat.name}</span>
              <span className="text-xs text-gray-400">{cat.productCount}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Brand */}
      <div>
        <label className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2 block">
          Brand
        </label>
        <Input
          value={brand}
          onChange={(e) => setBrand(e.target.value)}
          placeholder="Any brand…"
        />
      </div>

      {/* Price range */}
      <div>
        <button
          onClick={() => setPriceOpen((o) => !o)}
          className="flex items-center justify-between w-full text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2"
        >
          Price Range
          {priceOpen ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
        </button>
        {priceOpen && (
          <div className="flex gap-2">
            <Input
              type="number"
              value={minPrice}
              onChange={(e) => setMinPrice(e.target.value)}
              placeholder="Min ₹"
              className="w-full"
            />
            <Input
              type="number"
              value={maxPrice}
              onChange={(e) => setMaxPrice(e.target.value)}
              placeholder="Max ₹"
              className="w-full"
            />
          </div>
        )}
      </div>

      {/* In-stock only */}
      <label className="flex items-center gap-2 cursor-pointer">
        <input
          type="checkbox"
          checked={inStockOnly}
          onChange={(e) => setInStockOnly(e.target.checked)}
          className="rounded border-gray-300 text-indigo-600"
        />
        <span className="text-sm text-gray-700 dark:text-gray-300">In stock only</span>
      </label>
    </div>
  )

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="flex gap-6 max-w-screen-xl">
      {/* Desktop sidebar */}
      <aside className="hidden lg:block w-56 shrink-0">
        <div className="sticky top-20 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
          <h2 className="font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center gap-2">
            <SlidersHorizontal size={16} /> Filters
          </h2>
          <FilterPanel />
        </div>
      </aside>

      {/* Mobile filter sheet */}
      {mobileFiltersOpen && (
        <>
          <div className="fixed inset-0 z-30 bg-black/50 lg:hidden" onClick={() => setMobileFiltersOpen(false)} />
          <aside className="fixed inset-y-0 left-0 z-40 w-72 bg-white dark:bg-gray-800 p-4 overflow-y-auto lg:hidden">
            <div className="flex justify-between items-center mb-4">
              <h2 className="font-semibold">Filters</h2>
              <button onClick={() => setMobileFiltersOpen(false)}><X size={20} /></button>
            </div>
            <FilterPanel />
          </aside>
        </>
      )}

      {/* Main content */}
      <div className="flex-1 min-w-0 space-y-4">
        {/* Toolbar */}
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-3">
            <Button
              variant="outline"
              size="sm"
              className="lg:hidden"
              onClick={() => setMobileFiltersOpen(true)}
              leftIcon={<SlidersHorizontal size={14} />}
            >
              Filters {hasFilters && <span className="ml-1 bg-indigo-600 text-white rounded-full w-4 h-4 text-xs flex items-center justify-center">!</span>}
            </Button>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {data ? `${data.totalElements.toLocaleString()} products` : '…'}
              {isFetching && !isLoading && <span className="ml-2 text-indigo-500">Updating…</span>}
            </p>
          </div>

          <select
            value={sort}
            onChange={(e) => { setSort(e.target.value); setPage(0) }}
            className="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-sm px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-indigo-500 dark:text-gray-200"
          >
            {SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        </div>

        {/* Grid */}
        {isLoading ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-4">
            {Array.from({ length: 16 }).map((_, i) => (
              <div key={i} className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden animate-pulse">
                <div className="aspect-square bg-gray-200 dark:bg-gray-700" />
                <div className="p-4 space-y-2">
                  <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded w-2/3" />
                  <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded" />
                  <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-3/4" />
                  <div className="h-8 bg-gray-200 dark:bg-gray-700 rounded mt-2" />
                </div>
              </div>
            ))}
          </div>
        ) : data?.content.length === 0 ? (
          <div className="flex flex-col items-center py-24 text-gray-400 dark:text-gray-500 gap-3">
            <Search size={40} className="opacity-40" />
            <p className="text-lg">No products found</p>
            {hasFilters && (
              <Button variant="outline" size="sm" onClick={clearFilters}>Clear filters</Button>
            )}
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-4">
            {data?.content.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        )}

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <Pagination
            page={data.number}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            pageSize={data.size}
            onPageChange={(p) => { setPage(p); window.scrollTo({ top: 0, behavior: 'smooth' }) }}
          />
        )}
      </div>
    </div>
  )
}
