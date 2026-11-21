# EduBot — project notes for Claude

Telegram Bot + Mini App SaaS for education centers in Uzbekistan. Spring Boot 4 / Java 21 backend
(`backend/`), React + TS + Vite Mini App (`web/`), PostgreSQL 18. Design: `docs/architecture.md`,
security status: `docs/security.md`, user-facing overview and API table: `README.md`.

## Status (2026-11-21)

- MVP complete (roadmap days 1–8 + security pass, README, screenshots). CI green on `main`.
- **Next: deployment** — VPS + domain + HTTPS (Caddy) + daily encrypted `pg_dump` backups + production
  bot. Open items are listed at the end of `docs/security.md`. Waiting for the user's VPS, domain and
  production bot token.
- Before going public: rotate the dev bot token in @BotFather (it was visible once in a local log).

## Run locally (what the user does each time)

1. Docker Desktop running.
2. `.\scripts\dev.ps1` — starts PostgreSQL, builds `web/dist`, opens a Cloudflare quick tunnel and
   writes `MINI_APP_URL` + `FRONTEND_DIST_DIR` to `.env`. Window must stay open.
3. IntelliJ **Run** (`EdubotBackendApplication`, working dir = repo root). The backend serves the Mini
   App and sets the bot menu button to `MINI_APP_URL` on startup.

Only one backend may run at a time (port 8080 and bot long polling).

## Commands

- Backend tests: `cd backend && ./mvnw verify` (Testcontainers → Docker required).
- Frontend: `cd web && npm run typecheck && npm run lint && npm test && npm run build`.
- Demo data: `SPRING_PROFILES_ACTIVE=demo DEMO_OWNER_TELEGRAM_ID=<id>`.
- Browser testing without Telegram: `node web/scripts/dev-init-data.mjs <telegramId>` (signs real initData).

## Gotchas (learned the hard way)

- **IntelliJ rewrites `backend/target` while Maven runs** → random `ClassNotFoundException` in tests.
  Run Maven builds in a copy outside the repo (tar `backend/` without `target` into the scratchpad).
- **Never write secrets into `application.yml`**; it only maps `${ENV}` vars (`ApplicationConfigTest`
  enforces it). Secrets live in the git-ignored `.env`. Do not print `.env` values.
- `.env` is imported as a `.properties` file: backslashes are escapes → use forward slashes in paths.
- `backend/mvnw` must keep the git executable bit (CI fails with exit 126 otherwise).
- Hibernate reads the tenant when a session opens: `TenantContext.runAs` must wrap a call into a
  `@Transactional` service, never run inside an open transaction. Native SQL always passes `center_id`.
- Telegram Desktop may bring an open Mini App to front without reloading (stale auth state).
- The bash tool mis-parses some heredocs with quotes; for multi-file edits write a Python script to the
  scratchpad and run it.

## Conventions

- Explanations to the user in Uzbek, **short** (warnings and required actions first); code, comments,
  commits and docs in English. No attribution lines in commits.
- Package-by-feature modules (Spring Modulith verified): tenant data only via `TenantScopedEntity` /
  `TenantScopedRepository` or native SQL with explicit `center_id`; cross-center access returns 404.
- Every feature ships with tests, including a cross-center isolation test.
