/**
 * User-service API calls, routed via the API Gateway.
 * All requests carry the Bearer token via axiosInstance's request interceptor.
 */
import api from './axiosInstance'
import type { User, Page, PageableParams } from '@/types'

const BASE = '/v1/users'   // axiosInstance already prefixes /api → /api/v1/users

// ─── Read ─────────────────────────────────────────────────────────────────────

/**
 * Fetches a paginated list of users.
 * Spring Data Pageable params: page (0-indexed), size, sort (e.g. "name,asc").
 */
export async function getUsers(params: PageableParams = {}): Promise<Page<User>> {
  const { data } = await api.get<Page<User>>(BASE, { params })
  return data
}

/** Fetches a single user by ID. */
export async function getUserById(id: number): Promise<User> {
  const { data } = await api.get<User>(`${BASE}/${id}`)
  return data
}

/** Fetches the currently authenticated user's info from user-service. */
export async function getCurrentUser(): Promise<Record<string, unknown>> {
  const { data } = await api.get('/v1/auth/me')
  return data
}

// ─── Write ────────────────────────────────────────────────────────────────────

/**
 * Creates a new user.  id and version must be absent / null (backend ignores
 * them on create but validation rejects non-null id).
 */
export async function createUser(dto: Omit<User, 'id' | 'version'>): Promise<User> {
  const { data } = await api.post<User>(BASE, dto)
  return data
}

/**
 * Updates a user.  The `version` field is required for optimistic locking.
 * Returns HTTP 409 if the version doesn't match the current DB row.
 */
export async function updateUser(id: number, dto: User): Promise<User> {
  const { data } = await api.put<User>(`${BASE}/${id}`, dto)
  return data
}

/** Deletes a user by ID (admin only). */
export async function deleteUser(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`)
}
