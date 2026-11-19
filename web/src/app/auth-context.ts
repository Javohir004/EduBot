import { createContext, useContext } from 'react'
import type { MeResponse, MembershipDto } from '../shared/api/types'

export type AuthState =
  | { status: 'loading' }
  | { status: 'outside-telegram' }
  | { status: 'failed' }
  | { status: 'ready'; me: MeResponse }

export interface AuthContextValue {
  me: MeResponse
  activeMembership: MembershipDto | null
  isPlatformAdmin: boolean
  selectMembership(membershipId: string): Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside an authenticated AuthProvider')
  return value
}
