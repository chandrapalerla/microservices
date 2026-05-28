/**
 * CartContext — persisted shopping cart.
 *
 * Cart state is stored in localStorage so it survives page refreshes.
 * Items are keyed by productId.
 */
import React, { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { CartItem, ProductSummary } from '@/types'

const CART_KEY = 'ec_cart'

interface CartContextValue {
  items:       CartItem[]
  count:       number           // total units across all items
  subtotal:    number
  addItem:     (product: ProductSummary, qty?: number) => void
  removeItem:  (productId: number) => void
  updateQty:   (productId: number, qty: number) => void
  clearCart:   () => void
  hasItem:     (productId: number) => boolean
  getQty:      (productId: number) => number
}

const CartContext = createContext<CartContextValue | undefined>(undefined)

function loadCart(): CartItem[] {
  try {
    const raw = localStorage.getItem(CART_KEY)
    return raw ? (JSON.parse(raw) as CartItem[]) : []
  } catch {
    return []
  }
}

function saveCart(items: CartItem[]) {
  localStorage.setItem(CART_KEY, JSON.stringify(items))
}

export function CartProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<CartItem[]>(loadCart)

  // Persist on every change
  useEffect(() => {
    saveCart(items)
  }, [items])

  const addItem = useCallback((product: ProductSummary, qty = 1) => {
    setItems((prev) => {
      const existing = prev.find((i) => i.productId === product.id)
      if (existing) {
        const newQty = Math.min(existing.quantity + qty, product.stockQuantity)
        return prev.map((i) =>
          i.productId === product.id ? { ...i, quantity: newQty } : i,
        )
      }
      const item: CartItem = {
        productId:   product.id,
        productName: product.name,
        sku:         product.sku,
        price:       product.price,
        quantity:    Math.min(qty, product.stockQuantity),
        thumbnailUrl: product.thumbnailUrl,
        maxStock:    product.stockQuantity,
      }
      return [...prev, item]
    })
  }, [])

  const removeItem = useCallback((productId: number) => {
    setItems((prev) => prev.filter((i) => i.productId !== productId))
  }, [])

  const updateQty = useCallback((productId: number, qty: number) => {
    if (qty <= 0) {
      setItems((prev) => prev.filter((i) => i.productId !== productId))
    } else {
      setItems((prev) =>
        prev.map((i) =>
          i.productId === productId
            ? { ...i, quantity: Math.min(qty, i.maxStock) }
            : i,
        ),
      )
    }
  }, [])

  const clearCart = useCallback(() => setItems([]), [])

  const hasItem  = useCallback((productId: number) => items.some((i) => i.productId === productId), [items])
  const getQty   = useCallback((productId: number) => items.find((i) => i.productId === productId)?.quantity ?? 0, [items])

  const count    = items.reduce((s, i) => s + i.quantity, 0)
  const subtotal = items.reduce((s, i) => s + i.price * i.quantity, 0)

  return (
    <CartContext.Provider
      value={{ items, count, subtotal, addItem, removeItem, updateQty, clearCart, hasItem, getQty }}
    >
      {children}
    </CartContext.Provider>
  )
}

export function useCart(): CartContextValue {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart must be used inside <CartProvider>')
  return ctx
}
