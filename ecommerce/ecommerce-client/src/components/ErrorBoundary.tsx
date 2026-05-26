/**
 * Class-based error boundary — catches unhandled render errors so the entire
 * app doesn't blank-out on a component-level exception.
 *
 * WHY class component: React error boundaries can only be implemented as class
 * components (as of React 18); there is no hook equivalent for componentDidCatch.
 */
import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle } from 'lucide-react'
import { Button } from './ui/Button'

interface Props {
  children:  ReactNode
  fallback?: ReactNode  // optional custom fallback UI
}

interface State {
  hasError: boolean
  error:    Error | null
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // In production, send to your logging service (Sentry, Datadog, etc.)
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  reset = () => this.setState({ hasError: false, error: null })

  render() {
    if (!this.state.hasError) return this.props.children

    if (this.props.fallback) return this.props.fallback

    return (
      <div className="flex min-h-[400px] flex-col items-center justify-center gap-4 p-8 text-center">
        <div className="rounded-full bg-red-100 p-4 dark:bg-red-900/30">
          <AlertTriangle className="h-8 w-8 text-red-500" />
        </div>
        <div>
          <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
            Something went wrong
          </h2>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {this.state.error?.message ?? 'An unexpected error occurred.'}
          </p>
        </div>
        <Button variant="outline" onClick={this.reset}>
          Try again
        </Button>
      </div>
    )
  }
}
