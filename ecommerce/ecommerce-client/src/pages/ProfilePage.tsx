import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { User, Mail, Phone, Shield, Calendar, Edit2, Check, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { getMyProfile, updateUser } from '@/api/userApi'
import { Spinner } from '@/components/ui/Spinner'
import { formatDate } from '@/utils/formatDate'

export default function ProfilePage() {
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')

  const { data: profile, isLoading } = useQuery({
    queryKey: ['my-profile'],
    queryFn:  getMyProfile,
    staleTime: 300_000,
  })

  useEffect(() => {
    if (profile) {
      setName(profile.name ?? '')
      setPhone(profile.phone ?? '')
    }
  }, [profile])

  const { mutate: saveProfile, isPending: saving } = useMutation({
    mutationFn: () => {
      const uid = profile?.id
      if (!profile || !uid) throw new Error('Profile not loaded')
      return updateUser(uid, { ...profile, name: name.trim(), phone: phone.trim() })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['my-profile'] })
      setEditing(false)
      toast.success('Profile updated')
    },
    onError: () => toast.error('Failed to update profile'),
  })

  if (isLoading) {
    return <div className="flex justify-center py-24"><Spinner size="lg" /></div>
  }

  if (!profile) {
    return <div className="text-center py-24 text-gray-500">Could not load profile.</div>
  }

  const roleColor = profile.role === 'ADMIN'
    ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400'
    : 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'

  const statusColor = profile.status === 'ACTIVE'
    ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'

  return (
    <div className="max-w-[700px] mx-auto px-4 py-8 space-y-6">
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">My Account</h1>

      {/* Profile card */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
        {/* Banner */}
        <div className="h-24 bg-gradient-to-r from-[#131921] to-[#232f3e]" />

        {/* Avatar + edit button */}
        <div className="px-6 pb-5">
          <div className="flex items-end justify-between -mt-10 mb-4">
            <div className="w-20 h-20 rounded-full bg-[#FF9900] border-4 border-white dark:border-gray-800 flex items-center justify-center text-[#131921] text-2xl font-bold">
              {profile.name?.charAt(0).toUpperCase() ?? 'U'}
            </div>
            {!editing ? (
              <button
                onClick={() => setEditing(true)}
                className="flex items-center gap-1.5 text-sm text-[#FF9900] hover:underline border border-[#FF9900]/30 px-3 py-1.5 rounded-full"
              >
                <Edit2 size={13} /> Edit Profile
              </button>
            ) : (
              <div className="flex gap-2">
                <button
                  onClick={() => saveProfile()}
                  disabled={saving}
                  className="flex items-center gap-1 text-sm bg-green-600 text-white px-3 py-1.5 rounded-full hover:bg-green-700 disabled:opacity-60"
                >
                  <Check size={13} /> Save
                </button>
                <button
                  onClick={() => { setEditing(false); setName(profile.name ?? ''); setPhone(profile.phone ?? '') }}
                  className="flex items-center gap-1 text-sm border border-gray-300 dark:border-gray-600 px-3 py-1.5 rounded-full hover:bg-gray-50 dark:hover:bg-gray-700"
                >
                  <X size={13} /> Cancel
                </button>
              </div>
            )}
          </div>

          {editing ? (
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Full Name</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-[#FF9900]/50"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Phone</label>
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-[#FF9900]/50"
                />
              </div>
            </div>
          ) : (
            <>
              <h2 className="text-xl font-bold text-gray-900 dark:text-gray-100">{profile.name}</h2>
              <p className="text-gray-500 text-sm">{profile.email}</p>
              <div className="flex gap-2 mt-2">
                <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${roleColor}`}>
                  <Shield size={10} className="inline mr-0.5" /> {profile.role}
                </span>
                <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${statusColor}`}>
                  {profile.status}
                </span>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Details card */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
        <h3 className="font-semibold text-gray-900 dark:text-gray-100 mb-4">Account Details</h3>
        <dl className="space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-blue-50 dark:bg-blue-900/20 flex items-center justify-center shrink-0">
              <User size={16} className="text-blue-500" />
            </div>
            <div>
              <dt className="text-xs text-gray-500 dark:text-gray-400">Full Name</dt>
              <dd className="text-sm font-medium text-gray-900 dark:text-gray-100">{profile.name || '—'}</dd>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-green-50 dark:bg-green-900/20 flex items-center justify-center shrink-0">
              <Mail size={16} className="text-green-500" />
            </div>
            <div>
              <dt className="text-xs text-gray-500 dark:text-gray-400">Email Address</dt>
              <dd className="text-sm font-medium text-gray-900 dark:text-gray-100">{profile.email}</dd>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-orange-50 dark:bg-orange-900/20 flex items-center justify-center shrink-0">
              <Phone size={16} className="text-orange-500" />
            </div>
            <div>
              <dt className="text-xs text-gray-500 dark:text-gray-400">Phone Number</dt>
              <dd className="text-sm font-medium text-gray-900 dark:text-gray-100">{profile.phone || '—'}</dd>
            </div>
          </div>
          {profile.createdAt && (
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-lg bg-purple-50 dark:bg-purple-900/20 flex items-center justify-center shrink-0">
                <Calendar size={16} className="text-purple-500" />
              </div>
              <div>
                <dt className="text-xs text-gray-500 dark:text-gray-400">Member Since</dt>
                <dd className="text-sm font-medium text-gray-900 dark:text-gray-100">{formatDate(profile.createdAt)}</dd>
              </div>
            </div>
          )}
        </dl>
      </div>
    </div>
  )
}
