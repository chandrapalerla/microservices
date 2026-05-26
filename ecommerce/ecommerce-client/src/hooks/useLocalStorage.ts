import { useState, useCallback } from 'react'

/**
 * A useState-compatible hook that syncs state to localStorage.
 * Handles JSON serialization and parse errors gracefully.
 */
export function useLocalStorage<T>(
  key: string,
  initialValue: T,
): [T, (value: T | ((prev: T) => T)) => void, () => void] {
  const [storedValue, setStoredValue] = useState<T>(() => {
    try {
      const item = localStorage.getItem(key)
      return item ? (JSON.parse(item) as T) : initialValue
    } catch {
      return initialValue
    }
  })

  const setValue = useCallback(
    (value: T | ((prev: T) => T)) => {
      setStoredValue((prev) => {
        const next = typeof value === 'function' ? (value as (p: T) => T)(prev) : value
        try {
          localStorage.setItem(key, JSON.stringify(next))
        } catch {
          // Quota exceeded or private mode — continue silently
        }
        return next
      })
    },
    [key],
  )

  const removeValue = useCallback(() => {
    localStorage.removeItem(key)
    setStoredValue(initialValue)
  }, [key, initialValue])

  return [storedValue, setValue, removeValue]
}
