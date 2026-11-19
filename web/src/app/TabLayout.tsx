import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'
import { useAuth } from './auth-context'

interface Tab {
  to: string
  label: string
  icon: string
}

/** Tabs mirror the role only for navigation; every endpoint is still authorized by the backend. */
function useTabs(): Tab[] {
  const { t } = useTranslation()
  const { activeMembership, isPlatformAdmin } = useAuth()
  const role = activeMembership?.role
  const tabs: Tab[] = []
  if (activeMembership) tabs.push({ to: '/', label: t('tabs.home'), icon: '🏠' })
  if (role === 'OWNER' || role === 'ADMIN' || role === 'TEACHER') {
    tabs.push({ to: '/groups', label: t('tabs.groups'), icon: '👥' })
  }
  if (role === 'OWNER' || role === 'ADMIN') {
    tabs.push({ to: '/students', label: t('tabs.students'), icon: '🎓' })
    tabs.push({ to: '/teachers', label: t('tabs.teachers'), icon: '👩‍🏫' })
  }
  if (isPlatformAdmin) tabs.push({ to: '/platform', label: t('tabs.platform'), icon: '⚙️' })
  return tabs
}

export function TabLayout() {
  const tabs = useTabs()
  if (tabs.length < 2) return <Outlet />

  return (
    <div className="pb-[calc(4.5rem+env(safe-area-inset-bottom))]">
      <Outlet />
      <nav className="fixed inset-x-0 bottom-0 z-10 border-t border-separator bg-section pb-[env(safe-area-inset-bottom)]">
        <ul className="mx-auto flex max-w-xl">
          {tabs.map((tab) => (
            <li key={tab.to} className="flex-1">
              <NavLink
                to={tab.to}
                end={tab.to === '/'}
                className={({ isActive }) =>
                  `flex flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${isActive ? 'text-accent' : 'text-hint'}`
                }
              >
                <span className="text-xl leading-none" aria-hidden>
                  {tab.icon}
                </span>
                {tab.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  )
}
