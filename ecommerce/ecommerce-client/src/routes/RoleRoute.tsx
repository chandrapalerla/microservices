/**
 * Prevents users without the required role from accessing a route.
 * Must be nested inside <ProtectedRoute> (assumes user is already authenticated).
 */
import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'

interface RoleRouteProps {
  /** A single role or array of roles; user must have at least one. */
  roles: string | string[]
}

export function RoleRoute({ roles }: RoleRouteProps) {
  const { hasRole } = useAuth()
  const required = Array.isArray(roles) ? roles : [roles]
  const allowed  = required.some((r) => hasRole(r))

  return allowed ? <Outlet /> : <Navigate to="/unauthorized" replace />
}
