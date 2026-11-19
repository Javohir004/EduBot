const TASHKENT = 'Asia/Tashkent'

/** 199000 -> "199 000 so'm" (UZS has no minor units in practice). */
export function formatMoney(amount: number, currency = 'UZS'): string {
  const value = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 })
    .format(amount)
    .replace(/ /g, ' ')
  return currency === 'UZS' ? `${value} so'm` : `${value} ${currency}`
}

const UZ_MONTHS = ['Yanvar', 'Fevral', 'Mart', 'Aprel', 'May', 'Iyun', 'Iyul', 'Avgust', 'Sentabr', 'Oktabr', 'Noyabr', 'Dekabr']
const RU_MONTHS = ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь']

/**
 * "2026-11-01" -> "Noyabr 2026". Own month names: many WebViews ship ICU without Uzbek
 * and would print "2026 M11".
 */
export function formatMonth(isoDate: string, language = 'uz'): string {
  const [year, month] = isoDate.split('-').map(Number)
  return `${(language === 'ru' ? RU_MONTHS : UZ_MONTHS)[month - 1]} ${year}`
}

/** "+998901234567" -> "+998 90 123 45 67"; other numbers are shown as stored. */
export function formatPhone(e164: string | null | undefined): string {
  if (!e164) return ''
  const m = /^\+998(\d{2})(\d{3})(\d{2})(\d{2})$/.exec(e164)
  return m ? `+998 ${m[1]} ${m[2]} ${m[3]} ${m[4]}` : e164
}

/** Short form for tiles: 16900000 -> "16,9 mln so'm", 450000 -> "450 ming so'm". */
export function formatMoneyShort(amount: number, language = 'uz'): string {
  const ru = language === 'ru'
  const unit = ru ? 'сум' : "so'm"
  const fmt = (v: number) => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(v)
  if (Math.abs(amount) >= 1_000_000) return `${fmt(amount / 1_000_000)} ${ru ? 'млн' : 'mln'} ${unit}`
  if (Math.abs(amount) >= 10_000) return `${fmt(amount / 1_000)} ${ru ? 'тыс.' : 'ming'} ${unit}`
  return formatMoney(amount)
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
