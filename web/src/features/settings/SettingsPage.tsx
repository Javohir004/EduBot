import type { FormEvent } from 'react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { InviteLink } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { formatDate } from '../../shared/lib/format'
import { confirmAction, haptic } from '../../shared/telegram/telegram'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { InviteLinkCard } from '../../shared/ui/InviteLinkCard'
import { Button, Field, ListRow, Loading, Screen, Section } from '../../shared/ui/ui'
import type { CenterSettingsDto } from './api'
import { useCenterSettings, useInviteAdmin, useRevokeStaff, useSaveSettings, useStaff, useSubscription } from './api'

export function SettingsPage() {
  useBackButton()
  const { t } = useTranslation()
  return (
    <Screen title={t('settings.title')}>
      <SubscriptionCard />
      <NotificationSettings />
      <StaffSection />
    </Screen>
  )
}

function SubscriptionCard() {
  const { t } = useTranslation()
  const subscription = useSubscription()
  if (subscription.isPending) return <Loading />
  const s = subscription.data
  if (!s) return null
  const row = (key: 'students' | 'teachers' | 'groups', limitKey: string) => (
    <ListRow title={t(`settings.usage.${key}`)} subtitle={`${s.usage[key]} / ${s.limits[limitKey] ?? '—'}`} />
  )
  return (
    <>
      <h2 className="font-semibold">{t('settings.subscription')}</h2>
      <Section className="divide-y divide-separator p-0">
        <ListRow title={s.planName ?? '—'} subtitle={s.endsAt ? t('platform.until', { date: formatDate(s.endsAt) }) : undefined}
          trailing={<span className="text-sm text-hint">{t(`subscription.phase.${s.phase}`)}</span>} />
        {row('students', 'MAX_STUDENTS')}
        {row('teachers', 'MAX_TEACHERS')}
        {row('groups', 'MAX_GROUPS')}
      </Section>
      <p className="text-xs text-hint">{t('settings.upgradeHint')}</p>
    </>
  )
}

function NotificationSettings() {
  const { t } = useTranslation()
  const settings = useCenterSettings()
  if (!settings.data) return settings.isPending ? <Loading /> : null
  return <NotificationForm key={JSON.stringify(settings.data)} initial={settings.data} t={t} />
}

function NotificationForm({ initial, t }: { initial: CenterSettingsDto; t: ReturnType<typeof useTranslation>['t'] }) {
  const errorMessage = useErrorMessage()
  const save = useSaveSettings()
  const [form, setForm] = useState(initial)
  const changed = JSON.stringify(form) !== JSON.stringify(initial)

  const toggle = (key: 'absenceNotificationsEnabled' | 'paymentRemindersEnabled', label: string, hint: string) => (
    <label className="flex items-center justify-between gap-3 px-4 py-3">
      <span>
        <span className="block font-medium">{label}</span>
        <span className="block text-sm text-hint">{hint}</span>
      </span>
      <input type="checkbox" className="size-5 accent-[var(--color-accent)]" checked={form[key]}
        onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.checked }))} />
    </label>
  )

  const submit = (event: FormEvent) => {
    event.preventDefault()
    save.mutate(form, { onSuccess: () => haptic('success') })
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3">
      <h2 className="font-semibold">{t('settings.notifications')}</h2>
      <Section className="divide-y divide-separator p-0">
        {toggle('absenceNotificationsEnabled', t('settings.absence'), t('settings.absenceHint'))}
        {toggle('paymentRemindersEnabled', t('settings.reminders'), t('settings.remindersHint'))}
        <div className="px-4 py-3">
          <Field label={t('settings.dueDay')} hint={t('settings.dueDayHint')} type="number" inputMode="numeric" min={1} max={28}
            value={String(form.paymentDueDay)} onChange={(e) => setForm((f) => ({ ...f, paymentDueDay: Number(e.target.value) }))} />
        </div>
      </Section>
      {save.error && <p className="text-sm text-danger">{errorMessage(save.error)}</p>}
      {changed && <Button type="submit" loading={save.isPending}>{t('people.save')}</Button>}
    </form>
  )
}

function StaffSection() {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const staff = useStaff()
  const revoke = useRevokeStaff()
  const inviteAdmin = useInviteAdmin()
  const [phone, setPhone] = useState('')
  const [invite, setInvite] = useState<InviteLink | null>(null)

  const remove = async (membershipId: string, name: string) => {
    if (await confirmAction(t('settings.revokeConfirm', { name }))) revoke.mutate(membershipId, { onSuccess: () => haptic('success') })
  }

  return (
    <>
      <h2 className="font-semibold">{t('settings.staff')}</h2>
      {staff.isPending && <Loading />}
      {staff.data && (
        <Section className="divide-y divide-separator p-0">
          {staff.data.map((m) => {
            const name = [m.firstName, m.lastName].filter(Boolean).join(' ')
            return (
              <ListRow key={m.membershipId} title={name} subtitle={t(`roles.${m.role}`)}
                trailing={m.role !== 'OWNER' && (
                  <button type="button" className="text-sm text-danger" onClick={() => void remove(m.membershipId, name)}>
                    {t('students.remove')}
                  </button>
                )} />
            )
          })}
        </Section>
      )}
      {revoke.error && <p className="text-sm text-danger">{errorMessage(revoke.error)}</p>}

      {invite ? (
        <InviteLinkCard title={t('people.inviteTitle')} text={t('people.inviteText')} link={invite.link}
          shareText={t('platform.inviteShareText')} onDone={() => { setInvite(null); setPhone('') }} />
      ) : (
        <Section>
          <form className="flex flex-col gap-3" onSubmit={(e) => {
            e.preventDefault()
            inviteAdmin.mutate(phone.trim(), { onSuccess: setInvite })
          }}>
            <Field label={t('settings.inviteAdmin')} hint={t('settings.inviteAdminHint')} type="tel" inputMode="tel"
              placeholder="+998 90 123 45 67" required value={phone} onChange={(e) => setPhone(e.target.value)} />
            {inviteAdmin.error && <p className="text-sm text-danger">{errorMessage(inviteAdmin.error)}</p>}
            <Button type="submit" variant="secondary" loading={inviteAdmin.isPending}>{t('people.invite')}</Button>
          </form>
        </Section>
      )}
    </>
  )
}
