import type { FormEvent } from 'react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import type { GroupDetails } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { haptic } from '../../shared/telegram/telegram'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { Button, Field, Loading, Screen, Section, SelectField } from '../../shared/ui/ui'
import { useTeachers } from '../teachers/api'
import { useGroup, useSaveGroup } from './api'

export function GroupFormPage() {
  const { id } = useParams()
  const group = useGroup(id)
  if (id && group.isPending) return <Loading />
  return <GroupForm existing={id ? group.data : undefined} />
}

function todayIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tashkent' }).format(new Date())
}

function GroupForm({ existing }: { existing?: GroupDetails }) {
  useBackButton()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const save = useSaveGroup(existing)
  const teachers = useTeachers('', 'ACTIVE')
  const teacherOptions = teachers.data?.pages.flatMap((p) => p.items) ?? []
  const [form, setForm] = useState({
    name: existing?.name ?? '',
    subject: existing?.subject ?? '',
    teacherId: existing?.teacherId ?? '',
    monthlyFee: existing ? String(existing.monthlyFee) : '',
    startDate: existing?.startDate ?? todayIso(),
    endDate: existing?.endDate ?? '',
    capacity: existing?.capacity ? String(existing.capacity) : '',
  })
  const set = (key: keyof typeof form) => (value: string) => setForm((f) => ({ ...f, [key]: value }))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    save.mutate(
      {
        name: form.name.trim(),
        subject: form.subject.trim() || null,
        teacherId: form.teacherId || null,
        monthlyFee: Number(form.monthlyFee.replace(/\s/g, '')),
        startDate: form.startDate,
        endDate: form.endDate || null,
        capacity: form.capacity ? Number(form.capacity) : null,
      },
      {
        onSuccess: (saved) => {
          haptic('success')
          navigate(`/groups/${saved.id}`, { replace: true })
        },
        onError: () => haptic('error'),
      },
    )
  }

  return (
    <Screen title={existing ? t('groups.editTitle') : t('groups.new')}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Section className="flex flex-col gap-4">
          <Field label={t('groups.name')} value={form.name} onChange={(e) => set('name')(e.target.value)} required maxLength={120} />
          <Field label={t('groups.subject')} value={form.subject} onChange={(e) => set('subject')(e.target.value)} maxLength={120} />
          <SelectField label={t('groups.teacher')} value={form.teacherId} onChange={(e) => set('teacherId')(e.target.value)}>
            <option value="">{t('groups.noTeacher')}</option>
            {teacherOptions.map((teacher) => (
              <option key={teacher.id} value={teacher.id}>{teacher.fullName}</option>
            ))}
          </SelectField>
          <Field label={t('groups.monthlyFee')} hint={t('groups.monthlyFeeHint')} inputMode="numeric" required
            value={form.monthlyFee} onChange={(e) => set('monthlyFee')(e.target.value.replace(/[^\d\s]/g, ''))} />
          <div className="grid grid-cols-2 gap-3">
            <Field label={t('groups.startDate')} type="date" required value={form.startDate} onChange={(e) => set('startDate')(e.target.value)} />
            <Field label={t('groups.endDate')} type="date" value={form.endDate} onChange={(e) => set('endDate')(e.target.value)} />
          </div>
          <Field label={t('groups.capacity')} hint={t('groups.capacityHint')} type="number" inputMode="numeric" min={1}
            value={form.capacity} onChange={(e) => set('capacity')(e.target.value)} />
        </Section>
        {save.error && <p className="text-sm text-danger">{errorMessage(save.error)}</p>}
        <Button type="submit" loading={save.isPending}>{t('people.save')}</Button>
      </form>
    </Screen>
  )
}
