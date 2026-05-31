// ─── User Types ───────────────────────────────────────────────────────────────

export interface User {
  id: number | null
  version: number | null
  name: string
  email: string
  phone?: string
  role?: 'USER' | 'ADMIN'
  status?: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'
  createdAt?: string
  updatedAt?: string
}

// ─── Category Types ───────────────────────────────────────────────────────────

export interface Category {
  id: number
  name: string
  description?: string
  slug: string
  parentId?: number
  parentName?: string
  active: boolean
  productCount: number
  createdAt?: string
}

export interface CategoryRequest {
  name: string
  description?: string
  slug: string
  parentId?: number | null
  active?: boolean
}

// ─── Product Types ────────────────────────────────────────────────────────────

export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK' | 'DISCONTINUED'
export type StockStatus   = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK'

export interface ProductSummary {
  id: number
  name: string
  sku: string
  brand?: string
  categoryName: string
  price: number
  originalPrice?: number
  discountPercent?: number
  stockQuantity: number
  status: ProductStatus
  stockStatus: StockStatus
  thumbnailUrl?: string
}

export interface Product extends ProductSummary {
  description?: string
  category: Category
  reservedQuantity: number
  lowStockThreshold: number
  weight?: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface ProductRequest {
  name: string
  sku: string
  description?: string
  brand?: string
  categoryId: number
  price: number
  originalPrice?: number
  stockQuantity?: number
  lowStockThreshold?: number
  thumbnailUrl?: string
  weight?: number
}

export interface ProductSearchParams {
  name?: string
  brand?: string
  categoryId?: number
  minPrice?: number
  maxPrice?: number
  inStockOnly?: boolean
  status?: ProductStatus
  page?: number
  size?: number
  sort?: string
}

// ─── Cart Types ───────────────────────────────────────────────────────────────

export interface CartItem {
  productId: number
  productName: string
  sku: string
  price: number
  quantity: number
  thumbnailUrl?: string
  maxStock: number
}

// ─── Order Types ──────────────────────────────────────────────────────────────

export type OrderStatus =
  | 'PENDING'
  | 'PAYMENT_FAILED'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'RETURN_REQUESTED'
  | 'RETURNED'
  | 'REFUNDED'
  | 'CANCELLED'

export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED'
export type PaymentMethod = 'CARD' | 'UPI' | 'NET_BANKING' | 'CASH_ON_DELIVERY' | 'WALLET'

export interface OrderItem {
  id: number
  productId: number
  productName: string
  productSku: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface ShippingAddress {
  fullName: string
  phone: string
  street: string
  city: string
  state: string
  zip: string
  country: string
}

export interface Order {
  id: number
  orderNumber: string
  userId: number
  userEmail: string
  status: OrderStatus
  paymentStatus: PaymentStatus
  paymentMethod: PaymentMethod
  paymentReference?: string
  subtotal: number
  taxAmount: number
  shippingAmount: number
  discountAmount: number
  totalAmount: number
  couponCode?: string
  trackingNumber?: string
  courierName?: string
  notes?: string
  shippingAddress: ShippingAddress
  items: OrderItem[]
  estimatedDelivery?: string
  version: number
  createdAt: string
  confirmedAt?: string
  shippedAt?: string
  deliveredAt?: string
  cancelledAt?: string
}

export interface OrderStatusHistory {
  id: number
  orderId: number
  fromStatus: OrderStatus
  toStatus: OrderStatus
  reason?: string
  changedBy: string
  changedAt: string
}

export interface OrderRequest {
  userId: number
  items: { productId: number; quantity: number }[]
  shippingAddress: ShippingAddress
  paymentMethod: PaymentMethod
  couponCode?: string
  notes?: string
}

// ─── Pagination ───────────────────────────────────────────────────────────────

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
  numberOfElements: number
  pageable: {
    pageNumber: number
    pageSize: number
    sort: { sorted: boolean; unsorted: boolean }
    offset: number
  }
}

// ─── Auth / Identity ──────────────────────────────────────────────────────────

export interface DecodedToken {
  sub: string
  preferred_username: string
  email?: string
  given_name?: string
  family_name?: string
  name?: string
  realm_access?: { roles: string[] }
  resource_access?: Record<string, { roles: string[] }>
  exp: number
  iat: number
}

export interface UserInfo {
  username: string
  email: string
  displayName: string
  roles: string[]
}

export interface KeycloakTokenResponse {
  access_token: string
  refresh_token: string
  token_type: string
  expires_in: number
  refresh_expires_in: number
}

export interface StoredAuth {
  accessToken: string
  refreshToken: string
  expiresAt: number
  userInfo: UserInfo
}

export interface ApiError {
  message: string
  status?: number
  code?: string
}

export interface PageableParams {
  page?: number
  size?: number
  sort?: string
}

export type Role  = 'ROLE_ADMIN' | 'ROLE_USER'
export type Theme = 'light' | 'dark'

export interface NavItem {
  label: string
  path: string
  icon: string
  roles?: Role[]
}
