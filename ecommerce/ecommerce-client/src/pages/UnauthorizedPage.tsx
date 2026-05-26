import { Link } from 'react-router-dom'
import { ShieldOff } from 'lucide-react'
import { Button } from '@/components/ui/Button'

export default function UnauthorizedPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-5 text-center p-8">
      <div className="rounded-full bg-amber-100 dark:bg-amber-900/30 p-5">
        <ShieldOff size={40} className="text-amber-500" />
      </div>
      <div>
        <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100">403 — Unauthorized</h1>
        <p className="mt-2 text-gray-500 dark:text-gray-400 max-w-sm">
          You don't have permission to access this page. Contact your administrator if you think
          this is a mistake.
        </p>
      </div>
      <div className="flex gap-3">
        <Link to="/dashboard">
          <Button variant="primary">Go to Dashboard</Button>
        </Link>
      </div>
    </div>
  )
}
