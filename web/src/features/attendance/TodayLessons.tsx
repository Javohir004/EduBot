import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import type { TodayLesson } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { EmptyState, ListRow, Loading, Section } from '../../shared/ui/ui'
import { todayIso, useOpenLesson, useTodayLessons } from './api'

/** Home-screen block: today's lessons; one tap opens attendance. */
export function TodayLessons() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const lessons = useTodayLessons(true)
  const openLesson = useOpenLesson()

  const open = (lesson: TodayLesson) => {
    if (lesson.lessonId) {
      navigate(`/lessons/${lesson.lessonId}`)
      return
    }
    openLesson.mutate(
      { groupId: lesson.groupId, date: todayIso(), startTime: lesson.startTime },
      { onSuccess: ({ lessonId }) => navigate(`/lessons/${lessonId}`) },
    )
  }

  return (
    <>
      <h2 className="font-semibold">{t('attendance.today')}</h2>
      {lessons.isPending && <Loading />}
      {lessons.isError && <EmptyState title={errorMessage(lessons.error)} />}
      {lessons.data && lessons.data.length === 0 && <EmptyState title={t('attendance.noLessonsToday')} />}
      {lessons.data && lessons.data.length > 0 && (
        <Section className="divide-y divide-separator p-0">
          {lessons.data.map((l) => {
            const done = l.lessonId !== null && l.markedCount >= l.studentCount && l.studentCount > 0
            return (
              <ListRow
                key={`${l.groupId}-${l.startTime}`}
                title={l.groupName}
                subtitle={`${l.startTime.slice(0, 5)}–${l.endTime.slice(0, 5)}${l.room ? ` · ${l.room}` : ''}`}
                trailing={
                  <span className={`text-sm font-medium ${done ? 'text-emerald-600' : 'text-link'}`}>
                    {done ? `✓ ${l.markedCount}/${l.studentCount}` : t('attendance.mark')}
                  </span>
                }
                onClick={() => open(l)}
              />
            )
          })}
        </Section>
      )}
      {openLesson.error && <p className="text-sm text-danger">{errorMessage(openLesson.error)}</p>}
    </>
  )
}
