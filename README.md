# Vue + Quarkus + Postgres CRUD (JWT + Roles)

This project is a secure, full-stack CRUD app with **JWT authentication** and **role-based access**:

* **Frontend:** Vue 3 + Vite + Vue Router
* **Backend:** Java 21 + Quarkus (REST + Hibernate Panache + SmallRye JWT)
* **Database:** PostgreSQL (Docker)

You can log in, view your profile, search users, and (if you are an admin) manage users end-to-end.

---

## Features at a Glance

### Authentication + Authorization

* **JWT login** (`/auth/login`) + refresh (`/auth/refresh`) + logout (`/auth/logout`)
* **Roles:** `admin` and `user`
* **Menu + routing** in Vue hides or shows features based on role
* **Backend enforcement** blocks non-admin access to admin endpoints

### Default Account

* Username: **admin**
* Password: **admin**

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
```

* Vue authenticates with `/auth/login`, stores a short-lived access token in `localStorage`, and receives an HTTP-only refresh cookie.
* Axios sends the JWT in the `Authorization: Bearer <token>` header and refreshes tokens via `/auth/refresh` on schedule / 401.
* Quarkus verifies the token and enforces roles with `@RolesAllowed`.

---

## Backend Walkthrough (Quarkus)

### 1) Entity model (`User`)

The `User` entity stores authentication fields in addition to profile data:

* `username` (unique login handle)
* `passwordHash` (bcrypt, never returned to clients)
* `role` (`admin` or `user`)
* `active` (blocked vs active)
* `name` and `email` (display + search)

This lets us enforce **both** login security and business logic in the same table.

---

### 2) Auth lifecycle (`/auth/login`, `/auth/refresh`, `/auth/logout`)

The auth endpoints now implement short-lived access tokens + refresh-token rotation:

1. `POST /auth/login` verifies username/password and rejects inactive users.
2. Backend returns access token in JSON and sets HTTP-only refresh cookie (`refresh_token`).
3. `POST /auth/refresh` validates + rotates refresh token and returns a new access token.
4. `POST /auth/logout` revokes refresh tokens and clears the refresh cookie.

Access JWTs still include:

   * `sub` = username
   * `groups` = role

JWTs are signed using **RSA (RS256)** with a private key.

> Plain text secrets are **not supported** when using `Jwt.sign()` in Quarkus.
> The backend must use real cryptographic key material.

---

### 3) Role-protected endpoints

Quarkus uses annotations to enforce access:

* `@RolesAllowed("admin")` for admin-only endpoints like `/users` CRUD
* `@RolesAllowed({"admin", "user"})` for `/users/search` and `/users/me`
* `@PermitAll` for `/auth/login`, `/auth/refresh`, and `/auth/logout`

This means **even curl/Postman users are blocked** unless they have a valid JWT with the right role.

---

### 4) Default admin seeding

On startup, a default admin is created **if it does not already exist**:

```
username: admin
password: admin
role: admin
```

You can then create additional users in the admin UI.

If you already have users from the original CRUD app, startup will backfill:

* `username` derived from email (or a fallback)
* `role` set to `user`
* `active` set to `true`
* `password` set to **changeme** (bcrypt hash stored)

---

### 5) JWT signing keys (required)

This project uses **SmallRye JWT’s build API (`Jwt.sign()`)**, which requires
**real cryptographic keys**, not plain strings.

Using a configuration like:

```properties
smallrye.jwt.sign.key=super-secret
```

will cause runtime errors such as:

```
SRJWT05028: Signing key can not be created from the loaded content
```

To avoid this, the backend uses **RSA (RS256)** with:

* a **private key** to sign JWTs
* a **public key** to verify JWTs

This approach is safer and easier to scale.

---

### 6) Generating RSA `.pem` keys

Run the following commands:

```bash
# Generate a 2048-bit RSA private key (PKCS8)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out privateKey.pem

# Extract the public key
openssl pkey -in privateKey.pem -pubout -out publicKey.pem
```

Resulting files:

* `privateKey.pem` — used to sign JWTs
* `publicKey.pem` — used to verify JWTs

---

### 7) Key placement (local development)

Place the keys on the Quarkus classpath:

```
backend/src/main/resources/jwt/privateKey.pem
backend/src/main/resources/jwt/publicKey.pem
```

> Do not commit `privateKey.pem` in real production projects.
> Use Docker or Kubernetes secrets instead.

---

### 8) Key configuration (backend)

`backend/src/main/resources/application.properties` includes:

```properties
mp.jwt.verify.issuer=quarkus-crud

# Sign JWTs
smallrye.jwt.sign.key.location=jwt/privateKey.pem

# Verify JWTs
mp.jwt.verify.publickey.location=jwt/publicKey.pem

# Explicit algorithm
smallrye.jwt.new-token.signature-algorithm=RS256
mp.jwt.verify.publickey.algorithm=RS256
```

**Why this matters:**
The backend can now reliably sign and verify tokens without runtime failures.

---

## Frontend Walkthrough (Vue)

### 1) Router + menu

Vue Router defines the main pages:

* `/login` — login screen
* `/profile` — profile view
* `/search` — user search (all roles)
* `/users` — user management (admin only)

Route guards redirect users who are not authenticated or do not have the right role.

### 2) Auth state + API calls

The frontend stores the access JWT in `localStorage` and attaches it to every request:

```js
Authorization: Bearer <token>
```

This happens in a centralized Axios instance, so all pages inherit secure requests.

It also runs a configurable refresh scheduler (default every 5 seconds) and retries once on 401 by calling `/auth/refresh`.

### 3) Manage Users (Admin)

Admins can:

* Create users
* Update name/email/username
* Reset passwords
* Change roles
* Block/reactivate accounts
* Delete users

### 4) Search + Profile (All roles)

Regular users can:

* Search by name/email
* View their own profile details

They **cannot** access admin endpoints (front-end menu hides them and backend blocks them).

---

## How To Run (Step-by-Step)

### Prerequisites

* Java 21
* Maven 3.9+
* Node.js 18+
* Docker + Docker Compose

### 1) Start Postgres + backend

```bash
cd backend
mvn package
cd ..
docker-compose up --build
```

Backend: `http://localhost:8080`

Postgres port mapping is **5433:5432**:

* Use `localhost:5433` when connecting from your host machine.
* Use `postgres:5432` from containers on the same Docker network.

### 2) Start the Vue frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend: `http://localhost:5173`

If your backend runs on a different host/port, set:

```bash
export VITE_API_BASE_URL="http://localhost:8080"
export VITE_AUTH_REFRESH_INTERVAL_MS="5000"
```

Backend token/session tuning can be configured with:

```bash
export AUTH_ACCESS_TOKEN_TTL_SECONDS="300"
export AUTH_REFRESH_TOKEN_TTL_SECONDS="1209600"
export AUTH_REFRESH_COOKIE_SECURE="false"
export CORS_ORIGINS="http://localhost:5173"
```

---

## API Reference (JWT Required)

### Auth

| Method | Endpoint      | Description           |
| -----: | ------------- | --------------------- |
|   POST | `/auth/login` | Login and receive access JWT + refresh cookie |
|   POST | `/auth/refresh` | Rotate refresh token and receive fresh access JWT |
|   POST | `/auth/logout` | Revoke refresh token(s) and clear refresh cookie |

### Users (Admin-only)

| Method | Endpoint      | Description    |
| -----: | ------------- | -------------- |
|    GET | `/users`      | List all users |
|   POST | `/users`      | Create user    |
|    PUT | `/users/{id}` | Update user    |
| DELETE | `/users/{id}` | Delete user    |

### Users (All roles)

| Method | Endpoint           | Description       |
| -----: | ------------------ | ----------------- |
|    GET | `/users/me`        | View your profile |
|    GET | `/users/search?q=` | Search users      |

---

## cURL Examples

### 1) Login

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

```bash
export TOKEN="paste-jwt-here"
```

### 2) Admin: List users

```bash
curl -s http://localhost:8080/users \
  -H "Authorization: Bearer $TOKEN"
```

---

## Troubleshooting

**Login fails**

* Ensure `.pem` files exist and are readable
* Restart backend after changing keys
* Confirm issuer matches `quarkus-crud`

**403 Forbidden**

* Confirm the user role
* Confirm JWT is being sent correctly
