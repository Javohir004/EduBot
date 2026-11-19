import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { AuthContext } from '../../app/auth-context'
import { StudentBalance } from './StudentBalance'

const balance = {
  debt: 300000,
  credit: 0,
  charges: [{ id: 'C1', groupId: 'G1', groupName: 'IELTS', periodStart: '2026-11-01', periodEnd: '2026-11-30',
    amount: 500000, paidAmount: 200000, dueDate: '2026-11-05', status: 'OPEN', paymentStatus: 'PARTIAL' }],
  payments: [],
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

describe('StudentBalance', () => {
  afterEach(() => vi.restoreAllMocks())

  it('prefills the debt and sends the same idempotency key on retry', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (_url, init) =>
      init?.method === 'POST' ? json({ code: 'NETWORK_ERROR' }, 503) : json(balance))
    const auth = {
      me: { user: { id: 'U', firstName: 'A', lastName: null, username: null, languageCode: 'uz', platformRole: null },
        memberships: [], activeMembershipId: 'M' },
      activeMembership: { id: 'M', centerId: 'C', centerName: 'Alpha', role: 'OWNER' as const },
      isPlatformAdmin: false,
      selectMembership: async () => {},
    }
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AuthContext.Provider value={auth}>
          <StudentBalance studentId="S1" archived={false} />
        </AuthContext.Provider>
      </QueryClientProvider>,
    )

    expect(await screen.findByText("300 000 so'm")).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'To‘lov qabul qilish' }))
    expect(screen.getByLabelText('Summa, so‘m')).toHaveValue('300000')

    await userEvent.click(screen.getByRole('button', { name: 'Qabul qilish' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Qabul qilish' }))

    const keys = fetchMock.mock.calls
      .filter(([, init]) => init?.method === 'POST')
      .map(([, init]) => new Headers(init?.headers).get('Idempotency-Key'))
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBeTruthy()
    expect(keys[0]).toBe(keys[1])
  })
})
