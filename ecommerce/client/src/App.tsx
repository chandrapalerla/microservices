import { Suspense, lazy } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'

import { AuthProvider }     from '@/context/AuthContext'
import { ThemeProvider }    from '@/context/ThemeContext'
import { CartProvider }     from '@/context/CartContext'
import { ProtectedRoute }   from '@/routes/ProtectedRoute'
import { RoleRoute }        from '@/routes/RoleRoute'
import { AppLayout }        from '@/layouts/AppLayout'
import { AuthLayout }       from '@/layouts/AuthLayout'
import { StorefrontLayout } from '@/layouts/StorefrontLayout'
import { PageSpinner }      from '@/components/ui/Spinner'
import { ErrorBoundary }    from '@/components/ErrorBoundary'
import { ROLES }            from '@/constants'

// ── Storefront pages ─────────────────────────────────────────────────────────

const HomePage             = lazy(() => import('@/pages/HomePage'))
const ShopPage             = lazy(() => import('@/pages/ShopPage'))
const ProductDetailPage    = lazy(() => import('@/pages/ProductDetailPage'))
const CartPage             = lazy(() => import('@/pages/CartPage'))
const CheckoutPage         = lazy(() => import('@/pages/CheckoutPage'))
const OrderConfirmPage     = lazy(() => import('@/pages/OrderConfirmationPage'))
const MyOrdersPage         = lazy(() => import('@/pages/MyOrdersPage'))
const OrderDetailPage      = lazy(() => import('@/pages/OrderDetailPage'))
const ProfilePage          = lazy(() => import('@/pages/ProfilePage'))

// ── Admin pages ───────────────────────────────────────────────────────────────

const DashboardPage        = lazy(() => import('@/pages/DashboardPage'))
const UsersPage            = lazy(() => import('@/pages/UsersPage'))
const UserDetailPage       = lazy(() => import('@/pages/UserDetailPage'))
const AdminProductsPage    = lazy(() => import('@/pages/admin/AdminProductsPage'))
const AdminCategoriesPage  = lazy(() => import('@/pages/admin/AdminCategoriesPage'))
const AdminOrdersPage      = lazy(() => import('@/pages/admin/AdminOrdersPage'))

// ── Utility pages ─────────────────────────────────────────────────────────────

const LoginPage            = lazy(() => import('@/pages/LoginPage'))
const UnauthorizedPage     = lazy(() => import('@/pages/UnauthorizedPage'))
const NotFoundPage         = lazy(() => import('@/pages/NotFoundPage'))

// ── App ───────────────────────────────────────────────────────────────────────

export default function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider>
        <AuthProvider>
          <CartProvider>
            <BrowserRouter>
              <Suspense fallback={<PageSpinner />}>
                <Routes>

                  {/* ── Auth ─────────────────────────────────────────────── */}
                  <Route element={<AuthLayout />}>
                    <Route path="/login" element={<LoginPage />} />
                  </Route>

                  {/* ── Utility (public) ─────────────────────────────────── */}
                  <Route path="/unauthorized" element={<UnauthorizedPage />} />

                  {/* ── Storefront (public) ──────────────────────────────── */}
                  <Route element={<StorefrontLayout />}>
                    <Route index                element={<HomePage />} />
                    <Route path="/shop"         element={<ShopPage />} />
                    <Route path="/shop/:id"     element={<ProductDetailPage />} />
                    <Route path="/cart"         element={<CartPage />} />

                    {/* Storefront protected — must be logged in */}
                    <Route element={<ProtectedRoute />}>
                      <Route path="/checkout"              element={<CheckoutPage />} />
                      <Route path="/orders"                element={<MyOrdersPage />} />
                      <Route path="/orders/confirmation"   element={<OrderConfirmPage />} />
                      <Route path="/orders/:id"            element={<OrderDetailPage />} />
                      <Route path="/profile"               element={<ProfilePage />} />
                    </Route>
                  </Route>

                  {/* ── Admin area ───────────────────────────────────────── */}
                  <Route element={<ProtectedRoute />}>
                    <Route element={<RoleRoute roles={ROLES.ADMIN} />}>
                      <Route element={<AppLayout />}>
                        <Route path="/admin"            element={<Navigate to="/admin/dashboard" replace />} />
                        <Route path="/admin/dashboard"  element={<DashboardPage />} />
                        <Route path="/admin/orders"     element={<AdminOrdersPage />} />
                        <Route path="/admin/products"   element={<AdminProductsPage />} />
                        <Route path="/admin/categories" element={<AdminCategoriesPage />} />
                        <Route path="/admin/users"      element={<UsersPage />} />
                        <Route path="/admin/users/:id"  element={<UserDetailPage />} />
                      </Route>
                    </Route>
                  </Route>

                  {/* ── Legacy redirects ─────────────────────────────────── */}
                  <Route path="/dashboard" element={<Navigate to="/admin/dashboard" replace />} />

                  {/* ── 404 ──────────────────────────────────────────────── */}
                  <Route path="*" element={<NotFoundPage />} />

                </Routes>
              </Suspense>
            </BrowserRouter>
          </CartProvider>
        </AuthProvider>
      </ThemeProvider>
    </ErrorBoundary>
  )
}
