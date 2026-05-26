/**
 * Centered two-column layout for auth pages (login, register).
 * Left: branding panel (hidden on small screens).
 * Right: form content.
 */
import { Outlet } from 'react-router-dom'
import { Package } from 'lucide-react'
import { APP_NAME } from '@/constants'

export function AuthLayout() {
  return (
    <div className="min-h-screen flex bg-gray-50 dark:bg-gray-950">
      {/* Left branding panel — hidden on mobile */}
      <div className="hidden lg:flex lg:w-1/2 bg-indigo-600 dark:bg-indigo-800 flex-col items-center justify-center p-12 text-white relative overflow-hidden">
        {/* Decorative circles */}
        <div className="absolute -top-20 -left-20 h-80 w-80 rounded-full bg-white/5" />
        <div className="absolute -bottom-20 -right-20 h-80 w-80 rounded-full bg-white/5" />

        <div className="relative z-10 text-center space-y-6 max-w-sm">
          <div className="flex justify-center">
            <div className="rounded-2xl bg-white/10 p-5">
              <Package size={48} className="text-white" />
            </div>
          </div>
          <h1 className="text-4xl font-bold">{APP_NAME}</h1>
          <p className="text-indigo-100 text-lg">
            Manage your microservices platform from one unified dashboard.
          </p>
          <div className="grid grid-cols-3 gap-4 pt-4">
            {[
              { label: 'Services', value: '3' },
              { label: 'APIs',     value: '12' },
              { label: 'Uptime',   value: '99.9%' },
            ].map((stat) => (
              <div key={stat.label} className="rounded-xl bg-white/10 p-3">
                <p className="text-2xl font-bold">{stat.value}</p>
                <p className="text-xs text-indigo-200">{stat.label}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right form area */}
      <div className="flex flex-1 flex-col items-center justify-center p-6">
        {/* Mobile logo */}
        <div className="flex items-center gap-2 mb-8 lg:hidden">
          <div className="rounded-lg bg-indigo-600 p-2">
            <Package size={20} className="text-white" />
          </div>
          <span className="font-bold text-xl text-gray-900 dark:text-gray-100">{APP_NAME}</span>
        </div>

        <div className="w-full max-w-sm">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
