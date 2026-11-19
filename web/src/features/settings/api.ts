import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, json } from '../../shared/api/client'
import type { InviteLink, MembershipRole } from '../../shared/api/types'

export interface SubscriptionInfo {
  planCode: string | null
  planName: string | null
  phase: 'ACTIVE' | 'GRACE' | 'EXPIRED' | 'NONE'
  endsAt: string | null
  graceUntil: string | null
  usage: { students: number; teachers: number; groups: number }
  limits: Record<string, number>
}

export interface CenterSettingsDto {
  paymentDueDay: number
  absenceNotificationsEnabled: boolean
  paymentRemindersEnabled: boolean
}

export interface StaffMember {
  membershipId: string
  userId: string
  firstName: string
  lastName: string | null
  username: string | null
  role: MembershipRole
}

export function useSubscription() {
  return useQuery({ queryKey: ['subscription'], queryFn: () => api<SubscriptionInfo>('/api/v1/subscription') })
}

export function useCenterSettings() {
  return useQuery({ queryKey: ['center-settings'], queryFn: () => api<CenterSettingsDto>('/api/v1/center/settings') })
}

export function useSaveSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (settings: CenterSettingsDto) =>
      api<CenterSettingsDto>('/api/v1/center/settings', { method: 'PUT', body: json(settings) }),
    onSuccess: (saved) => queryClient.setQueryData(['center-settings'], saved),
  })
}

export function useStaff() {
  return useQuery({ queryKey: ['staff'], queryFn: () => api<StaffMember[]>('/api/v1/staff') })
}

export function useRevokeStaff() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (membershipId: string) => api<void>(`/api/v1/staff/${membershipId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['staff'] }),
  })
}

export function useInviteAdmin() {
  return useMutation({
    mutationFn: (phone: string) =>
      api<InviteLink>('/api/v1/invites', { method: 'POST', body: json({ role: 'ADMIN', phone }) }),
  })
}
