import api from './axiosInstance'
import type { Order, OrderRequest, OrderStatusHistory, OrderStatus, Page } from '@/types'

const BASE = '/v1/orders'

export interface ShipOrderDto {
  trackingNumber: string
  courierName: string
}

// ─── Create ───────────────────────────────────────────────────────────────────

export async function createOrder(req: OrderRequest): Promise<Order> {
  const { data } = await api.post<Order>(BASE, req)
  return data
}

// ─── Read ─────────────────────────────────────────────────────────────────────

export async function getMyOrders(
  params: { page?: number; size?: number; sort?: string } = {},
): Promise<Page<Order>> {
  const { data } = await api.get<Page<Order>>(`${BASE}/my`, { params })
  return data
}

export async function getAllOrders(
  params: { page?: number; size?: number; sort?: string; status?: OrderStatus | '' } = {},
): Promise<Page<Order>> {
  const { data } = await api.get<Page<Order>>(BASE, { params })
  return data
}

export async function getOrderById(id: number): Promise<Order> {
  const { data } = await api.get<Order>(`${BASE}/${id}`)
  return data
}

export async function getOrderByNumber(orderNumber: string): Promise<Order> {
  const { data } = await api.get<Order>(`${BASE}/${orderNumber}/by-number`)
  return data
}

export async function getOrderHistory(id: number): Promise<OrderStatusHistory[]> {
  const { data } = await api.get<OrderStatusHistory[]>(`${BASE}/${id}/history`)
  return data
}

// ─── Status transitions ───────────────────────────────────────────────────────

export async function confirmOrder(id: number): Promise<Order> {
  const { data } = await api.post<Order>(`${BASE}/${id}/confirm-payment`)
  return data
}

export async function processOrder(id: number): Promise<Order> {
  const { data } = await api.post<Order>(`${BASE}/${id}/process`)
  return data
}

export async function shipOrder(id: number, dto: ShipOrderDto): Promise<Order> {
  const { data } = await api.post<Order>(`${BASE}/${id}/ship`, dto)
  return data
}

export async function outForDelivery(id: number): Promise<Order> {
  const { data } = await api.post<Order>(`${BASE}/${id}/out-for-delivery`)
  return data
}

export async function deliverOrder(id: number): Promise<Order> {
  const { data } = await api.post<Order>(`${BASE}/${id}/deliver`)
  return data
}

export async function cancelOrder(id: number): Promise<Order> {
  const { data } = await api.post<Order>(`${BASE}/${id}/cancel`)
  return data
}

export async function requestReturn(id: number): Promise<Order> {
  const { data } = await api.post<Order>(`${BASE}/${id}/return`)
  return data
}
