# Doller Platform

Hardened Flutter + Spring Boot system for USD buy/sell ledger, partial settlements, costs, day close, carry-forward balances, and BDT P/L.

## Structure
- `backend/`: Spring Boot API + PostgreSQL + Flyway
- `mobile/`: Flutter Android-first client with durable sync outbox

## Security and Initialization
- No default owner credentials are auto-created.
- First deployment must call `POST /auth/init-owner` once with `X-Bootstrap-Token`.
- Owner initialization is disabled by default and must be explicitly enabled.
- Swagger and API docs are disabled by default and must be explicitly enabled.
- JWT and DB credentials are environment-driven with no insecure fallbacks.

Required env vars (backend):
- `DB_URL`, `DB_USER`, `DB_PASS`
- `JWT_ACTIVE_SECRET` (required in production)
- `JWT_PREVIOUS_SECRET` (optional for key rotation)
- `JWT_ISSUER`
- `JWT_ACCESS_EXP_MIN`, `JWT_REFRESH_EXP_DAYS`
- `APP_INIT_ENABLED` (defaults to `false`)
- `APP_INIT_BOOTSTRAP_TOKEN` (required when `APP_INIT_ENABLED=true`)
- `APP_INIT_OWNER_USERNAME`, `APP_INIT_OWNER_PASSWORD` (optional startup auto-init path)
- `APP_SECURITY_DOCS_ENABLED` (optional, defaults to `false`)

Backend env template:
- copy [backend/.env.example](/home/mhcybroot/Projects/doller-platform/backend/.env.example) and load those values into your shell, IDE run config, Docker, or deployment platform
- note: Spring Boot does not automatically read `backend/.env` by itself

## Backend Run
1. Start DB:
   - `docker compose up -d postgres`
2. Run API:
   - `cd backend && mvn spring-boot:run`
3. Initialize owner once:
   - `POST /auth/init-owner` with header `X-Bootstrap-Token: <token>` and body `{ "username": "owner", "password": "StrongPass123!" }`
4. Disable bootstrap again:
   - set `APP_INIT_ENABLED=false` after the first owner is created

Alternative (easy startup init from env):
- Set `APP_INIT_ENABLED=true`
- Set `APP_INIT_OWNER_USERNAME` and `APP_INIT_OWNER_PASSWORD`
- Start backend once; owner is auto-created if user table is empty
- Then set `APP_INIT_ENABLED=false` and restart

Example first owner:
- username: `owner`
- password: `OwnerPass123!`
- bootstrap header: `X-Bootstrap-Token: your-one-time-token`

Example curl:
```bash
curl -X POST http://localhost:8089/auth/init-owner \
  -H 'Content-Type: application/json' \
  -H 'X-Bootstrap-Token: your-one-time-token' \
  -d '{"username":"owner","password":"OwnerPass123!"}'
```

## Mobile Run
1. `cd mobile`
2. `flutter pub get`
3. `flutter run`

Android emulator API base URL: `http://10.0.2.2:8080`.

## Implemented APIs
- Auth: `/auth/init-owner`, `/auth/login`, `/auth/refresh`, `/auth/change-password`
- Master: `/users`, `/users/{id}/deactivate`, `/parties`
- Trading: `/deals`, `/settlements`, `/expenses`
- Closing: `GET /day-close/{date}`, `POST /day-close/{date}`, `POST /day-close/{date}/reopen`
- Reporting: `/dashboard`, `/statements/daily`, `/statements/range`, `/ledgers/party/{id}`
- Export: `/exports/csv`, `/exports/pdf`
- Audit: `/audit/logs` (OWNER)

## Hardening Highlights
- Flyway migration baseline with DB constraints and indexes.
- Access + refresh token lifecycle with refresh-token storage/revocation.
- Immutable audit logs with payload hashing.
- Closed-day write guards and owner-only reopen with reason.
- Settlement controls for applied amount vs advance.
- Real PDF statement generation on server.
- SQLite-backed durable mobile outbox with retry/backoff and poison handling.
