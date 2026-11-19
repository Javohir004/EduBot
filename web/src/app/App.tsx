import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { HomePage } from '../features/home/HomePage'
import { CenterPickerPage } from '../features/onboarding/CenterPickerPage'
import { NoCenterPage } from '../features/onboarding/NoCenterPage'
import { PlatformCentersPage } from '../features/platform/PlatformCentersPage'
import { ApiError } from '../shared/api/client'
import { FullScreenMessage, Spinner } from '../shared/ui/ui'
import { AuthProvider } from './auth'
import { useAuth } from './auth-context'

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
  return (
    <Routes>
      <Route path="/" element={activeMembership ? <HomePage /> : <Navigate to="/platform" replace />} />
      {isPlatformAdmin && <Route path="/platform" element={<PlatformCentersPage />} />}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
