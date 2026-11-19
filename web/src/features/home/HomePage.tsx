import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useAuth } from '../../app/auth-context'
import { Screen, Section } from '../../shared/ui/ui'
import { TodayLessons } from '../attendance/TodayLessons'
import { ActivityFeed, DashboardTiles } from './Dashboard'
import { ParentChildren } from '../parent/ParentPages'
import { SubscriptionBanner } from '../settings/SubscriptionBanner'

export function HomePage() {
  const { t } = useTranslation()
  const { me, activeMembership, isPlatformAdmin, selectMembership } = useAuth()
  const otherMemberships = me.memberships.filter((m) => m.id !== activeMembership?.id)
  const role = activeMembership?.role
  const marksAttendance = role === 'OWNER' || role === 'ADMIN' || role === 'TEACHER'
  const managesCenter = role === 'OWNER' || role === 'ADMIN'

  return (
    <Screen>
      <header className="pt-2">
        <p className="text-sm text-hint">
          {activeMembership?.centerName} · {activeMembership && t(`roles.${activeMembership.role}`)}
        </p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight">
          {t('home.greeting', { name: me.user.firstName })}
        </h1>
      </header>

      {(role === 'OWNER' || role === 'ADMIN' || role === 'TEACHER') && <SubscriptionBanner />}

      {managesCenter && <DashboardTiles />}

      {marksAttendance ? (
        <TodayLessons />
      ) : role === 'PARENT' ? (
        <ParentChildren />
      ) : (
        <Section>
          <p className="text-[15px] leading-relaxed text-hint">{t('home.inProgress')}</p>
        </Section>
      )}

      {managesCenter && <ActivityFeed />}

      {role === 'OWNER' && (
        <Link to="/settings" className="block">
          <Section className="flex items-center justify-between active:opacity-80">
            <span>
              <span className="block font-medium">{t('settings.title')}</span>
              <span className="block text-sm text-hint">{t('settings.hint')}</span>
            </span>
            <span className="text-hint">›</span>
          </Section>
        </Link>
      )}

      {isPlatformAdmin && (
        <Link to="/platform" className="block">
          <Section className="flex items-center justify-between active:opacity-80">
            <span>
              <span className="block font-medium">{t('home.platform')}</span>
              <span className="block text-sm text-hint">{t('home.platformHint')}</span>
            </span>
            <span className="text-hint">›</span>
          </Section>
        </Link>
      )}

      {otherMemberships.length > 0 && (
        <Section className="p-0">
          <p className="px-4 pt-3 text-sm font-medium text-hint">{t('home.switchCenter')}</p>
          {otherMemberships.map((m) => (
            <button
              key={m.id}
              type="button"
              onClick={() => void selectMembership(m.id)}
              className="flex w-full items-center justify-between px-4 py-3 text-left active:bg-surface"
            >
              <span>
                {m.centerName} <span className="text-sm text-hint">· {t(`roles.${m.role}`)}</span>
              </span>
              <span className="text-hint">›</span>
            </button>
          ))}
        </Section>
      )}
    </Screen>
  )
}
