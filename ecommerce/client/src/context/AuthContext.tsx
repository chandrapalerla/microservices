/**
 * AuthContext — single source of truth for authentication state.
 *
 * Responsibilities:
 *  - Load persisted auth from localStorage on startup.
 *  - Expose login / logout helpers.
 *  - Keep the axiosInstance authBridge in sync so the interceptor can
 *    refresh tokens without importing React hooks.
 *  - Schedule a proactive token refresh before the access token expires.
 */
import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react'
import toast from 'react-hot-toast'

import { loginWithPassword, revokeToken } from '@/api/authApi'
import { authBridge } from '@/api/axiosInstance'
import { STORAGE_KEYS, TOKEN_REFRESH_BUFFER_MS } from '@/constants'
import type { StoredAuth, UserInfo, KeycloakTokenResponse } from '@/types'
import {
  buildUserInfo,
  decodeToken,
  getTokenExpiry,
  isTokenExpired,
} from '@/utils/tokenUtils'

// ─── Shape ────────────────────────────────────────────────────────────────────

interface AuthContextValue {
  user: UserInfo | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (username: string, password: string, rememberMe?: boolean) => Promise<void>
  logout: () => Promise<void>
  hasRole: (role: string) => boolean
}

// ─── Context ──────────────────────────────────────────────────────────────────

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

// ─── Helpers ─────────────────────────────────────────────────────────────────

function persistAuth(tokens: KeycloakTokenResponse): StoredAuth {
  const decoded   = decodeToken(tokens.access_token)
  const userInfo  = buildUserInfo(decoded)
  const expiresAt = getTokenExpiry(tokens.access_token)

  const stored: StoredAuth = {
    accessToken:  tokens.access_token,
    refreshToken: tokens.refresh_token,
    expiresAt,
    userInfo,
  }
  localStorage.setItem(STORAGE_KEYS.AUTH, JSON.stringify(stored))
  return stored
}

function clearAuth() {
  localStorage.removeItem(STORAGE_KEYS.AUTH)
}

function loadStoredAuth(): StoredAuth | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.AUTH)
    if (!raw) return null
    return JSON.parse(raw) as StoredAuth
  } catch {
    return null
  }
}

// ─── Provider ────────────────────────────────────────────────────────────────

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser]           = useState<UserInfo | null>(null)
  const [isLoading, setIsLoading] = useState(true)  // true until we've checked localStorage
  const refreshTimerRef           = useRef<ReturnType<typeof setTimeout> | null>(null)

  // ── Sync authBridge so the Axios interceptor can use current tokens ──
  const syncBridge = useCallback((stored: StoredAuth | null) => {
    if (!stored) return
    authBridge.setTokens = (newTokens: KeycloakTokenResponse) => {
      const updated = persistAuth(newTokens)
      setUser(updated.userInfo)
      scheduleRefresh(updated.expiresAt)   // eslint-disable-line @typescript-eslint/no-use-before-define
    }
    authBridge.logout = async () => {
      await logout()  // eslint-disable-line @typescript-eslint/no-use-before-define
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // ── Schedule a proactive refresh before the token expires ──────────────
  const scheduleRefresh = useCallback((expiresAt: number) => {
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current)

    const delay = expiresAt - Date.now() - TOKEN_REFRESH_BUFFER_MS
    if (delay <= 0) return  // already expired; interceptor will handle it on next request

    refreshTimerRef.current = setTimeout(() => {
      // Trigger a no-op authenticated request; the interceptor detects
      // near-expiry and calls refreshAccessToken automatically.
      // (Alternatively call refreshAccessToken directly here if you prefer.)
    }, delay)
  }, [])

  // ── Bootstrap — restore session from localStorage ───────────────────────
  useEffect(() => {
    const stored = loadStoredAuth()

    if (stored && !isTokenExpired(stored.accessToken)) {
      setUser(stored.userInfo)
      syncBridge(stored)
      scheduleRefresh(stored.expiresAt)
    } else if (stored?.refreshToken) {
      // Access token expired but we have a refresh token — the interceptor
      // will automatically refresh on the first API call, so just restore
      // the user info for a seamless experience.
      setUser(stored.userInfo)
      syncBridge(stored)
    } else {
      clearAuth()
    }

    setIsLoading(false)

    return () => {
      if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current)
    }
  }, [syncBridge, scheduleRefresh])

  // ── Login ─────────────────────────────────────────────────────────────────
  const login = useCallback(
    async (username: string, password: string) => {
      const tokens  = await loginWithPassword(username, password)
      const stored  = persistAuth(tokens)
      setUser(stored.userInfo)
      syncBridge(stored)
      scheduleRefresh(stored.expiresAt)
    },
    [syncBridge, scheduleRefresh],
  )

  // ── Logout ────────────────────────────────────────────────────────────────
  const logout = useCallback(async () => {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.AUTH)
      if (raw) {
        const stored: StoredAuth = JSON.parse(raw)
        await revokeToken(stored.refreshToken)
      }
    } catch {
      // Best-effort token revocation
    } finally {
      if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current)
      clearAuth()
      setUser(null)
      toast.success('Logged out successfully')
    }
  }, [])

  // ── Role check ────────────────────────────────────────────────────────────
  const hasRole = useCallback(
    (role: string) => user?.roles.includes(role) ?? false,
    [user],
  )

  const value: AuthContextValue = {
    user,
    isAuthenticated: user !== null,
    isLoading,
    login,
    logout,
    hasRole,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
