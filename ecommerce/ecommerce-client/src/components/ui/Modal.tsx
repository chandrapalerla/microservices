/**
 * Accessible modal dialog using a native <dialog> element.
 * Opens/closes via the `open` prop; backdrop click dismisses.
 */
import React, { useEffect, useRef } from 'react'
import { X } from 'lucide-react'
import { cn } from '@/utils/cn'

interface ModalProps {
  open:       boolean
  onClose:    () => void
  title?:     string
  children:   React.ReactNode
  className?: string
  size?:      'sm' | 'md' | 'lg' | 'xl'
}

const sizeClasses = {
  sm:  'max-w-sm',
  md:  'max-w-md',
  lg:  'max-w-lg',
  xl:  'max-w-2xl',
}

export function Modal({ open, onClose, title, children, className, size = 'md' }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return

    if (open && !dialog.open) {
      dialog.showModal()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  // Dismiss on backdrop click
  const handleClick = (e: React.MouseEvent<HTMLDialogElement>) => {
    if (e.target === dialogRef.current) onClose()
  }

  return (
    <dialog
      ref={dialogRef}
      onClick={handleClick}
      onClose={onClose}
      className={cn(
        'rounded-xl shadow-2xl border-0 p-0',
        'bg-white dark:bg-gray-800',
        'text-gray-900 dark:text-gray-100',
        'backdrop:bg-black/50 backdrop:backdrop-blur-sm',
        'w-full',
        sizeClasses[size],
        className,
      )}
    >
      <div className="flex flex-col">
        {/* Header */}
        {title && (
          <div className="flex items-center justify-between border-b border-gray-200 dark:border-gray-700 px-6 py-4">
            <h2 className="text-lg font-semibold">{title}</h2>
            <button
              onClick={onClose}
              className="rounded-lg p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600"
              aria-label="Close"
            >
              <X size={20} />
            </button>
          </div>
        )}

        {/* Body */}
        <div className="px-6 py-4">{children}</div>
      </div>
    </dialog>
  )
}
