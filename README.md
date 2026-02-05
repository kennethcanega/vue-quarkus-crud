# Vue + Quarkus + PostgreSQL CRUD (JWT + Refresh Token Rotation)

A full-stack user management app with:

- **Frontend:** Vue 3 + Vite
- **Backend:** Quarkus (Java 21) + Panache + SmallRye JWT
- **Database:** PostgreSQL

## Highlights

- JWT-based authentication with role-aware routing and backend enforcement.
- Configurable **access token TTL** and **refresh token TTL**.
- Configurable frontend auto-refresh interval (default **5 seconds**).
- Refresh token rotation and server-backed revocation.
- Industry-standard logout behavior:
  - server revokes refresh tokens,
  - browser refresh cookie is cleared,
  - frontend session state is removed.
- Refreshed, professional visual design across all pages.

## Auth flow (current)

1. User logs in through `/auth/login`.
2. Backend returns short-lived access token + sets HTTP-only refresh cookie.
3. Frontend sends access token in `Authorization` header.
4. Frontend refreshes access token periodically and on 401 (`/auth/refresh`).
5. Logout (`/auth/logout`) revokes refresh token and clears cookie.

## Configuration

### Frontend

```bash
VITE_API_BASE_URL=http://localhost:8080
VITE_AUTH_REFRESH_INTERVAL_MS=5000
```

### Backend

```bash
AUTH_ACCESS_TOKEN_TTL_SECONDS=300
AUTH_REFRESH_TOKEN_TTL_SECONDS=1209600
AUTH_REFRESH_COOKIE_SECURE=false
CORS_ORIGINS=http://localhost:5173
```

## Quick start

```bash
docker compose up --build
```

Or run services separately:

- Backend instructions: `BACKEND_README.md`
- Frontend instructions: `FRONTEND_README.md`
