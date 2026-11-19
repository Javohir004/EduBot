import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../app/auth-context'
import { useErrorMessage } from '../../shared/i18n'
import { Screen, Section, Spinner } from '../../shared/ui/ui'

export function CenterPickerPage() {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const { me, selectMembership } = useAuth()
  const [pendingId, setPendingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const choose = async (membershipId: string) => {
    setPendingId(membershipId)
    setError(null)
    try {
      await selectMembership(membershipId)
    } catch (e) {
      setError(errorMessage(e))
      setPendingId(null)
    }
  }

  return (
    <Screen title={t('onboarding.pickCenterTitle')}>
      <p className="text-[15px] text-hint">{t('onboarding.pickCenterText')}</p>
      <Section className="divide-y divide-separator p-0">
        {me.memberships.map((m) => (
          <button
            key={m.id}
            type="button"
            disabled={pendingId !== null}
            onClick={() => choose(m.id)}
            className="flex w-full items-center justify-between gap-3 px-4 py-3.5 text-left active:bg-surface"
          >
            <span>
              <span className="block font-medium">{m.centerName}</span>
              <span className="block text-sm text-hint">{t(`roles.${m.role}`)}</span>
            </span>
            {pendingId === m.id ? <Spinner small /> : <span className="text-hint">›</span>}
          </button>
        ))}
      </Section>
      {error && <p className="text-sm text-danger">{error}</p>}
    </Screen>
  )
}
