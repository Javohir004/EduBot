# EduBot — Security review

Status of the threat model from [architecture.md](architecture.md#13-threat-model), checked against
the code on 2026-11-21. "Test" names the automated test that proves the control.

| # | Threat | Control | Status | Test |
|---|---|---|---|---|
| 1 | Forged Telegram initData | HMAC-SHA256 per Telegram's procedure, constant-time compare; user id and role never taken from the client | ✅ | `TelegramInitDataValidatorTest`, `AuthApiIT` |
| 2 | Replay of captured initData | `auth_date` max age 1 h, future skew 1 min; initData and tokens are never logged | ✅ | `TelegramInitDataValidatorTest` |
| 3 | Cross-center access (IDOR) | Tenant only from the authenticated membership; Hibernate `@TenantId` (fails closed), explicit `center_id` in all native SQL, composite FKs, 404 for foreign ids | ✅ | `StudentApiIT`, `TeacherApiIT`, `GroupApiIT`, `AttendanceApiIT`, `BillingApiIT`, `InviteFlowIT`, `DashboardAndDemoIT` |
| 4 | Over-reach inside a center | Permission per endpoint (`@PreAuthorize`) plus resource checks: teachers see own groups only, parents own children only, revenue for owners only | ✅ | `GroupApiIT`, `AttendanceApiIT`, `ParentsRemindersStaffIT`, `BillingApiIT` |
| 5 | Privilege escalation | Roles read from the DB on every request; owners only invited by the platform; nobody can revoke themselves; last owner protected | ✅ | `InviteFlowIT`, `ParentsRemindersStaffIT` |
| 6 | Revoked staff keeps access | Membership re-validated per request → 401 | ✅ | `AuthApiIT`, `InviteFlowIT` |
| 7 | Invite theft / brute force | 128-bit random token, only SHA-256 hash stored, single use, 7-day expiry, Telegram-verified phone must match, own contact only | ✅ | `InviteFlowIT`, `BotUpdateHandlerTest` |
| 8 | Brute force / flooding | Bucket4j per client IP: 20/min on `/api/v1/auth/**`, 600/min on the API (bounded cache); `forward-headers-strategy=native` behind the proxy | ✅ | `RateLimitFilterTest` |
| 9 | Double payment / double marking | `Idempotency-Key` + unique constraint; attendance upsert on `(lesson, student)` | ✅ | `BillingApiIT`, `AttendanceApiIT` (concurrent saves) |
| 10 | Money tampering without trace | Payments are voided, never deleted; voids and charge corrections require a reason and write `audit_log` in the same transaction | ✅ | `BillingApiIT` |
| 11 | Mass assignment | Explicit request records; ids, center and roles are never bound from the body | ✅ | — (by construction) |
| 12 | SQL injection | Parameterized JPQL/SQL only; LIKE patterns escaped | ✅ | `StudentApiIT` (`%` search) |
| 13 | XSS in the Mini App | React escaping, no `dangerouslySetInnerHTML`, no HTML from the API | ✅ | — (by construction) |
| 14 | Error detail leakage | RFC 9457 problem details with stable codes; no stack traces or messages from exceptions | ✅ | `AuthApiIT` |
| 15 | Secrets in the repository | Secrets only in env / git-ignored `.env`; `application.yml` holds placeholders (test enforces it); gitleaks in CI | ✅ | `ApplicationConfigTest`, CI secret scan |
| 16 | Notification spam / wrong recipient | Only guardians who linked their own Telegram account; dedup keys; quiet hours; 15-min grace for corrections | ✅ | `AttendanceApiIT`, `ParentsRemindersStaffIT` |
| 17 | Half-started instance consuming bot updates | Polling starts on `ApplicationReadyEvent`; updates handled on the polling thread | ✅ | manual (startup with busy port) |
| 19 | Vulnerable dependencies | Dependabot weekly for Maven, npm and GitHub Actions | ✅ | `.github/dependabot.yml` |
| 18 | Expired subscription keeps writing | Read-only guard returns 402 after the grace period; data is never deleted | ✅ | `SubscriptionLifecycleIT` |

## Open items (deployment phase)

| Item | Why | Plan |
|---|---|---|
| HTTPS, HSTS, CSP headers | Set at the reverse proxy | Configured in `web/Caddyfile` (automatic TLS, HSTS, CSP allowing the `telegram.org` script, Telegram-only `frame-ancestors`); verify on the server |
| Backups | Data loss | `backup` service: daily `pg_dump` encrypted with age, retention; restore tested locally. Still to do: copy off the server, one restore test in production |
| Rotate the dev bot token | It was visible once in a local session log | `/revoke` in @BotFather before going public |
| Personal data localization | Law ZRU-547 (2021 amendment) | Legal check; host in Uzbekistan for commercial use |
| Actuator exposure | Health only | Production: backend publishes no ports and Caddy proxies only `/api/*` |
