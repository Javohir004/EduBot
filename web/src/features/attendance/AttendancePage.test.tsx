import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { vi } from 'vitest'
import { AttendancePage } from './AttendancePage'

const lesson = {
  lessonId: 'L1',
  groupId: 'G1',
  groupName: 'IELTS',
  date: '2026-11-19',
  startTime: '14:00:00',
  editable: true,
  students: [
    { studentId: 'S1', firstName: 'Ali', lastName: 'Valiyev', status: null },
    { studentId: 'S2', firstName: 'Vali', lastName: 'Aliyev', status: 'ABSENT' },
  ],
}

const ok = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })

describe('AttendancePage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('"rest present" fills only unmarked students and saves all marks', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => ok(lesson))
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/lessons/L1']}>
          <Routes>
            <Route path="/lessons/:lessonId" element={<AttendancePage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    await userEvent.click(await screen.findByRole('button', { name: /Qolganlar keldi/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Saqlash' }))

    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')
    expect(JSON.parse(String(put?.[1]?.body))).toEqual([
      { studentId: 'S1', status: 'PRESENT' },
      { studentId: 'S2', status: 'ABSENT' },
    ])
  })
})
