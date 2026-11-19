import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { useErrorMessage } from '../../shared/i18n'
import { formatDate, formatMoney } from '../../shared/lib/format'
import { Button, EmptyState, Loading, Screen, Section } from '../../shared/ui/ui'
import { useDebtors } from './api'

export function DebtorsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const debtors = useDebtors()

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
              <div key={d.studentId} className="flex items-center justify-between gap-3 px-4 py-3">
                <button type="button" className="min-w-0 flex-1 text-left" onClick={() => navigate(`/students/${d.studentId}`)}>
                  <span className="block truncate font-medium">{d.lastName} {d.firstName}</span>
                  <span className="block text-sm text-hint">
                    {t('payments.since', { date: formatDate(`${d.oldestDue}T12:00:00Z`) })}
                  </span>
                </button>
                <div className="flex flex-col items-end gap-1">
                  <span className="font-semibold text-danger">{formatMoney(d.debt)}</span>
                  {phone && <a href={`tel:${phone}`} className="text-sm text-link">📞 {t('people.call')}</a>}
                </div>
              </div>
            )
          })}
        </Section>
      )}
    </Screen>
  )
}
