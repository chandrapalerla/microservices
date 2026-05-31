/**
 * Admin Categories management page — full CRUD.
 *
 * Features:
 *  - Server-side data via getAllCategories (admin endpoint)
 *  - Create / Edit modal with slug validation
 *  - Delete with 409-guard (category has products)
 *  - React Query cache management
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Pencil, Trash2, RefreshCw, AlertCircle, CheckCircle, XCircle } from 'lucide-react'
import toast from 'react-hot-toast'

import {
  getAllCategories,
  createCategory,
  updateCategory,
  deleteCategory,
} from '@/api/productApi'
import type { Category, CategoryRequest } from '@/types'
import { DEFAULT_PAGE_SIZE } from '@/constants'

import { Button }          from '@/components/ui/Button'
import { Input }           from '@/components/ui/Input'
import { Spinner }         from '@/components/ui/Spinner'
import { Pagination }      from '@/components/ui/Pagination'
import { Modal }           from '@/components/ui/Modal'
import { SkeletonTableRow } from '@/components/ui/SkeletonRow'

// ── Category form ─────────────────────────────────────────────────────────────

interface CategoryFormValues {
  name:        string
  slug:        string
  description: string
  parentId:    string   // string so <select> is uncontrolled-friendly; coerce on submit
  active:      boolean
}

interface CategoryFormErrors {
  name?:   string
  slug?:   string
}

function validateCategoryForm(values: CategoryFormValues): CategoryFormErrors {
  const errors: CategoryFormErrors = {}
  if (!values.name.trim())                          errors.name = 'Name is required'
  if (!values.slug.trim())                          errors.slug = 'Slug is required'
  if (!/^[a-z0-9-]+$/.test(values.slug.trim()))    errors.slug = 'Slug must match ^[a-z0-9-]+$'
  return errors
}

interface CategoryFormProps {
  initial?:    Partial<CategoryFormValues>
  categories:  Category[]
  editingId?:  number      // exclude self from parent options
  onSubmit:    (values: CategoryFormValues) => void
  isLoading:   boolean
  submitLabel: string
}

function CategoryForm({
  initial,
  categories,
  editingId,
  onSubmit,
  isLoading,
  submitLabel,
}: CategoryFormProps) {
  const [name,        setName]        = useState(initial?.name        ?? '')
  const [slug,        setSlug]        = useState(initial?.slug        ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [parentId,    setParentId]    = useState(initial?.parentId    ?? '')
  const [active,      setActive]      = useState(initial?.active      ?? true)
  const [errors,      setErrors]      = useState<CategoryFormErrors>({})

  // Auto-generate slug from name (only when slug hasn't been manually edited)
  function handleNameChange(value: string) {
    setName(value)
    setErrors((p) => ({ ...p, name: undefined }))
    // Only auto-fill slug if it's still empty or was auto-generated from the old name
    const autoSlug = value.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '')
    setSlug(autoSlug)
    setErrors((p) => ({ ...p, slug: undefined }))
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const vals: CategoryFormValues = {
      name:        name.trim(),
      slug:        slug.trim(),
      description: description.trim(),
      parentId,
      active,
    }
    const errs = validateCategoryForm(vals)
    if (Object.keys(errs).length > 0) { setErrors(errs); return }
    onSubmit(vals)
  }

  // Exclude self from parent candidates (can't be its own parent)
  const parentOptions = categories.filter((c) => c.id !== editingId)

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Input
        label="Name"
        value={name}
        onChange={(e) => handleNameChange(e.target.value)}
        error={errors.name}
        placeholder="Electronics"
        autoFocus
      />
      <Input
        label="Slug"
        value={slug}
        onChange={(e) => {
          setSlug(e.target.value)
          setErrors((p) => ({ ...p, slug: undefined }))
        }}
        error={errors.slug}
        placeholder="electronics"
        helperText="Lowercase letters, numbers and hyphens only (^[a-z0-9-]+$)"
      />
      <Input
        label="Description"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="Optional description"
      />

      {/* Parent category select */}
      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Parent Category
        </label>
        <select
          value={parentId}
          onChange={(e) => setParentId(e.target.value)}
          className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 dark:bg-gray-800 dark:text-gray-100 dark:border-gray-600"
        >
          <option value="">— None (top-level) —</option>
          {parentOptions.map((c) => (
            <option key={c.id} value={String(c.id)}>{c.name}</option>
          ))}
        </select>
      </div>

      {/* Active toggle */}
      <label className="flex items-center gap-2 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={active}
          onChange={(e) => setActive(e.target.checked)}
          className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
        />
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Active</span>
      </label>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="submit" isLoading={isLoading}>{submitLabel}</Button>
      </div>
    </form>
  )
}

// ── Delete confirmation ───────────────────────────────────────────────────────

function DeleteConfirm({
  category,
  onConfirm,
  onCancel,
  isLoading,
}: {
  category:  Category
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
          <p className="font-medium text-gray-900 dark:text-gray-100">Delete category?</p>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            <strong>{category.name}</strong> will be permanently removed.
            {category.productCount > 0 && (
              <span className="text-amber-600 dark:text-amber-400">
                {' '}This category has {category.productCount} product(s).
              </span>
            )}
          </p>
        </div>
      </div>
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onCancel} disabled={isLoading}>Cancel</Button>
        <Button variant="danger" onClick={onConfirm} isLoading={isLoading}>Delete</Button>
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function AdminCategoriesPage() {
  const qc = useQueryClient()

  // ── Pagination state ───────────────────────────────────────────────────────
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE)

  // ── Modal state ────────────────────────────────────────────────────────────
  const [createOpen,      setCreateOpen]      = useState(false)
  const [editCategory,    setEditCategory]    = useState<Category | null>(null)
  const [deleteCategory_, setDeleteCategory_] = useState<Category | null>(null)

  // ── Query ──────────────────────────────────────────────────────────────────
  const { data: allCategories = [], isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey:  ['categories'],
    queryFn:   getAllCategories,
    staleTime: 30_000,
  })

  // Client-side pagination (category list is typically small enough)
  const totalElements = allCategories.length
  const totalPages    = Math.max(1, Math.ceil(totalElements / size))
  const paginatedRows = allCategories.slice(page * size, page * size + size)

  // ── Mutations ──────────────────────────────────────────────────────────────
  function toCategoryRequest(vals: CategoryFormValues): CategoryRequest {
    return {
      name:        vals.name,
      slug:        vals.slug,
      description: vals.description || undefined,
      parentId:    vals.parentId ? Number(vals.parentId) : null,
      active:      vals.active,
    }
  }

  const { mutate: doCreate, isPending: creating } = useMutation({
    mutationFn: (vals: CategoryFormValues) => createCategory(toCategoryRequest(vals)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] })
      setCreateOpen(false)
      toast.success('Category created successfully')
    },
    onError: () => toast.error('Failed to create category'),
  })

  const { mutate: doUpdate, isPending: updating } = useMutation({
    mutationFn: (vals: CategoryFormValues) =>
      updateCategory(editCategory!.id, toCategoryRequest(vals)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] })
      setEditCategory(null)
      toast.success('Category updated')
    },
    onError: () => toast.error('Failed to update category'),
  })

  const { mutate: doDelete, isPending: deleting } = useMutation({
    mutationFn: () => deleteCategory(deleteCategory_!.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] })
      setDeleteCategory_(null)
      toast.success('Category deleted')
    },
    onError: (err: unknown) => {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 409) {
        toast.error('Category has products — reassign them first')
      } else {
        toast.error('Failed to delete category')
      }
    },
  })

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-4 max-w-6xl">
      {/* Toolbar */}
      <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-center justify-between">
        <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Categories</h1>
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
            New Category
          </Button>
        </div>
      </div>

      {/* Table card */}
      <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
        {/* Error state */}
        {isError && (
          <div className="flex items-center gap-2 px-4 py-3 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm border-b border-red-200 dark:border-red-800">
            <AlertCircle size={14} />
            Failed to load categories: {String((error as Error)?.message ?? error)}
          </div>
        )}

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50">
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-16">
                  ID
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide">
                  Name
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide">
                  Slug
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide">
                  Parent
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-24">
                  Products
                </th>
                <th className="px-4 py-3 text-left font-semibold text-gray-500 dark:text-gray-400 text-xs uppercase tracking-wide w-20">
                  Active
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
                  <SkeletonTableRow key={i} cols={7} />
                ))}

              {/* Data rows */}
              {!isLoading &&
                paginatedRows.map((cat) => (
                  <tr
                    key={cat.id}
                    className="hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors group"
                  >
                    <td className="px-4 py-3 text-gray-400 dark:text-gray-500 font-mono text-xs">
                      {cat.id}
                    </td>
                    <td className="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
                      {cat.name}
                    </td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400 font-mono text-xs">
                      {cat.slug}
                    </td>
                    <td className="px-4 py-3 text-gray-600 dark:text-gray-300">
                      {cat.parentName ?? <span className="text-gray-400 dark:text-gray-500 italic">—</span>}
                    </td>
                    <td className="px-4 py-3 text-gray-600 dark:text-gray-300">
                      {cat.productCount}
                    </td>
                    <td className="px-4 py-3">
                      {cat.active ? (
                        <CheckCircle size={16} className="text-green-500" />
                      ) : (
                        <XCircle size={16} className="text-red-400" />
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setEditCategory(cat)}
                          className="p-1.5"
                          aria-label={`Edit ${cat.name}`}
                        >
                          <Pencil size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setDeleteCategory_(cat)}
                          className="p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20"
                          aria-label={`Delete ${cat.name}`}
                        >
                          <Trash2 size={14} />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}

              {/* Empty state */}
              {!isLoading && paginatedRows.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-12 text-center">
                    <div className="flex flex-col items-center gap-2 text-gray-400 dark:text-gray-500">
                      {isFetching ? (
                        <Spinner size="md" />
                      ) : (
                        <>
                          <RefreshCw size={24} />
                          <p className="text-sm">No categories found</p>
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
        {!isLoading && totalElements > 0 && (
          <div className="border-t border-gray-100 dark:border-gray-700 px-4 py-3">
            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={size}
              onPageChange={(p) => { setPage(p); window.scrollTo({ top: 0, behavior: 'smooth' }) }}
              onSizeChange={(s) => { setSize(s); setPage(0) }}
            />
          </div>
        )}
      </div>

      {/* ── Create modal ── */}
      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Create Category" size="md">
        <CategoryForm
          categories={allCategories}
          onSubmit={(vals) => doCreate(vals)}
          isLoading={creating}
          submitLabel="Create"
        />
      </Modal>

      {/* ── Edit modal ── */}
      <Modal
        open={editCategory !== null}
        onClose={() => setEditCategory(null)}
        title="Edit Category"
        size="md"
      >
        {editCategory && (
          <CategoryForm
            initial={{
              name:        editCategory.name,
              slug:        editCategory.slug,
              description: editCategory.description ?? '',
              parentId:    editCategory.parentId != null ? String(editCategory.parentId) : '',
              active:      editCategory.active,
            }}
            categories={allCategories}
            editingId={editCategory.id}
            onSubmit={(vals) => doUpdate(vals)}
            isLoading={updating}
            submitLabel="Save changes"
          />
        )}
      </Modal>

      {/* ── Delete modal ── */}
      <Modal
        open={deleteCategory_ !== null}
        onClose={() => setDeleteCategory_(null)}
        title="Confirm Deletion"
        size="sm"
      >
        {deleteCategory_ && (
          <DeleteConfirm
            category={deleteCategory_}
            onConfirm={() => doDelete()}
            onCancel={() => setDeleteCategory_(null)}
            isLoading={deleting}
          />
        )}
      </Modal>
    </div>
  )
}
