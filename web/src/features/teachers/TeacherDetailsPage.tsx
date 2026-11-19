import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import type { InviteLink } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { confirmAction, haptic } from '../../shared/telegram/telegram'
import { useBackButton } from '../../shared/telegram/useBackButton'
import { InviteLinkCard } from '../../shared/ui/InviteLinkCard'
import { Badge, Button, EmptyState, ListRow, Loading, Screen, Section } from '../../shared/ui/ui'
import { useInviteTeacher, useSetTeacherArchived, useTeacher } from './api'

export function TeacherDetailsPage() {
  useBackButton()
  const { id = '' } = useParams()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const teacher = useTeacher(id)
  const setArchived = useSetTeacherArchived(id)
  const inviteTeacher = useInviteTeacher(id)
  const [invite, setInvite] = useState<InviteLink | null>(null)

  if (teacher.isPending) return <Loading />
  if (teacher.isError) return <Screen><EmptyState title={errorMessage(teacher.error)} /></Screen>

  const data = teacher.data
  const archived = data.status === 'ARCHIVED'
  const toggleArchive = async () => {
    if (!archived && !(await confirmAction(t('teachers.archiveConfirm')))) return
    setArchived.mutate(!archived, { onSuccess: () => haptic('success') })
  }
  const error = setArchived.error ?? inviteTeacher.error

  return (
    <Screen
      title={data.fullName}
      action={!archived && (
        <Button variant="secondary" onClick={() => navigate(`/teachers/${id}/edit`)} className="min-h-9 px-3 text-sm">
          {t('people.edit')}
        </Button>
      )}
    >
      <div className="flex gap-2">
        {archived && <Badge tone="warn">{t('people.archivedBadge')}</Badge>}
        <Badge tone={data.linked ? 'good' : 'neutral'}>{data.linked ? t('people.usesApp') : t('people.notInApp')}</Badge>
      </div>

      <Section className="divide-y divide-separator p-0">
        <ListRow title={t('people.phone')} subtitle={data.phone ?? t('people.noPhone')}
          trailing={data.phone && <a className="text-link" href={`tel:${data.phone}`}>{t('people.call')}</a>} />
        {data.subjects && <ListRow title={t('teachers.subjects')} subtitle={data.subjects} />}
      </Section>

      {invite ? (
        <InviteLinkCard title={t('people.inviteTitle')} text={t('people.inviteText')} link={invite.link}
          shareText={t('platform.inviteShareText')} onDone={() => setInvite(null)} />
      ) : (
        !archived && !data.linked && (
          <Button loading={inviteTeacher.isPending} disabled={!data.phone}
            onClick={() => inviteTeacher.mutate(undefined, { onSuccess: setInvite })}>
            {t('people.invite')}
          </Button>
        )
      )}
      {!data.phone && !data.linked && !archived && <p className="text-sm text-hint">{t('teachers.phoneForInvite')}</p>}

      {error && <p className="text-sm text-danger">{errorMessage(error)}</p>}

      <Button variant="secondary" loading={setArchived.isPending} onClick={() => void toggleArchive()}
        className={archived ? '' : 'text-danger'}>
        {archived ? t('people.restore') : t('people.archive')}
      </Button>
    </Screen>
  )
}
