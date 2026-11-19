import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatDate } from '../../shared/lib/format'
import { useSubscription } from './api'

const WEEK_MS = 7 * 24 * 60 * 60 * 1000

/** Warns staff when the subscription is about to end, in grace, or expired (read-only). */
export function SubscriptionBanner() {
  const { t } = useTranslation()
  const subscription = useSubscription()
  const [now] = useState(() => Date.now())
  const data = subscription.data
  if (!data || !data.endsAt) return null

  const endsAt = data.endsAt
  const endsSoon = data.phase === 'ACTIVE' && new Date(endsAt).getTime() - now < WEEK_MS
  if (data.phase === 'ACTIVE' && !endsSoon) return null

  const expired = data.phase === 'EXPIRED' || data.phase === 'NONE'
  const text = expired
    ? t('subscription.expired')
    : data.phase === 'GRACE'
      ? t('subscription.grace', { date: formatDate(data.graceUntil ?? endsAt) })
      : t('subscription.endsSoon', { date: formatDate(endsAt) })

  return (
    <div role="alert" className={`rounded-2xl px-4 py-3 text-sm font-medium ${expired ? 'bg-red-500/15 text-red-700 dark:text-red-400' : 'bg-amber-500/15 text-amber-700 dark:text-amber-400'}`}>
      {text}
    </div>
  )
}
