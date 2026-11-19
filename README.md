# EduBot

Education center management SaaS for Uzbekistan, delivered as a **Telegram Bot + Telegram Mini App** on top of a Spring Boot API.

> Status: MVP in development. Architecture and roadmap: [docs/architecture.md](docs/architecture.md).

## Stack

Java 21 · Spring Boot 4 · Spring Security (JWT) · PostgreSQL 18 · Flyway · Testcontainers · React + TypeScript (Mini App, in progress) · Docker · GitHub Actions

## Implemented so far

- Telegram Mini App authentication: `initData` HMAC validation per the official Telegram procedure, freshness check, no auth bypass
- JWT access tokens carry identifiers only; membership, role and permissions are re-read from the database on every request
- Tenant context bound per request; Hibernate `@TenantId` infrastructure that fails closed
- Platform onboarding: create a center with a trial subscription and a one-time owner invite
- Invites: hashed one-time links, phone verified through Telegram's contact sharing in the bot
- Telegram bot (long polling): `/start inv_<token>` → share contact → membership
- Attendance with parent absence messages through a transactional outbox (15-min grace, quiet hours, dedup)
- Students, guardians (shared by siblings) and teachers: search, archive, optimistic locking, plan limits
- Mini App (React + TypeScript + Tailwind): Telegram theme, tab bar, students/teachers screens, platform centers screen, uz/ru i18n
- RFC 9457 problem-detail errors, module boundary verification (Spring Modulith)

## Run locally

Prerequisites: JDK 21, Docker Desktop, Git, Node.js LTS, cloudflared (for opening the Mini App from Telegram).

```bash
cp .env.example .env          # then fill TELEGRAM_BOT_TOKEN, JWT_SECRET, DB_PASSWORD
docker compose up -d          # PostgreSQL 18 on localhost:5433
cd backend && ./mvnw spring-boot:run
```

- Health: http://localhost:8080/actuator/health
- Swagger UI (when `API_DOCS_ENABLED=true`): http://localhost:8080/swagger-ui.html
- Migrations run automatically on startup.

Run the backend in Docker instead: `docker compose --profile app up -d --build`.

### Open the Mini App in Telegram (local)

Telegram only opens Mini Apps over HTTPS, so expose the Vite dev server through a tunnel:

```bash
cd web && npm install && npm run dev                 # http://localhost:5173, proxies /api to :8080
cloudflared tunnel --url http://localhost:5173      # prints an https://*.trycloudflare.com URL
```

1. Put the tunnel URL into `.env` as `MINI_APP_URL` and restart the backend.
2. In @BotFather: *Bot Settings → Menu Button* → set the same URL.
3. Open the bot, press the menu button. Your Telegram id from `PLATFORM_ADMIN_TELEGRAM_IDS` gets the platform screen.

## Tests

```bash
cd backend && ./mvnw verify   # unit + integration tests (needs Docker for Testcontainers)
cd web && npm test            # frontend unit/component tests
```

## API (so far)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/telegram` | – | Exchange Telegram `initData` for an access token |
| POST | `/api/v1/auth/session` | Bearer | Bind the token to one of the user's memberships |
| GET | `/api/v1/me` | Bearer | Current user, memberships, active membership |
| POST/GET | `/api/v1/invites` | `INVITE_*` | Create / list pending invites of the current center |
| DELETE | `/api/v1/invites/{id}` | `INVITE_*` | Revoke an invite |
| POST/GET | `/api/v1/platform/centers` | `PLATFORM_MANAGE` | Create center (trial + owner invite) / list centers |
| PATCH | `/api/v1/platform/centers/{id}/status` | `PLATFORM_MANAGE` | Activate / suspend / deactivate |
| GET | `/api/v1/platform/plans` | `PLATFORM_MANAGE` | Subscription plans |
| GET/POST/PUT | `/api/v1/students[/{id}]` | `STUDENT_*` | Search, create (with guardians), update with `version` |
| POST | `/api/v1/students/{id}/archive` · `/restore` | `STUDENT_WRITE` | Archive instead of delete |
| POST/DELETE | `/api/v1/students/{id}/guardians[/{gid}]` | `STUDENT_WRITE` | Link / unlink a guardian (shared by phone) |
| POST | `/api/v1/students/{id}/guardians/{gid}/invite` | `INVITE_STAFF` | One-time parent invite link |
| GET/POST/PUT | `/api/v1/teachers[/{id}]` | `TEACHER_MANAGE` | Teachers; `/archive`, `/restore`, `/invite` |
| GET/POST/PUT | `/api/v1/groups[/{id}]` | `GROUP_*` | Groups (teachers see only their own); `/complete` |
| PUT | `/api/v1/groups/{id}/schedule` | `GROUP_WRITE` | Replace weekly schedule |
| GET/POST | `/api/v1/groups/{id}/members` · `/enrollments` | `GROUP_*` | Members / enroll a student |
| POST/PUT | `/api/v1/enrollments/{id}/end` · `/fee` | `GROUP_WRITE` | Leave group / individual fee |
| GET | `/api/v1/lessons/today` | `ATTENDANCE_MARK` | Today's scheduled lessons with marking progress |
| POST | `/api/v1/groups/{id}/lessons` | `ATTENDANCE_MARK` | Open (get-or-create) a lesson |
| GET/PUT | `/api/v1/lessons/{id}/attendance` | `ATTENDANCE_MARK` | Roster and bulk marking (upsert); absences notify parents |
| GET | `/api/v1/groups/{id}/attendance?month=` | `ATTENDANCE_MARK` | Monthly attendance grid |
| GET | `/api/v1/students/{id}/balance` | `PAYMENT_READ` | Debt, credit, charges, payments |
| POST | `/api/v1/students/{id}/payments` | `PAYMENT_WRITE` | Record payment (`Idempotency-Key` header), FIFO allocation |
| POST | `/api/v1/payments/{id}/void` | `PAYMENT_VOID` | Void with reason (audited) |
| PUT/POST | `/api/v1/charges/{id}` · `/void` | `PAYMENT_VOID` | Correct or void a charge (audited) |
| GET | `/api/v1/debtors` | `PAYMENT_READ` | Debtors with totals and parent phone |
