// ─── API ──────────────────────────────────────────────────────────────────────

export const API_BASE_URL = import.meta.env.VITE_API_GATEWAY_URL ?? 'http://localhost:2027'

export const KEYCLOAK_URL    = import.meta.env.VITE_KEYCLOAK_URL       ?? 'http://localhost:30080'
export const KEYCLOAK_REALM  = import.meta.env.VITE_KEYCLOAK_REALM     ?? 'microservices-realm'
export const KEYCLOAK_CLIENT = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'apigateway-client'

// ─── App ──────────────────────────────────────────────────────────────────────

export const APP_NAME    = import.meta.env.VITE_APP_NAME    ?? 'ShopZone'
export const APP_VERSION = import.meta.env.VITE_APP_VERSION ?? '1.0.0'

// ─── Storage Keys ─────────────────────────────────────────────────────────────

export const STORAGE_KEYS = {
  AUTH:  'ec_auth',
  THEME: 'ec_theme',
} as const

// ─── Routes ───────────────────────────────────────────────────────────────────

export const ROUTES = {
  HOME:          '/',
  SHOP:          '/shop',
  PRODUCT:       '/shop/:id',
  CART:          '/cart',
  CHECKOUT:      '/checkout',
  ORDERS:        '/orders',
  ORDER_DETAIL:  '/orders/:id',
  ORDER_CONFIRM: '/orders/confirmation',
  PROFILE:       '/profile',
  LOGIN:         '/login',
  UNAUTHORIZED:  '/unauthorized',
  // Admin
  ADMIN:            '/admin',
  ADMIN_DASHBOARD:  '/admin/dashboard',
  ADMIN_PRODUCTS:   '/admin/products',
  ADMIN_CATEGORIES: '/admin/categories',
  ADMIN_ORDERS:     '/admin/orders',
  ADMIN_USERS:      '/admin/users',
  ADMIN_USER_DETAIL:'/admin/users/:id',
} as const

// ─── Roles ────────────────────────────────────────────────────────────────────

export const ROLES = {
  ADMIN: 'ROLE_ADMIN',
  USER:  'ROLE_USER',
} as const

// ─── Pagination defaults ──────────────────────────────────────────────────────

export const DEFAULT_PAGE_SIZE = 10
export const PAGE_SIZE_OPTIONS = [5, 10, 20, 50]

// ─── Debounce ─────────────────────────────────────────────────────────────────

export const SEARCH_DEBOUNCE_MS = 350

// ─── Token ────────────────────────────────────────────────────────────────────

export const TOKEN_REFRESH_BUFFER_MS = 30_000
