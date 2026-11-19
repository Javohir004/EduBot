import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'
import { StudentsPage } from './StudentsPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <StudentsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const page = (items: unknown[]) =>
  new Response(JSON.stringify({ items, page: 0, size: 30, totalItems: items.length, totalPages: 1 }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

describe('StudentsPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('shows the empty state with a call to action', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(page([]))

    renderPage()

    expect(await screen.findByText('Hali o‘quvchilar yo‘q')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Yangi o‘quvchi' })).toBeInTheDocument()
  })

  it('lists students by last name and shows a missing phone', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      page([{ id: '1', firstName: 'Ali', lastName: 'Valiyev', phone: null, status: 'ACTIVE' }]),
    )

    renderPage()

    expect(await screen.findByText('Valiyev Ali')).toBeInTheDocument()
    expect(screen.getByText('Telefon kiritilmagan')).toBeInTheDocument()
  })

  it('shows a localized error when the request is forbidden', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 'FORBIDDEN' }), { status: 403, headers: { 'Content-Type': 'application/json' } }),
    )

    renderPage()

    expect(await screen.findByText('Bu amal uchun ruxsatingiz yo‘q')).toBeInTheDocument()
  })
})
