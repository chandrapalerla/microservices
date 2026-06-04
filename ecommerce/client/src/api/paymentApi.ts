import api from './axiosInstance'
import type { Payment } from '@/types'

const BASE = '/v1/payments'

export async function getPaymentByOrderId(orderId: number): Promise<Payment> {
  const { data } = await api.get<Payment>(`${BASE}/${orderId}`)
  return data
}

export async function retryPayment(orderId: number): Promise<Payment> {
  const { data } = await api.post<Payment>(`${BASE}/${orderId}/retry`)
  return data
}

export async function getAllPayments(
  params: { page?: number; size?: number } = {},
): Promise<import('@/types').Page<Payment>> {
  const { data } = await api.get(`${BASE}`, { params })
  return data
}
