import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, json } from '../../shared/api/client'
import type {
  GuardianDto,
  GuardianRelation,
  InviteLink,
  PageResponse,
  PersonStatus,
  StudentDetails,
  StudentFields,
  StudentSummary,
} from '../../shared/api/types'

const PAGE_SIZE = 30
const studentsKey = ['students'] as const

export const RELATIONS: GuardianRelation[] = ['MOTHER', 'FATHER', 'GUARDIAN', 'OTHER']

export interface GuardianInput {
  fullName: string
  phone: string
  relation: GuardianRelation
}

export function useStudents(query: string, status: PersonStatus) {
  return useInfiniteQuery({
    queryKey: [...studentsKey, 'list', status, query],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ status, page: String(pageParam), size: String(PAGE_SIZE) })
      if (query) params.set('q', query)
      return api<PageResponse<StudentSummary>>(`/api/v1/students?${params}`)
    },
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

export function useStudent(id: string | undefined) {
  return useQuery({
    queryKey: [...studentsKey, 'detail', id],
    queryFn: () => api<StudentDetails>(`/api/v1/students/${id}`),
    enabled: Boolean(id),
  })
}

/** Every write returns the fresh student, which replaces the cached detail. */
function useStudentMutation<TVariables>(request: (variables: TVariables) => Promise<StudentDetails>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (student) => {
      queryClient.setQueryData([...studentsKey, 'detail', student.id], student)
      void queryClient.invalidateQueries({ queryKey: [...studentsKey, 'list'] })
    },
  })
}

export function useCreateStudent() {
  return useStudentMutation((input: { student: StudentFields; guardians: GuardianInput[] }) =>
    api<StudentDetails>('/api/v1/students', { method: 'POST', body: json(input) }),
  )
}

export function useUpdateStudent(id: string) {
  return useStudentMutation((input: { student: StudentFields; version: number }) =>
    api<StudentDetails>(`/api/v1/students/${id}`, { method: 'PUT', body: json(input) }),
  )
}

export function useSetStudentArchived(id: string) {
  return useStudentMutation((archived: boolean) =>
    api<StudentDetails>(`/api/v1/students/${id}/${archived ? 'archive' : 'restore'}`, { method: 'POST' }),
  )
}

export function useAddGuardian(studentId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: GuardianInput) =>
      api<GuardianDto>(`/api/v1/students/${studentId}/guardians`, { method: 'POST', body: json(input) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [...studentsKey, 'detail', studentId] }),
  })
}

export function useRemoveGuardian(studentId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (guardianId: string) =>
      api<void>(`/api/v1/students/${studentId}/guardians/${guardianId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [...studentsKey, 'detail', studentId] }),
  })
}

export function useInviteGuardian(studentId: string) {
  return useMutation({
    mutationFn: (guardianId: string) =>
      api<InviteLink>(`/api/v1/students/${studentId}/guardians/${guardianId}/invite`, { method: 'POST' }),
  })
}
