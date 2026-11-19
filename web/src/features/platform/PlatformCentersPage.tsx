import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CenterSummary, CreatedCenterResponse } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { formatDate } from '../../shared/lib/format'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { useAuth } from '../../app/auth-context'
import { InviteLinkCard } from '../../shared/ui/InviteLinkCard'
import { Badge, Button, EmptyState, Screen, Section, Spinner } from '../../shared/ui/ui'
import { useCenters } from './api'
import { CreateCenterForm } from './CreateCenterForm'

export function PlatformCentersPage() {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const { activeMembership } = useAuth()
  // A platform admin without a center lands here directly, so there is nothing to go back to.
  useBackButton(activeMembership !== null)
  const centers = useCenters()
  const [creating, setCreating] = useState(false)
  const [created, setCreated] = useState<CreatedCenterResponse | null>(null)

  return (
    <Screen
      title={t('platform.title')}
      action={
        !creating &&
        !created && (
          <Button onClick={() => setCreating(true)} className="min-h-9 px-3 text-sm">
            + {t('platform.create')}
          </Button>
        )
      }
    >
      {creating && (
        <CreateCenterForm
          onCancel={() => setCreating(false)}
          onCreated={(result) => {
            setCreating(false)
            setCreated(result)
          }}
        />
      )}

      {created && (
        <InviteLinkCard
          title={t('platform.inviteTitle')}
          text={t('platform.inviteText')}
          link={created.ownerInvite.link}
          shareText={`${t('platform.inviteShareText')}: ${created.center.name}`}
          onDone={() => setCreated(null)}
        />
      )}

      {centers.isPending && (
        <div className="flex justify-center py-10">
          <Spinner />
        </div>
      )}

      {centers.isError && (
        <EmptyState
          title={errorMessage(centers.error)}
          action={
            <Button variant="secondary" onClick={() => void centers.refetch()}>
              {t('common.retry')}
            </Button>
          }
        />
      )}

      {centers.data && centers.data.items.length === 0 && !creating && (
        <EmptyState title={t('platform.empty')} text={t('platform.emptyHint')} />
      )}

      {centers.data && centers.data.items.length > 0 && (
        <>
          <p className="text-sm text-hint">{t('platform.total', { count: centers.data.totalItems })}</p>
          <Section className="divide-y divide-separator p-0">
            {centers.data.items.map((center) => (
              <CenterRow key={center.id} center={center} />
            ))}
          </Section>
        </>
      )}
    </Screen>
  )
}

function CenterRow({ center }: { center: CenterSummary }) {
  const { t } = useTranslation()
  const statusTone = center.status === 'ACTIVE' ? 'good' : center.status === 'SUSPENDED' ? 'warn' : 'bad'
  return (
    <div className="flex items-start justify-between gap-3 px-4 py-3.5">
      <div className="min-w-0">
        <p className="truncate font-medium">{center.name}</p>
        <p className="text-sm text-hint">
          {center.planCode ?? '—'}
          {center.subscriptionStatus && ` · ${t(`platform.subscription.${center.subscriptionStatus}`)}`}
          {center.subscriptionEndsAt && ` · ${t('platform.until', { date: formatDate(center.subscriptionEndsAt) })}`}
        </p>
      </div>
      <Badge tone={statusTone}>{t(`platform.status.${center.status}`)}</Badge>
    </div>
  )
}
