# Backend Guide (Quarkus + PostgreSQL + Keycloak OIDC)

This guide explains the backend for developers new to Quarkus, with emphasis on **how Keycloak replaces local JWT signing** and why each step/configuration exists.

---

## 1) Libraries used (`backend/pom.xml`) and why

- **`quarkus-resteasy-reactive-jackson`**
  - REST APIs + JSON serialization/deserialization.
- **`quarkus-hibernate-orm-panache`**
  - ORM + active-record style persistence for entities.
- **`quarkus-jdbc-postgresql`**
  - PostgreSQL driver integration.
- **`quarkus-arc`**
  - CDI dependency injection support.
- **`quarkus-oidc`**
  - Validates Keycloak-issued access tokens and maps claims/roles to Quarkus security identity.
- **`quarkus-elytron-security-common`**
  - Password hashing utility for local user profile management.

### Why this changed

Previously, the backend generated and signed JWTs itself.
Now Keycloak issues tokens, and Quarkus focuses on:

1. brokering auth/refresh/logout flows,
2. validating incoming Bearer tokens,
3. enforcing roles with `@RolesAllowed`.

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
- **role-claim-path**: maps Keycloak realm roles to `@RolesAllowed("admin")` / `@RolesAllowed("user")`.

Additional backend auth broker properties:

- `keycloak.server-url`
- `keycloak.realm`
- `keycloak.client-id`
- `keycloak.client-secret`

These are used by `AuthResource` for direct token/logout HTTP calls.

---

## 3) Keycloak implementation steps (detailed + purpose)

### Step 1: Run Keycloak

**Purpose:** Provide a standards-based identity server that issues signed access/refresh tokens.

```bash
docker run --name quarkus-keycloak \
  -p 8180:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:25.0.6 \
  start-dev
```

### Step 2: Create realm `quarkus-crud`

**Purpose:** Keep this app’s identity domain isolated.

### Step 3: Create realm roles `admin` and `user`

**Purpose:** Match the exact roles enforced by backend security annotations.

### Step 4: Create client `quarkus-crud-client`

**Purpose:** Let backend exchange credentials/refresh tokens with Keycloak securely.

Required settings:

- Client authentication: enabled
- Direct access grants: enabled (required for username/password grant in `/auth/login`)

Copy generated client secret.

### Step 5: Create users and assign roles

**Purpose:** Users authenticated by Keycloak must carry valid role claims.

At minimum create:

- `admin` user with `admin` role.

### Step 6: Export env vars for backend

**Purpose:** Inject realm/client coordinates at runtime without hardcoding secrets.

```bash
export KEYCLOAK_SERVER_URL="http://localhost:8180"
export KEYCLOAK_REALM="quarkus-crud"
export KEYCLOAK_CLIENT_ID="quarkus-crud-client"
export KEYCLOAK_CLIENT_SECRET="<your-client-secret>"
export AUTH_REFRESH_COOKIE_SECURE="false"
```

### Step 7: Start backend

**Purpose:** Activate Quarkus OIDC verification and Keycloak-backed auth broker endpoints.

```bash
cd backend
mvn quarkus:dev
```

---

## 4) Auth flow in code (`AuthResource`)

`/auth/*` endpoints now broker Keycloak flows:

- `POST /auth/login`
  - exchanges username/password against Keycloak token endpoint (`grant_type=password`)
  - validates that local app user exists and is active
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

Security remains annotation-based:

- Admin-only: `@RolesAllowed("admin")`
- Authenticated users: `@RolesAllowed({"admin", "user"})`

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
