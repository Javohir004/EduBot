# EduBot

Education center management SaaS for Uzbekistan, delivered as a **Telegram Bot + Telegram Mini App** on top of a Spring Boot API.

> Status: MVP in development. Architecture and roadmap: [docs/architecture.md](docs/architecture.md).

## Stack

Java 21 · Spring Boot 4 · Spring Security (JWT) · PostgreSQL 18 · Flyway · Testcontainers · React + TypeScript (Mini App, in progress) · Docker · GitHub Actions

## Implemented so far

- Telegram Mini App authentication: `initData` HMAC validation per the official Telegram procedure, freshness check, no auth bypass
- JWT access tokens (identifiers only; memberships re-checked against the database)
- Multi-center memberships with center selection (`/api/v1/auth/session`)
- Core schema: users, centers, memberships, subscription plans and entitlements (Flyway)
- RFC 9457 problem-detail errors, module boundary verification (Spring Modulith)

## Run locally

Prerequisites: JDK 21, Docker Desktop, Git.

```bash
cp .env.example .env          # then fill TELEGRAM_BOT_TOKEN, JWT_SECRET, DB_PASSWORD
docker compose up -d          # PostgreSQL 18 on localhost:5433
cd backend && ./mvnw spring-boot:run
```

- Health: http://localhost:8080/actuator/health
- Swagger UI (when `API_DOCS_ENABLED=true`): http://localhost:8080/swagger-ui.html
- Migrations run automatically on startup.

Run the backend in Docker instead: `docker compose --profile app up -d --build`.

## Tests

```bash
cd backend && ./mvnw verify   # unit + integration tests (needs Docker for Testcontainers)
```

## API (so far)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/telegram` | – | Exchange Telegram `initData` for an access token |
| POST | `/api/v1/auth/session` | Bearer | Bind the token to one of the user's memberships |
| GET | `/api/v1/me` | Bearer | Current user, memberships, active membership |
