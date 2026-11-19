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

export type PersonStatus = 'ACTIVE' | 'ARCHIVED'
export type GuardianRelation = 'MOTHER' | 'FATHER' | 'GUARDIAN' | 'OTHER'

export interface StudentSummary {
  id: string
  firstName: string
  lastName: string
  phone: string | null
  status: PersonStatus
}

export interface GuardianDto {
  id: string
  fullName: string
  phone: string
  relation: GuardianRelation
  linked: boolean
}

export interface StudentDetails extends StudentSummary {
  birthDate: string | null
  note: string | null
  version: number
  guardians: GuardianDto[]
}

export interface StudentFields {
  firstName: string
  lastName: string
  birthDate?: string | null
  phone?: string | null
  note?: string | null
}

export interface TeacherDto {
  id: string
  fullName: string
  phone: string | null
  subjects: string | null
  status: PersonStatus
  linked: boolean
  version: number
}

export interface InviteLink {
  link: string
  expiresAt: string
}

export type GroupStatus = 'ACTIVE' | 'COMPLETED'
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface Slot {
  day: DayOfWeek
  startTime: string
  endTime: string
  room?: string | null
}

export interface GroupSummary {
  id: string
  name: string
  subject: string | null
  teacherName: string | null
  monthlyFee: number
  status: GroupStatus
  studentCount: number
  capacity: number | null
}

export interface GroupDetails extends GroupSummary {
  teacherId: string | null
  startDate: string
  endDate: string | null
  version: number
  schedule: Slot[]
}

export interface GroupMember {
  enrollmentId: string
  studentId: string
  firstName: string
  lastName: string
  phone: string | null
  joinedOn: string
  feeOverride: number | null
}

export interface EnrollmentDto {
  id: string
  studentId: string
  groupId: string
  groupName: string | null
  joinedOn: string
  leftOn: string | null
  status: 'ACTIVE' | 'ENDED'
  feeOverride: number | null
}

export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED'

export interface TodayLesson {
  groupId: string
  groupName: string
  startTime: string
  endTime: string
  room: string | null
  lessonId: string | null
  studentCount: number
  markedCount: number
}

export interface RosterEntry {
  studentId: string
  firstName: string
  lastName: string
  status: AttendanceStatus | null
}

export interface LessonView {
  lessonId: string
  groupId: string
  groupName: string
  date: string
  startTime: string
  editable: boolean
  students: RosterEntry[]
}

export interface AttendanceHistory {
  lessons: { lessonId: string; date: string; startTime: string }[]
  students: { studentId: string; firstName: string; lastName: string; marks: Record<string, AttendanceStatus> }[]
}
