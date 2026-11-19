import i18n from 'i18next'
import { initReactI18next, useTranslation } from 'react-i18next'
import { ApiError } from '../api/client'
import { telegramLanguage } from '../telegram/telegram'
import { ru } from './ru'
import { uz } from './uz'

void i18n.use(initReactI18next).init({
  resources: { uz: { translation: uz }, ru: { translation: ru } },
  lng: telegramLanguage(),
  fallbackLng: 'uz',
  interpolation: { escapeValue: false },
})

/** Localized message for any error thrown by the API client. */
export function useErrorMessage(): (error: unknown) => string {
  const { t } = useTranslation()
  return (error: unknown) => {
    const code = error instanceof ApiError ? error.code : 'UNKNOWN'
    return i18n.exists(`errors.${code}`) ? t(`errors.${code}`) : t('errors.UNKNOWN')
  }
}

export default i18n
