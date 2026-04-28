# Doller Platform

Hardened Flutter + Spring Boot system for USD buy/sell ledger, partial settlements, costs, day close, carry-forward balances, and BDT P/L.

## Structure
- `backend/`: Spring Boot API + PostgreSQL + Flyway
- `mobile/`: Flutter Android-first client with durable sync outbox

## Security and Initialization
- No default owner credentials are auto-created.
- First deployment must call `POST /auth/init-owner` once.
- JWT and DB credentials are environment-driven.

Required env vars (backend):
- `DB_URL`, `DB_USER`, `DB_PASS`
- `JWT_ACTIVE_SECRET` (required in production)
- `JWT_PREVIOUS_SECRET` (optional for key rotation)
- `JWT_ISSUER`
- `JWT_ACCESS_EXP_MIN`, `JWT_REFRESH_EXP_DAYS`
- `APP_INIT_ENABLED` (set `false` after first owner creation)

## Backend Run
1. Start DB:
   - `docker compose up -d postgres`
2. Run API:
   - `cd backend && mvn spring-boot:run`
3. Initialize owner once:
   - `POST /auth/init-owner` with `{ "username": "owner", "password": "..." }`

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
