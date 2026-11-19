import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, json } from '../../shared/api/client'
import type { CenterSummary, CreatedCenterResponse, PageResponse, PlanResponse } from '../../shared/api/types'

export interface CreateCenterInput {
  name: string
  phone?: string
  ownerPhone: string
  planCode: string
  trialDays: number
}

const centersKey = ['platform', 'centers'] as const

export function useCenters() {
  return useQuery({
    queryKey: centersKey,
    queryFn: () => api<PageResponse<CenterSummary>>('/api/v1/platform/centers?size=50'),
  })
}

export function usePlans() {
  return useQuery({
    queryKey: ['platform', 'plans'],
    queryFn: () => api<PlanResponse[]>('/api/v1/platform/plans'),
    staleTime: 5 * 60_000,
  })
}

export function useActivateSubscription() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { centerId: string; planCode: string; months: number }) =>
      api(`/api/v1/platform/centers/${input.centerId}/subscriptions`, {
        method: 'POST',
        body: json({ planCode: input.planCode, months: input.months }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: centersKey }),
  })
}

export function useCreateCenter() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateCenterInput) =>
      api<CreatedCenterResponse>('/api/v1/platform/centers', { method: 'POST', body: json(input) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: centersKey }),
  })
}
