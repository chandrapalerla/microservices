/**
 * Application entry point.
 *
 * Provider order (outermost → innermost):
 *   StrictMode          — double-renders in dev to surface side-effect bugs
 *   QueryClientProvider — React Query cache
 *   App                 — ThemeProvider, AuthProvider, BrowserRouter (see App.tsx)
 *   Toaster             — react-hot-toast global notification container
 */
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { Toaster } from 'react-hot-toast'
import App from './App.tsx'
import './index.css'

// ── React Query client ────────────────────────────────────────────────────────

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: import.meta.env.PROD,
      staleTime: 30_000,
      retry: 1,
      retryDelay: 1_000,
    },
    mutations: {
      retry: 0,
    },
  },
})

// ── Mount ─────────────────────────────────────────────────────────────────────

const rootEl = document.getElementById('root')
if (!rootEl) throw new Error('#root element not found in index.html')

createRoot(rootEl).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />

      {/* Global toast notifications */}
      <Toaster
        position="top-right"
        gutter={8}
        toastOptions={{
          duration: 4000,
          style: { borderRadius: '0.75rem', fontSize: '0.875rem' },
          success: { iconTheme: { primary: '#4f46e5', secondary: '#fff' } },
        }}
      />

      {/* Query devtools — only bundled in dev */}
      {import.meta.env.DEV && (
        <ReactQueryDevtools initialIsOpen={false} buttonPosition="bottom-right" />
      )}
    </QueryClientProvider>
  </StrictMode>,
)
