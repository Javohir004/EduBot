import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import type { AttendanceStatus } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { formatMonth } from '../../shared/lib/format'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { Button, EmptyState, Loading, Screen, Section } from '../../shared/ui/ui'
import { todayIso, useAttendanceHistory } from './api'

const SYMBOL: Record<AttendanceStatus, { text: string; className: string }> = {
  PRESENT: { text: '✓', className: 'text-emerald-600' },
  ABSENT: { text: '✗', className: 'text-red-600' },
  LATE: { text: '⏰', className: 'text-amber-600' },
  EXCUSED: { text: 'S', className: 'text-sky-600' },
}

function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number)
  const d = new Date(Date.UTC(y, m - 1 + delta, 1))
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

export function AttendanceHistoryPage() {
  useBackButton()
  const { id = '' } = useParams()
  const { t, i18n } = useTranslation()
  const errorMessage = useErrorMessage()
  const [month, setMonth] = useState(todayIso().slice(0, 7))
  const history = useAttendanceHistory(id, month)
  const monthLabel = formatMonth(`${month}-01`, i18n.language)

  return (
    <Screen title={t('attendance.historyTitle')}>
      <div className="flex items-center justify-between">
        <Button variant="secondary" className="min-h-9 px-3" onClick={() => setMonth((m) => shiftMonth(m, -1))}>‹</Button>
        <span className="font-medium capitalize">{monthLabel}</span>
        <Button variant="secondary" className="min-h-9 px-3" disabled={month >= todayIso().slice(0, 7)}
          onClick={() => setMonth((m) => shiftMonth(m, 1))}>›</Button>
      </div>

      {history.isPending && <Loading />}
      {history.isError && <EmptyState title={errorMessage(history.error)} />}
      {history.data && history.data.lessons.length === 0 && <EmptyState title={t('attendance.noLessons')} />}
      {history.data && history.data.lessons.length > 0 && (
        <Section className="overflow-x-auto p-0">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="text-hint">
                <th className="sticky left-0 bg-section px-3 py-2 text-left font-medium">{t('groups.students')}</th>
                {history.data.lessons.map((l) => (
                  <th key={l.lessonId} className="px-2 py-2 text-center font-medium">{l.date.slice(8, 10)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {history.data.students.map((s) => {
                const absent = Object.values(s.marks).filter((m) => m === 'ABSENT').length
                return (
                  <tr key={s.studentId} className="border-t border-separator">
                    <td className="sticky left-0 bg-section px-3 py-2 whitespace-nowrap">
                      {s.lastName} {s.firstName.charAt(0)}.
                      {absent > 0 && <span className="ml-1 text-xs text-red-600">({absent})</span>}
                    </td>
                    {history.data.lessons.map((l) => {
                      const mark = s.marks[l.lessonId]
                      return (
                        <td key={l.lessonId} className={`px-2 py-2 text-center ${mark ? SYMBOL[mark].className : 'text-hint'}`}>
                          {mark ? SYMBOL[mark].text : '·'}
                        </td>
                      )
                    })}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </Section>
      )}
      <p className="text-xs text-hint">{t('attendance.legend')}</p>
    </Screen>
  )
}
