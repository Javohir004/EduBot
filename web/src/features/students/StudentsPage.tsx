import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import type { PersonStatus } from '../../shared/api/types'
import { useDebounced } from '../../shared/hooks/useDebounced'
import { useErrorMessage } from '../../shared/i18n'
import { formatPhone } from '../../shared/lib/format'
import { Button, EmptyState, ListRow, Loading, Screen, SearchInput, Section, Segmented } from '../../shared/ui/ui'
import { useStudents } from './api'

export function StudentsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<PersonStatus>('ACTIVE')
  const students = useStudents(useDebounced(query.trim()), status)
  const items = students.data?.pages.flatMap((p) => p.items) ?? []
  const total = students.data?.pages[0]?.totalItems ?? 0

  return (
    <Screen
      title={t('students.title')}
      action={
        <Button onClick={() => navigate('/students/new')} className="min-h-9 px-3 text-sm">
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

      {students.isPending && <Loading />}
      {students.isError && (
        <EmptyState
          title={errorMessage(students.error)}
          action={<Button variant="secondary" onClick={() => void students.refetch()}>{t('common.retry')}</Button>}
        />
      )}
      {students.isSuccess && items.length === 0 && (
        query ? (
          <EmptyState title={t('people.nothingFound')} />
        ) : status === 'ACTIVE' ? (
          <EmptyState
            title={t('students.empty')}
            text={t('students.emptyHint')}
            action={<Button onClick={() => navigate('/students/new')}>{t('students.new')}</Button>}
          />
        ) : (
          <EmptyState title={t('students.emptyArchived')} />
        )
      )}
      {items.length > 0 && (
        <>
          <p className="text-sm text-hint">{t('platform.total', { count: total })}</p>
          <Section className="divide-y divide-separator p-0">
            {items.map((s) => (
              <ListRow
                key={s.id}
                title={`${s.lastName} ${s.firstName}`}
                subtitle={s.phone ? formatPhone(s.phone) : t('people.noPhone')}
                onClick={() => navigate(`/students/${s.id}`)}
              />
            ))}
          </Section>
          {students.hasNextPage && (
            <Button variant="secondary" loading={students.isFetchingNextPage} onClick={() => void students.fetchNextPage()}>
              {t('people.loadMore')}
            </Button>
          )}
        </>
      )}
    </Screen>
  )
}
