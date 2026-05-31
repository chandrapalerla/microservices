import { jwtDecode } from 'jwt-decode'
import type { DecodedToken, UserInfo } from '@/types'

/**
 * Decodes a Keycloak JWT and extracts the user info object.
 * Keycloak stores realm-level roles in `realm_access.roles`.
 */
export function decodeToken(token: string): DecodedToken {
  return jwtDecode<DecodedToken>(token)
}

/**
 * Extracts Spring Security-compatible role strings from a Keycloak JWT.
 * Keycloak roles come in as plain strings (e.g. "ADMIN"); we prefix them
 * with "ROLE_" to match what the backend asserts in SecurityConfig.
 */
export function extractRoles(decoded: DecodedToken): string[] {
  const realmRoles = decoded.realm_access?.roles ?? []
  return realmRoles.map((r) => (r.startsWith('ROLE_') ? r : `ROLE_${r}`))
}

/** Builds the lightweight UserInfo object kept in AuthContext / localStorage. */
export function buildUserInfo(decoded: DecodedToken): UserInfo {
  return {
    username:    decoded.preferred_username,
    email:       decoded.email ?? '',
    displayName: decoded.name ?? decoded.preferred_username,
    roles:       extractRoles(decoded),
  }
}

/**
 * Returns true when the access token expires within the next `bufferMs`
 * milliseconds (default 30 s).  A missing/unparseable token is treated
 * as expired.
 */
export function isTokenExpired(token: string, bufferMs = 30_000): boolean {
  try {
    const { exp } = jwtDecode<{ exp: number }>(token)
    return Date.now() >= exp * 1000 - bufferMs
  } catch {
    return true
  }
}

/** Returns the raw expiry epoch (ms) from a JWT, or 0 on error. */
export function getTokenExpiry(token: string): number {
  try {
    const { exp } = jwtDecode<{ exp: number }>(token)
    return exp * 1000
  } catch {
    return 0
  }
}
