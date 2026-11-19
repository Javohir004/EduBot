import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../app/auth-context'
import { Button, FullScreenMessage } from '../../shared/ui/ui'

/**
 * Shown to a Telegram user without a center. Telegram may only bring an already open Mini App to
 * the front (no reload) after the user accepts an invite in the bot, so the state is re-checked
 * whenever the app becomes visible again, and on demand.
 */
export function NoCenterPage() {
  const { t } = useTranslation()
  const { refresh } = useAuth()
  const [checking, setChecking] = useState(false)

  const check = async () => {
    setChecking(true)
    try {
      await refresh()
    } finally {
      setChecking(false)
    }
  }

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible') void refresh().catch(() => undefined)
    }
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener('focus', onVisible)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      window.removeEventListener('focus', onVisible)
    }
  }, [refresh])

  return (
    <FullScreenMessage
      icon="🔗"
      title={t('onboarding.noCenterTitle')}
      text={t('onboarding.noCenterText')}
      action={<Button variant="secondary" loading={checking} onClick={() => void check()}>{t('onboarding.recheck')}</Button>}
    />
  )
}
