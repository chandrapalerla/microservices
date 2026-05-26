/**
 * Dashboard — welcome screen with summary stats and quick actions.
 */
import { useQuery } from '@tanstack/react-query'
import { Users, Activity, Shield, TrendingUp } from 'lucide-react'
import { useAuth } from '@/context/AuthContext'
import { getUsers } from '@/api/userApi'
import { ROLES } from '@/constants'
import { SkeletonCard } from '@/components/ui/SkeletonRow'
import { RoleBadge } from '@/components/ui/Badge'
import { Link } from 'react-router-dom'

// ── Stat card ──────────────────────────────────────────────────────────────────

interface StatCardProps {
  label: string
  value: string | number
  icon:  React.ElementType
  color: string
  hint?: string
}

function StatCard({ label, value, icon: Icon, color, hint }: StatCardProps) {
  return (
    <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 flex items-start gap-4">
      <div className={`rounded-lg p-2.5 ${color}`}>
        <Icon size={20} className="text-white" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-gray-500 dark:text-gray-400">{label}</p>
        <p className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-0.5">{value}</p>
        {hint && <p className="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{hint}</p>}
      </div>
    </div>
  )
}

// ── Page ───────────────────────────────────────────────────────────────────────

export default function DashboardPage() {
  const { user, hasRole } = useAuth()
  const isAdmin = hasRole(ROLES.ADMIN)

  // Prefetch total user count (admin only — non-admins can't call /users)
  const { data: usersPage, isLoading: usersLoading } = useQuery({
    queryKey: ['users-count'],
    queryFn:  () => getUsers({ page: 0, size: 1 }),
    enabled:  isAdmin,
    staleTime: 60_000,
  })

  const totalUsers = usersPage?.totalElements ?? '—'

  const greeting = (() => {
    const h = new Date().getHours()
    if (h < 12) return 'Good morning'
    if (h < 18) return 'Good afternoon'
    return 'Good evening'
  })()

  return (
    <div className="space-y-6 max-w-5xl">
      {/* Welcome banner */}
      <div className="rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-400 dark:from-indigo-700 dark:to-indigo-500 p-6 text-white">
        <p className="text-indigo-100 text-sm">{greeting},</p>
        <h2 className="text-2xl font-bold mt-1">{user?.displayName ?? user?.username}</h2>
        <div className="flex gap-2 mt-3 flex-wrap">
          {user?.roles.map((r) => <RoleBadge key={r} role={r} />)}
        </div>
        <p className="text-indigo-100 text-sm mt-3">
          {isAdmin
            ? 'You have full administrative access to the platform.'
            : 'Welcome to the ECommerce dashboard.'}
        </p>
      </div>

      {/* Stats grid */}
      <div>
        <h3 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
          Overview
        </h3>
        {usersLoading && isAdmin ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} />)}
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {isAdmin && (
              <StatCard
                label="Total Users"
                value={totalUsers}
                icon={Users}
                color="bg-indigo-500"
                hint="Registered in MySQL"
              />
            )}
            <StatCard
              label="Services"
              value={3}
              icon={Activity}
              color="bg-green-500"
              hint="Eureka + Gateway + User"
            />
            <StatCard
              label="Your Role"
              value={isAdmin ? 'Admin' : 'User'}
              icon={Shield}
              color={isAdmin ? 'bg-purple-500' : 'bg-blue-500'}
            />
            <StatCard
              label="API Gateway"
              value="Port 2027"
              icon={TrendingUp}
              color="bg-amber-500"
              hint="Keycloak secured"
            />
          </div>
        )}
      </div>

      {/* Quick actions (admin only) */}
      {isAdmin && (
        <div>
          <h3 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
            Quick Actions
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Link
              to="/users"
              className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 hover:border-indigo-400 dark:hover:border-indigo-500 transition-colors group"
            >
              <div className="flex items-center gap-3">
                <div className="rounded-lg bg-indigo-50 dark:bg-indigo-900/30 p-2.5 group-hover:bg-indigo-100 dark:group-hover:bg-indigo-900/50 transition-colors">
                  <Users size={18} className="text-indigo-600 dark:text-indigo-400" />
                </div>
                <div>
                  <p className="font-medium text-gray-900 dark:text-gray-100">Manage Users</p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">View, create, update, delete</p>
                </div>
              </div>
            </Link>

            <a
              href="http://localhost:2026/swagger-ui/index.html"
              target="_blank"
              rel="noopener noreferrer"
              className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 hover:border-green-400 dark:hover:border-green-500 transition-colors group"
            >
              <div className="flex items-center gap-3">
                <div className="rounded-lg bg-green-50 dark:bg-green-900/30 p-2.5 group-hover:bg-green-100 transition-colors">
                  <Activity size={18} className="text-green-600 dark:text-green-400" />
                </div>
                <div>
                  <p className="font-medium text-gray-900 dark:text-gray-100">API Docs</p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">Swagger UI (user-service)</p>
                </div>
              </div>
            </a>
          </div>
        </div>
      )}

      {/* Session info */}
      <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-5">
        <h3 className="font-medium text-gray-900 dark:text-gray-100 mb-3">Session Info</h3>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <dt className="text-gray-500 dark:text-gray-400">Username</dt>
          <dd className="font-medium text-gray-900 dark:text-gray-100">{user?.username}</dd>
          <dt className="text-gray-500 dark:text-gray-400">Email</dt>
          <dd className="font-medium text-gray-900 dark:text-gray-100">{user?.email || '—'}</dd>
          <dt className="text-gray-500 dark:text-gray-400">Roles</dt>
          <dd className="flex gap-1 flex-wrap">
            {user?.roles.map((r) => <RoleBadge key={r} role={r} />)}
          </dd>
        </dl>
      </div>
    </div>
  )
}
