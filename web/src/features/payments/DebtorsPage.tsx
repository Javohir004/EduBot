import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { useErrorMessage } from '../../shared/i18n'
import { formatDate, formatMoney } from '../../shared/lib/format'
import { Button, EmptyState, Loading, Screen, Section } from '../../shared/ui/ui'
import { haptic } from '../../shared/telegram/telegram'
import { useDebtors, useRemind } from './api'

export function DebtorsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const debtors = useDebtors()
  const remind = useRemind()
  const [reminded, setReminded] = useState<Set<string>>(new Set())
  const sendReminder = (studentId: string) =>
    remind.mutate(studentId, {
      onSuccess: () => { haptic('success'); setReminded((s) => new Set(s).add(studentId)) },
      onError: () => haptic('error'),
    })

  return (
    <Screen title={t('payments.title')}>
      {debtors.isPending && <Loading />}
      {debtors.isError && (
        <EmptyState title={errorMessage(debtors.error)}
          action={<Button variant="secondary" onClick={() => void debtors.refetch()}>{t('common.retry')}</Button>} />
      )}
      {debtors.data && (
        <Section className="grid grid-cols-2 gap-3">
          <div>
            <p className="text-sm text-hint">{t('payments.totalDebt')}</p>
            <p className="text-xl font-semibold text-danger">{formatMoney(debtors.data.total)}</p>
          </div>
          <div>
            <p className="text-sm text-hint">{t('payments.debtors')}</p>
            <p className="text-xl font-semibold">{debtors.data.students}</p>
          </div>
        </Section>
      )}
      {debtors.data && debtors.data.items.length === 0 && (
        <EmptyState title={t('payments.noDebtors')} text={t('payments.noDebtorsHint')} />
      )}
      {debtors.data && debtors.data.items.length > 0 && (
        <Section className="divide-y divide-separator p-0">
          {debtors.data.items.map((d) => {
            const phone = d.guardianPhone ?? d.phone
            return (
              <div key={d.studentId} className="flex flex-col gap-1.5 px-4 py-3">
                <button type="button" className="flex items-baseline justify-between gap-3 text-left"
                  onClick={() => navigate(`/students/${d.studentId}`)}>
                  <span className="min-w-0 truncate font-medium">{d.lastName} {d.firstName}</span>
                  <span className="shrink-0 font-semibold text-danger tabular-nums">{formatMoney(d.debt)}</span>
                </button>
                <div className="flex items-center justify-between gap-3">
                  <span className="text-sm whitespace-nowrap text-hint">
                    {t('payments.since', { date: formatDate(`${d.oldestDue}T12:00:00Z`) })}
                  </span>
                  <span className="flex shrink-0 gap-4">
                    {phone && <a href={`tel:${phone}`} className="text-sm text-link">📞 {t('people.call')}</a>}
                    {reminded.has(d.studentId)
                      ? <span className="text-sm text-emerald-600">✓ {t('payments.reminded')}</span>
                      : <button type="button" className="text-sm text-link" onClick={() => sendReminder(d.studentId)}>🔔 {t('payments.remind')}</button>}
                  </span>
                </div>
              </div>
            )
          })}
        </Section>
      )}
      {remind.error && <p className="text-sm text-danger">{errorMessage(remind.error)}</p>}
    </Screen>
  )
}
