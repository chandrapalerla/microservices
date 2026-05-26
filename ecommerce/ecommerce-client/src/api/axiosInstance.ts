/**
 * Central Axios instance.
 *
 * Request interceptor  → attaches the Bearer JWT to every outgoing request.
 * Response interceptor → on 401, attempts a silent token refresh; if that
 *                        also fails the user is logged out and redirected to
 *                        /login.
 *
 * WHY we don't import AuthContext here:
 *   Contexts are React-land (component tree). The axios instance is plain JS
 *   and created once at module init time.  We communicate with the auth layer
 *   through a small shared object (`authBridge`) that AuthContext writes into
 *   on mount and clears on unmount.
 */
import axios, {
  type AxiosInstance,
  type InternalAxiosRequestConfig,
  type AxiosResponse,
  type AxiosError,
} from 'axios'
import { STORAGE_KEYS } from '@/constants'
import type { KeycloakTokenResponse, StoredAuth } from '@/types'

// ─── Auth bridge ──────────────────────────────────────────────────────────────
// AuthContext writes these callbacks so the interceptor can act without
// importing React context directly.
export const authBridge = {
  getAccessToken: (): string | null => {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.AUTH)
      if (!raw) return null
      const stored: StoredAuth = JSON.parse(raw)
      return stored.accessToken ?? null
    } catch {
      return null
    }
  },
  getRefreshToken: (): string | null => {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.AUTH)
      if (!raw) return null
      const stored: StoredAuth = JSON.parse(raw)
      return stored.refreshToken ?? null
    } catch {
      return null
    }
  },
  setTokens: (_tokens: KeycloakTokenResponse) => {
    // Overwritten by AuthContext on mount
  },
  logout: () => {
    // Overwritten by AuthContext on mount
    localStorage.removeItem(STORAGE_KEYS.AUTH)
    window.location.href = '/login'
  },
}

// ─── Axios instance ───────────────────────────────────────────────────────────

const api: AxiosInstance = axios.create({
  baseURL: '/api',      // Vite dev proxy → http://localhost:2027/api
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

// ─── Request interceptor — attach JWT ─────────────────────────────────────────

api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = authBridge.getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ─── Response interceptor — handle 401 with token refresh ─────────────────────

let isRefreshing = false
// Queue of callbacks waiting for the refreshed token
let pendingQueue: Array<{
  resolve: (token: string) => void
  reject: (err: unknown) => void
}> = []

function processPendingQueue(error: unknown, token: string | null) {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error)
    else resolve(token!)
  })
  pendingQueue = []
}

api.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    // Only attempt refresh on 401; skip if this IS the retry or no refresh token
    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error)
    }

    const refreshToken = authBridge.getRefreshToken()
    if (!refreshToken) {
      authBridge.logout()
      return Promise.reject(error)
    }

    // Multiple concurrent 401s: queue them while one refresh is in flight
    if (isRefreshing) {
      return new Promise<AxiosResponse>((resolve, reject) => {
        pendingQueue.push({
          resolve: (token) => {
            original.headers.Authorization = `Bearer ${token}`
            resolve(api(original))
          },
          reject,
        })
      })
    }

    original._retry = true
    isRefreshing = true

    try {
      // Call the gateway's /auth/refresh endpoint (server → Keycloak, no CORS)
      const { data } = await axios.post<KeycloakTokenResponse>(
        '/auth/refresh',
        { refreshToken },
        { headers: { 'Content-Type': 'application/json' } },
      )

      // Persist new tokens via the bridge
      authBridge.setTokens(data)

      processPendingQueue(null, data.access_token)

      original.headers.Authorization = `Bearer ${data.access_token}`
      return api(original)
    } catch (refreshError) {
      processPendingQueue(refreshError, null)
      authBridge.logout()
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)

export default api
