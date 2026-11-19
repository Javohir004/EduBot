const TASHKENT = 'Asia/Tashkent'

/** 199000 -> "199 000 so'm" (UZS has no minor units in practice). */
export function formatMoney(amount: number, currency = 'UZS'): string {
  const value = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 })
    .format(amount)
    .replace(/ /g, ' ')
  return currency === 'UZS' ? `${value} so'm` : `${value} ${currency}`
}

/** ISO timestamp -> "19.11.2026" in Tashkent time, regardless of the device time zone. */
export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone: TASHKENT,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(iso))
}
