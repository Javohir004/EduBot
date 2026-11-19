/**
 * Minimal fetch client for the EduBot API. The access token lives in memory only
 * (never in localStorage): a reopened Mini App simply re-authenticates with fresh initData.
 */

export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  code?: string
  errors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: { field: string; message: string }[]

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`)
    this.status = problem.status
    this.code = problem.code ?? 'UNKNOWN'
    this.fieldErrors = problem.errors ?? []
  }
}

let accessToken: string | null = null
let unauthorizedHandler: (() => Promise<boolean>) | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

/** Called once on 401; returns true when a new token was obtained and the request may be retried. */
export function onUnauthorized(handler: (() => Promise<boolean>) | null): void {
  unauthorizedHandler = handler
}

export async function api<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError({ status: 0, code: 'NETWORK_ERROR', detail: 'Network error' })
  }

  if (response.status === 401 && !retried && unauthorizedHandler && (await unauthorizedHandler())) {
    return api<T>(path, init, true)
  }
  if (!response.ok) {
    throw new ApiError(await readProblem(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  try {
    const body = (await response.json()) as Partial<ProblemDetail>
    return { ...body, status: response.status }
  } catch {
    return { status: response.status }
  }
}

export const json = (body: unknown): RequestInit['body'] => JSON.stringify(body)
