# Frontend (Vue 3 + Vite)

This frontend provides a professional user directory UI with secure JWT session handling.

## What changed

- Added automatic JWT refresh scheduling in the browser.
- Refresh interval is configurable and defaults to **5 seconds**.
- Added refresh-on-401 retry flow through Axios interceptor.
- Added logout endpoint integration (`POST /auth/logout`) to clear server refresh cookie and local auth state.
- Updated app styling to a cleaner professional design across all pages (login, profile, search, admin).

## Auth/session flow

1. Login (`/auth/login`) returns a short-lived access token and sets an HTTP-only refresh cookie.
2. Frontend stores only the access token in `localStorage`.
3. Frontend refreshes access token on a timer and when a 401 response appears.
4. Logout calls `/auth/logout`, clears refresh cookie server-side, and clears browser auth state.

## Configurable environment variables

Create `frontend/.env` if needed:

```bash
VITE_API_BASE_URL=http://localhost:8080
VITE_AUTH_REFRESH_INTERVAL_MS=5000
```

- `VITE_AUTH_REFRESH_INTERVAL_MS`: refresh cadence in milliseconds.
- Set to a positive integer. Invalid/zero values disable auto-scheduler.

## Run

```bash
cd frontend
npm install
npm run dev
```

The app runs at `http://localhost:5173` by default.
