/**
 * Auth API — all calls go through the API Gateway (/auth/*).
 *
 * WHY we no longer call Keycloak directly:
 *   A browser on localhost:5173 POSTing to Keycloak on localhost:30080 is a
 *   cross-origin request.  Keycloak's CORS policy blocks it unless "Web Origins"
 *   is explicitly configured in the realm client.  Instead we route every auth
 *   call through the gateway (/auth/token, /auth/refresh), which forwards them
 *   server-to-server — no CORS involved from the browser's perspective.
 *
 * The gateway endpoints live at:
 *   POST /auth/token    — ROPC login  (proxies to Keycloak internally)
 *   POST /auth/refresh  — refresh     (proxies to Keycloak internally)
 *   POST /auth/logout   — clear session
 *
 * These use the GATEWAY axios instance (base "/") so they hit the Vite proxy
 * → API Gateway → Keycloak, all within the same origin.
 */
import axios from 'axios'
import type { KeycloakTokenResponse } from '@/types'

/**
 * Dedicated axios instance for auth endpoints.
 * Base URL is "/" so the Vite dev-server proxy forwards /auth/* → gateway:2027.
 * In production point this at the gateway's public URL.
 */
const gatewayAuth = axios.create({
  baseURL: '/',
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

// ── Login ─────────────────────────────────────────────────────────────────────

/**
 * Exchanges username + password for Keycloak tokens via the gateway.
 * The gateway calls Keycloak server-to-server (ROPC), so no CORS issues.
 */
export async function loginWithPassword(
  username: string,
  password: string,
): Promise<KeycloakTokenResponse> {
  const { data } = await gatewayAuth.post<KeycloakTokenResponse>('/auth/token', {
    username,
    password,
  })
  return data
}

// ── Refresh ───────────────────────────────────────────────────────────────────

/**
 * Silently exchanges a refresh token for a new access token via the gateway.
 */
export async function refreshAccessToken(
  refreshToken: string,
): Promise<KeycloakTokenResponse> {
  const { data } = await gatewayAuth.post<KeycloakTokenResponse>('/auth/refresh', {
    refreshToken,
  })
  return data
}

// ── Revoke / Logout ────────────────────────────────────────────────────────────

/**
 * Notifies the gateway to clear its session.
 * For stateless JWT the real logout happens on the client (discard tokens);
 * this call is best-effort to clean any server-side state.
 */
export async function revokeToken(_token: string): Promise<void> {
  try {
    await gatewayAuth.post('/auth/logout')
  } catch {
    // Best-effort — local token removal still proceeds even if this fails
  }
}
