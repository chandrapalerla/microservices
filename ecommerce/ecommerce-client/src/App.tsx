/**
 * Application route tree.
 *
 * Structure:
 *   /                    → redirect to /dashboard
 *   /login               → AuthLayout > LoginPage          (public)
 *   /unauthorized        → UnauthorizedPage                (public)
 *   /*                   → ProtectedRoute                  (requires auth)
 *     /dashboard         → AppLayout > DashboardPage
 *     /users             → RoleRoute(ADMIN) > UsersPage
 *     /users/:id         → RoleRoute(ADMIN) > UserDetailPage
 *   *                    → NotFoundPage
 *
 * All page components are lazily loaded to enable code-splitting per route.
 */
import { Suspense, lazy } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'

import { AuthProvider } from '@/context/AuthContext'
import { ThemeProvider } from '@/context/ThemeContext'
import { ProtectedRoute } from '@/routes/ProtectedRoute'
import { RoleRoute } from '@/routes/RoleRoute'
import { AppLayout } from '@/layouts/AppLayout'
import { AuthLayout } from '@/layouts/AuthLayout'
import { PageSpinner } from '@/components/ui/Spinner'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { ROLES } from '@/constants'

// ── Lazy page imports ────────────────────────────────────────────────────────

const LoginPage       = lazy(() => import('@/pages/LoginPage'))
const DashboardPage   = lazy(() => import('@/pages/DashboardPage'))
const UsersPage       = lazy(() => import('@/pages/UsersPage'))
const UserDetailPage  = lazy(() => import('@/pages/UserDetailPage'))
const UnauthorizedPage = lazy(() => import('@/pages/UnauthorizedPage'))
const NotFoundPage    = lazy(() => import('@/pages/NotFoundPage'))

// ── App ───────────────────────────────────────────────────────────────────────

export default function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider>
        <AuthProvider>
          <BrowserRouter>
            <Suspense fallback={<PageSpinner />}>
              <Routes>
                {/* Default redirect */}
                <Route path="/" element={<Navigate to="/dashboard" replace />} />

                {/* Auth routes (unauthenticated) */}
                <Route element={<AuthLayout />}>
                  <Route path="/login" element={<LoginPage />} />
                </Route>

                {/* Public utility pages */}
                <Route path="/unauthorized" element={<UnauthorizedPage />} />

                {/* Protected routes (require authentication) */}
                <Route element={<ProtectedRoute />}>
                  <Route element={<AppLayout />}>
                    {/* All authenticated users */}
                    <Route path="/dashboard" element={<DashboardPage />} />

                    {/* Admin only */}
                    <Route element={<RoleRoute roles={ROLES.ADMIN} />}>
                      <Route path="/users"     element={<UsersPage />} />
                      <Route path="/users/:id" element={<UserDetailPage />} />
                    </Route>
                  </Route>
                </Route>

                {/* 404 fallback */}
                <Route path="*" element={<NotFoundPage />} />
              </Routes>
            </Suspense>
          </BrowserRouter>
        </AuthProvider>
      </ThemeProvider>
    </ErrorBoundary>
  )
}
