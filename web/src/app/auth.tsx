import type { ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, json, onUnauthorized, setAccessToken } from '../shared/api/client'
import type { AuthResponse } from '../shared/api/types'
import { getWebApp } from '../shared/telegram/telegram'
import type { AuthContextValue, AuthState } from './auth-context'
import { AuthContext } from './auth-context'

async function exchangeInitData(initData: string): Promise<AuthResponse> {
  return api<AuthResponse>('/api/v1/auth/telegram', { method: 'POST', body: json({ initData }) })
}

/**
 * Authenticates with Telegram initData on start. The backend verifies the signature; the client
 * never sends user ids or roles. On 401 it re-authenticates once with the same initData.
 */
export function AuthProvider({ children, render }: { children: ReactNode; render(state: AuthState): ReactNode }) {
  const [state, setState] = useState<AuthState>(() =>
    getWebApp() ? { status: 'loading' } : { status: 'outside-telegram' },
  )
  const initDataRef = useRef<string | null>(null)

  const applyAuth = useCallback((response: AuthResponse) => {
    setAccessToken(response.accessToken)
    setState({ status: 'ready', me: response.me })
  }, [])

  useEffect(() => {
    const webApp = getWebApp()
    if (!webApp) return
    webApp.ready()
    webApp.expand()
    initDataRef.current = webApp.initData

    onUnauthorized(async () => {
      if (!initDataRef.current) return false
      try {
        applyAuth(await exchangeInitData(initDataRef.current))
        return true
      } catch {
        setState({ status: 'failed' })
        return false
      }
    })

    exchangeInitData(webApp.initData)
      .then(applyAuth)
      .catch(() => setState({ status: 'failed' }))

    return () => onUnauthorized(null)
  }, [applyAuth])

  const selectMembership = useCallback(
    async (membershipId: string) => {
      applyAuth(await api<AuthResponse>('/api/v1/auth/session', { method: 'POST', body: json({ membershipId }) }))
    },
    [applyAuth],
  )

  const value = useMemo<AuthContextValue | null>(() => {
    if (state.status !== 'ready') return null
    const { me } = state
    return {
      me,
      activeMembership: me.memberships.find((m) => m.id === me.activeMembershipId) ?? null,
      isPlatformAdmin: me.user.platformRole === 'SUPER_ADMIN',
      selectMembership,
    }
  }, [state, selectMembership])

  return <AuthContext.Provider value={value}>{value ? children : render(state)}</AuthContext.Provider>
}
