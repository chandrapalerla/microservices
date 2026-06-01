import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Pencil, Trash2, Mail, Hash, Lock, Phone, Shield, Activity, Calendar, Clock } from 'lucide-react'
import toast from 'react-hot-toast'

import { getUserById, updateUser, deleteUser } from '@/api/userApi'
import type { User } from '@/types'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Skeleton } from '@/components/ui/SkeletonRow'
import { Modal } from '@/components/ui/Modal'
import { formatDateTime } from '@/utils/formatDate'

const SELECT_CLS = 'w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500'

function RoleBadge({ role }: { role?: string }) {
  const cls = role === 'ADMIN'
    ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400'
    : 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
  return <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${cls}`}>{role ?? '—'}</span>
}

function StatusBadge({ status }: { status?: string }) {
  const cls = status === 'ACTIVE'
    ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    : status === 'SUSPENDED'
    ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    : 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
  return <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${cls}`}>{status ?? '—'}</span>
}

export default function UserDetailPage() {
  const { id }   = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc       = useQueryClient()
  const userId   = Number(id)

  const [editOpen,   setEditOpen]   = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const [editName,   setEditName]   = useState('')
  const [editEmail,  setEditEmail]  = useState('')
  const [editPhone,  setEditPhone]  = useState('')
  const [editRole,   setEditRole]   = useState<'USER' | 'ADMIN'>('USER')
  const [editStatus, setEditStatus] = useState<'ACTIVE' | 'INACTIVE' | 'SUSPENDED'>('ACTIVE')
  const [editErrors, setEditErrors] = useState<{ name?: string; email?: string; phone?: string }>({})

  const { data: user, isLoading, isError } = useQuery({
    queryKey: ['user', userId],
    queryFn:  () => getUserById(userId),
    enabled:  !isNaN(userId),
  })

  const { mutate: doUpdate, isPending: updating } = useMutation({
    mutationFn: (vals: Pick<User, 'name' | 'email' | 'phone' | 'role' | 'status'>) =>
      updateUser(userId, { ...user!, ...vals }),
    onSuccess: (updated) => {
      qc.setQueryData(['user', userId], updated)
      qc.invalidateQueries({ queryKey: ['users'] })
      setEditOpen(false)
      toast.success('User updated successfully')
    },
    onError: (err: unknown) => {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 409) toast.error('Conflict — please refresh and try again.')
      else                toast.error('Failed to update user')
    },
  })

  const { mutate: doDelete, isPending: deleting } = useMutation({
    mutationFn: () => deleteUser(userId),
    onSuccess:  () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      toast.success('User deleted')
      navigate('/users', { replace: true })
    },
    onError: () => toast.error('Failed to delete user'),
  })

  function openEdit() {
    setEditName(user?.name   ?? '')
    setEditEmail(user?.email ?? '')
    setEditPhone(user?.phone ?? '')
    setEditRole(user?.role   ?? 'USER')
    setEditStatus(user?.status ?? 'ACTIVE')
    setEditErrors({})
    setEditOpen(true)
  }

  function handleEditSubmit(e: React.FormEvent) {
    e.preventDefault()
    const errors: { name?: string; email?: string; phone?: string } = {}
    if (!editName.trim())  errors.name  = 'Name is required'
    if (!editEmail.trim()) errors.email = 'Email is required'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editEmail)) errors.email = 'Invalid email'
    if (editPhone && !/^[+\d][\d\s\-().]{6,19}$/.test(editPhone))
      errors.phone = 'Phone: 7–20 chars, digits/spaces/+-() only'
    if (Object.keys(errors).length > 0) { setEditErrors(errors); return }
    doUpdate({
      name:   editName.trim(),
      email:  editEmail.trim(),
      phone:  editPhone.trim() || undefined,
      role:   editRole,
      status: editStatus,
    })
  }

  if (isError)                     return <p className="text-red-500">Failed to load user.</p>
  if (!isLoading && isNaN(userId)) return <p className="text-red-500">Invalid user ID.</p>

  const fields = [
    { icon: Hash,     label: 'ID',         value: user?.id },
    { icon: Lock,     label: 'Version',     value: user?.version != null ? `v${user.version}` : null },
    { icon: Mail,     label: 'Email',       value: user?.email },
    { icon: Phone,    label: 'Phone',       value: user?.phone || '—' },
    { icon: Shield,   label: 'Role',        value: user ? <RoleBadge role={user.role} /> : null },
    { icon: Activity, label: 'Status',      value: user ? <StatusBadge status={user.status} /> : null },
    { icon: Calendar, label: 'Created',     value: user?.createdAt ? formatDateTime(user.createdAt) : '—' },
    { icon: Clock,    label: 'Last Updated', value: user?.updatedAt ? formatDateTime(user.updatedAt) : '—' },
  ]

  return (
    <div className="max-w-2xl space-y-6">
      <Link
        to="/users"
        className="inline-flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
      >
        <ArrowLeft size={14} /> Back to Users
      </Link>

      <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
        {/* Header */}
        <div className="px-6 py-5 border-b border-gray-100 dark:border-gray-700 flex items-start justify-between gap-4">
          {isLoading ? (
            <div className="space-y-2 flex-1">
              <Skeleton className="h-6 w-40" />
              <Skeleton className="h-4 w-56" />
            </div>
          ) : (
            <div className="flex items-center gap-4">
              <div className="h-14 w-14 rounded-full bg-indigo-100 dark:bg-indigo-900/40 flex items-center justify-center text-indigo-600 dark:text-indigo-400 text-xl font-bold">
                {user?.name?.charAt(0).toUpperCase()}
              </div>
              <div>
                <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100">{user?.name}</h2>
                <p className="text-sm text-gray-500 dark:text-gray-400">{user?.email}</p>
                <div className="flex gap-2 mt-1">
                  <RoleBadge role={user?.role} />
                  <StatusBadge status={user?.status} />
                </div>
              </div>
            </div>
          )}

          <div className="flex gap-2 shrink-0">
            <Button variant="outline" size="sm" onClick={openEdit} leftIcon={<Pencil size={13} />}>
              Edit
            </Button>
            <Button variant="danger" size="sm" onClick={() => setDeleteOpen(true)} leftIcon={<Trash2 size={13} />}>
              Delete
            </Button>
          </div>
        </div>

        {/* Fields */}
        <dl className="divide-y divide-gray-100 dark:divide-gray-700">
          {fields.map(({ icon: Icon, label, value }) => (
            <div key={label} className="flex items-center gap-4 px-6 py-4">
              <div className="w-8 flex justify-center text-gray-400">
                <Icon size={16} />
              </div>
              <dt className="w-28 text-sm text-gray-500 dark:text-gray-400 shrink-0">{label}</dt>
              <dd className="text-sm font-medium text-gray-900 dark:text-gray-100">
                {isLoading ? <Skeleton className="h-4 w-32" /> : (value ?? '—')}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      {/* Edit modal */}
      <Modal open={editOpen} onClose={() => setEditOpen(false)} title="Edit User" size="md">
        <form onSubmit={handleEditSubmit} className="space-y-4">
          <Input
            label="Full Name"
            value={editName}
            onChange={(e) => { setEditName(e.target.value); setEditErrors((p) => ({ ...p, name: undefined })) }}
            error={editErrors.name}
            autoFocus
          />
          <Input
            label="Email"
            type="email"
            value={editEmail}
            onChange={(e) => { setEditEmail(e.target.value); setEditErrors((p) => ({ ...p, email: undefined })) }}
            error={editErrors.email}
          />
          <Input
            label="Phone (optional)"
            type="tel"
            value={editPhone}
            onChange={(e) => { setEditPhone(e.target.value); setEditErrors((p) => ({ ...p, phone: undefined })) }}
            error={editErrors.phone}
            placeholder="+91-9876543210"
          />
          <div className="flex gap-4">
            <div className="flex-1 flex flex-col gap-1">
              <label className="text-sm font-medium text-gray-700 dark:text-gray-300">Role</label>
              <select value={editRole} onChange={(e) => setEditRole(e.target.value as 'USER' | 'ADMIN')} className={SELECT_CLS}>
                <option value="USER">USER</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </div>
            <div className="flex-1 flex flex-col gap-1">
              <label className="text-sm font-medium text-gray-700 dark:text-gray-300">Status</label>
              <select value={editStatus} onChange={(e) => setEditStatus(e.target.value as 'ACTIVE' | 'INACTIVE' | 'SUSPENDED')} className={SELECT_CLS}>
                <option value="ACTIVE">ACTIVE</option>
                <option value="INACTIVE">INACTIVE</option>
                <option value="SUSPENDED">SUSPENDED</option>
              </select>
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setEditOpen(false)}>Cancel</Button>
            <Button type="submit" isLoading={updating}>Save changes</Button>
          </div>
        </form>
      </Modal>

      {/* Delete modal */}
      <Modal open={deleteOpen} onClose={() => setDeleteOpen(false)} title="Delete User" size="sm">
        <div className="space-y-4">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            Are you sure you want to delete <strong>{user?.name}</strong>? This cannot be undone.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setDeleteOpen(false)} disabled={deleting}>Cancel</Button>
            <Button variant="danger" onClick={() => doDelete()} isLoading={deleting}>Delete</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
