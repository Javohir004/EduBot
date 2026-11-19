import type { FormEvent } from 'react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../app/auth-context'
import type { ChargeDto, PaymentDto, PaymentMethod } from '../../shared/api/types'
import { useErrorMessage } from '../../shared/i18n'
import { formatDate, formatMoney, formatMonth } from '../../shared/lib/format'
import { haptic } from '../../shared/telegram/telegram'
import { Badge, Button, Field, Loading, Section } from '../../shared/ui/ui'
import { METHODS, newIdempotencyKey, useBalance, useRecordPayment, useVoidPayment } from './api'

const CHARGE_TONE = { PAID: 'good', PARTIAL: 'warn', UNPAID: 'bad', VOID: 'neutral' } as const

/** Balance block on the student profile: debt, payment form, charges and payment history. */
export function StudentBalance({ studentId, archived }: { studentId: string; archived: boolean }) {
  const { t } = useTranslation()
  const { activeMembership } = useAuth()
  const balance = useBalance(studentId)
  const [paying, setPaying] = useState(false)

  if (balance.isPending) return <Loading />
  if (!balance.data) return null
  const { debt, credit, charges, payments } = balance.data

  return (
    <>
      <h2 className="font-semibold">{t('payments.balance')}</h2>
      <Section className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm text-hint">{debt > 0 ? t('payments.debt') : t('payments.noDebt')}</p>
          <p className={`text-xl font-semibold ${debt > 0 ? 'text-danger' : 'text-emerald-600'}`}>{formatMoney(debt)}</p>
          {credit > 0 && <p className="text-sm text-emerald-600">{t('payments.credit', { amount: formatMoney(credit) })}</p>}
        </div>
        {!paying && !archived && <Button onClick={() => setPaying(true)}>{t('payments.receive')}</Button>}
      </Section>

      {paying && <PaymentForm studentId={studentId} suggested={debt} onDone={() => setPaying(false)} />}

      {charges.length > 0 && (
        <Section className="divide-y divide-separator p-0">
          {charges.map((c) => <ChargeRow key={c.id} charge={c} />)}
        </Section>
      )}

      {payments.length > 0 && (
        <>
          <h3 className="text-sm font-medium text-hint">{t('payments.history')}</h3>
          <Section className="divide-y divide-separator p-0">
            {payments.map((p) => (
              <PaymentRow key={p.id} studentId={studentId} payment={p} canVoid={activeMembership?.role === 'OWNER'} />
            ))}
          </Section>
        </>
      )}
    </>
  )
}

function ChargeRow({ charge }: { charge: ChargeDto }) {
  const { t, i18n } = useTranslation()
  return (
    <div className="flex items-center justify-between gap-3 px-4 py-3">
      <span className="min-w-0">
        <span className="block font-medium capitalize">{formatMonth(charge.periodStart, i18n.language)}</span>
        <span className="block truncate text-sm text-hint">{charge.groupName}</span>
      </span>
      <span className="flex flex-col items-end gap-1">
        <span className="text-sm">
          {charge.paymentStatus === 'PARTIAL' ? `${formatMoney(charge.paidAmount)} / ` : ''}{formatMoney(charge.amount)}
        </span>
        <Badge tone={CHARGE_TONE[charge.paymentStatus]}>{t(`payments.chargeStatus.${charge.paymentStatus}`)}</Badge>
      </span>
    </div>
  )
}

function PaymentRow({ studentId, payment, canVoid }: { studentId: string; payment: PaymentDto; canVoid: boolean }) {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const voidPayment = useVoidPayment(studentId)
  const [voiding, setVoiding] = useState(false)
  const [reason, setReason] = useState('')
  const voided = payment.status === 'VOIDED'

  return (
    <div className="flex flex-col gap-2 px-4 py-3">
      <div className="flex items-start justify-between gap-3">
        <span className="min-w-0">
          <span className={`block font-medium ${voided ? 'text-hint line-through' : ''}`}>{formatMoney(payment.amount)}</span>
          <span className="block text-sm text-hint">
            {formatDate(payment.paidAt)} · {t(`payments.method.${payment.method}`)} · {payment.receivedBy}
          </span>
          {payment.note && <span className="block text-sm text-hint">{payment.note}</span>}
          {voided && payment.voidReason && <span className="block text-sm text-danger">{payment.voidReason}</span>}
        </span>
        {voided ? <Badge tone="neutral">{t('payments.voided')}</Badge>
          : canVoid && !voiding && (
            <button type="button" className="text-sm text-danger" onClick={() => setVoiding(true)}>{t('payments.void')}</button>
          )}
      </div>
      {voiding && (
        <form className="flex flex-col gap-2" onSubmit={(e) => {
          e.preventDefault()
          voidPayment.mutate({ paymentId: payment.id, reason: reason.trim() }, { onSuccess: () => { haptic('success'); setVoiding(false) } })
        }}>
          <Field label={t('payments.voidReason')} value={reason} onChange={(e) => setReason(e.target.value)} required maxLength={500} />
          {voidPayment.error && <p className="text-sm text-danger">{errorMessage(voidPayment.error)}</p>}
          <div className="flex gap-2">
            <Button type="button" variant="secondary" className="flex-1" onClick={() => setVoiding(false)}>{t('common.cancel')}</Button>
            <Button type="submit" className="flex-1" loading={voidPayment.isPending}>{t('payments.void')}</Button>
          </div>
        </form>
      )}
    </div>
  )
}

function PaymentForm({ studentId, suggested, onDone }: { studentId: string; suggested: number; onDone(): void }) {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const [idempotencyKey] = useState(newIdempotencyKey)
  const record = useRecordPayment(studentId, idempotencyKey)
  const [amount, setAmount] = useState(suggested > 0 ? String(suggested) : '')
  const [method, setMethod] = useState<PaymentMethod>('CASH')
  const [note, setNote] = useState('')
  const value = Number(amount.replace(/\s/g, ''))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    record.mutate({ amount: value, method, note: note.trim() || null }, {
      onSuccess: () => { haptic('success'); onDone() },
      onError: () => haptic('error'),
    })
  }

  return (
    <Section>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field label={t('payments.amount')} inputMode="numeric" required value={amount}
          hint={value > 0 ? formatMoney(value) : undefined}
          onChange={(e) => setAmount(e.target.value.replace(/[^\d\s]/g, ''))} />
        <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label={t('payments.methodLabel')}>
          {METHODS.map((m) => (
            <button key={m} type="button" role="radio" aria-checked={method === m} onClick={() => setMethod(m)}
              className={`min-h-10 rounded-xl text-sm font-medium ${method === m ? 'bg-accent text-accent-text' : 'bg-surface text-hint'}`}>
              {t(`payments.method.${m}`)}
            </button>
          ))}
        </div>
        <Field label={t('payments.note')} value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
        {record.error && <p className="text-sm text-danger">{errorMessage(record.error)}</p>}
        <div className="flex gap-2">
          <Button type="button" variant="secondary" className="flex-1" onClick={onDone}>{t('common.cancel')}</Button>
          <Button type="submit" className="flex-1" loading={record.isPending} disabled={!(value > 0)}>{t('payments.save')}</Button>
        </div>
      </form>
    </Section>
  )
}
