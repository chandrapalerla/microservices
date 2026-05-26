/**
 * Login page.
 * - Validates username + password client-side before making the request.
 * - On success, redirects back to the page the user was trying to reach
 *   (preserved in location.state.from by ProtectedRoute), or /dashboard.
 * - "Remember me" note: tokens are stored in localStorage regardless; a
 *   real "remember me" toggle would differentiate sessionStorage vs localStorage.
 */
import { useState, type FormEvent } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { Eye, EyeOff, LogIn } from 'lucide-react'
import toast from 'react-hot-toast'
import { useAuth } from '@/context/AuthContext'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'

interface FormErrors {
  username?: string
  password?: string
}

export default function LoginPage() {
  const { login } = useAuth()
  const navigate  = useNavigate()
  const location  = useLocation()

  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/dashboard'

  const [username,    setUsername]    = useState('')
  const [password,    setPassword]    = useState('')
  const [showPass,    setShowPass]    = useState(false)
  const [rememberMe,  setRememberMe]  = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errors,      setErrors]      = useState<FormErrors>({})

  // ── Validation ───────────────────────────────────────────────────────────
  function validate(): boolean {
    const next: FormErrors = {}
    if (!username.trim())       next.username = 'Username is required'
    if (username.length > 150)  next.username = 'Username is too long'
    if (!password)              next.password = 'Password is required'
    if (password.length < 3)    next.password = 'Password must be at least 3 characters'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  // ── Submit ────────────────────────────────────────────────────────────────
  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!validate()) return

    setIsSubmitting(true)
    try {
      await login(username.trim(), password, rememberMe)
      toast.success(`Welcome back, ${username}!`)
      navigate(from, { replace: true })
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 401) {
        toast.error('Invalid username or password')
      } else {
        toast.error('Login failed. Please try again.')
        console.error('[LoginPage]', err)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* Heading */}
      <div>
        <h2 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
          Sign in to your account
        </h2>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          Enter your Keycloak credentials to continue
        </p>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <Input
          label="Username"
          type="text"
          autoComplete="username"
          autoFocus
          value={username}
          onChange={(e) => {
            setUsername(e.target.value)
            setErrors((prev) => ({ ...prev, username: undefined }))
          }}
          error={errors.username}
          placeholder="your-username"
        />

        <Input
          label="Password"
          type={showPass ? 'text' : 'password'}
          autoComplete="current-password"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value)
            setErrors((prev) => ({ ...prev, password: undefined }))
          }}
          error={errors.password}
          placeholder="••••••••"
          rightAddon={
            <button
              type="button"
              onClick={() => setShowPass((v) => !v)}
              className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              tabIndex={-1}
              aria-label={showPass ? 'Hide password' : 'Show password'}
            >
              {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          }
        />

        {/* Remember me */}
        <label className="flex items-center gap-2 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={rememberMe}
            onChange={(e) => setRememberMe(e.target.checked)}
            className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 dark:border-gray-600"
          />
          <span className="text-sm text-gray-700 dark:text-gray-300">Remember me</span>
        </label>

        <Button
          type="submit"
          className="w-full"
          isLoading={isSubmitting}
          leftIcon={<LogIn size={16} />}
        >
          {isSubmitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>

      {/* Footer note */}
      <p className="text-center text-xs text-gray-500 dark:text-gray-500">
        Secured by{' '}
        <span className="font-medium text-gray-700 dark:text-gray-400">Keycloak</span>
        {' · '}
        <Link to="/unauthorized" className="text-indigo-500 hover:underline">
          Having trouble?
        </Link>
      </p>
    </div>
  )
}
