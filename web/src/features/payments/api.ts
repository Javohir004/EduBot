import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, json } from '../../shared/api/client'
import type { BalanceResponse, DebtorsResponse, PaymentMethod } from '../../shared/api/types'

export const METHODS: PaymentMethod[] = ['CASH', 'CARD', 'TRANSFER', 'OTHER']

export function useBalance(studentId: string, enabled = true) {
  return useQuery({
    queryKey: ['balance', studentId],
    queryFn: () => api<BalanceResponse>(`/api/v1/students/${studentId}/balance`),
    enabled,
  })
}

export function useDebtors() {
  return useQuery({
    queryKey: ['debtors'],
    queryFn: () => api<DebtorsResponse>('/api/v1/debtors?size=100'),
  })
}

function useBalanceWrite<T>(studentId: string, request: (input: T) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['balance', studentId] })
      void queryClient.invalidateQueries({ queryKey: ['debtors'] })
    },
  })
}

/**
 * The idempotency key is created once per form: if the network drops and the admin taps
 * "save" again, the backend returns the same payment instead of recording it twice.
 */
export function useRecordPayment(studentId: string, idempotencyKey: string) {
  return useBalanceWrite(studentId, (input: { amount: number; method: PaymentMethod; note: string | null }) =>
    api<BalanceResponse>(`/api/v1/students/${studentId}/payments`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: json(input),
    }),
  )
}

export function useVoidPayment(studentId: string) {
  return useBalanceWrite(studentId, (input: { paymentId: string; reason: string }) =>
    api(`/api/v1/payments/${input.paymentId}/void`, { method: 'POST', body: json({ reason: input.reason }) }),
  )
}

export function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
}
