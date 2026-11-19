import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, json } from '../../shared/api/client'
import type { AttendanceHistory, AttendanceStatus, LessonView, TodayLesson } from '../../shared/api/types'

export const STATUSES: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'LATE', 'EXCUSED']

export function useTodayLessons(enabled: boolean) {
  return useQuery({
    queryKey: ['lessons', 'today'],
    queryFn: () => api<TodayLesson[]>('/api/v1/lessons/today'),
    enabled,
  })
}

/** Get-or-create the lesson row, then the attendance screen loads it by id. */
export function useOpenLesson() {
  return useMutation({
    mutationFn: (input: { groupId: string; date: string; startTime: string }) =>
      api<{ lessonId: string }>(`/api/v1/groups/${input.groupId}/lessons`, {
        method: 'POST',
        body: json({ date: input.date, startTime: input.startTime.slice(0, 5) }),
      }),
  })
}

export function useLesson(lessonId: string) {
  return useQuery({
    queryKey: ['lessons', 'detail', lessonId],
    queryFn: () => api<LessonView>(`/api/v1/lessons/${lessonId}/attendance`),
  })
}

export function useSaveAttendance(lessonId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (marks: { studentId: string; status: AttendanceStatus }[]) =>
      api<LessonView>(`/api/v1/lessons/${lessonId}/attendance`, { method: 'PUT', body: json(marks) }),
    onSuccess: (lesson) => {
      queryClient.setQueryData(['lessons', 'detail', lessonId], lesson)
      void queryClient.invalidateQueries({ queryKey: ['lessons', 'today'] })
      void queryClient.invalidateQueries({ queryKey: ['attendance', 'history'] })
    },
  })
}

export function useAttendanceHistory(groupId: string, month: string) {
  return useQuery({
    queryKey: ['attendance', 'history', groupId, month],
    queryFn: () => api<AttendanceHistory>(`/api/v1/groups/${groupId}/attendance?month=${month}`),
  })
}

/** Today's date in Tashkent as yyyy-MM-dd, independent of the device time zone. */
export function todayIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tashkent' }).format(new Date())
}

/** Today's ISO weekday name in Tashkent, e.g. "MONDAY". */
export function tashkentWeekday(): string {
  return new Intl.DateTimeFormat('en-US', { weekday: 'long', timeZone: 'Asia/Tashkent' }).format(new Date()).toUpperCase()
}
