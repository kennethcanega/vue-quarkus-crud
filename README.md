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
* **Backend enforcement** blocks non-admin access to admin endpoints using `@RolesAllowed`
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
* Quarkus validates Bearer tokens using OIDC configuration and enforces roles with `@RolesAllowed`.
* User credentials (passwords) are stored only in Keycloak; backend DB stores app profile/role metadata only.

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

**Purpose:** Backend authorization (`@RolesAllowed`) checks these exact role names.

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
4. Continue and set:
   - **Client authentication**: ON
   - **Authorization**: OFF
   - **Direct access grants**: ON
   - (Standard flow may stay ON/OFF; not required for current backend broker flow)
5. Save, then open **Credentials** tab and copy the client secret.

### 6) Enable Service Account and grant admin API roles

**Purpose:** Without these permissions, Manage Users cannot create/update/delete Keycloak users.

Navigation:
1. Open client `quarkus-crud-client`.
2. Ensure **Service accounts roles** is enabled.
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
