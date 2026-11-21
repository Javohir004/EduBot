# Production deployment (one Ubuntu VPS)

Stack: `compose.prod.yaml` — PostgreSQL 18 (internal only), backend, Caddy (HTTPS, Mini App files,
`/api` proxy, security headers), daily encrypted backups. Only ports 80/443 are public; `/actuator`
and the database are reachable only inside the Docker network.

Assumed: Ubuntu 24.04, 2 vCPU / 4 GB RAM, a domain, a **production** bot from @BotFather.

> Legal: commercial use with real personal data may require hosting in Uzbekistan (law ZRU-547).
> Check before onboarding paying centers.

## 1. Server preparation

Log in with an SSH key (`ssh user@SERVER_IP`), as a normal user with `sudo`.

```bash
sudo apt update && sudo apt -y upgrade
# Docker Engine + compose plugin (official convenience script)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # log out and back in afterwards

# Firewall: SSH, HTTP, HTTPS only
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp
sudo ufw enable
```

Docker bypasses ufw for published ports; that is fine here because only Caddy publishes ports.
Recommended: `PasswordAuthentication no` in `/etc/ssh/sshd_config`, then `sudo systemctl restart ssh`.

## 2. DNS

At the domain registrar create an **A record**: `edubot.example.uz -> SERVER_IP` (and `AAAA` if the
server has IPv6). Check with `nslookup edubot.example.uz` before the first start — Caddy needs it to
get the certificate.

## 3. Backup key (on your own PC, once)

```powershell
winget install FiloSottile.age
age-keygen -o edubot-backup.key
```

The command prints `Public key: age1...` — that goes into the server `.env` as `BACKUP_AGE_RECIPIENT`.
`edubot-backup.key` is the **private** key: keep it in a password manager / second disk, never on the
server. Without it backups cannot be decrypted.

## 4. Get the code and configure

```bash
sudo mkdir -p /opt/edubot && sudo chown $USER: /opt/edubot
git clone https://github.com/Javohir004/EduBot.git /opt/edubot
cd /opt/edubot
mkdir -p backups
cp .env.production.example .env && chmod 600 .env
nano .env
```

Fill in `DOMAIN`, `ACME_EMAIL`, `DB_PASSWORD` and `JWT_SECRET` (`openssl rand -base64 32` / `48`),
`TELEGRAM_BOT_TOKEN` and `TELEGRAM_BOT_USERNAME` of the production bot,
`PLATFORM_ADMIN_TELEGRAM_IDS`, `BACKUP_AGE_RECIPIENT`. If `id -u` is not `1000`, also set
`BACKUP_UID` / `BACKUP_GID`.

## 5. First start

```bash
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml ps          # all services "running", backend "healthy"
docker compose -f compose.prod.yaml logs -f caddy backend
```

Checks:

- `curl -I https://edubot.example.uz/` returns `200` with `strict-transport-security` and
  `content-security-policy` headers.
- `curl -i https://edubot.example.uz/api/v1/dashboard/summary` returns `401` (API reachable).
- `curl -i https://edubot.example.uz/actuator/health` must **not** return backend health.
- In Telegram, `/start` the production bot: the menu button opens the Mini App (the backend sets it
  to `https://$DOMAIN` on startup).

Only one backend may run per bot token (long polling): stop any local backend that uses the same bot.

## 6. Update to a new version

```bash
cd /opt/edubot
git pull
docker compose -f compose.prod.yaml up -d --build
docker image prune -f
```

Flyway migrates the database on startup. Downtime is the backend restart (~30 s).

Alternative without building on the server: CI pushes images to GHCR on every push to `main`. Set
`BACKEND_IMAGE`, `WEB_IMAGE`, `BACKUP_IMAGE` in `.env` (see the example file), then
`docker compose -f compose.prod.yaml pull && docker compose -f compose.prod.yaml up -d`.
If the repository is private, first `docker login ghcr.io` with a token that has `read:packages`.

## 7. Backups

- Every day at `BACKUP_TIME` (`TZ`, default 03:00 Tashkent) the `backup` service writes
  `backups/edubot-YYYYMMDD-HHMMSS.dump.age` (`pg_dump` custom format, compressed, encrypted with age)
  and deletes files older than `BACKUP_RETENTION_DAYS`.
- Run one now: `docker compose -f compose.prod.yaml exec backup backup.sh`
- Logs: `docker compose -f compose.prod.yaml logs backup`
- **Copy them off the server** regularly (from your PC, PowerShell):
  `scp "user@SERVER_IP:/opt/edubot/backups/*.age" D:\edubot-backups\`

### Restore

```bash
cd /opt/edubot
# from your PC: scp edubot-backup.key user@SERVER_IP:/opt/edubot/restore.key
docker compose -f compose.prod.yaml stop backend
docker compose -f compose.prod.yaml run --rm -v "$PWD/restore.key:/restore.key:ro" \
  backup restore.sh /backups/edubot-YYYYMMDD-HHMMSS.dump.age /restore.key
shred -u restore.key
docker compose -f compose.prod.yaml start backend
```

`restore.sh` decrypts the file and runs `pg_restore --clean --if-exists --single-transaction`: either
the whole backup is restored or nothing changes. To check a backup without touching production, restore
into another database (`-e PGDATABASE=edubot_restore_test` after `CREATE DATABASE edubot_restore_test`).

## Before going public

- Rotate the development bot token in @BotFather (`/revoke`); production uses its own bot.
- Copy a backup off the server and do one test restore.
