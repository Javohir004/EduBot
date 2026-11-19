// Mirrors backend DTOs. To be replaced by types generated from the OpenAPI spec.

export type MembershipRole = 'OWNER' | 'ADMIN' | 'TEACHER' | 'PARENT'
export type CenterStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED'
export type SubscriptionStatus = 'TRIAL' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED'

export interface UserDto {
  id: string
  firstName: string
  lastName: string | null
  username: string | null
  languageCode: string | null
  platformRole: 'SUPER_ADMIN' | null
}

export interface MembershipDto {
  id: string
  centerId: string
  centerName: string
  role: MembershipRole
}

export interface MeResponse {
  user: UserDto
  memberships: MembershipDto[]
  activeMembershipId: string | null
}

export interface AuthResponse {
  accessToken: string
  expiresAt: string
  me: MeResponse
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface CenterSummary {
  id: string
  name: string
  phone: string | null
  status: CenterStatus
  createdAt: string
  planCode: string | null
  subscriptionStatus: SubscriptionStatus | null
  subscriptionEndsAt: string | null
}

export interface CreatedCenterResponse {
  center: CenterSummary
  ownerInvite: { link: string; expiresAt: string }
}

export interface PlanResponse {
  code: string
  name: string
  monthlyPrice: number
  currency: string
}
