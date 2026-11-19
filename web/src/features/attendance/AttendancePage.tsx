import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import type { AttendanceStatus, LessonView } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { formatDate } from '../../shared/lib/format'
import { haptic } from '../../shared/telegram/telegram'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { Button, EmptyState, Loading, Screen, Section } from '../../shared/ui/ui'
import { STATUSES, useLesson, useSaveAttendance } from './api'

const STATUS_STYLE: Record<AttendanceStatus, string> = {
  PRESENT: 'bg-emerald-500 text-white',
  ABSENT: 'bg-red-500 text-white',
  LATE: 'bg-amber-500 text-white',
  EXCUSED: 'bg-sky-500 text-white',
}

export function AttendancePage() {
  useBackButton()
  const { lessonId = '' } = useParams()
  const errorMessage = useErrorMessage()
  const lesson = useLesson(lessonId)
  if (lesson.isPending) return <Loading />
  if (lesson.isError) return <Screen><EmptyState title={errorMessage(lesson.error)} /></Screen>
  return <AttendanceForm key={lesson.dataUpdatedAt} lesson={lesson.data} />
}

function AttendanceForm({ lesson }: { lesson: LessonView }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const save = useSaveAttendance(lesson.lessonId)
  const [marks, setMarks] = useState<Record<string, AttendanceStatus | null>>(
    Object.fromEntries(lesson.students.map((s) => [s.studentId, s.status])),
  )
  const unmarked = lesson.students.filter((s) => !marks[s.studentId]).length
  const changed = lesson.students.filter((s) => marks[s.studentId] && marks[s.studentId] !== s.status)

  const setAll = (status: AttendanceStatus) =>
    setMarks((m) => Object.fromEntries(Object.keys(m).map((id) => [id, m[id] ?? status])))

  const submit = () => {
    const payload = lesson.students
      .filter((s) => marks[s.studentId])
      .map((s) => ({ studentId: s.studentId, status: marks[s.studentId]! }))
    save.mutate(payload, {
      onSuccess: () => {
        haptic('success')
        navigate(-1)
      },
      onError: () => haptic('error'),
    })
  }

  return (
    <Screen title={lesson.groupName}>
      <p className="-mt-2 text-sm text-hint">
        {formatDate(`${lesson.date}T12:00:00Z`)} · {lesson.startTime.slice(0, 5)}
      </p>

      {lesson.students.length === 0 ? (
        <EmptyState title={t('attendance.noStudents')} />
      ) : (
        <>
          {lesson.editable && unmarked > 0 && (
            <Button variant="secondary" onClick={() => setAll('PRESENT')}>✓ {t('attendance.restPresent')}</Button>
          )}
          <Section className="divide-y divide-separator p-0">
            {lesson.students.map((s) => (
              <div key={s.studentId} className="flex flex-col gap-2 px-4 py-3">
                <span className="font-medium">{s.lastName} {s.firstName}</span>
                <div className="grid grid-cols-4 gap-1.5" role="radiogroup" aria-label={`${s.lastName} ${s.firstName}`}>
                  {STATUSES.map((status) => {
                    const selected = marks[s.studentId] === status
                    return (
                      <button
                        key={status}
                        type="button"
                        role="radio"
                        aria-checked={selected}
                        disabled={!lesson.editable}
                        onClick={() => setMarks((m) => ({ ...m, [s.studentId]: status }))}
                        className={`min-h-10 rounded-lg text-xs font-medium transition ${selected ? STATUS_STYLE[status] : 'bg-surface text-hint'} disabled:opacity-60`}
                      >
                        {t(`attendance.status.${status}`)}
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
          </Section>
        </>
      )}

      {!lesson.editable && <p className="text-sm text-hint">{t('attendance.readOnly')}</p>}
      {lesson.editable && <p className="text-xs text-hint">{t('attendance.parentHint')}</p>}
      {save.error && <p className="text-sm text-danger">{errorMessage(save.error)}</p>}
      {lesson.editable && lesson.students.length > 0 && (
        <Button onClick={submit} loading={save.isPending} disabled={changed.length === 0}>
          {unmarked > 0 ? t('attendance.saveWithUnmarked', { count: unmarked }) : t('people.save')}
        </Button>
      )}
    </Screen>
  )
}
