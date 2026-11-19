import { useTranslation } from 'react-i18next'
import { FullScreenMessage } from '../../shared/ui/ui'

export function NoCenterPage() {
  const { t } = useTranslation()
  return <FullScreenMessage icon="🔗" title={t('onboarding.noCenterTitle')} text={t('onboarding.noCenterText')} />
}
