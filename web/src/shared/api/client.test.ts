import { afterEach, vi } from 'vitest'
import { api, ApiError, onUnauthorized, setAccessToken } from './client'

const respond = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

afterEach(() => {
  vi.restoreAllMocks()
  setAccessToken(null)
  onUnauthorized(null)
})

describe('api client', () => {
  it('sends the bearer token', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(respond(200, { ok: true }))
    setAccessToken('abc')

    await api('/api/v1/me')

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers)
    expect(headers.get('Authorization')).toBe('Bearer abc')
  })

  it('maps problem details to ApiError with the backend code', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(respond(400, { code: 'INVALID_PHONE', detail: 'Invalid phone number' }))

    const error = await api('/api/v1/invites', { method: 'POST' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('INVALID_PHONE')
    expect((error as ApiError).status).toBe(400)
  })

  it('re-authenticates once on 401 and retries the request', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(respond(401, { code: 'UNAUTHORIZED' }))
      .mockResolvedValueOnce(respond(200, { value: 1 }))
    const reauth = vi.fn().mockResolvedValue(true)
    onUnauthorized(reauth)

    await expect(api<{ value: number }>('/api/v1/me')).resolves.toEqual({ value: 1 })
    expect(reauth).toHaveBeenCalledTimes(1)
  })

  it('reports network failures as NETWORK_ERROR', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(api('/api/v1/me')).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
  })
})
