import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, json } from '../../shared/api/client'
import type {
  DayOfWeek,
  EnrollmentDto,
  GroupDetails,
  GroupMember,
  GroupStatus,
  GroupSummary,
  PageResponse,
  Slot,
} from '../../shared/api/types'

export const DAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

export interface GroupInput {
  name: string
  subject: string | null
  teacherId: string | null
  monthlyFee: number
  startDate: string
  endDate: string | null
  capacity: number | null
}

const groupsKey = ['groups'] as const

export function useGroups(query: string, status: GroupStatus) {
  return useInfiniteQuery({
    queryKey: [...groupsKey, 'list', status, query],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ status, page: String(pageParam), size: '30' })
      if (query) params.set('q', query)
      return api<PageResponse<GroupSummary>>(`/api/v1/groups?${params}`)
    },
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

export function useGroup(id: string | undefined) {
  return useQuery({
    queryKey: [...groupsKey, 'detail', id],
    queryFn: () => api<GroupDetails>(`/api/v1/groups/${id}`),
    enabled: Boolean(id),
  })
}

export function useGroupMembers(id: string) {
  return useQuery({
    queryKey: [...groupsKey, 'members', id],
    queryFn: () => api<GroupMember[]>(`/api/v1/groups/${id}/members`),
  })
}

export function useStudentEnrollments(studentId: string) {
  return useQuery({
    queryKey: ['students', 'enrollments', studentId],
    queryFn: () => api<EnrollmentDto[]>(`/api/v1/students/${studentId}/enrollments`),
  })
}

/** Group writes affect lists, details, members and student pages: refresh them all. */
function useGroupWrite<TVariables, TResult>(request: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: groupsKey })
      void queryClient.invalidateQueries({ queryKey: ['students', 'enrollments'] })
    },
  })
}

export function useSaveGroup(existing?: GroupDetails) {
  return useGroupWrite((input: GroupInput) =>
    existing
      ? api<GroupDetails>(`/api/v1/groups/${existing.id}`, { method: 'PUT', body: json({ ...input, version: existing.version }) })
      : api<GroupDetails>('/api/v1/groups', { method: 'POST', body: json(input) }),
  )
}

export function useCompleteGroup(id: string) {
  return useGroupWrite(() => api<GroupDetails>(`/api/v1/groups/${id}/complete`, { method: 'POST' }))
}

export function useSaveSchedule(id: string) {
  return useGroupWrite((slots: Slot[]) => api<Slot[]>(`/api/v1/groups/${id}/schedule`, { method: 'PUT', body: json(slots) }))
}

export function useEnroll(groupId: string) {
  return useGroupWrite((studentId: string) =>
    api<EnrollmentDto>(`/api/v1/groups/${groupId}/enrollments`, { method: 'POST', body: json({ studentId }) }),
  )
}

export function useEndEnrollment() {
  return useGroupWrite((enrollmentId: string) =>
    api<EnrollmentDto>(`/api/v1/enrollments/${enrollmentId}/end`, { method: 'POST', body: json({}) }),
  )
}
