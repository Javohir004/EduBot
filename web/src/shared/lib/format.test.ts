import { formatDate, formatMoney } from './format'

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
