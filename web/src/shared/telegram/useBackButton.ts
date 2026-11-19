import { useEffect } from 'react'
import { useNavigate } from 'react-router'
import { getWebApp } from './telegram'

/** Shows Telegram's native back button while the calling screen is mounted. */
export function useBackButton(enabled = true): void {
  const navigate = useNavigate()

  useEffect(() => {
    const webApp = getWebApp()
    if (!webApp || !enabled) return
    const goBack = () => navigate(-1)
    webApp.BackButton.onClick(goBack)
    webApp.BackButton.show()
    return () => {
      webApp.BackButton.offClick(goBack)
      webApp.BackButton.hide()
    }
  }, [enabled, navigate])
}
