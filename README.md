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

### 1) Start Keycloak

**Purpose:** Run an OIDC provider that issues and validates user tokens.

This repository now includes Keycloak in `docker-compose.yml`, exposed at `http://localhost:8180` (admin/admin by default).

If you prefer running it separately, you can still use:

```bash
docker run --name quarkus-keycloak \
  -p 8180:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:25.0.6 \
  start-dev
```

Admin console: `http://localhost:8180`.

### 2) Create Realm

**Purpose:** Isolate this project’s users/roles/clients from other applications.

1. Log in to Keycloak admin console.
2. Create realm: `quarkus-crud`.

### 3) Create Realm Roles

**Purpose:** Provide the exact roles Quarkus checks with `@RolesAllowed`.

Create roles:

* `admin`
* `user`

### 4) Create Client

**Purpose:** Allow backend to exchange username/password and refresh token grants securely.

Create client:

* Client ID: `quarkus-crud-client`
* Client authentication: **Enabled**
* Authorization: Disabled
* Standard flow: Optional
* Direct access grants: **Enabled** (required for `/auth/login` password grant)

After creating client, generate/copy the **client secret**.

Also enable **Service Accounts** for this client and grant it realm-management roles needed for user administration (at minimum: `manage-users`, `view-users`, `view-realm`).
This is required because the backend creates/updates/deletes users in Keycloak from the Manage Users screen.

### 5) Create Users and Assign Roles

**Purpose:** Keycloak is now the source of authentication and role claims.

Create at least:

* Username: `admin`
* Password: `admin` (or stronger password)
* Assign role: `admin`

For non-admin users, assign `user` role.

### 6) Configure Backend Environment

**Purpose:** Point Quarkus to the correct Keycloak realm/client.

Set:

```bash
export KEYCLOAK_SERVER_URL="http://localhost:8180"
export KEYCLOAK_REALM="quarkus-crud"
export KEYCLOAK_CLIENT_ID="quarkus-crud-client"
export KEYCLOAK_CLIENT_SECRET="<your-client-secret>"
export CORS_ORIGINS="http://localhost:5173"
export AUTH_REFRESH_COOKIE_SECURE="false"
```

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
