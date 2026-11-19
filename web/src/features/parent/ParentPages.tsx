import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { api } from '../../shared/api/client'
import type { AttendanceStatus } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { formatDate, formatMoney } from '../../shared/lib/format'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { Badge, EmptyState, ListRow, Loading, Screen, Section } from '../../shared/ui/ui'

interface ChildSummary {
  studentId: string
  firstName: string
  lastName: string
  debt: number
}

interface ChildDetails {
  studentId: string
  firstName: string
  lastName: string
  groups: string[]
  attendance: { date: string; startTime: string; groupName: string; status: AttendanceStatus }[]
  debt: number
  credit: number
  charges: { periodStart: string; groupName: string; amount: number; paidAmount: number; status: string }[]
}

const TONE: Record<AttendanceStatus, 'good' | 'bad' | 'warn' | 'neutral'> = {
  PRESENT: 'good', ABSENT: 'bad', LATE: 'warn', EXCUSED: 'neutral',
}

const day = (iso: string) => formatDate(`${iso}T12:00:00Z`)

/** Home block for parents: their children with current debt. */
export function ParentChildren() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const children = useQuery({ queryKey: ['parent', 'children'], queryFn: () => api<ChildSummary[]>('/api/v1/parent/children') })

  return (
    <>
      <h2 className="font-semibold">{t('parent.children')}</h2>
      {children.isPending && <Loading />}
      {children.isError && <EmptyState title={errorMessage(children.error)} />}
      {children.data && children.data.length === 0 && <EmptyState title={t('parent.noChildren')} />}
      {children.data && children.data.length > 0 && (
        <Section className="divide-y divide-separator p-0">
          {children.data.map((c) => (
            <ListRow key={c.studentId} title={`${c.firstName} ${c.lastName}`}
              subtitle={c.debt > 0 ? t('parent.debt', { amount: formatMoney(c.debt) }) : t('payments.noDebt')}
              onClick={() => navigate(`/children/${c.studentId}`)} />
          ))}
        </Section>
      )}
    </>
  )
}

export function ChildPage() {
  useBackButton()
  const { id = '' } = useParams()
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const child = useQuery({ queryKey: ['parent', 'child', id], queryFn: () => api<ChildDetails>(`/api/v1/parent/children/${id}`) })

  if (child.isPending) return <Loading />
  if (child.isError) return <Screen><EmptyState title={errorMessage(child.error)} /></Screen>
  const c = child.data
  const absences = c.attendance.filter((a) => a.status === 'ABSENT').length

  return (
    <Screen title={`${c.firstName} ${c.lastName}`}>
      <Section className="flex items-center justify-between">
        <div>
          <p className="text-sm text-hint">{c.debt > 0 ? t('payments.debt') : t('payments.noDebt')}</p>
          <p className={`text-xl font-semibold ${c.debt > 0 ? 'text-danger' : 'text-emerald-600'}`}>{formatMoney(c.debt)}</p>
        </div>
        <div className="text-right">
          <p className="text-sm text-hint">{t('parent.absences30')}</p>
          <p className="text-xl font-semibold">{absences}</p>
        </div>
      </Section>

      <h2 className="font-semibold">{t('groups.studentGroups')}</h2>
      {c.groups.length === 0 ? <EmptyState title={t('groups.noStudentGroups')} /> : (
        <Section className="divide-y divide-separator p-0">
          {c.groups.map((g) => <ListRow key={g} title={g} />)}
        </Section>
      )}

      <h2 className="font-semibold">{t('parent.attendance')}</h2>
      {c.attendance.length === 0 ? <EmptyState title={t('attendance.noLessons')} /> : (
        <Section className="divide-y divide-separator p-0">
          {c.attendance.map((a) => (
            <ListRow key={`${a.date}-${a.startTime}-${a.groupName}`} title={`${day(a.date)} · ${a.startTime.slice(0, 5)}`}
              subtitle={a.groupName} trailing={<Badge tone={TONE[a.status]}>{t(`attendance.status.${a.status}`)}</Badge>} />
          ))}
        </Section>
      )}
    </Screen>
  )
}
