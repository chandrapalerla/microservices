/**
 * Prevents unauthenticated users from reaching secured routes.
 * Redirects to /login and preserves the originally requested path
 * in `location.state` so the login page can redirect back after success.
 */
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { PageSpinner } from '@/components/ui/Spinner'

export function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  // Still bootstrapping from localStorage — render a spinner instead of
  // briefly flashing the login page.
  if (isLoading) return <PageSpinner />

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location }}
      />
    )
  }

  return <Outlet />
}
