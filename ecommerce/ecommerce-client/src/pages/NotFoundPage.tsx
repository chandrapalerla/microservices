import { Link } from 'react-router-dom'
import { SearchX } from 'lucide-react'
import { Button } from '@/components/ui/Button'

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-5 text-center p-8 bg-gray-50 dark:bg-gray-950">
      <div className="rounded-full bg-gray-100 dark:bg-gray-800 p-5">
        <SearchX size={40} className="text-gray-400 dark:text-gray-500" />
      </div>
      <div>
        <h1 className="text-5xl font-black text-gray-200 dark:text-gray-700">404</h1>
        <h2 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-2">Page not found</h2>
        <p className="mt-2 text-gray-500 dark:text-gray-400 max-w-sm">
          The page you're looking for doesn't exist or has been moved.
        </p>
      </div>
      <Link to="/dashboard">
        <Button>Back to Dashboard</Button>
      </Link>
    </div>
  )
}
