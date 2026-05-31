/**
 * Admin Products management page — full CRUD.
 *
 * Features:
 *  - Server-side paginated list with status filter
 *  - Create / Edit modal with all product fields
 *  - Quick actions: Update Stock, Change Status, Soft Delete
 *  - Stock/product status badges
 *  - React Query cache management
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus,
  Pencil,
  Trash2,
  RefreshCw,
  Package,
  AlertTriangle,
  BarChart2,
  AlertCircle,
} from 'lucide-react'
import toast from 'react-hot-toast'

import {
  getAllProducts,
  createProduct,
  updateProduct,
  updateProductStatus,
  updateProductStock,
  deleteProduct,
  getAllCategories,
} from '@/api/productApi'
import type { ProductSummary, ProductStatus, StockStatus, Category, ProductRequest } from '@/types'
import { DEFAULT_PAGE_SIZE } from '@/constants'

import { Button }           from '@/components/ui/Button'
import { Input }            from '@/components/ui/Input'
import { Spinner }          from '@/components/ui/Spinner'
import { Pagination }       from '@/components/ui/Pagination'
import { Modal }            from '@/components/ui/Modal'
import { Badge }            from '@/components/ui/Badge'
import { SkeletonTableRow }  from '@/components/ui/SkeletonRow'

// ── Status badges ─────────────────────────────────────────────────────────────

type BadgeVariant = 'default' | 'success' | 'warning' | 'danger' | 'info' | 'admin' | 'user'

const STOCK_STATUS_BADGE: Record<StockStatus, BadgeVariant> = {
  IN_STOCK:    'success',
  LOW_STOCK:   'warning',
  OUT_OF_STOCK: 'danger',
}

function StockBadge({ status }: { status: StockStatus }) {
  return (
    <Badge variant={STOCK_STATUS_BADGE[status]}>
      {status.replace(/_/g, ' ')}
    </Badge>
  )
}

function ProductStatusBadge({ status }: { status: ProductStatus }) {
  const variantMap: Record<ProductStatus, BadgeVariant> = {
    ACTIVE:       'success',
    INACTIVE:     'default',
    OUT_OF_STOCK: 'danger',
    DISCONTINUED: 'default',
  }
  return (
    <Badge
      variant={variantMap[status]}
      className={status === 'DISCONTINUED' ? 'line-through opacity-70' : undefined}
    >
      {status}
    </Badge>
  )
}

// ── Product form ──────────────────────────────────────────────────────────────

interface ProductFormValues {
  name:               string
  sku:                string
  description:        string
  brand:              string
  categoryId:         string   // coerce to number on submit
  price:              string   // string for input; coerce on submit
  originalPrice:      string
  stockQuantity:      string
  lowStockThreshold:  string
  thumbnailUrl:       string
}

interface ProductFormErrors {
  name?:        string
  sku?:         string
  categoryId?:  string
  price?:       string
  stockQuantity?: string
}

function validateProductForm(values: ProductFormValues): ProductFormErrors {
  const errors: ProductFormErrors = {}
  if (!values.name.trim())                        errors.name        = 'Name is required'
  if (!values.sku.trim())                         errors.sku         = 'SKU is required'
  if (!values.categoryId)                         errors.categoryId  = 'Category is required'
  if (!values.price || Number(values.price) <= 0) errors.price       = 'Price must be greater than 0'
  if (values.stockQuantity !== '' && Number(values.stockQuantity) < 0)
                                                  errors.stockQuantity = 'Stock cannot be negative'
  return errors
}

interface ProductFormProps {
  initial?:    Partial<ProductFormValues>
  categories:  Category[]
  onSubmit:    (values: ProductFormValues) => void
  isLoading:   boolean
  submitLabel: string
}

function ProductForm({ initial, categories, onSubmit, isLoading, submitLabel }: ProductFormProps) {
  const [name,              setName]              = useState(initial?.name              ?? '')
  const [sku,               setSku]               = useState(initial?.sku               ?? '')
  const [description,       setDescription]       = useState(initial?.description       ?? '')
  const [brand,             setBrand]             = useState(initial?.brand             ?? '')
  const [categoryId,        setCategoryId]        = useState(initial?.categoryId        ?? '')
  const [price,             setPrice]             = useState(initial?.price             ?? '')
  const [originalPrice,     setOriginalPrice]     = useState(initial?.originalPrice     ?? '')
  const [stockQuantity,     setStockQuantity]     = useState(initial?.stockQuantity     ?? '')
  const [lowStockThreshold, setLowStockThreshold] = useState(initial?.lowStockThreshold ?? '')
  const [thumbnailUrl,      setThumbnailUrl]      = useState(initial?.thumbnailUrl      ?? '')
  const [errors,            setErrors]            = useState<ProductFormErrors>({})

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const vals: ProductFormValues = {
      name: name.trim(), sku: sku.trim(), description: description.trim(),
      brand: brand.trim(), categoryId, price, originalPrice,
      stockQuantity, lowStockThreshold, thumbnailUrl: thumbnailUrl.trim(),
    }
    const errs = validateProductForm(vals)
    if (Object.keys(errs).length > 0) { setErrors(errs); return }
    onSubmit(vals)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Input
          label="Product Name"
          value={name}
          onChange={(e) => { setName(e.target.value); setErrors((p) => ({ ...p, name: undefined })) }}
          error={errors.name}
          placeholder="Wireless Headphones"
          autoFocus
        />
        <Input
          label="SKU"
          value={sku}
          onChange={(e) => { setSku(e.target.value); setErrors((p) => ({ ...p, sku: undefined })) }}
          error={errors.sku}
          placeholder="WH-1000XM5"
        />
      </div>

      <Input
        label="Description"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="Optional product description"
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Input
          label="Brand"
          value={brand}
          onChange={(e) => setBrand(e.target.value)}
          placeholder="Sony"
        />

        {/* Category select */}
        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
            Category
          </label>
          <select
            value={categoryId}
            onChange={(e) => { setCategoryId(e.target.value); setErrors((p) => ({ ...p, categoryId: undefined })) }}
            className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 dark:bg-gray-800 dark:text-gray-100 dark:border-gray-600"
          >
            <option value="">— Select a category —</option>
            {categories.filter((c) => c.active).map((c) => (
              <option key={c.id} value={String(c.id)}>{c.name}</option>
            ))}
          </select>
          {errors.categoryId && (
            <p className="text-xs text-red-500 dark:text-red-400">{errors.categoryId}</p>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Input
          label="Price (₹)"
          type="number"
          min="0.01"
          step="0.01"
          value={price}
          onChange={(e) => { setPrice(e.target.value); setErrors((p) => ({ ...p, price: undefined })) }}
          error={errors.price}
          placeholder="2999"
        />
        <Input
          label="Original Price (₹)"
          type="number"
          min="0"
          step="0.01"
          value={originalPrice}
          onChange={(e) => setOriginalPrice(e.target.value)}
          placeholder="3999 (optional)"
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Input
          label="Stock Quantity"
          type="number"
          min="0"
          step="1"
          value={stockQuantity}
          onChange={(e) => { setStockQuantity(e.target.value); setErrors((p) => ({ ...p, stockQuantity: undefined })) }}
          error={errors.stockQuantity}
          placeholder="100"
        />
        <Input
          label="Low Stock Threshold"
          type="number"
          min="0"
          step="1"
          value={lowStockThreshold}
          onChange={(e) => setLowStockThreshold(e.target.value)}
          placeholder="10 (optional)"
        />
      </div>

      <Input
        label="Thumbnail URL"
        value={thumbnailUrl}
        onChange={(e) => setThumbnailUrl(e.target.value)}
        placeholder="https://cdn.example.com/image.jpg (optional)"
      />

      <div className="flex justify-end gap-2 pt-2">
        <Button type="submit" isLoading={isLoading}>{submitLabel}</Button>
      </div>
    </form>
  )
}

function toProductRequest(vals: ProductFormValues): ProductRequest {
  return {
    name:               vals.name,
    sku:                vals.sku,
    description:        vals.description || undefined,
    brand:              vals.brand       || undefined,
    categoryId:         Number(vals.categoryId),
    price:              Number(vals.price),
    originalPrice:      vals.originalPrice  ? Number(vals.originalPrice)  : undefined,
    stockQuantity:      vals.stockQuantity  ? Number(vals.stockQuantity)  : undefined,
    lowStockThreshold:  vals.lowStockThreshold ? Number(vals.lowStockThreshold) : undefined,
    thumbnailUrl:       vals.thumbnailUrl   || undefined,
  }
}

// ── Update Stock modal ────────────────────────────────────────────────────────

interface StockFormProps {
  product:   ProductSummary
  onSubmit:  (qty: number) => void
  onCancel:  () => void
  isLoading: boolean
}

function StockForm({ product, onSubmit, onCancel, isLoading }: StockFormProps) {
  const [qty,   setQty]   = useState(String(product.stockQuantity))
  const [error, setError] = useState<string | undefined>()

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const n = Number(qty)
    if (!qty || isNaN(n) || n < 0) { setError('Enter a valid non-negative number'); return }
    onSubmit(n)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <p className="text-sm text-gray-500 dark:text-gray-400">
        Updating stock for <strong>{product.name}</strong> (current: {product.stockQuantity})
      </p>
      <Input
        label="New Stock Quantity"
        type="number"
        min="0"
        step="1"
        value={qty}
        onChange={(e) => { setQty(e.target.value); setError(undefined) }}
        error={error}
        autoFocus
      />
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="outline" type="button" onClick={onCancel} disabled={isLoading}>Cancel</Button>
        <Button type="submit" isLoading={isLoading} leftIcon={<BarChart2 size={14} />}>
          Update Stock
        </Button>
      </div>
    </form>
  )
}

// ── Change Status modal ───────────────────────────────────────────────────────

type ChangeableStatus = 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED'

interface StatusFormProps {
  product:   ProductSummary
  onSubmit:  (status: ChangeableStatus) => void
  onCancel:  () => void
  isLoading: boolean
}

function StatusForm({ product, onSubmit, onCancel, isLoading }: StatusFormProps) {
  const [status, setStatus] = useState<ChangeableStatus>(
    product.status === 'OUT_OF_STOCK' ? 'INACTIVE' : (product.status as ChangeableStatus),
  )

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    onSubmit(status)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <p className="text-sm text-gray-500 dark:text-gray-400">
        Change status for <strong>{product.name}</strong>
      </p>
      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700 dark:text-gray-300">Status</label>
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value as ChangeableStatus)}
          className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 dark:bg-gray-800 dark:text-gray-100 dark:border-gray-600"
        >
          <option value="ACTIVE">ACTIVE</option>
          <option value="INACTIVE">INACTIVE</option>
          <option value="DISCONTINUED">DISCONTINUED</option>
        </select>
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="outline" type="button" onClick={onCancel} disabled={isLoading}>Cancel</Button>
        <Button type="submit" isLoading={isLoading}>Save</Button>
      </div>
    </form>
  )
}

// ── Delete confirmation ───────────────────────────────────────────────────────

function DeleteConfirm({
  product,
  onConfirm,
  onCancel,
  isLoading,
}: {
  product:   ProductSummary
  onConfirm: () => void
  onCancel:  () => void
  isLoading: boolean
}) {
  return (
    <div className="space-y-4">
      <div className="flex items-start gap-3">
        <div className="rounded-full bg-red-100 dark:bg-red-900/30 p-2 shrink-0">
          <AlertCircle size={20} className="text-red-500" />
        </div>
        <div>
          <p className="font-medium text-gray-900 dark:text-gray-100">Delete product?</p>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            <strong>{product.name}</strong> ({product.sku}) will be soft-deleted (set to
            DISCONTINUED). This can be reversed by changing status.
          </p>
        </div>
      </div>
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onCancel} disabled={isLoading}>Cancel</Button>
        <Button variant="danger" onClick={onConfirm} isLoading={isLoading}>Delete</Button>
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function AdminProductsPage() {
  const qc = useQueryClient()

  // ── State ──────────────────────────────────────────────────────────────────
  const [page,         setPage]         = useState(0)
  const [size,         setSize]         = useState(DEFAULT_PAGE_SIZE)
  const [statusFilter, setStatusFilter] = useState<ProductStatus | ''>('')

  // Modal state
  const [createOpen,      setCreateOpen]      = useState(false)
  const [editProduct,     setEditProduct]     = useState<ProductSummary | null>(null)
  const [stockProduct,    setStockProduct]    = useState<ProductSummary | null>(null)
  const [statusProduct,   setStatusProduct]   = useState<ProductSummary | null>(null)
  const [deleteProduct_,  setDeleteProduct_]  = useState<ProductSummary | null>(null)

  // ── Queries ────────────────────────────────────────────────────────────────
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey:  ['admin-products', page, size, statusFilter],
    queryFn:   () =>
      getAllProducts({
        page,
        size,
        sort: 'name,asc',
        ...(statusFilter ? { status: statusFilter } : {}),
      }),
    staleTime: 30_000,
    placeholderData: (prev) => prev,
  })

  const { data: categories = [] } = useQuery({
    queryKey:  ['categories'],
    queryFn:   getAllCategories,
    staleTime: 60_000,
  })

  // ── Mutations ──────────────────────────────────────────────────────────────
  function invalidate() {
    qc.invalidateQueries({ queryKey: ['admin-products'] })
  }

  const { mutate: doCreate, isPending: creating } = useMutation({
    mutationFn: (vals: ProductFormValues) => createProduct(toProductRequest(vals)),
    onSuccess: () => {
      invalidate()
      setCreateOpen(false)
      toast.success('Product created successfully')
    },
    onError: () => toast.error('Failed to create product'),
  })

  const { mutate: doUpdate, isPending: updating } = useMutation({
    mutationFn: (vals: ProductFormValues) =>
      updateProduct(editProduct!.id, toProductRequest(vals)),
    onSuccess: () => {
      invalidate()
      setEditProduct(null)
      toast.success('Product updated')
    },
    onError: () => toast.error('Failed to update product'),
  })

  const { mutate: doUpdateStock, isPending: updatingStock } = useMutation({
    mutationFn: (qty: number) => updateProductStock(stockProduct!.id, qty),
    onSuccess: () => {
      invalidate()
      setStockProduct(null)
      toast.success('Stock updated')
    },
    onError: () => toast.error('Failed to update stock'),
  })

  const { mutate: doUpdateStatus, isPending: updatingStatus } = useMutation({
    mutationFn: (status: ChangeableStatus) => updateProductStatus(statusProduct!.id, status),
    onSuccess: () => {
      invalidate()
      setStatusProduct(null)
      toast.success('Product status updated')
    },
    onError: () => toast.error('Failed to update product status'),
  })

  const { mutate: doDelete, isPending: deleting } = useMutation({
    mutationFn: () => deleteProduct(deleteProduct_!.id),
    onSuccess: () => {
      invalidate()
      setDeleteProduct_(null)
      toast.success('Product deleted')
    },
    onError: () => toast.error('Failed to delete product'),
  })

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-4 max-w-7xl">
      {/* Toolbar */}
      <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-center justify-between">
        <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Products</h1>
        <div className="flex flex-wrap gap-2 items-center">
          {/* Status filter */}
          <select
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value as ProductStatus | '')
              setPage(0)
            }}
            className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-900 shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 dark:bg-gray-800 dark:text-gray-100 dark:border-gray-600"
          >
            <option value="">All Statuses</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
            <option value="OUT_OF_STOCK">OUT OF STOCK</option>
            <option value="DISCONTINUED">DISCONTINUED</option>
          </select>

          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            isLoading={isFetching && !isLoading}
            leftIcon={<RefreshCw size={14} />}
          >
            Refresh
          </Button>
          <Button
            size="sm"
            onClick={() => setCreateOpen(true)}
            leftIcon={<Plus size={14} />}
          >
            New Product
          </Button>
        </div>
      </div>

      {/* Table card */}
      <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
        {isError && (
          <div className="flex items-center gap-2 px-4 py-3 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm border-b border-red-200 dark:border-red-800">
            <AlertCircle size={14} />
            Failed to load products: {String((error as Error)?.message ?? error)}
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50">
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-16">
                  ID
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide">
                  Name / SKU
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide">
                  Category
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-28">
                  Price
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-28">
                  Stock
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-32">
                  Status
                </th>
                <th className="px-4 py-3 text-right font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-40">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
              {isLoading &&
                Array.from({ length: size }).map((_, i) => (
                  <SkeletonTableRow key={i} cols={7} />
                ))}

              {!isLoading &&
                (data?.content ?? []).map((product) => (
                  <tr
                    key={product.id}
                    className="hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors group"
                  >
                    <td className="px-4 py-3 text-gray-400 dark:text-gray-500 font-mono text-xs">
                      {product.id}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        {product.thumbnailUrl && (
                          <img
                            src={product.thumbnailUrl}
                            alt={product.name}
                            className="h-8 w-8 rounded object-cover shrink-0 border border-gray-200 dark:border-gray-700"
                          />
                        )}
                        <div>
                          <p className="font-medium text-gray-900 dark:text-gray-100 line-clamp-1">
                            {product.name}
                          </p>
                          <p className="text-xs text-gray-400 dark:text-gray-500 font-mono">
                            {product.sku}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-600 dark:text-gray-300">
                      {product.categoryName}
                    </td>
                    <td className="px-4 py-3">
                      <div>
                        <p className="font-medium text-gray-900 dark:text-gray-100">
                          ₹{product.price.toLocaleString('en-IN')}
                        </p>
                        {product.originalPrice && product.originalPrice > product.price && (
                          <p className="text-xs text-gray-400 line-through">
                            ₹{product.originalPrice.toLocaleString('en-IN')}
                          </p>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1.5">
                        {product.stockStatus === 'LOW_STOCK' && (
                          <AlertTriangle size={12} className="text-amber-500 shrink-0" />
                        )}
                        <span className="text-gray-700 dark:text-gray-300 font-medium text-xs">
                          {product.stockQuantity}
                        </span>
                        <StockBadge status={product.stockStatus} />
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <ProductStatusBadge status={product.status} />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setEditProduct(product)}
                          className="p-1.5"
                          aria-label={`Edit ${product.name}`}
                        >
                          <Pencil size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setStockProduct(product)}
                          className="p-1.5"
                          aria-label={`Update stock for ${product.name}`}
                          title="Update stock"
                        >
                          <BarChart2 size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setStatusProduct(product)}
                          className="p-1.5"
                          aria-label={`Change status of ${product.name}`}
                          title="Change status"
                        >
                          <Package size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setDeleteProduct_(product)}
                          className="p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20"
                          aria-label={`Delete ${product.name}`}
                        >
                          <Trash2 size={14} />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}

              {!isLoading && (data?.content ?? []).length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-12 text-center">
                    <div className="flex flex-col items-center gap-2 text-gray-400 dark:text-gray-500">
                      {isFetching ? (
                        <Spinner size="md" />
                      ) : (
                        <>
                          <Package size={24} />
                          <p className="text-sm">
                            {statusFilter
                              ? `No products with status "${statusFilter}"`
                              : 'No products found'}
                          </p>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {data && !isLoading && (
          <div className="border-t border-gray-100 dark:border-gray-700 px-4 py-3">
            <Pagination
              page={data.number}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              pageSize={data.size}
              onPageChange={(p) => { setPage(p); window.scrollTo({ top: 0, behavior: 'smooth' }) }}
              onSizeChange={(s) => { setSize(s); setPage(0) }}
            />
          </div>
        )}
      </div>

      {/* ── Create modal ── */}
      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Create Product" size="xl">
        <ProductForm
          categories={categories}
          onSubmit={(vals) => doCreate(vals)}
          isLoading={creating}
          submitLabel="Create Product"
        />
      </Modal>

      {/* ── Edit modal ── */}
      <Modal
        open={editProduct !== null}
        onClose={() => setEditProduct(null)}
        title="Edit Product"
        size="xl"
      >
        {editProduct && (
          <ProductForm
            initial={{
              name:          editProduct.name,
              sku:           editProduct.sku,
              brand:         editProduct.brand       ?? '',
              categoryId:    '',   // categoryId not on ProductSummary; user must re-select
              price:         String(editProduct.price),
              originalPrice: editProduct.originalPrice ? String(editProduct.originalPrice) : '',
              stockQuantity: String(editProduct.stockQuantity),
            }}
            categories={categories}
            onSubmit={(vals) => doUpdate(vals)}
            isLoading={updating}
            submitLabel="Save changes"
          />
        )}
      </Modal>

      {/* ── Update Stock modal ── */}
      <Modal
        open={stockProduct !== null}
        onClose={() => setStockProduct(null)}
        title="Update Stock"
        size="sm"
      >
        {stockProduct && (
          <StockForm
            product={stockProduct}
            onSubmit={(qty) => doUpdateStock(qty)}
            onCancel={() => setStockProduct(null)}
            isLoading={updatingStock}
          />
        )}
      </Modal>

      {/* ── Change Status modal ── */}
      <Modal
        open={statusProduct !== null}
        onClose={() => setStatusProduct(null)}
        title="Change Product Status"
        size="sm"
      >
        {statusProduct && (
          <StatusForm
            product={statusProduct}
            onSubmit={(status) => doUpdateStatus(status)}
            onCancel={() => setStatusProduct(null)}
            isLoading={updatingStatus}
          />
        )}
      </Modal>

      {/* ── Delete modal ── */}
      <Modal
        open={deleteProduct_ !== null}
        onClose={() => setDeleteProduct_(null)}
        title="Confirm Deletion"
        size="sm"
      >
        {deleteProduct_ && (
          <DeleteConfirm
            product={deleteProduct_}
            onConfirm={() => doDelete()}
            onCancel={() => setDeleteProduct_(null)}
            isLoading={deleting}
          />
        )}
      </Modal>
    </div>
  )
}
