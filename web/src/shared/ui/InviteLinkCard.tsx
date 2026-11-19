import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { shareLink } from '../telegram/telegram'
import { Button, Section } from './ui'

/** Shows a one-time invite link with share/copy actions. The link is never shown again later. */
export function InviteLinkCard({
  title,
  text,
  link,
  shareText,
  onDone,
}: {
  title: string
  text: string
  link: string
  shareText: string
  onDone(): void
}) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    await navigator.clipboard.writeText(link)
    setCopied(true)
  }

  return (
    <Section className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">✅ {title}</h2>
      <p className="text-[15px] text-hint">{text}</p>
      <p className="rounded-xl bg-surface px-3 py-2.5 font-mono text-sm break-all">{link}</p>
      <div className="flex flex-col gap-2">
        <Button onClick={() => shareLink(link, shareText)}>{t('common.share')}</Button>
        <Button variant="secondary" onClick={() => void copy()}>
          {copied ? t('common.copied') : t('common.copy')}
        </Button>
        <Button variant="ghost" onClick={onDone}>
          {t('common.done')}
        </Button>
      </div>
    </Section>
  )
}
