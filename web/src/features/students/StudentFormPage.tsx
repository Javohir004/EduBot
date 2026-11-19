import type { FormEvent } from 'react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../../shared/api/client'
import type { GuardianRelation, StudentDetails } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { haptic } from '../../shared/telegram/telegram'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { Button, Field, Loading, Screen, Section, SelectField, TextArea } from '../../shared/ui/ui'
import { RELATIONS, useCreateStudent, useStudent, useUpdateStudent } from './api'

export function StudentFormPage() {
  const { id } = useParams()
  const student = useStudent(id)
  if (id && student.isPending) return <Loading />
  return <StudentForm existing={id ? student.data : undefined} />
}

function StudentForm({ existing }: { existing?: StudentDetails }) {
  useBackButton()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const create = useCreateStudent()
  const update = useUpdateStudent(existing?.id ?? '')
  const mutation = existing ? update : create

  const [form, setForm] = useState({
    firstName: existing?.firstName ?? '',
    lastName: existing?.lastName ?? '',
    birthDate: existing?.birthDate ?? '',
    phone: existing?.phone ?? '',
    note: existing?.note ?? '',
    guardianName: '',
    guardianPhone: '',
    relation: 'MOTHER' as GuardianRelation,
  })
  const set = (key: keyof typeof form) => (value: string) => setForm((f) => ({ ...f, [key]: value }))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const fields = {
      firstName: form.firstName.trim(),
      lastName: form.lastName.trim(),
      birthDate: form.birthDate || null,
      phone: form.phone.trim() || null,
      note: form.note.trim() || null,
    }
    const options = {
      onSuccess: (saved: StudentDetails) => {
        haptic('success')
        navigate(`/students/${saved.id}`, { replace: true })
      },
      onError: () => haptic('error'),
    }
    if (existing) {
      update.mutate({ student: fields, version: existing.version }, options)
    } else {
      const guardians = form.guardianPhone.trim()
        ? [{ fullName: form.guardianName.trim() || form.lastName.trim(), phone: form.guardianPhone.trim(), relation: form.relation }]
        : []
      create.mutate({ student: fields, guardians }, options)
    }
  }

  const error = mutation.error
  const phoneError = error instanceof ApiError && error.code === 'INVALID_PHONE' ? errorMessage(error) : undefined

  return (
    <Screen title={existing ? t('students.editTitle') : t('students.new')}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Section className="flex flex-col gap-4">
          <Field label={t('students.firstName')} value={form.firstName} onChange={(e) => set('firstName')(e.target.value)} required maxLength={100} />
          <Field label={t('students.lastName')} value={form.lastName} onChange={(e) => set('lastName')(e.target.value)} required maxLength={100} />
          <Field label={t('students.birthDate')} type="date" value={form.birthDate} onChange={(e) => set('birthDate')(e.target.value)} />
          <Field label={t('people.phone')} type="tel" inputMode="tel" placeholder="+998 90 123 45 67" value={form.phone} onChange={(e) => set('phone')(e.target.value)} error={phoneError} />
          <TextArea label={t('students.note')} value={form.note} onChange={(e) => set('note')(e.target.value)} maxLength={1000} />
        </Section>

        {!existing && (
          <Section className="flex flex-col gap-4">
            <h2 className="font-semibold">{t('students.guardians')}</h2>
            <Field label={t('students.guardianName')} value={form.guardianName} onChange={(e) => set('guardianName')(e.target.value)} maxLength={200} />
            <Field label={t('students.guardianPhone')} type="tel" inputMode="tel" placeholder="+998 90 123 45 67" value={form.guardianPhone} onChange={(e) => set('guardianPhone')(e.target.value)} />
            <SelectField label={t('students.relation')} value={form.relation} onChange={(e) => set('relation')(e.target.value)}>
              {RELATIONS.map((r) => (
                <option key={r} value={r}>{t(`students.relations.${r}`)}</option>
              ))}
            </SelectField>
          </Section>
        )}

        {error && !phoneError && <p className="text-sm text-danger">{errorMessage(error)}</p>}
        <Button type="submit" loading={mutation.isPending}>{t('people.save')}</Button>
      </form>
    </Screen>
  )
}
