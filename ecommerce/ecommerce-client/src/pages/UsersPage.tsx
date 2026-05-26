/**
 * Users management page — full CRUD for admin users.
 *
 * Features:
 *  - Server-side pagination via Spring Data Pageable
 *  - Client-side search/filter (debounced)
 *  - Column sorting
 *  - Skeleton loading
 *  - Create / Edit modal with form validation
 *  - Delete confirmation
 *  - React Query cache management (invalidate on mutation)
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Search, Plus, Pencil, Trash2, RefreshCw, AlertCircle } from 'lucide-react'
import toast from 'react-hot-toast'

import { getUsers, createUser, updateUser, deleteUser } from '@/api/userApi'
import { useDebounce } from '@/hooks/useDebounce'
import type { User } from '@/types'
import { DEFAULT_PAGE_SIZE } from '@/constants'

import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Spinner } from '@/components/ui/Spinner'
import { Pagination } from '@/components/ui/Pagination'
import { Modal } from '@/components/ui/Modal'
import { SkeletonTableRow } from '@/components/ui/SkeletonRow'

// ── User form ─────────────────────────────────────────────────────────────────

interface UserFormValues {
  name:  string
  email: string
}

interface UserFormErrors {
  name?:  string
  email?: string
}

function validateUserForm(values: UserFormValues): UserFormErrors {
  const errors: UserFormErrors = {}
  if (!values.name.trim())            errors.name  = 'Name is required'
  if (values.name.length > 200)       errors.name  = 'Name must be at most 200 characters'
  if (!values.email.trim())           errors.email = 'Email is required'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email))
                                      errors.email = 'Must be a valid email address'
  return errors
}

interface UserFormProps {
  initial?:    Partial<UserFormValues>
  onSubmit:   (values: UserFormValues) => void
  isLoading:  boolean
  submitLabel: string
}

function UserForm({ initial, onSubmit, isLoading, submitLabel }: UserFormProps) {
  const [name,   setName]   = useState(initial?.name  ?? '')
  const [email,  setEmail]  = useState(initial?.email ?? '')
  const [errors, setErrors] = useState<UserFormErrors>({})

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const vals = { name: name.trim(), email: email.trim() }
    const errs = validateUserForm(vals)
    if (Object.keys(errs).length > 0) { setErrors(errs); return }
    onSubmit(vals)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Input
        label="Full Name"
        value={name}
        onChange={(e) => { setName(e.target.value); setErrors((p) => ({ ...p, name: undefined })) }}
        error={errors.name}
        placeholder="John Doe"
        autoFocus
      />
      <Input
        label="Email"
        type="email"
        value={email}
        onChange={(e) => { setEmail(e.target.value); setErrors((p) => ({ ...p, email: undefined })) }}
        error={errors.email}
        placeholder="john@example.com"
      />
      <div className="flex justify-end gap-2 pt-2">
        <Button type="submit" isLoading={isLoading}>{submitLabel}</Button>
      </div>
    </form>
  )
}

// ── Delete confirmation dialog ────────────────────────────────────────────────

function DeleteConfirm({
  user,
  onConfirm,
  onCancel,
  isLoading,
}: {
  user:      User
  onConfirm: () => void
  onCancel:  () => void
  isLoading: boolean
}) {
  return (
    <div className="space-y-4">
      <div className="flex items-start gap-3">
        <div className="rounded-full bg-red-100 dark:bg-red-900/30 p-2 shrink-0">
          <AlertCircle size={20} className="text-red-500" />
        </div>
        <div>
          <p className="font-medium text-gray-900 dark:text-gray-100">Delete user?</p>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            <strong>{user.name}</strong> ({user.email}) will be permanently removed.
            This action cannot be undone.
          </p>
        </div>
      </div>
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onCancel} disabled={isLoading}>Cancel</Button>
        <Button variant="danger" onClick={onConfirm} isLoading={isLoading}>
          Delete
        </Button>
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

type SortField = 'name' | 'email'
type SortDir   = 'asc' | 'desc'

export default function UsersPage() {
  const qc = useQueryClient()

  // ── State ──────────────────────────────────────────────────────────────────
  const [page,    setPage]    = useState(0)
  const [size,    setSize]    = useState(DEFAULT_PAGE_SIZE)
  const [search,  setSearch]  = useState('')
  const [sortBy,  setSortBy]  = useState<SortField>('name')
  const [sortDir, setSortDir] = useState<SortDir>('asc')

  const debouncedSearch = useDebounce(search, 350)

  // Modal state
  const [createOpen, setCreateOpen] = useState(false)
  const [editUser,   setEditUser]   = useState<User | null>(null)
  const [deleteUser_, setDeleteUser] = useState<User | null>(null)

  // ── Query ──────────────────────────────────────────────────────────────────
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: ['users', page, size, sortBy, sortDir],
    queryFn:  () => getUsers({ page, size, sort: `${sortBy},${sortDir}` }),
    staleTime: 30_000,
    placeholderData: (prev) => prev,  // keep previous data while fetching next page
  })

  // Client-side filter (because the backend doesn't expose a search param)
  const filtered = debouncedSearch
    ? (data?.content ?? []).filter(
        (u) =>
          u.name.toLowerCase().includes(debouncedSearch.toLowerCase()) ||
          u.email.toLowerCase().includes(debouncedSearch.toLowerCase()),
      )
    : (data?.content ?? [])

  // ── Mutations ──────────────────────────────────────────────────────────────
  const { mutate: doCreate, isPending: creating } = useMutation({
    mutationFn: (vals: UserFormValues) => createUser(vals),
    onSuccess:  () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      qc.invalidateQueries({ queryKey: ['users-count'] })
      setCreateOpen(false)
      toast.success('User created successfully')
    },
    onError: () => toast.error('Failed to create user'),
  })

  const { mutate: doUpdate, isPending: updating } = useMutation({
    mutationFn: (vals: UserFormValues) =>
      updateUser(editUser!.id!, { ...editUser!, ...vals }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      setEditUser(null)
      toast.success('User updated')
    },
    onError: (err: unknown) => {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 409) toast.error('Update conflict — someone else changed this user. Refresh and try again.')
      else                toast.error('Failed to update user')
    },
  })

  const { mutate: doDelete, isPending: deleting } = useMutation({
    mutationFn: () => deleteUser(deleteUser_!.id!),
    onSuccess:  () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      qc.invalidateQueries({ queryKey: ['users-count'] })
      setDeleteUser(null)
      toast.success('User deleted')
    },
    onError: () => toast.error('Failed to delete user'),
  })

  // ── Sort toggle ────────────────────────────────────────────────────────────
  function toggleSort(field: SortField) {
    if (sortBy === field) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    else                  { setSortBy(field); setSortDir('asc') }
    setPage(0)
  }

  function SortIcon({ field }: { field: SortField }) {
    if (sortBy !== field) return <span className="text-gray-300 dark:text-gray-600">↕</span>
    return <span className="text-indigo-600">{sortDir === 'asc' ? '↑' : '↓'}</span>
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-4 max-w-6xl">
      {/* Toolbar */}
      <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-center justify-between">
        <div className="relative w-full sm:w-72">
          <Input
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0) }}
            placeholder="Search by name or email…"
            leftAddon={<Search size={14} />}
          />
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            isLoading={isFetching && !isLoading}
            leftIcon={<RefreshCw size={14} />}
          >
            Refresh
          </Button>
          <Button
            size="sm"
            onClick={() => setCreateOpen(true)}
            leftIcon={<Plus size={14} />}
          >
            New User
          </Button>
        </div>
      </div>

      {/* Table card */}
      <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
        {/* Error state */}
        {isError && (
          <div className="flex items-center gap-2 px-4 py-3 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm border-b border-red-200 dark:border-red-800">
            <AlertCircle size={14} />
            Failed to load users: {String((error as Error)?.message ?? error)}
          </div>
        )}

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50">
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-16">
                  #
                </th>
                <th
                  className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide cursor-pointer hover:text-gray-700 dark:hover:text-gray-200 select-none"
                  onClick={() => toggleSort('name')}
                >
                  Name <SortIcon field="name" />
                </th>
                <th
                  className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide cursor-pointer hover:text-gray-700 dark:hover:text-gray-200 select-none"
                  onClick={() => toggleSort('email')}
                >
                  Email <SortIcon field="email" />
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-24">
                  Version
                </th>
                <th className="px-4 py-3 text-right font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-32">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
              {/* Loading skeletons */}
              {isLoading &&
                Array.from({ length: size }).map((_, i) => (
                  <SkeletonTableRow key={i} cols={5} />
                ))}

              {/* Data rows */}
              {!isLoading &&
                filtered.map((user) => (
                  <tr
                    key={user.id}
                    className="hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors group"
                  >
                    <td className="px-4 py-3 text-gray-400 dark:text-gray-500 font-mono text-xs">
                      {user.id}
                    </td>
                    <td className="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
                      {user.name}
                    </td>
                    <td className="px-4 py-3 text-gray-600 dark:text-gray-300">
                      {user.email}
                    </td>
                    <td className="px-4 py-3 text-gray-400 dark:text-gray-500 font-mono text-xs">
                      v{user.version ?? 0}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setEditUser(user)}
                          className="p-1.5"
                          aria-label={`Edit ${user.name}`}
                        >
                          <Pencil size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setDeleteUser(user)}
                          className="p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20"
                          aria-label={`Delete ${user.name}`}
                        >
                          <Trash2 size={14} />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}

              {/* Empty state */}
              {!isLoading && filtered.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-12 text-center">
                    <div className="flex flex-col items-center gap-2 text-gray-400 dark:text-gray-500">
                      {isFetching ? (
                        <Spinner size="md" />
                      ) : (
                        <>
                          <Search size={24} />
                          <p className="text-sm">
                            {search ? `No users match "${search}"` : 'No users found'}
                          </p>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {data && !isLoading && (
          <div className="border-t border-gray-100 dark:border-gray-700 px-4 py-3">
            <Pagination
              page={data.number}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              pageSize={data.size}
              onPageChange={(p) => { setPage(p); window.scrollTo({ top: 0, behavior: 'smooth' }) }}
              onSizeChange={(s) => { setSize(s); setPage(0) }}
            />
          </div>
        )}
      </div>

      {/* ── Create modal ── */}
      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Create User" size="md">
        <UserForm
          onSubmit={(vals) => doCreate(vals)}
          isLoading={creating}
          submitLabel="Create"
        />
      </Modal>

      {/* ── Edit modal ── */}
      <Modal open={editUser !== null} onClose={() => setEditUser(null)} title="Edit User" size="md">
        {editUser && (
          <UserForm
            initial={{ name: editUser.name, email: editUser.email }}
            onSubmit={(vals) => doUpdate(vals)}
            isLoading={updating}
            submitLabel="Save changes"
          />
        )}
      </Modal>

      {/* ── Delete modal ── */}
      <Modal open={deleteUser_ !== null} onClose={() => setDeleteUser(null)} title="Confirm Deletion" size="sm">
        {deleteUser_ && (
          <DeleteConfirm
            user={deleteUser_}
            onConfirm={() => doDelete()}
            onCancel={() => setDeleteUser(null)}
            isLoading={deleting}
          />
        )}
      </Modal>
    </div>
  )
}
