import { formatDate, formatMoney, formatMoneyShort, formatMonth, formatPhone } from './format'

describe('formatMoney', () => {
  it('formats soum with thousand separators', () => {
    expect(formatMoney(199000)).toBe("199 000 so'm")
  })

  it('keeps other currency codes', () => {
    expect(formatMoney(25, 'USD')).toBe('25 USD')
  })
})

describe('formatDate', () => {
  it('uses Tashkent time, not the device time zone', () => {
    // 20:30 UTC is already the next day in Tashkent (UTC+5).
    expect(formatDate('2026-11-19T20:30:00Z')).toBe('20.11.2026')
  })
})

describe('formatMoneyShort', () => {
  it('shortens millions and thousands for tiles', () => {
    expect(formatMoneyShort(16900000)).toBe("16,9 mln so'm")
    expect(formatMoneyShort(450000)).toBe("450 ming so'm")
    expect(formatMoneyShort(5000)).toBe("5 000 so'm")
  })
})

describe('formatMonth and formatPhone', () => {
  it('uses own month names (WebViews may lack Uzbek ICU data)', () => {
    expect(formatMonth('2026-11-01')).toBe('Noyabr 2026')
    expect(formatMonth('2026-01-15', 'ru')).toBe('Январь 2026')
  })

  it('groups Uzbek numbers and keeps foreign ones', () => {
    expect(formatPhone('+998901234567')).toBe('+998 90 123 45 67')
    expect(formatPhone('+79161234567')).toBe('+79161234567')
  })
})
