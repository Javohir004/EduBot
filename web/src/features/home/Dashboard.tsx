import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { api } from '../../shared/api/client'
import { formatDate, formatMoney, formatMoneyShort } from '../../shared/lib/format'
import { ListRow, Section } from '../../shared/ui/ui'

interface Summary {
  activeStudents: number
  newStudentsThisMonth: number
  activeGroups: number
  activeTeachers: number
  attendanceMarkedToday: number
  presentToday: number
  absentToday: number
  revenueThisMonth: number | null
  outstandingDebt: number | null
  debtorCount: number | null
}

interface Activity {
  type: 'STUDENT_ADDED' | 'PAYMENT_RECEIVED' | 'PAYMENT_VOIDED'
  at: string
  studentId: string
  title: string
  amount: number | null
}

function Tile({ label, value, detail, to, wide = false }: { label: string; value: ReactNode; detail?: ReactNode; to?: string; wide?: boolean }) {
  const body = (
    <Section className="flex h-full flex-col gap-1 p-3.5">
      <span className="text-xs font-medium text-hint">{label}</span>
      <span className="text-[22px] leading-tight font-semibold tabular-nums">{value}</span>
      {detail && <span className="text-xs text-hint">{detail}</span>}
    </Section>
  )
  const span = wide ? 'col-span-2' : ''
  return to ? <Link to={to} className={`block active:opacity-80 ${span}`}>{body}</Link> : <div className={span}>{body}</div>
}

function TileSkeleton() {
  return <div className="h-[88px] animate-pulse rounded-2xl bg-section" />
}

/** Key figures for owners and admins. Money tiles appear only when the API returns them. */
export function DashboardTiles() {
  const { t, i18n } = useTranslation()
  const summary = useQuery({ queryKey: ['dashboard'], queryFn: () => api<Summary>('/api/v1/dashboard/summary') })
  const s = summary.data

  if (!s) {
    return (
      <div className="grid grid-cols-2 gap-3" aria-busy="true">
        {Array.from({ length: 4 }, (_, i) => <TileSkeleton key={i} />)}
      </div>
    )
  }
  return (
    <div className="grid grid-cols-2 gap-3">
      <Tile label={t('dashboard.students')} value={s.activeStudents} to="/students"
        detail={s.newStudentsThisMonth > 0 ? t('dashboard.newThisMonth', { count: s.newStudentsThisMonth }) : undefined} />
      <Tile label={t('dashboard.groups')} value={s.activeGroups} to="/groups"
        detail={t('dashboard.teachers', { count: s.activeTeachers })} />
      <Tile label={t('dashboard.attendanceToday')}
        value={s.attendanceMarkedToday === 0 ? '—' : `${Math.round((s.presentToday / s.attendanceMarkedToday) * 100)}%`}
        detail={s.attendanceMarkedToday === 0 ? t('dashboard.notMarkedYet')
          : t('dashboard.absentToday', { count: s.absentToday })} />
      {s.revenueThisMonth !== null && (
        <Tile label={t('dashboard.revenue')} value={formatMoneyShort(s.revenueThisMonth, i18n.language)} />
      )}
      {s.outstandingDebt !== null && (
        <Tile label={t('dashboard.debt')} to="/payments" wide={s.revenueThisMonth !== null}
          value={<span className={s.outstandingDebt > 0 ? 'text-danger' : ''}>{formatMoneyShort(s.outstandingDebt, i18n.language)}</span>}
          detail={t('dashboard.debtors', { count: s.debtorCount ?? 0 })} />
      )}
    </div>
  )
}

const ICON: Record<Activity['type'], string> = { STUDENT_ADDED: '🎓', PAYMENT_RECEIVED: '💳', PAYMENT_VOIDED: '↩️' }

export function ActivityFeed() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const activity = useQuery({ queryKey: ['activity'], queryFn: () => api<Activity[]>('/api/v1/activity') })
  if (!activity.data || activity.data.length === 0) return null

  return (
    <>
      <h2 className="font-semibold">{t('dashboard.activity')}</h2>
      <Section className="divide-y divide-separator p-0">
        {activity.data.slice(0, 8).map((a) => (
          <ListRow
            key={`${a.type}-${a.at}-${a.studentId}`}
            title={<><span aria-hidden>{ICON[a.type]} </span>{a.title}</>}
            subtitle={`${t(`dashboard.event.${a.type}`)}${a.amount !== null ? ` · ${formatMoney(a.amount)}` : ''} · ${formatDate(a.at)}`}
            onClick={() => navigate(`/students/${a.studentId}`)}
          />
        ))}
      </Section>
    </>
  )
}
