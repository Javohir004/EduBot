#!/usr/bin/env node
// Development helper: opens the Mini App in a normal browser with Telegram initData that is
// signed exactly like Telegram does, using the DEV bot token. There is no auth bypass in the
// backend; this only works for a bot whose token you own.
//
// Usage: node scripts/dev-init-data.mjs <telegramUserId> [firstName] [baseUrl]
// Prints a URL like http://localhost:5173/#tgWebAppData=...
import { createHmac } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

function botToken() {
  if (process.env.TELEGRAM_BOT_TOKEN) return process.env.TELEGRAM_BOT_TOKEN
  const envFile = resolve(import.meta.dirname, '../../.env')
  if (existsSync(envFile)) {
    const line = readFileSync(envFile, 'utf8').split(/\r?\n/).find((l) => l.startsWith('TELEGRAM_BOT_TOKEN='))
    if (line) return line.slice('TELEGRAM_BOT_TOKEN='.length).trim()
  }
  throw new Error('TELEGRAM_BOT_TOKEN not found in the environment or ../.env')
}

const [id, firstName = 'Dev', baseUrl = 'http://localhost:5173/'] = process.argv.slice(2)
if (!id) {
  console.error('Usage: node scripts/dev-init-data.mjs <telegramUserId> [firstName] [baseUrl]')
  process.exit(1)
}

const fields = {
  auth_date: String(Math.floor(Date.now() / 1000)),
  query_id: 'dev',
  user: JSON.stringify({ id: Number(id), first_name: firstName, language_code: 'uz' }),
}
const dataCheckString = Object.keys(fields).sort().map((k) => `${k}=${fields[k]}`).join('\n')
const secret = createHmac('sha256', 'WebAppData').update(botToken()).digest()
const hash = createHmac('sha256', secret).update(dataCheckString).digest('hex')
const initData = new URLSearchParams({ ...fields, hash }).toString()

console.log(`${baseUrl}#tgWebAppData=${encodeURIComponent(initData)}&tgWebAppVersion=8.0&tgWebAppPlatform=web`)
