# Vue + Quarkus + Postgres CRUD (Keycloak + Roles)

This project is a secure, full-stack CRUD app with **Keycloak-backed authentication** and **role-based access**:

* **Frontend:** Vue 3 + Vite + Vue Router
* **Backend:** Java 21 + Quarkus (REST + Hibernate Panache + OIDC)
* **Identity Provider:** Keycloak (OpenID Connect)
* **Database:** PostgreSQL (Docker)

You can log in, view your profile, search users, and (if you are an admin) manage users end-to-end.

---

## Features at a Glance

### Authentication + Authorization

* **Keycloak token login** via `/auth/login` + refresh (`/auth/refresh`) + logout (`/auth/logout`)
* **Roles:** `admin` and `user` (from Keycloak `realm_access.roles`)
* **Menu + routing** in Vue hides or shows features based on role
* **Backend enforcement** blocks non-admin access to admin endpoints using local profile role checks (`admin`/`user`) after token authentication
* **Manage users provisions Keycloak users** so newly created users can immediately log in via app login

### Default Account

* Local app profile seeded in PostgreSQL: **admin**
* You must also create matching Keycloak credentials for login.

### Role Capabilities

| Feature               | Admin | Regular user |
| --------------------- | ----- | ------------ |
| Login                 | ✅     | ✅            |
| View profile          | ✅     | ✅            |
| Search users          | ✅     | ✅            |
| Manage users (CRUD)   | ✅     | ❌            |
| Block/reactivate user | ✅     | ❌            |

---

## Architecture Overview

```
Vue (frontend) --> Quarkus API (backend) --> PostgreSQL
                              |
                              +--> Keycloak (OIDC token issuer)
```

* Vue authenticates against backend `/auth/*` endpoints.
* Quarkus brokers credential/token exchanges with Keycloak OIDC endpoints.
* Access tokens are stored by the frontend in `localStorage`; refresh tokens are stored as HTTP-only cookies.
* Quarkus validates Bearer tokens using OIDC configuration. Endpoint authorization is then enforced against the local user profile role (`admin`/`user`) to keep access decisions aligned with app data.
* User credentials (passwords) are stored only in Keycloak; backend DB stores app profile/role metadata only.
* Backend maps token identity to local user by `preferred_username`, with `sub`→`keycloakUserId` fallback for reliability.

---

## Keycloak Setup (Detailed, With Purpose)

> Keycloak Admin Console URL: `http://localhost:8180/admin` (local docker-compose dev stack).

### 1) Start Keycloak

**Purpose:** Run an identity provider that issues signed access and refresh tokens.

This repository includes Keycloak in `docker-compose.yml`.

```bash
docker compose up --build
```

Keycloak can take 15-60 seconds to become fully ready. The backend is configured with OIDC connection delay so early startup races do not break the app.

### 2) Log in to the Admin Console

**Purpose:** All realm/client/user setup is done in Keycloak Admin UI.

1. Open `http://localhost:8180/admin`.
2. Log in with:
   - Username: `admin`
   - Password: `admin`

### 3) Create realm `quarkus-crud`

**Purpose:** A realm isolates this application's users, roles, and clients from other apps.

Navigation:
1. Top-left realm dropdown (usually `master`) → **Create realm**.
2. Realm name: `quarkus-crud`.
3. Click **Create**.

### 4) Create realm roles `admin` and `user`

**Purpose:** These role names are used by backend local profile authorization checks.

Navigation:
1. Left menu → **Realm roles**.
2. Click **Create role** → name `admin` → save.
3. Click **Create role** → name `user` → save.

### 5) Create OIDC client `quarkus-crud-client`

**Purpose:** The backend uses this client to:
- exchange username/password (`/auth/login`),
- refresh sessions (`/auth/refresh`),
- call Keycloak Admin API for Manage Users provisioning.

Navigation:
1. Left menu → **Clients** → **Create client**.
2. Client type: **OpenID Connect**.
3. Client ID: `quarkus-crud-client`.
4. Continue and set (this matches your screenshot):
   - **Client authentication**: ON
   - **Authorization**: OFF
   - **Authentication flow / Direct access grants**: ON
   - **Authentication flow / Service accounts roles**: ON
   - **Standard flow**: can stay ON (not used by current backend flow, but harmless)
5. Click **Next**.
6. In **Login settings** (Root URL, Home URL, Valid redirect URIs, Valid post logout redirect URIs, Web origins):
   - for this backend-to-Keycloak client, you can leave all of them empty
   - these are mainly needed for browser redirect/OIDC code flow clients
7. Click **Save**, then open **Credentials** tab and copy the client secret.



**If you are stuck on the screen you shared:**
- In your screenshot, the top-left dropdown shows `quarkus-crud-client` as the **realm**. That means you are currently viewing **Realm settings**, not the client details page.
- To reach the correct page: left menu → **Clients** → click client **`quarkus-crud-client`**.
- On that client page, open **Settings** (or during create flow, **Capability config**) and ensure **Service accounts roles = ON**. Save.
- After saving, a **Service account roles** tab becomes available for that client. Open it to assign `realm-management` roles.
### 6) Enable Service Account and grant admin API roles

**Purpose:** Without these permissions, Manage Users cannot create/update/delete Keycloak users.

Navigation:
1. Open client `quarkus-crud-client`.
2. Verify **Service accounts roles** is ON (if OFF, turn it ON and save).
3. Go to **Service account roles** tab.
4. In client roles selector choose `realm-management`.
5. Assign at minimum:
   - `manage-users`
   - `view-users`
   - `view-realm`

### 7) Create initial admin user in Keycloak

**Purpose:** This is the account used to sign in to the app initially.

Navigation:
1. Left menu → **Users** → **Create new user**.
2. Username: `admin`, Email (optional), Email verified as needed.
3. Save.
4. Open **Credentials** tab → set password (for example `admin`) and disable temporary password.
5. Open **Role mapping** tab → assign realm role `admin`.

### 8) Configure backend env vars

**Purpose:** Tell Quarkus which realm/client to trust and use.

```bash
export KEYCLOAK_SERVER_URL="http://localhost:8180"
export KEYCLOAK_REALM="quarkus-crud"
export KEYCLOAK_CLIENT_ID="quarkus-crud-client"
export KEYCLOAK_CLIENT_SECRET="<your-client-secret>"
export CORS_ORIGINS="http://localhost:5173"
export AUTH_REFRESH_COOKIE_SECURE="false"
```

### 9) Production-level recommendations (important)

1. **Do not use `start-dev`** in production; run Keycloak with production settings and persistent external DB.
2. Put Keycloak behind HTTPS + trusted reverse proxy.
3. Store `KEYCLOAK_CLIENT_SECRET` in secret manager (Vault/K8s secrets/AWS/GCP secrets), never in git.
4. Restrict service-account roles to least privilege.
5. Use stronger password and MFA policies in Keycloak realm.
6. Set `AUTH_REFRESH_COOKIE_SECURE=true` and use strict CORS origins.
7. Configure backup/restore for both Postgres and Keycloak DB.


### 10) Troubleshooting: `invalid_client_credentials` on login

If backend logs or Keycloak events show:

```
error="invalid_client_credentials", grant_type="password"
```

it means the backend's client secret does **not** match the secret configured in Keycloak for `quarkus-crud-client`.

Why this happens:
- backend `/auth/login` sends `client_id` + `client_secret` to Keycloak token endpoint.
- if the client secret is wrong, Keycloak rejects before checking username/password.

Fix:
1. In Keycloak: **Clients** → `quarkus-crud-client` → **Credentials** → copy secret.
2. Set the same value in backend runtime env:

```bash
export KEYCLOAK_CLIENT_SECRET="<copied-secret>"
```

3. Restart backend container/app so new env is applied.
4. Retry login.

For Docker Compose, backend reads:

```yaml
KEYCLOAK_CLIENT_SECRET: ${KEYCLOAK_CLIENT_SECRET:-change-me}
```

So you can also place the secret in a local `.env` file (never commit real secrets).




### 11) Troubleshooting: login works but `/users*` returns 403

If login succeeds but calls like `/users`, `/users/me`, or `/users/search` return 403:

1. Ensure the logged-in username exists in local app DB (`users` table).
2. Ensure local user is active (`active=true`).
3. For admin endpoints (`/users` CRUD), ensure local role is `admin`.

Why: authentication is from Keycloak token, while endpoint permission checks are resolved against local user profile role/state.



### 12) Troubleshooting: user creation succeeds (201) but new user login returns 401

If `POST /users` returns 201 but login for that new user returns 401:

1. Confirm user exists in Keycloak (**Users** list) and has a password set under **Credentials**.
2. Confirm local `users` table row exists and has `keycloak_user_id` populated.
3. Confirm user is active in local DB (`active=true`).

Why this can happen:
- Keycloak token may not always carry `preferred_username` in some client scope setups.
- Backend now resolves local user by `preferred_username` first, then falls back to token `sub` matched against local `keycloakUserId`.

If it still fails, verify the `keycloak_user_id` stored locally matches Keycloak user ID exactly.




### 13) Troubleshooting: `POST /users` returns 502

Common causes:
1. Keycloak client secret mismatch (`invalid_client_credentials`).
2. Service account lacks permissions for admin APIs.
3. User already exists in Keycloak (conflict), often after a previous partial failure.

What changed in backend:
- User creation now handles Keycloak `409 Conflict` by resolving the existing Keycloak user id and continuing local sync.
- User-id resolution now retries briefly (to handle eventual consistency right after create).
- Role assignment is best-effort (create/update do not fail hard if role mapping fails).

Recommended Keycloak service account roles:
- `manage-users`
- `view-users`
- `view-realm`


When `POST /users` returns 502, check backend logs for `Keycloak operation failed [...]` entries. These include HTTP status and response body from Keycloak admin/token endpoints to speed up diagnosis.
If you see `createUser could not resolve userId`, the backend created/conflicted user in Keycloak but could not immediately resolve id; it now retries lookup several times before failing.




### 14) Troubleshooting: `resolve_required_actions` / "Account is not fully set up"

If Keycloak logs show:

```
error="resolve_required_actions", reason="Account is not fully set up"
```

it means Keycloak still has pending required actions for that user (for example `VERIFY_EMAIL` or `UPDATE_PROFILE`) and password grant login is blocked until actions are completed.

Current backend behavior:
- During user create/update sync, backend now explicitly clears `requiredActions` and marks `emailVerified=true` for managed users.

What to check in Keycloak for affected users:
1. Users → select user → **Details** tab → **Required user actions** should be empty.
2. If not empty, clear required actions and save.
3. Ensure user has a non-temporary password in **Credentials**.


---

## How To Run (Step-by-Step)

### Prerequisites

* Java 21
* Maven 3.9+
* Node.js 18+
* Docker + Docker Compose

### 1) Start Postgres + Keycloak + backend

```bash
cd backend
mvn package
cd ..
docker-compose up --build
```

Backend: `http://localhost:8080`

### 2) Start the Vue frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend: `http://localhost:5173`

If backend runs elsewhere:

```bash
export VITE_API_BASE_URL="http://localhost:8080"
export VITE_AUTH_REFRESH_INTERVAL_MS="5000"
```

---

## API Reference (Bearer Token Required)

### Auth

| Method | Endpoint        | Description |
| -----: | --------------- | ----------- |
|   POST | `/auth/login`   | Exchanges username/password with Keycloak and returns access token + refresh cookie |
|   POST | `/auth/refresh` | Exchanges refresh cookie with Keycloak and rotates token |
|   POST | `/auth/logout`  | Calls Keycloak logout and clears refresh cookie |

### Users (Admin-only)

| Method | Endpoint      | Description    |
| -----: | ------------- | -------------- |
|    GET | `/users`      | List all users |
|   POST | `/users`      | Create user in app DB + Keycloak SSO    |
|    PUT | `/users/{id}` | Update user    |
| DELETE | `/users/{id}` | Delete user    |

### Users (All roles)

| Method | Endpoint           | Description       |
| -----: | ------------------ | ----------------- |
|    GET | `/users/me`        | View your profile |
|    GET | `/users/search?q=` | Search users      |
