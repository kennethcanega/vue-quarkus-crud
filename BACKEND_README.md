# Backend (Quarkus + PostgreSQL)

This backend exposes user CRUD APIs with JWT auth and role-based authorization.

## What changed

- Added refresh-token persistence (`refresh_tokens` table via Hibernate Panache).
- Added `POST /auth/refresh` to rotate refresh tokens and issue fresh access tokens.
- Added `POST /auth/logout` to revoke refresh tokens and clear the refresh cookie.
- Access token lifetime is now configurable (default shorter lifetime for better security).
- Refresh cookie is HTTP-only and SameSite=Strict.

## Auth endpoints

### `POST /auth/login`
- Validates credentials and active status.
- Issues access token.
- Sets refresh cookie (`refresh_token`).

### `POST /auth/refresh`
- Reads refresh cookie.
- Validates + rotates refresh token.
- Returns new access token and updated user payload.
- Replaces refresh cookie.

### `POST /auth/logout`
- Revokes refresh token(s) for the session/user.
- Clears refresh cookie.

## Config (`backend/src/main/resources/application.properties`)

```properties
auth.access-token.ttl-seconds=${AUTH_ACCESS_TOKEN_TTL_SECONDS:300}
auth.refresh-token.ttl-seconds=${AUTH_REFRESH_TOKEN_TTL_SECONDS:1209600}
auth.refresh-cookie.secure=${AUTH_REFRESH_COOKIE_SECURE:false}

quarkus.http.cors.origins=${CORS_ORIGINS:http://localhost:5173}
quarkus.http.cors.access-control-allow-credentials=true
```

### Notes

- `AUTH_ACCESS_TOKEN_TTL_SECONDS`: access token expiration window.
- `AUTH_REFRESH_TOKEN_TTL_SECONDS`: refresh token validity window.
- `AUTH_REFRESH_COOKIE_SECURE`: set `true` under HTTPS.
- `CORS_ORIGINS`: must match frontend origin when credentials are used.

## Run backend locally

```bash
cd backend
mvn quarkus:dev
```

Default datasource expects Postgres on `localhost:5433` database `userdb`.
