import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { useAuth } from '../../app/auth-context'
import type { GroupStatus } from '../../shared/api/types'
import { useDebounced } from '../../shared/hooks/useDebounced'
import { useErrorMessage } from '../../shared/i18n'
import { formatMoney } from '../../shared/lib/format'
import { Button, EmptyState, ListRow, Loading, Screen, SearchInput, Section, Segmented } from '../../shared/ui/ui'
import { useGroups } from './api'

export function GroupsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const { activeMembership } = useAuth()
  const canEdit = activeMembership?.role === 'OWNER' || activeMembership?.role === 'ADMIN'
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<GroupStatus>('ACTIVE')
  const groups = useGroups(useDebounced(query.trim()), status)
  const items = groups.data?.pages.flatMap((p) => p.items) ?? []

  return (
    <Screen
      title={t('groups.title')}
      action={canEdit && (
        <Button onClick={() => navigate('/groups/new')} className="min-h-9 px-3 text-sm">+ {t('people.add')}</Button>
      )}
    >
      <SearchInput value={query} onChange={setQuery} placeholder={t('groups.search')} />
      <Segmented<GroupStatus>
        value={status}
        onChange={setStatus}
        options={[
          { value: 'ACTIVE', label: t('people.active') },
          { value: 'COMPLETED', label: t('groups.completed') },
        ]}
      />

      {groups.isPending && <Loading />}
      {groups.isError && <EmptyState title={errorMessage(groups.error)} />}
      {groups.isSuccess && items.length === 0 && (
        query ? <EmptyState title={t('people.nothingFound')} />
          : status === 'COMPLETED' ? <EmptyState title={t('groups.emptyCompleted')} />
          : <EmptyState title={t('groups.empty')} text={canEdit ? t('groups.emptyHint') : t('groups.emptyTeacher')}
              action={canEdit && <Button onClick={() => navigate('/groups/new')}>{t('groups.new')}</Button>} />
      )}
      {items.length > 0 && (
        <Section className="divide-y divide-separator p-0">
          {items.map((g) => (
            <ListRow
              key={g.id}
              title={g.name}
              subtitle={[g.teacherName ?? t('groups.noTeacher'), formatMoney(g.monthlyFee)].join(' · ')}
              trailing={
                <span className="text-sm text-hint">
                  {g.capacity ? `${g.studentCount}/${g.capacity}` : g.studentCount} 👤
                </span>
              }
              onClick={() => navigate(`/groups/${g.id}`)}
            />
          ))}
        </Section>
      )}
      {groups.hasNextPage && (
        <Button variant="secondary" loading={groups.isFetchingNextPage} onClick={() => void groups.fetchNextPage()}>
          {t('people.loadMore')}
        </Button>
      )}
    </Screen>
  )
}
