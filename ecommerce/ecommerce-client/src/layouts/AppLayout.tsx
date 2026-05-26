/**
 * Main authenticated layout: Sidebar + Header + scrollable content area.
 * Wraps every protected page via the route tree.
 */
import { useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Header } from './Header'
import { ErrorBoundary } from '@/components/ErrorBoundary'

/** Derive a human-readable page title from the current URL path. */
function deriveTitle(pathname: string): string {
  const segment = pathname.split('/').filter(Boolean)[0] ?? ''
  return segment.charAt(0).toUpperCase() + segment.slice(1)
}

export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const location = useLocation()

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      {/* Sidebar */}
      <Sidebar
        mobileOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      {/* Main area — offset by sidebar width on large screens */}
      <div className="lg:pl-64 flex flex-col min-h-screen">
        <Header
          onMenuClick={() => setSidebarOpen(true)}
          title={deriveTitle(location.pathname)}
        />

        {/* Page content */}
        <main className="flex-1 p-4 lg:p-6">
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </main>

        {/* Footer */}
        <footer className="px-6 py-3 text-xs text-center text-gray-400 dark:text-gray-600 border-t border-gray-100 dark:border-gray-800">
          ECommerce Admin © {new Date().getFullYear()}
        </footer>
      </div>
    </div>
  )
}
