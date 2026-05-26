// ─── Domain Types ────────────────────────────────────────────────────────────

/**
 * Mirrors the backend UserDto exactly.
 * Backend: user-service/src/main/java/com/user/dto/UserDto.java
 */
export interface User {
  id: number | null;
  version: number | null;
  name: string;
  email: string;
}

/** Spring Data Page<T> wrapper returned by GET /api/v1/users */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;        // 0-indexed current page
  first: boolean;
  last: boolean;
  empty: boolean;
  numberOfElements: number;
  pageable: {
    pageNumber: number;
    pageSize: number;
    sort: { sorted: boolean; unsorted: boolean };
    offset: number;
  };
}

// ─── Auth / Identity Types ────────────────────────────────────────────────────

/** Decoded payload from a Keycloak-issued JWT */
export interface DecodedToken {
  sub: string;
  preferred_username: string;
  email?: string;
  given_name?: string;
  family_name?: string;
  name?: string;
  realm_access?: { roles: string[] };
  resource_access?: Record<string, { roles: string[] }>;
  exp: number;
  iat: number;
}

/** Subset of user info we keep in auth context */
export interface UserInfo {
  username: string;
  email: string;
  displayName: string;
  roles: string[];
}

/** Raw token response from Keycloak token endpoint */
export interface KeycloakTokenResponse {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
  refresh_expires_in: number;
}

/** Payload stored in localStorage */
export interface StoredAuth {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;   // epoch ms
  userInfo: UserInfo;
}

// ─── API Helpers ──────────────────────────────────────────────────────────────

export interface ApiError {
  message: string;
  status?: number;
  code?: string;
}

/** Parameters for paginated list requests */
export interface PageableParams {
  page?: number;
  size?: number;
  sort?: string;
}

// ─── App / UI Types ───────────────────────────────────────────────────────────

export type Role = 'ROLE_ADMIN' | 'ROLE_USER';

export type Theme = 'light' | 'dark';

export interface NavItem {
  label: string;
  path: string;
  icon: string;
  roles?: Role[];  // undefined = accessible to all authenticated users
}
