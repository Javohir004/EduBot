import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, json } from '../../shared/api/client'
import type { InviteLink, PageResponse, PersonStatus, TeacherDto } from '../../shared/api/types'

const PAGE_SIZE = 30
const teachersKey = ['teachers'] as const

export interface TeacherInput {
  fullName: string
  phone: string | null
  subjects: string | null
}

export function useTeachers(query: string, status: PersonStatus) {
  return useInfiniteQuery({
    queryKey: [...teachersKey, 'list', status, query],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ status, page: String(pageParam), size: String(PAGE_SIZE) })
      if (query) params.set('q', query)
      return api<PageResponse<TeacherDto>>(`/api/v1/teachers?${params}`)
    },
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

export function useTeacher(id: string | undefined) {
  return useQuery({
    queryKey: [...teachersKey, 'detail', id],
    queryFn: () => api<TeacherDto>(`/api/v1/teachers/${id}`),
    enabled: Boolean(id),
  })
}

function useTeacherMutation<TVariables>(request: (variables: TVariables) => Promise<TeacherDto>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (teacher) => {
      queryClient.setQueryData([...teachersKey, 'detail', teacher.id], teacher)
      void queryClient.invalidateQueries({ queryKey: [...teachersKey, 'list'] })
    },
  })
}

export function useSaveTeacher(existing?: TeacherDto) {
  return useTeacherMutation((input: TeacherInput) =>
    existing
      ? api<TeacherDto>(`/api/v1/teachers/${existing.id}`, {
          method: 'PUT',
          body: json({ ...input, version: existing.version }),
        })
      : api<TeacherDto>('/api/v1/teachers', { method: 'POST', body: json(input) }),
  )
}

export function useSetTeacherArchived(id: string) {
  return useTeacherMutation((archived: boolean) =>
    api<TeacherDto>(`/api/v1/teachers/${id}/${archived ? 'archive' : 'restore'}`, { method: 'POST' }),
  )
}

export function useInviteTeacher(id: string) {
  return useMutation({
    mutationFn: () => api<InviteLink>(`/api/v1/teachers/${id}/invite`, { method: 'POST' }),
  })
}
