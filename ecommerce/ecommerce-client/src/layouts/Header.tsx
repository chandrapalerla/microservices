/**
 * Top header bar: hamburger (mobile), page title, theme toggle, user menu.
 */
import { useState } from 'react'
import { Menu, Sun, Moon, LogOut, User, ChevronDown } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { useTheme } from '@/context/ThemeContext'
import { RoleBadge } from '@/components/ui/Badge'

interface HeaderProps {
  onMenuClick: () => void
  title?:      string
}

export function Header({ onMenuClick, title }: HeaderProps) {
  const { user, logout }     = useAuth()
  const { toggleTheme, isDark } = useTheme()
  const navigate              = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  const handleLogout = async () => {
    setMenuOpen(false)
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className="sticky top-0 z-10 flex h-16 items-center gap-4 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-4 lg:px-6">
      {/* Hamburger — mobile only */}
      <button
        onClick={onMenuClick}
        className="rounded-lg p-2 text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 lg:hidden"
        aria-label="Open menu"
      >
        <Menu size={20} />
      </button>

      {/* Page title */}
      {title && (
        <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100 hidden sm:block">
          {title}
        </h1>
      )}

      <div className="ml-auto flex items-center gap-2">
        {/* Theme toggle */}
        <button
          onClick={toggleTheme}
          className="rounded-lg p-2 text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
        >
          {isDark ? <Sun size={18} /> : <Moon size={18} />}
        </button>

        {/* User menu */}
        <div className="relative">
          <button
            onClick={() => setMenuOpen((o) => !o)}
            className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-800"
          >
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 font-semibold text-sm">
              {user?.displayName?.charAt(0).toUpperCase() ?? 'U'}
            </div>
            <div className="hidden sm:flex flex-col items-start leading-tight">
              <span className="font-medium text-gray-900 dark:text-gray-100 max-w-[120px] truncate">
                {user?.displayName}
              </span>
              <span className="text-xs text-gray-500 dark:text-gray-400 max-w-[120px] truncate">
                {user?.email}
              </span>
            </div>
            <ChevronDown size={14} className="text-gray-400" />
          </button>

          {/* Dropdown */}
          {menuOpen && (
            <>
              <div
                className="fixed inset-0 z-10"
                onClick={() => setMenuOpen(false)}
              />
              <div className="absolute right-0 z-20 mt-1 w-56 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-xl py-1">
                {/* User info */}
                <div className="px-4 py-3 border-b border-gray-100 dark:border-gray-700">
                  <p className="font-medium text-gray-900 dark:text-gray-100 truncate">
                    {user?.displayName}
                  </p>
                  <p className="text-xs text-gray-500 dark:text-gray-400 truncate mt-0.5">
                    {user?.email}
                  </p>
                  <div className="flex gap-1 mt-2 flex-wrap">
                    {user?.roles.map((r) => <RoleBadge key={r} role={r} />)}
                  </div>
                </div>

                {/* Profile link */}
                <button
                  onClick={() => { setMenuOpen(false) }}
                  className="flex w-full items-center gap-2 px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
                >
                  <User size={15} />
                  Profile
                </button>

                {/* Logout */}
                <button
                  onClick={handleLogout}
                  className="flex w-full items-center gap-2 px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
                >
                  <LogOut size={15} />
                  Sign out
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
