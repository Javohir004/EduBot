import type { FormEvent } from 'react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../../shared/api/client'
import type { CreatedCenterResponse } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { formatMoney } from '../../shared/lib/format'
import { haptic } from '../../shared/telegram/telegram'
import { Button, Field, Section, SelectField } from '../../shared/ui/ui'
import { useCreateCenter, usePlans } from './api'

export function CreateCenterForm({
  onCreated,
  onCancel,
}: {
  onCreated(result: CreatedCenterResponse): void
  onCancel(): void
}) {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const plans = usePlans()
  const createCenter = useCreateCenter()
  const [form, setForm] = useState({ name: '', phone: '', ownerPhone: '', planCode: 'START', trialDays: '14' })

  const set = (key: keyof typeof form) => (value: string) => setForm((f) => ({ ...f, [key]: value }))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    createCenter.mutate(
      {
        name: form.name.trim(),
        phone: form.phone.trim() || undefined,
        ownerPhone: form.ownerPhone.trim(),
        planCode: form.planCode,
        trialDays: Number(form.trialDays),
      },
      {
        onSuccess: (result) => {
          haptic('success')
          onCreated(result)
        },
        onError: () => haptic('error'),
      },
    )
  }

  const error = createCenter.error
  const phoneError = error instanceof ApiError && error.code === 'INVALID_PHONE' ? errorMessage(error) : undefined

  return (
    <Section>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">{t('platform.create')}</h2>
        <Field label={t('platform.name')} value={form.name} onChange={(e) => set('name')(e.target.value)} required maxLength={200} />
        <Field
          label={t('platform.ownerPhone')}
          hint={t('platform.ownerPhoneHint')}
          error={phoneError}
          value={form.ownerPhone}
          onChange={(e) => set('ownerPhone')(e.target.value)}
          type="tel"
          inputMode="tel"
          placeholder="+998 90 123 45 67"
          required
        />
        <Field
          label={t('platform.centerPhone')}
          value={form.phone}
          onChange={(e) => set('phone')(e.target.value)}
          type="tel"
          inputMode="tel"
        />
        <SelectField label={t('platform.plan')} value={form.planCode} onChange={(e) => set('planCode')(e.target.value)}>
          {(plans.data ?? []).map((p) => (
            <option key={p.code} value={p.code}>
              {p.name} — {formatMoney(p.monthlyPrice, p.currency)}
              {t('platform.perMonth')}
            </option>
          ))}
        </SelectField>
        <Field
          label={t('platform.trialDays')}
          value={form.trialDays}
          onChange={(e) => set('trialDays')(e.target.value)}
          type="number"
          inputMode="numeric"
          min={1}
          max={90}
          required
        />
        {error && !phoneError && <p className="text-sm text-danger">{errorMessage(error)}</p>}
        <div className="flex gap-2">
          <Button type="button" variant="secondary" onClick={onCancel} className="flex-1">
            {t('common.cancel')}
          </Button>
          <Button type="submit" loading={createCenter.isPending} className="flex-1">
            {t('platform.submit')}
          </Button>
        </div>
      </form>
    </Section>
  )
}
