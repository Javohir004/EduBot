import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import { SubscriptionBanner } from './SubscriptionBanner'

const respond = (phase: string, endsAt: string) =>
  new Response(JSON.stringify({ planCode: 'START', planName: 'Start', phase, endsAt, graceUntil: endsAt,
    usage: { students: 0, teachers: 0, groups: 0 }, limits: {} }), { status: 200, headers: { 'Content-Type': 'application/json' } })

function renderBanner() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SubscriptionBanner />
    </QueryClientProvider>,
  )
}

describe('SubscriptionBanner', () => {
  afterEach(() => vi.restoreAllMocks())

  it('warns that an expired center is read-only', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(respond('EXPIRED', '2026-01-01T00:00:00Z'))
    renderBanner()
    expect(await screen.findByRole('alert')).toHaveTextContent('faqat ko‘rish')
  })

  it('stays hidden for a subscription that is far from ending', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(respond('ACTIVE', '2099-01-01T00:00:00Z'))
    renderBanner()
    await new Promise((r) => setTimeout(r, 50))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
