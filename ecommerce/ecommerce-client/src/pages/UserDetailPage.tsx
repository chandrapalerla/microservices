/**
 * User detail page — shows a single user's info and allows in-place editing.
 */
import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Pencil, Trash2, Mail, Hash, Lock } from 'lucide-react'
import toast from 'react-hot-toast'

import { getUserById, updateUser, deleteUser } from '@/api/userApi'
import type { User } from '@/types'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Skeleton } from '@/components/ui/SkeletonRow'
import { Modal } from '@/components/ui/Modal'

export default function UserDetailPage() {
  const { id }    = useParams<{ id: string }>()
  const navigate  = useNavigate()
  const qc        = useQueryClient()
  const userId    = Number(id)

  const [editOpen,   setEditOpen]   = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [editName,   setEditName]   = useState('')
  const [editEmail,  setEditEmail]  = useState('')
  const [editErrors, setEditErrors] = useState<{ name?: string; email?: string }>({})

  const { data: user, isLoading, isError } = useQuery({
    queryKey: ['user', userId],
    queryFn:  () => getUserById(userId),
    enabled:  !isNaN(userId),
  })

  const { mutate: doUpdate, isPending: updating } = useMutation({
    mutationFn: (vals: Pick<User, 'name' | 'email'>) =>
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
    setEditName(user?.name  ?? '')
    setEditEmail(user?.email ?? '')
    setEditErrors({})
    setEditOpen(true)
  }

  function handleEditSubmit(e: React.FormEvent) {
    e.preventDefault()
    const errors: { name?: string; email?: string } = {}
    if (!editName.trim())  errors.name  = 'Name is required'
    if (!editEmail.trim()) errors.email = 'Email is required'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editEmail)) errors.email = 'Invalid email'
    if (Object.keys(errors).length > 0) { setEditErrors(errors); return }
    doUpdate({ name: editName.trim(), email: editEmail.trim() })
  }

  if (isError)                    return <p className="text-red-500">Failed to load user.</p>
  if (!isLoading && isNaN(userId)) return <p className="text-red-500">Invalid user ID.</p>

  return (
    <div className="max-w-2xl space-y-6">
      {/* Back link */}
      <Link
        to="/users"
        className="inline-flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
      >
        <ArrowLeft size={14} /> Back to Users
      </Link>

      {/* Card */}
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
          {[
            { icon: Hash,  label: 'ID',      value: user?.id },
            { icon: Lock,  label: 'Version', value: user?.version != null ? `v${user.version}` : null },
            { icon: Mail,  label: 'Email',   value: user?.email },
          ].map(({ icon: Icon, label, value }) => (
            <div key={label} className="flex items-center gap-4 px-6 py-4">
              <div className="w-8 flex justify-center text-gray-400">
                <Icon size={16} />
              </div>
              <dt className="w-24 text-sm text-gray-500 dark:text-gray-400 shrink-0">{label}</dt>
              <dd className="text-sm font-medium text-gray-900 dark:text-gray-100">
                {isLoading ? <Skeleton className="h-4 w-32" /> : (value ?? '—')}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      {/* Edit modal */}
      <Modal open={editOpen} onClose={() => setEditOpen(false)} title="Edit User">
        <form onSubmit={handleEditSubmit} className="space-y-4">
          <Input
            label="Name"
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
