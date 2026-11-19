import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { useAuth } from '../../app/auth-context'
import type { GroupDetails, Slot } from '../../shared/api/types'
import { useDebounced } from '../../shared/hooks/useDebounced'
import { useErrorMessage } from '../../shared/i18n'
import { formatDate, formatMoney } from '../../shared/lib/format'
import { confirmAction, haptic } from '../../shared/telegram/telegram'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { Badge, Button, EmptyState, ListRow, Loading, Screen, SearchInput, Section, SelectField } from '../../shared/ui/ui'
import { useStudents } from '../students/api'
import { DAYS, useCompleteGroup, useEndEnrollment, useEnroll, useGroup, useGroupMembers, useSaveSchedule } from './api'

const time = (value: string) => value.slice(0, 5)
const date = (iso: string) => formatDate(`${iso}T12:00:00Z`)

export function GroupDetailsPage() {
  useBackButton()
  const { id = '' } = useParams()
  const errorMessage = useErrorMessage()
  const group = useGroup(id)
  if (group.isPending) return <Loading />
  if (group.isError) return <Screen><EmptyState title={errorMessage(group.error)} /></Screen>
  return <GroupView group={group.data} />
}

function GroupView({ group }: { group: GroupDetails }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const { activeMembership } = useAuth()
  const canEdit = (activeMembership?.role === 'OWNER' || activeMembership?.role === 'ADMIN') && group.status === 'ACTIVE'
  const members = useGroupMembers(group.id)
  const complete = useCompleteGroup(group.id)
  const endEnrollment = useEndEnrollment()
  const [editingSchedule, setEditingSchedule] = useState(false)
  const [addingStudent, setAddingStudent] = useState(false)

  const finish = async () => {
    if (await confirmAction(t('groups.completeConfirm'))) complete.mutate(undefined, { onSuccess: () => haptic('success') })
  }
  const removeMember = async (enrollmentId: string) => {
    if (await confirmAction(t('groups.removeConfirm'))) endEnrollment.mutate(enrollmentId)
  }
  const error = complete.error ?? endEnrollment.error

  return (
    <Screen
      title={group.name}
      action={canEdit && (
        <Button variant="secondary" className="min-h-9 px-3 text-sm" onClick={() => navigate(`/groups/${group.id}/edit`)}>
          {t('people.edit')}
        </Button>
      )}
    >
      {group.status === 'COMPLETED' && <Badge tone="warn">{t('groups.completed')}</Badge>}

      <Section className="divide-y divide-separator p-0">
        <ListRow title={t('groups.teacher')} subtitle={group.teacherName ?? t('groups.noTeacher')} />
        <ListRow title={t('groups.monthlyFee')} subtitle={formatMoney(group.monthlyFee)} />
        <ListRow title={t('groups.period')}
          subtitle={`${date(group.startDate)} — ${group.endDate ? date(group.endDate) : t('groups.openEnded')}`} />
        {group.subject && <ListRow title={t('groups.subject')} subtitle={group.subject} />}
      </Section>

      <div className="flex items-center justify-between">
        <h2 className="font-semibold">{t('groups.schedule')}</h2>
        {canEdit && !editingSchedule && (
          <Button variant="ghost" className="min-h-9 px-2 text-sm" onClick={() => setEditingSchedule(true)}>{t('people.edit')}</Button>
        )}
      </div>
      {editingSchedule ? (
        <ScheduleEditor groupId={group.id} initial={group.schedule} onDone={() => setEditingSchedule(false)} />
      ) : group.schedule.length === 0 ? (
        <EmptyState title={t('groups.noSchedule')} />
      ) : (
        <Section className="divide-y divide-separator p-0">
          {group.schedule.map((s) => (
            <ListRow key={`${s.day}-${s.startTime}`} title={t(`days.${s.day}`)}
              subtitle={`${time(s.startTime)}–${time(s.endTime)}${s.room ? ` · ${s.room}` : ''}`} />
          ))}
        </Section>
      )}

      <div className="flex items-center justify-between">
        <h2 className="font-semibold">
          {t('groups.students')} · {group.capacity ? `${group.studentCount}/${group.capacity}` : group.studentCount}
        </h2>
        {canEdit && !addingStudent && (
          <Button variant="ghost" className="min-h-9 px-2 text-sm" onClick={() => setAddingStudent(true)}>+ {t('groups.addStudent')}</Button>
        )}
      </div>
      {addingStudent && (
        <StudentPicker groupId={group.id} exclude={new Set(members.data?.map((m) => m.studentId))}
          onDone={() => setAddingStudent(false)} />
      )}
      {members.isPending && <Loading />}
      {members.data && members.data.length === 0 && !addingStudent && <EmptyState title={t('groups.noStudents')} />}
      {members.data && members.data.length > 0 && (
        <Section className="divide-y divide-separator p-0">
          {members.data.map((m) => (
            <ListRow
              key={m.enrollmentId}
              title={`${m.lastName} ${m.firstName}`}
              subtitle={[
                t('groups.since', { date: date(m.joinedOn) }),
                m.feeOverride !== null ? formatMoney(m.feeOverride) : null,
              ].filter(Boolean).join(' · ')}
              trailing={canEdit && (
                <button type="button" className="text-sm text-danger" onClick={() => void removeMember(m.enrollmentId)}>
                  {t('students.remove')}
                </button>
              )}
            />
          ))}
        </Section>
      )}

      {error && <p className="text-sm text-danger">{errorMessage(error)}</p>}
      {canEdit && (
        <Button variant="secondary" className="text-danger" loading={complete.isPending} onClick={() => void finish()}>
          {t('groups.complete')}
        </Button>
      )}
    </Screen>
  )
}

function ScheduleEditor({ groupId, initial, onDone }: { groupId: string; initial: Slot[]; onDone(): void }) {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const save = useSaveSchedule(groupId)
  const [slots, setSlots] = useState<Slot[]>(
    initial.map((s) => ({ ...s, startTime: time(s.startTime), endTime: time(s.endTime) })),
  )
  const update = (index: number, patch: Partial<Slot>) =>
    setSlots((list) => list.map((s, i) => (i === index ? { ...s, ...patch } : s)))
  const addSlot = () => {
    const last = slots.at(-1)
    setSlots((list) => [...list, { day: 'MONDAY', startTime: last?.startTime ?? '14:00', endTime: last?.endTime ?? '15:30', room: last?.room ?? '' }])
  }

  return (
    <Section className="flex flex-col gap-3">
      {slots.map((slot, i) => (
        <div key={i} className="flex flex-col gap-2 rounded-xl bg-surface p-3">
          <SelectField label={t('groups.day')} value={slot.day} onChange={(e) => update(i, { day: e.target.value as Slot['day'] })}>
            {DAYS.map((d) => <option key={d} value={d}>{t(`days.${d}`)}</option>)}
          </SelectField>
          <div className="grid grid-cols-2 gap-2">
            <input aria-label={t('groups.from')} type="time" value={slot.startTime} onChange={(e) => update(i, { startTime: e.target.value })}
              className="min-h-11 rounded-xl border border-separator bg-bg px-3 text-text" />
            <input aria-label={t('groups.to')} type="time" value={slot.endTime} onChange={(e) => update(i, { endTime: e.target.value })}
              className="min-h-11 rounded-xl border border-separator bg-bg px-3 text-text" />
          </div>
          <div className="flex gap-2">
            <input placeholder={t('groups.room')} value={slot.room ?? ''} maxLength={60} onChange={(e) => update(i, { room: e.target.value })}
              className="min-h-11 flex-1 rounded-xl border border-separator bg-bg px-3 text-text" />
            <Button type="button" variant="ghost" className="text-danger" onClick={() => setSlots((l) => l.filter((_, j) => j !== i))}>✕</Button>
          </div>
        </div>
      ))}
      <Button type="button" variant="secondary" onClick={addSlot}>+ {t('groups.addLesson')}</Button>
      {save.error && <p className="text-sm text-danger">{errorMessage(save.error)}</p>}
      <div className="flex gap-2">
        <Button type="button" variant="secondary" className="flex-1" onClick={onDone}>{t('common.cancel')}</Button>
        <Button type="button" className="flex-1" loading={save.isPending}
          onClick={() => save.mutate(slots.map((s) => ({ ...s, room: s.room?.trim() || null })), { onSuccess: () => { haptic('success'); onDone() } })}>
          {t('people.save')}
        </Button>
      </div>
    </Section>
  )
}

function StudentPicker({ groupId, exclude, onDone }: { groupId: string; exclude: Set<string>; onDone(): void }) {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const [query, setQuery] = useState('')
  const students = useStudents(useDebounced(query.trim()), 'ACTIVE')
  const enroll = useEnroll(groupId)
  const candidates = (students.data?.pages.flatMap((p) => p.items) ?? []).filter((s) => !exclude.has(s.id))

  return (
    <Section className="flex flex-col gap-3">
      <div className="flex gap-2">
        <SearchInput value={query} onChange={setQuery} placeholder={t('people.search')} />
        <Button variant="ghost" onClick={onDone}>{t('common.done')}</Button>
      </div>
      {students.isPending && <Loading />}
      {students.isSuccess && candidates.length === 0 && <p className="text-sm text-hint">{t('people.nothingFound')}</p>}
      <div className="divide-y divide-separator">
        {candidates.slice(0, 20).map((s) => (
          <ListRow key={s.id} title={`${s.lastName} ${s.firstName}`} subtitle={s.phone ?? undefined}
            trailing={<span className="text-link">+ {t('people.add')}</span>}
            onClick={() => enroll.mutate(s.id, { onSuccess: () => haptic('success'), onError: () => haptic('error') })} />
        ))}
      </div>
      {enroll.error && <p className="text-sm text-danger">{errorMessage(enroll.error)}</p>}
    </Section>
  )
}
