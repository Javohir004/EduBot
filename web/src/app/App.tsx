import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AttendanceHistoryPage } from '../features/attendance/AttendanceHistoryPage'
import { AttendancePage } from '../features/attendance/AttendancePage'
import { GroupDetailsPage } from '../features/groups/GroupDetailsPage'
import { GroupFormPage } from '../features/groups/GroupFormPage'
import { GroupsPage } from '../features/groups/GroupsPage'
import { HomePage } from '../features/home/HomePage'
import { CenterPickerPage } from '../features/onboarding/CenterPickerPage'
import { NoCenterPage } from '../features/onboarding/NoCenterPage'
import { DebtorsPage } from '../features/payments/DebtorsPage'
import { ChildPage } from '../features/parent/ParentPages'
import { SettingsPage } from '../features/settings/SettingsPage'
import { PlatformCentersPage } from '../features/platform/PlatformCentersPage'
import { StudentDetailsPage } from '../features/students/StudentDetailsPage'
import { StudentFormPage } from '../features/students/StudentFormPage'
import { StudentsPage } from '../features/students/StudentsPage'
import { TeacherDetailsPage } from '../features/teachers/TeacherDetailsPage'
import { TeacherFormPage } from '../features/teachers/TeacherFormPage'
import { TeachersPage } from '../features/teachers/TeachersPage'
import { ApiError } from '../shared/api/client'
import { FullScreenMessage, Spinner } from '../shared/ui/ui'
import { AuthProvider } from './auth'
import { useAuth } from './auth-context'
import { TabLayout } from './TabLayout'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Do not retry client errors (403/404...), only transient failures.
      retry: (count, error) => count < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
    },
  },
})

export function App() {
  const { t } = useTranslation()
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider
        render={(state) => {
          switch (state.status) {
            case 'loading':
              return (
                <div className="flex min-h-dvh items-center justify-center">
                  <Spinner />
                </div>
              )
            case 'outside-telegram':
              return <FullScreenMessage icon="📱" title={t('auth.outsideTitle')} text={t('auth.outsideText')} />
            default:
              return <FullScreenMessage icon="⚠️" title={t('auth.failedTitle')} text={t('auth.failedText')} />
          }
        }}
      >
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  )
}

function AppRoutes() {
  const { me, activeMembership, isPlatformAdmin } = useAuth()

  if (!activeMembership && me.memberships.length > 1) {
    return <CenterPickerPage />
  }
  if (!activeMembership && !isPlatformAdmin) {
    return <NoCenterPage />
  }
  const managesPeople = activeMembership?.role === 'OWNER' || activeMembership?.role === 'ADMIN'
  const seesGroups = managesPeople || activeMembership?.role === 'TEACHER'
  return (
    <Routes>
      <Route element={<TabLayout />}>
        <Route path="/" element={activeMembership ? <HomePage /> : <Navigate to="/platform" replace />} />
        {seesGroups && <Route path="/groups" element={<GroupsPage />} />}
        {managesPeople && <Route path="/students" element={<StudentsPage />} />}
        {managesPeople && <Route path="/payments" element={<DebtorsPage />} />}
        {managesPeople && <Route path="/teachers" element={<TeachersPage />} />}
        {isPlatformAdmin && <Route path="/platform" element={<PlatformCentersPage />} />}
      </Route>
      {seesGroups && <Route path="/groups/:id" element={<GroupDetailsPage />} />}
      {activeMembership?.role === 'OWNER' && <Route path="/settings" element={<SettingsPage />} />}
      {activeMembership?.role === 'PARENT' && <Route path="/children/:id" element={<ChildPage />} />}
      {seesGroups && <Route path="/groups/:id/attendance" element={<AttendanceHistoryPage />} />}
      {seesGroups && <Route path="/lessons/:lessonId" element={<AttendancePage />} />}
      {managesPeople && (
        <>
          <Route path="/groups/new" element={<GroupFormPage />} />
          <Route path="/groups/:id/edit" element={<GroupFormPage />} />
          <Route path="/students/new" element={<StudentFormPage />} />
          <Route path="/students/:id" element={<StudentDetailsPage />} />
          <Route path="/students/:id/edit" element={<StudentFormPage />} />
          <Route path="/teachers/new" element={<TeacherFormPage />} />
          <Route path="/teachers/:id" element={<TeacherDetailsPage />} />
          <Route path="/teachers/:id/edit" element={<TeacherFormPage />} />
        </>
      )}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
