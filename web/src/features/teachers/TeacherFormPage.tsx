import type { FormEvent } from 'react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../../shared/api/client'
import type { TeacherDto } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { haptic } from '../../shared/telegram/telegram'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { Button, Field, Loading, Screen, Section } from '../../shared/ui/ui'
import { useSaveTeacher, useTeacher } from './api'

export function TeacherFormPage() {
  const { id } = useParams()
  const teacher = useTeacher(id)
  if (id && teacher.isPending) return <Loading />
  return <TeacherForm existing={id ? teacher.data : undefined} />
}

function TeacherForm({ existing }: { existing?: TeacherDto }) {
  useBackButton()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const save = useSaveTeacher(existing)
  const [form, setForm] = useState({
    fullName: existing?.fullName ?? '',
    phone: existing?.phone ?? '',
    subjects: existing?.subjects ?? '',
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    save.mutate(
      { fullName: form.fullName.trim(), phone: form.phone.trim() || null, subjects: form.subjects.trim() || null },
      {
        onSuccess: (saved) => {
          haptic('success')
          navigate(`/teachers/${saved.id}`, { replace: true })
        },
        onError: () => haptic('error'),
      },
    )
  }

  const phoneError = save.error instanceof ApiError && save.error.code === 'INVALID_PHONE' ? errorMessage(save.error) : undefined

  return (
    <Screen title={existing ? t('teachers.editTitle') : t('teachers.new')}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Section className="flex flex-col gap-4">
          <Field label={t('teachers.fullName')} value={form.fullName} required maxLength={200}
            onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))} />
          <Field label={t('people.phone')} type="tel" inputMode="tel" placeholder="+998 90 123 45 67"
            hint={t('teachers.phoneForInvite')} error={phoneError} value={form.phone}
            onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))} />
          <Field label={t('teachers.subjects')} hint={t('teachers.subjectsHint')} value={form.subjects} maxLength={300}
            onChange={(e) => setForm((f) => ({ ...f, subjects: e.target.value }))} />
        </Section>
        {save.error && !phoneError && <p className="text-sm text-danger">{errorMessage(save.error)}</p>}
        <Button type="submit" loading={save.isPending}>{t('people.save')}</Button>
      </form>
    </Screen>
  )
}
