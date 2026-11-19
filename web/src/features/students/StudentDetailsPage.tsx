import type { FormEvent } from 'react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import type { GuardianRelation, InviteLink, StudentDetails } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { formatDate } from '../../shared/lib/format'
import { confirmAction, haptic } from '../../shared/telegram/telegram'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { InviteLinkCard } from '../../shared/ui/InviteLinkCard'
import { useStudentEnrollments } from '../groups/api'
import { StudentBalance } from '../payments/StudentBalance'
import { Badge, Button, EmptyState, Field, ListRow, Loading, Screen, Section, SelectField } from '../../shared/ui/ui'
import { RELATIONS, useAddGuardian, useInviteGuardian, useRemoveGuardian, useSetStudentArchived, useStudent } from './api'

export function StudentDetailsPage() {
  useBackButton()
  const { id = '' } = useParams()
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const student = useStudent(id)

  if (student.isPending) return <Loading />
  if (student.isError) return <Screen><EmptyState title={errorMessage(student.error)} /></Screen>
  return <StudentDetailsView student={student.data} t={t} />
}

function StudentDetailsView({ student, t }: { student: StudentDetails; t: ReturnType<typeof useTranslation>['t'] }) {
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const setArchived = useSetStudentArchived(student.id)
  const removeGuardian = useRemoveGuardian(student.id)
  const inviteGuardian = useInviteGuardian(student.id)
  const [invite, setInvite] = useState<InviteLink | null>(null)
  const [addingGuardian, setAddingGuardian] = useState(false)
  const archived = student.status === 'ARCHIVED'

  const toggleArchive = async () => {
    if (!archived && !(await confirmAction(t('students.archiveConfirm')))) return
    setArchived.mutate(!archived, { onSuccess: () => haptic('success') })
  }

  const remove = async (guardianId: string) => {
    if (await confirmAction(t('students.removeGuardianConfirm'))) removeGuardian.mutate(guardianId)
  }

  const error = setArchived.error ?? removeGuardian.error ?? inviteGuardian.error

  return (
    <Screen
      title={`${student.lastName} ${student.firstName}`}
      action={!archived && (
        <Button variant="secondary" onClick={() => navigate(`/students/${student.id}/edit`)} className="min-h-9 px-3 text-sm">
          {t('people.edit')}
        </Button>
      )}
    >
      {archived && <Badge tone="warn">{t('people.archivedBadge')}</Badge>}

      <Section className="divide-y divide-separator p-0">
        <ListRow title={t('people.phone')} subtitle={student.phone ?? t('people.noPhone')}
          trailing={student.phone && <a className="text-link" href={`tel:${student.phone}`}>{t('people.call')}</a>} />
        {student.birthDate && <ListRow title={t('students.birthDate')} subtitle={formatDate(`${student.birthDate}T12:00:00Z`)} />}
        {student.note && <ListRow title={t('students.note')} subtitle={student.note} />}
      </Section>

      <StudentGroups studentId={student.id} />

      <StudentBalance studentId={student.id} archived={archived} />

      {invite && (
        <InviteLinkCard title={t('people.inviteTitle')} text={t('people.inviteText')} link={invite.link}
          shareText={t('platform.inviteShareText')} onDone={() => setInvite(null)} />
      )}

      <div className="flex items-center justify-between">
        <h2 className="font-semibold">{t('students.guardians')}</h2>
        {!archived && !addingGuardian && (
          <Button variant="ghost" className="min-h-9 px-2 text-sm" onClick={() => setAddingGuardian(true)}>
            + {t('students.addGuardian')}
          </Button>
        )}
      </div>

      {addingGuardian && <AddGuardianForm studentId={student.id} onDone={() => setAddingGuardian(false)} />}

      {student.guardians.length === 0 && !addingGuardian ? (
        <EmptyState title={t('students.noGuardians')} />
      ) : (
        student.guardians.length > 0 && (
          <Section className="divide-y divide-separator p-0">
            {student.guardians.map((g) => (
              <div key={g.id} className="flex flex-col gap-2 px-4 py-3">
                <div className="flex items-start justify-between gap-3">
                  <span className="min-w-0">
                    <span className="block font-medium">{g.fullName}</span>
                    <span className="block text-sm text-hint">
                      {t(`students.relations.${g.relation}`)} · <a className="text-link" href={`tel:${g.phone}`}>{g.phone}</a>
                    </span>
                  </span>
                  <Badge tone={g.linked ? 'good' : 'neutral'}>{g.linked ? t('people.usesApp') : t('people.notInApp')}</Badge>
                </div>
                {!archived && (
                  <div className="flex gap-2">
                    {!g.linked && (
                      <Button variant="secondary" className="min-h-9 flex-1 text-sm" loading={inviteGuardian.isPending}
                        onClick={() => inviteGuardian.mutate(g.id, { onSuccess: setInvite })}>
                        {t('people.invite')}
                      </Button>
                    )}
                    <Button variant="ghost" className="min-h-9 text-sm text-danger" onClick={() => void remove(g.id)}>
                      {t('students.remove')}
                    </Button>
                  </div>
                )}
              </div>
            ))}
          </Section>
        )
      )}

      {error && <p className="text-sm text-danger">{errorMessage(error)}</p>}

      <Button variant="secondary" loading={setArchived.isPending} onClick={() => void toggleArchive()}
        className={archived ? '' : 'text-danger'}>
        {archived ? t('people.restore') : t('people.archive')}
      </Button>
    </Screen>
  )
}

function AddGuardianForm({ studentId, onDone }: { studentId: string; onDone(): void }) {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const addGuardian = useAddGuardian(studentId)
  const [form, setForm] = useState({ fullName: '', phone: '', relation: 'MOTHER' as GuardianRelation })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    addGuardian.mutate(
      { fullName: form.fullName.trim(), phone: form.phone.trim(), relation: form.relation },
      { onSuccess: () => { haptic('success'); onDone() } },
    )
  }

  return (
    <Section>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field label={t('students.guardianName')} value={form.fullName} required maxLength={200}
          onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))} />
        <Field label={t('students.guardianPhone')} type="tel" inputMode="tel" required placeholder="+998 90 123 45 67"
          value={form.phone} onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))} />
        <SelectField label={t('students.relation')} value={form.relation}
          onChange={(e) => setForm((f) => ({ ...f, relation: e.target.value as GuardianRelation }))}>
          {RELATIONS.map((r) => <option key={r} value={r}>{t(`students.relations.${r}`)}</option>)}
        </SelectField>
        {addGuardian.error && <p className="text-sm text-danger">{errorMessage(addGuardian.error)}</p>}
        <div className="flex gap-2">
          <Button type="button" variant="secondary" className="flex-1" onClick={onDone}>{t('common.cancel')}</Button>
          <Button type="submit" className="flex-1" loading={addGuardian.isPending}>{t('people.save')}</Button>
        </div>
      </form>
    </Section>
  )
}

function StudentGroups({ studentId }: { studentId: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const enrollments = useStudentEnrollments(studentId)
  const active = enrollments.data?.filter((e) => e.status === 'ACTIVE') ?? []
  if (enrollments.isPending) return null
  return (
    <>
      <h2 className="font-semibold">{t('groups.studentGroups')}</h2>
      {active.length === 0 ? (
        <EmptyState title={t('groups.noStudentGroups')} />
      ) : (
        <Section className="divide-y divide-separator p-0">
          {active.map((e) => (
            <ListRow key={e.id} title={e.groupName ?? '—'}
              subtitle={t('groups.since', { date: formatDate(`${e.joinedOn}T12:00:00Z`) })}
              onClick={() => navigate(`/groups/${e.groupId}`)} />
          ))}
        </Section>
      )}
    </>
  )
}
