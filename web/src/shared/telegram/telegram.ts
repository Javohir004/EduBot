/**
 * Thin typed wrapper over the official window.Telegram.WebApp object.
 * Only the APIs EduBot uses are typed here.
 */

type HapticNotification = 'error' | 'success' | 'warning'

interface BackButton {
  show(): void
  hide(): void
  onClick(callback: () => void): void
  offClick(callback: () => void): void
}

export interface TelegramWebApp {
  initData: string
  initDataUnsafe: { user?: { language_code?: string } }
  colorScheme: 'light' | 'dark'
  ready(): void
  expand(): void
  BackButton: BackButton
  HapticFeedback?: { notificationOccurred(type: HapticNotification): void }
  openTelegramLink(url: string): void
  showConfirm?(message: string, callback: (confirmed: boolean) => void): void
}

declare global {
  interface Window {
    Telegram?: { WebApp?: TelegramWebApp }
  }
}

/**
 * Returns the Mini App object only when actually opened inside Telegram. The script also defines
 * WebApp in a normal browser, but then initData is empty.
 */
export function getWebApp(): TelegramWebApp | null {
  const webApp = window.Telegram?.WebApp
  return webApp && webApp.initData ? webApp : null
}

export function haptic(type: HapticNotification): void {
  getWebApp()?.HapticFeedback?.notificationOccurred(type)
}

export function telegramLanguage(): 'uz' | 'ru' {
  const code = getWebApp()?.initDataUnsafe.user?.language_code ?? navigator.language
  return code?.toLowerCase().startsWith('ru') ? 'ru' : 'uz'
}

/** Native Telegram confirmation dialog for destructive actions (browser confirm as fallback). */
export function confirmAction(message: string): Promise<boolean> {
  const webApp = getWebApp()
  if (webApp?.showConfirm) {
    return new Promise((resolve) => webApp.showConfirm!(message, resolve))
  }
  return Promise.resolve(window.confirm(message))
}

/** Opens Telegram's share sheet for a link (falls back to a new tab outside Telegram). */
export function shareLink(url: string, text: string): void {
  const shareUrl = `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(text)}`
  const webApp = getWebApp()
  if (webApp) {
    webApp.openTelegramLink(shareUrl)
  } else {
    window.open(shareUrl, '_blank', 'noopener')
  }
}
