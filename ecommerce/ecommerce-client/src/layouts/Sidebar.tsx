import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard, Users, ShieldCheck, X, Package,
  Tag, ShoppingBag, ClipboardList,
} from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { ROLES, APP_NAME } from '@/constants'
import { cn } from '@/utils/cn'

interface SidebarProps {
  mobileOpen: boolean
  onClose:    () => void
}

const navItems = [
  {
    label: 'Dashboard',
    path:  '/admin/dashboard',
    icon:  LayoutDashboard,
    roles: null,
  },
  {
    label: 'Orders',
    path:  '/admin/orders',
    icon:  ClipboardList,
    roles: [ROLES.ADMIN],
  },
  {
    label: 'Products',
    path:  '/admin/products',
    icon:  ShoppingBag,
    roles: [ROLES.ADMIN],
  },
  {
    label: 'Categories',
    path:  '/admin/categories',
    icon:  Tag,
    roles: [ROLES.ADMIN],
  },
  {
    label: 'Users',
    path:  '/admin/users',
    icon:  Users,
    roles: [ROLES.ADMIN],
  },
]

function NavItem({
  path,
  icon: Icon,
  label,
  onClick,
}: {
  path:    string
  icon:    React.ElementType
  label:   string
  onClick?: () => void
}) {
  return (
    <NavLink
      to={path}
      onClick={onClick}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium',
          'transition-colors duration-150',
          isActive
            ? 'bg-indigo-600/10 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400'
            : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800',
        )
      }
    >
      <Icon size={18} />
      {label}
    </NavLink>
  )
}

function SidebarContent({ onClose }: { onClose?: () => void }) {
  const { hasRole } = useAuth()

  return (
    <div className="flex h-full flex-col">
      {/* Logo */}
      <div className="flex items-center justify-between px-4 py-5 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-2">
          <div className="rounded-lg bg-indigo-600 p-1.5">
            <Package size={18} className="text-white" />
          </div>
          <div>
            <span className="font-bold text-gray-900 dark:text-gray-100">{APP_NAME}</span>
            <p className="text-xs text-gray-400">Admin Panel</p>
          </div>
        </div>
        {onClose && (
          <button
            onClick={onClose}
            className="rounded-lg p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 lg:hidden"
          >
            <X size={20} />
          </button>
        )}
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-0.5">
        {navItems.map((item) => {
          if (item.roles && !item.roles.some((r) => hasRole(r))) return null
          return (
            <NavItem
              key={item.path}
              path={item.path}
              icon={item.icon}
              label={item.label}
              onClick={onClose}
            />
          )
        })}
      </nav>

      {/* Footer */}
      {hasRole(ROLES.ADMIN) && (
        <div className="border-t border-gray-200 dark:border-gray-700 px-4 py-3">
          <div className="flex items-center gap-2 text-xs text-purple-600 dark:text-purple-400">
            <ShieldCheck size={14} />
            <span>Admin access</span>
          </div>
        </div>
      )}
    </div>
  )
}

export function Sidebar({ mobileOpen, onClose }: SidebarProps) {
  return (
    <>
      <aside className="hidden lg:flex lg:flex-col lg:w-64 lg:fixed lg:inset-y-0 lg:z-20 bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700">
        <SidebarContent />
      </aside>

      {mobileOpen && (
        <>
          <div className="fixed inset-0 z-30 bg-black/50 lg:hidden" onClick={onClose} />
          <aside className="fixed inset-y-0 left-0 z-40 w-64 bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 lg:hidden">
            <SidebarContent onClose={onClose} />
          </aside>
        </>
      )}
    </>
  )
}
