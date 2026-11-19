import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import type { PersonStatus } from '../../shared/api/types'
import { useDebounced } from '../../shared/hooks/useDebounced'
import { useErrorMessage } from '../../shared/i18n'
import { formatPhone } from '../../shared/lib/format'
import { Badge, Button, EmptyState, ListRow, Loading, Screen, SearchInput, Section, Segmented } from '../../shared/ui/ui'
import { useTeachers } from './api'

export function TeachersPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<PersonStatus>('ACTIVE')
  const teachers = useTeachers(useDebounced(query.trim()), status)
  const items = teachers.data?.pages.flatMap((p) => p.items) ?? []

  return (
    <Screen
      title={t('teachers.title')}
      action={
        <Button onClick={() => navigate('/teachers/new')} className="min-h-9 px-3 text-sm">
          + {t('people.add')}
        </Button>
      }
    >
      <SearchInput value={query} onChange={setQuery} placeholder={t('people.search')} />
      <Segmented<PersonStatus>
        value={status}
        onChange={setStatus}
        options={[
          { value: 'ACTIVE', label: t('people.active') },
          { value: 'ARCHIVED', label: t('people.archived') },
        ]}
      />

      {teachers.isPending && <Loading />}
      {teachers.isError && (
        <EmptyState
          title={errorMessage(teachers.error)}
          action={<Button variant="secondary" onClick={() => void teachers.refetch()}>{t('common.retry')}</Button>}
        />
      )}
      {teachers.isSuccess && items.length === 0 && (
        query ? (
          <EmptyState title={t('people.nothingFound')} />
        ) : status === 'ACTIVE' ? (
          <EmptyState
            title={t('teachers.empty')}
            text={t('teachers.emptyHint')}
            action={<Button onClick={() => navigate('/teachers/new')}>{t('teachers.new')}</Button>}
          />
        ) : (
          <EmptyState title={t('teachers.emptyArchived')} />
        )
      )}
      {items.length > 0 && (
        <>
          <Section className="divide-y divide-separator p-0">
            {items.map((teacher) => (
              <ListRow
                key={teacher.id}
                title={teacher.fullName}
                subtitle={teacher.subjects ?? (teacher.phone ? formatPhone(teacher.phone) : t('people.noPhone'))}
                trailing={teacher.linked ? <Badge tone="good">{t('people.usesApp')}</Badge> : undefined}
                onClick={() => navigate(`/teachers/${teacher.id}`)}
              />
            ))}
          </Section>
          {teachers.hasNextPage && (
            <Button variant="secondary" loading={teachers.isFetchingNextPage} onClick={() => void teachers.fetchNextPage()}>
              {t('people.loadMore')}
            </Button>
          )}
        </>
      )}
    </Screen>
  )
}
