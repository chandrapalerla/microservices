/**
 * Product-service and Category API calls, routed via the API Gateway.
 *
 * Public (no auth):   GET /v1/products, GET /v1/categories
 * Authenticated:      POST /v1/products/{id}/deduct-stock  (USER/ADMIN)
 * Admin only:         POST/PUT/PATCH/DELETE on products & categories
 */
import api from './axiosInstance'
import type {
  Category, CategoryRequest,
  Product, ProductSummary, ProductRequest, ProductSearchParams, ProductStatus,
  Page,
} from '@/types'

const P = '/v1/products'
const C = '/v1/categories'

// ─── Categories ───────────────────────────────────────────────────────────────

/** Active categories — public. Used in filter dropdowns. */
export async function getCategories(): Promise<Category[]> {
  const { data } = await api.get<Category[]>(C)
  return data
}

/** All categories including inactive — admin. */
export async function getAllCategories(): Promise<Category[]> {
  const { data } = await api.get<Category[]>(`${C}/all`)
  return data
}

export async function getCategoryById(id: number): Promise<Category> {
  const { data } = await api.get<Category>(`${C}/${id}`)
  return data
}

export async function createCategory(req: CategoryRequest): Promise<Category> {
  const { data } = await api.post<Category>(C, req)
  return data
}

export async function updateCategory(id: number, req: CategoryRequest): Promise<Category> {
  const { data } = await api.put<Category>(`${C}/${id}`, req)
  return data
}

export async function deleteCategory(id: number): Promise<void> {
  await api.delete(`${C}/${id}`)
}

// ─── Products (public reads) ──────────────────────────────────────────────────

/** Paginated ACTIVE products — public. */
export async function getProducts(
  params: ProductSearchParams = {},
): Promise<Page<ProductSummary>> {
  const { data } = await api.get<Page<ProductSummary>>(P, { params })
  return data
}

/** Dynamic search with all filters — public. */
export async function searchProducts(
  params: ProductSearchParams = {},
): Promise<Page<ProductSummary>> {
  const { data } = await api.get<Page<ProductSummary>>(`${P}/search`, { params })
  return data
}

/** Products in a category — public. */
export async function getProductsByCategory(
  categoryId: number,
  params: { page?: number; size?: number; sort?: string } = {},
): Promise<Page<ProductSummary>> {
  const { data } = await api.get<Page<ProductSummary>>(`${P}/category/${categoryId}`, { params })
  return data
}

/** Full product detail — public. */
export async function getProductById(id: number): Promise<Product> {
  const { data } = await api.get<Product>(`${P}/${id}`)
  return data
}

// ─── Products (admin) ─────────────────────────────────────────────────────────

/** All products regardless of status — admin. */
export async function getAllProducts(
  params: { page?: number; size?: number; sort?: string; status?: ProductStatus } = {},
): Promise<Page<ProductSummary>> {
  const { data } = await api.get<Page<ProductSummary>>(`${P}/all`, { params })
  return data
}

/** ACTIVE products below low-stock threshold — admin. */
export async function getLowStockProducts(
  params: { page?: number; size?: number } = {},
): Promise<Page<ProductSummary>> {
  const { data } = await api.get<Page<ProductSummary>>(`${P}/low-stock`, { params })
  return data
}

export async function createProduct(req: ProductRequest): Promise<Product> {
  const { data } = await api.post<Product>(P, req)
  return data
}

export async function updateProduct(id: number, req: ProductRequest): Promise<Product> {
  const { data } = await api.put<Product>(`${P}/${id}`, req)
  return data
}

export async function updateProductStatus(
  id: number,
  status: 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED',
): Promise<Product> {
  const { data } = await api.patch<Product>(`${P}/${id}/status`, { status })
  return data
}

/** Admin: set absolute stock value. Backend UpdateStockRequest field is `quantity`. */
export async function updateProductStock(id: number, quantity: number): Promise<Product> {
  const { data } = await api.patch<Product>(`${P}/${id}/stock`, { quantity })
  return data
}

/** Soft-delete — sets status to DISCONTINUED. */
export async function deleteProduct(id: number): Promise<void> {
  await api.delete(`${P}/${id}`)
}

// ─── Stock operations (order checkout flow) ───────────────────────────────────

export async function deductStock(id: number, quantity: number): Promise<Product> {
  const { data } = await api.post<Product>(`${P}/${id}/deduct-stock`, { quantity })
  return data
}

export async function restoreStock(id: number, quantity: number): Promise<Product> {
  const { data } = await api.post<Product>(`${P}/${id}/restore-stock`, { quantity })
  return data
}
