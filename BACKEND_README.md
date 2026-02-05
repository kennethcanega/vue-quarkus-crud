# Backend Guide (Quarkus + PostgreSQL + Keycloak OIDC)

This guide explains the backend for developers new to Quarkus, with emphasis on **how Keycloak replaces local JWT signing** and why each step/configuration exists.

---

## 1) Libraries used (`backend/pom.xml`) and why

- **`quarkus-rest-jackson`**
  - REST APIs + JSON serialization/deserialization.
- **`quarkus-hibernate-orm-panache`**
  - ORM + active-record style persistence for entities.
- **`quarkus-jdbc-postgresql`**
  - PostgreSQL driver integration.
- **`quarkus-arc`**
  - CDI dependency injection support.
- **`quarkus-oidc`**
  - Validates Keycloak-issued access tokens and maps claims/roles to Quarkus security identity.

### Why this changed

Previously, the backend generated and signed JWTs itself.
Now Keycloak issues tokens, and Quarkus focuses on:

1. brokering auth/refresh/logout flows,
2. validating incoming Bearer tokens,
3. enforcing permissions using local user profile role/state (`admin`/`user`, `active`) after token authentication.

---

## 2) Application config (`backend/src/main/resources/application.properties`)

### Database and CORS

Unchanged core behavior:

- PostgreSQL on `localhost:5433` for local runtime
- Hibernate schema update for dev convenience
- CORS enabled for Vue origin and credentialed requests

### OIDC + Keycloak config

Key properties:

- `quarkus.oidc.auth-server-url=${KEYCLOAK_SERVER_URL}/realms/${KEYCLOAK_REALM}`
- `quarkus.oidc.client-id=${KEYCLOAK_CLIENT_ID}`
- `quarkus.oidc.credentials.secret=${KEYCLOAK_CLIENT_SECRET}`
- `quarkus.oidc.roles.role-claim-path=realm_access/roles`

Purpose of each:

- **auth-server-url**: tells Quarkus where token metadata and key material come from.
- **client-id/client-secret**: allows backend to securely call token/logout endpoints.
- **role-claim-path**: keeps token role claims available when needed for integrations and diagnostics.

Additional backend auth broker properties:

- `keycloak.server-url`
- `keycloak.realm`
- `keycloak.client-id`
- `keycloak.client-secret`

These are used by `AuthResource` for direct token/logout HTTP calls.

---

## 3) Keycloak implementation steps (detailed + purpose)

> Admin console URL (local): `http://localhost:8180/admin`

### Step 1: Start stack

**Purpose:** Bring up Postgres + Keycloak + backend together.

```bash
docker compose up --build
```

Keycloak needs extra warm-up time. The backend uses `quarkus.oidc.connection-delay` so startup race conditions are tolerated.

### Step 2: Create realm `quarkus-crud`

**Purpose:** Identity boundary for this app.

UI path:
- Realm dropdown (top-left) → **Create realm** → name `quarkus-crud`.

### Step 3: Create realm roles

**Purpose:** These map directly to backend role guards.

UI path:
- **Realm roles** → create `admin`
- **Realm roles** → create `user`

### Step 4: Create client `quarkus-crud-client`

**Purpose:** Backend uses this client for token exchange and admin operations.

UI path:
- **Clients** → **Create client**
- Type: OpenID Connect
- ID: `quarkus-crud-client`
- Settings (same values shown in your Keycloak create-client screenshots):
  - Client authentication: enabled
  - Direct access grants: enabled
  - Service accounts roles: enabled
  - Authorization: disabled
  - Standard flow: optional for this backend flow

- Next screen (**Login settings**): Root URL, Home URL, Valid redirect URIs, post-logout URIs, and Web origins can stay empty for this backend service client.

Then open **Credentials** tab and copy client secret.



**If you are stuck on the screen you shared:**
- In your screenshot, the top-left dropdown shows `quarkus-crud-client` as the **realm**. That means you are currently viewing **Realm settings**, not the client details page.
- To reach the correct page: left menu → **Clients** → click client **`quarkus-crud-client`**.
- On that client page, open **Settings** (or during create flow, **Capability config**) and ensure **Service accounts roles = ON**. Save.
- After saving, a **Service account roles** tab becomes available for that client. Open it to assign `realm-management` roles.
### Step 5: Grant service-account roles

**Purpose:** Required for backend provisioning in Manage Users.

UI path:
- Client `quarkus-crud-client` → verify **Service accounts roles** is ON, then open **Service account roles** tab
- Select client `realm-management`
- Assign:
  - `manage-users`
  - `view-users`
  - `view-realm`

### Step 6: Create admin login user

**Purpose:** first app login account.

UI path:
- **Users** → **Create new user** (`admin`)
- **Credentials** tab → set non-temporary password
- **Role mapping** tab → assign realm role `admin`

### Step 7: Set backend env vars

**Purpose:** Bind backend OIDC and admin-client config to created realm/client.

```bash
export KEYCLOAK_SERVER_URL="http://localhost:8180"
export KEYCLOAK_REALM="quarkus-crud"
export KEYCLOAK_CLIENT_ID="quarkus-crud-client"
export KEYCLOAK_CLIENT_SECRET="<your-client-secret>"
export AUTH_REFRESH_COOKIE_SECURE="false"
```

### Step 8: Production guidance

- Use production Keycloak mode (not `start-dev`) with managed database.
- Enforce HTTPS end-to-end.
- Put client secret in secure secret store.
- Keep service account privileges minimal.
- Enable stricter password/MFA/lockout policies in realm.
- Rotate secrets periodically and audit admin events.
---

## 3.1) User data ownership (important)

- Keycloak stores authentication credentials (passwords).
- Local `users` table stores only app profile + authorization metadata (`name`, `email`, `username`, `role`, `active`, optional `keycloakUserId`).
- The backend no longer stores password hashes.



## 3.2) Troubleshooting `invalid_client_credentials`

Symptom in Keycloak logs:

```
error="invalid_client_credentials", grant_type="password"
```

Meaning:
- Keycloak rejected the **client secret** for `quarkus-crud-client`.
- User password is not evaluated yet at this stage.

Why:
- `AuthResource` sends `client_id` + `client_secret` in password-grant token exchange.
- If backend env secret and Keycloak client secret differ, login always fails.

Fix path:
1. Keycloak UI → **Clients** → `quarkus-crud-client` → **Credentials** → copy secret.
2. Set backend env `KEYCLOAK_CLIENT_SECRET` to that exact value.
3. Restart backend container/process.
4. Retry login.

Compose note:
- `docker-compose.yml` uses `KEYCLOAK_CLIENT_SECRET: ${KEYCLOAK_CLIENT_SECRET:-change-me}`.
- Set `KEYCLOAK_CLIENT_SECRET` in shell or `.env` before `docker compose up --build`.




## 3.3) Troubleshooting login success but API 403

Symptom:
- `/auth/login` succeeds
- `/users`, `/users/me`, `/users/search` return 403

Meaning:
- Token is valid, but local user authorization checks failed.

Checks:
1. Local `users` row exists for token username (`preferred_username`).
2. Local `active` is true.
3. Local `role` is `admin` for admin CRUD endpoints.

Reasoning:
- Keycloak authenticates identity.
- Backend then authorizes using local profile metadata to keep business access aligned with app-managed roles.



## 3.4) Troubleshooting create-user 201 but login 401

Symptom:
- Admin creates user successfully (`POST /users` -> 201).
- New user login returns 401 from `/auth/login`.

Common cause:
- Access token claims may not include `preferred_username` depending on client scope mapping.
- If backend only used username claim, local lookup could fail despite valid Keycloak credentials.

Implemented behavior:
- Backend resolves local user by `preferred_username` first.
- If missing/unmatched, backend falls back to `sub` claim and matches local `keycloakUserId`.

Verification steps:
1. Keycloak user exists and has a non-temporary password.
2. Local DB row exists with correct `username` and `keycloak_user_id`.
3. Local `active=true`.
4. Stored `keycloak_user_id` equals Keycloak user UUID exactly.




## 3.5) Troubleshooting `POST /users` returns 502

Potential causes:
- Keycloak client credentials are invalid.
- Service account lacks required admin permissions.
- Keycloak user already exists from a previous partial run.

Current backend behavior:
- On Keycloak create `409`, backend resolves existing user id and proceeds with local sync.
- Role mapping failures are logged but do not fail create/update API responses.

Required service account roles (realm-management):
- `manage-users`
- `view-users`
- `view-realm`


---

## 4) Auth flow in code (`AuthResource`)

`/auth/*` endpoints now broker Keycloak flows:

- `POST /auth/login`
  - exchanges username/password against Keycloak token endpoint (`grant_type=password`)
  - validates that local app user exists and is active (`preferred_username` lookup with `sub`/`keycloakUserId` fallback)
  - returns access token + app user payload
  - sets HTTP-only refresh cookie

- `POST /auth/refresh`
  - exchanges cookie refresh token with Keycloak (`grant_type=refresh_token`)
  - rotates refresh token cookie
  - returns new access token + app user payload

- `POST /auth/logout`
  - calls Keycloak logout endpoint
  - clears refresh cookie

Purpose of keeping `/auth/*` in backend:

- frontend remains simple and never handles Keycloak client secret,
- local user `active` status can still be enforced,
- API contract with frontend remains stable.

---

## 5) Protected APIs (`UserResource`)

Manage-users operations are synchronized to Keycloak via `KeycloakAdminService`:
- create: creates Keycloak user, sets password, attempts role assignment, then persists local profile
- update: updates Keycloak profile/enabled state/role/password, attempts role assignment, then updates local row
- delete: removes Keycloak user then deletes local row


Authorization is profile-based after authentication:

- `@Authenticated` ensures only valid Keycloak tokens can access `/users/*`.
- Local `users` table role/state (`admin`/`user`, `active`) determines operation permission.

Profile resolution for `/users/me`:

- reads `preferred_username` claim when present,
- falls back to principal name,
- maps that username to local `User` row.

Why this matters:

- Keycloak token subject may be UUID depending on mapper,
- `preferred_username` keeps profile lookup aligned with app usernames.

---

## 6) Important operational notes

- The local `users` table is still used for app profile/business data.
- Keycloak remains the authentication source of truth.
- Ensure usernames in Keycloak and local DB match for successful `/users/me` and login payload mapping.
- For production, store `KEYCLOAK_CLIENT_SECRET` in secure secret storage (not plaintext files).

---

## 7) Quick verification checklist

1. Login succeeds with Keycloak user credentials.
2. `/users/me` returns local profile for that username.
3. `admin` role token can access `/users`; `user` role token cannot.
4. `/auth/refresh` rotates refresh cookie and returns fresh access token.
5. `/auth/logout` clears session cookie.
