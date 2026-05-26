// ─── API ──────────────────────────────────────────────────────────────────────

/**
 * All business API calls route through the API Gateway.
 * The Vite dev server proxies /api and /auth to localhost:2027,
 * so in dev mode we use relative paths to avoid CORS.
 */
export const API_BASE_URL = import.meta.env.VITE_API_GATEWAY_URL ?? 'http://localhost:2027'

/**
 * Keycloak metadata (for display / documentation only).
 * The browser never calls Keycloak directly — all token exchange goes through
 * the API Gateway's /auth/token and /auth/refresh endpoints.
 */
export const KEYCLOAK_URL    = import.meta.env.VITE_KEYCLOAK_URL    ?? 'http://localhost:30080'
export const KEYCLOAK_REALM  = import.meta.env.VITE_KEYCLOAK_REALM  ?? 'microservices-realm'
export const KEYCLOAK_CLIENT = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'apigateway-client'

// ─── App ──────────────────────────────────────────────────────────────────────

export const APP_NAME    = import.meta.env.VITE_APP_NAME    ?? 'ECommerce Admin'
export const APP_VERSION = import.meta.env.VITE_APP_VERSION ?? '1.0.0'

// ─── Storage Keys ─────────────────────────────────────────────────────────────

export const STORAGE_KEYS = {
  AUTH:  'ec_auth',
  THEME: 'ec_theme',
} as const

// ─── Routes ───────────────────────────────────────────────────────────────────

export const ROUTES = {
  LOGIN:        '/login',
  DASHBOARD:    '/dashboard',
  USERS:        '/users',
  USER_DETAIL:  '/users/:id',
  UNAUTHORIZED: '/unauthorized',
  NOT_FOUND:    '*',
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

/** Refresh the access token this many ms before it actually expires */
export const TOKEN_REFRESH_BUFFER_MS = 30_000
