<#
.SYNOPSIS
  Prepares everything the Telegram Mini App needs for local development, in one command.

.DESCRIPTION
  1. Starts PostgreSQL (Docker).
  2. Builds the Mini App into web/dist (skip with -SkipBuild) and points FRONTEND_DIST_DIR at it,
     so the backend serves the app itself: one process, one port, no Vite needed.
  3. Opens a Cloudflare quick tunnel to the backend port and writes its URL to .env (MINI_APP_URL).

  Then start the backend (IntelliJ "Run" or ./mvnw spring-boot:run). On startup it also points the
  bot's menu button at the new URL, so nothing has to be changed in @BotFather.

  Keep this window open: closing it (Ctrl+C) stops the tunnel.

.EXAMPLE
  .\scripts\dev.ps1
  .\scripts\dev.ps1 -SkipBuild
#>
param(
    [switch]$SkipBuild,
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'

function Set-EnvValue([string]$Key, [string]$Value) {
    $lines = @(if (Test-Path $envFile) { Get-Content $envFile })
    if ($lines | Where-Object { $_ -match "^$Key=" }) {
        $lines = $lines | ForEach-Object { if ($_ -match "^$Key=") { "$Key=$Value" } else { $_ } }
    } else {
        $lines += "$Key=$Value"
    }
    Set-Content -Path $envFile -Value $lines -Encoding utf8NoBOM
}

if (-not (Test-Path $envFile)) {
    throw ".env not found. Copy .env.example to .env and fill TELEGRAM_BOT_TOKEN first."
}

Write-Host "1/3 PostgreSQL" -ForegroundColor Cyan
docker compose -f (Join-Path $root 'compose.yaml') up -d postgres
if ($LASTEXITCODE -ne 0) { throw "Docker is not running. Start Docker Desktop and try again." }

if (-not $SkipBuild) {
    Write-Host "2/3 Building the Mini App" -ForegroundColor Cyan
    Push-Location (Join-Path $root 'web')
    try {
        if (-not (Test-Path 'node_modules')) { npm ci; if ($LASTEXITCODE -ne 0) { throw "npm ci failed" } }
        npm run build
        if ($LASTEXITCODE -ne 0) { throw "Mini App build failed" }
    } finally {
        Pop-Location
    }
}
# Forward slashes: .env is read as a .properties file, where '\' is an escape character.
Set-EnvValue 'FRONTEND_DIST_DIR' ((Join-Path $root 'web\dist') -replace '\\', '/')

Write-Host "3/3 Cloudflare tunnel -> http://localhost:$Port" -ForegroundColor Cyan
$log = Join-Path $env:TEMP 'edubot-tunnel.log'
Remove-Item $log -ErrorAction SilentlyContinue
$tunnel = Start-Process cloudflared -ArgumentList @('tunnel', '--url', "http://localhost:$Port") `
    -RedirectStandardError $log -NoNewWindow -PassThru

$url = $null
for ($i = 0; $i -lt 60 -and -not $url; $i++) {
    Start-Sleep -Seconds 1
    if (Test-Path $log) {
        $match = Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
        if ($match) { $url = $match.Matches[0].Value }
    }
}
if (-not $url) {
    Stop-Process -Id $tunnel.Id -ErrorAction SilentlyContinue
    throw "The tunnel did not report a URL within 60 s. See $log"
}
Set-EnvValue 'MINI_APP_URL' $url

Write-Host ""
Write-Host "Mini App URL: $url" -ForegroundColor Green
Write-Host "Saved to .env. Now start the backend in IntelliJ (Run) - it updates the bot menu button itself."
Write-Host "Keep this window open. Press Ctrl+C to stop the tunnel."
try {
    Wait-Process -Id $tunnel.Id
} finally {
    Stop-Process -Id $tunnel.Id -ErrorAction SilentlyContinue
}
