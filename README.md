# Vue + Quarkus + Postgres CRUD (JWT + Roles)

This project is a secure, full-stack CRUD app with **JWT authentication** and **role-based access**:

- **Frontend:** Vue 3 + Vite + Vue Router
- **Backend:** Java 21 + Quarkus (REST + Hibernate Panache + SmallRye JWT)
- **Database:** PostgreSQL (Docker)

You can log in, view your profile, search users, and (if you are an admin) manage users end-to-end.

---

## Features at a Glance

### Authentication + Authorization
- **JWT login** (`/auth/login`)
- **Roles:** `admin` and `user`
- **Menu + routing** in Vue hides or shows features based on role
- **Backend enforcement** blocks non-admin access to admin endpoints

### Default Account
- Username: **admin**
- Password: **admin**

### Role Capabilities
| Feature | Admin | Regular user |
|--------|-------|--------------|
| Login | ✅ | ✅ |
| View profile | ✅ | ✅ |
| Search users | ✅ | ✅ |
| Manage users (CRUD) | ✅ | ❌ |
| Block/reactivate user | ✅ | ❌ |

---

## Architecture Overview

```
Vue (frontend) --> Quarkus API (backend) --> PostgreSQL
```

- Vue authenticates with `/auth/login` and stores a JWT in `localStorage`.
- Axios sends the JWT in the `Authorization: Bearer <token>` header.
- Quarkus verifies the token and enforces roles with `@RolesAllowed`.

---

## Backend Walkthrough (Quarkus)

### 1) Entity model (`User`)
The `User` entity stores authentication fields in addition to profile data:

- `username` (unique login handle)
- `passwordHash` (bcrypt, never returned to clients)
- `role` (`admin` or `user`)
- `active` (blocked vs active)
- `name` and `email` (display + search)

This lets us enforce **both** login security and business logic in the same table.

### 2) JWT Login (`/auth/login`)
The login endpoint:

1. Verifies username/password.
2. Rejects inactive users.
3. Builds a JWT with:
   - `sub` = username
   - `groups` = role

The JWT is signed with a shared secret configured in `application.properties`.

### 3) Role-protected endpoints
Quarkus uses annotations to enforce access:

- `@RolesAllowed("admin")` for admin-only endpoints like `/users` CRUD
- `@RolesAllowed({"admin", "user"})` for `/users/search` and `/users/me`
- `@PermitAll` for `/auth/login`

This means **even curl/Postman users are blocked** unless they have a valid JWT with the right role.

### 4) Default admin seeding
On startup, a default admin is created **if it does not already exist**:

```
username: admin
password: admin
role: admin
```

You can then create additional users in the admin UI.

If you already have users from the original CRUD app, startup will backfill:
- `username` derived from email (or a fallback)
- `role` set to `user`
- `active` set to `true`
- `password` set to **changeme** (bcrypt hash stored)

### 5) Key configuration (backend)
`backend/src/main/resources/application.properties` includes:

```properties
mp.jwt.verify.issuer=quarkus-crud
smallrye.jwt.sign.key=super-secret-change-me
smallrye.jwt.verify.key=super-secret-change-me
```

**Why this matters:** the backend signs and verifies tokens with the same secret. In production,
store this value securely (environment variable or secrets manager).

---

## Frontend Walkthrough (Vue)

### 1) Router + menu
Vue Router defines the main pages:

- `/login` — login screen
- `/profile` — profile view
- `/search` — user search (all roles)
- `/users` — user management (admin only)

Route guards redirect users who are not authenticated or do not have the right role.

### 2) Auth state + API calls
The frontend stores the JWT in `localStorage` and attaches it to every request:

```js
Authorization: Bearer <token>
```

This happens in a centralized Axios instance, so all pages inherit secure requests.

### 3) Manage Users (Admin)
Admins can:

- Create users
- Update name/email/username
- Reset passwords
- Change roles
- Block/reactivate accounts
- Delete users

### 4) Search + Profile (All roles)
Regular users can:

- Search by name/email
- View their own profile details

They **cannot** access admin endpoints (front-end menu hides them and backend blocks them).

---

## How To Run (Step-by-Step)

### Prerequisites
- Java 21
- Maven 3.9+
- Node.js 18+
- Docker + Docker Compose

### 1) Start Postgres + backend

```bash
cd backend
mvn package
cd ..
docker-compose up --build
```

Backend: `http://localhost:8080`

Postgres port mapping is **5433:5432**:
- Use `localhost:5433` when connecting from your host machine (DBeaver, psql, local backend).
- Use `postgres:5432` from containers on the same Docker network (the Quarkus container).

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
```

---

## API Reference (JWT Required)

### Auth
| Method | Endpoint | Description |
|-------:|----------|-------------|
| POST | `/auth/login` | Login and receive JWT |

### Users (Admin-only)
| Method | Endpoint | Description |
|-------:|----------|-------------|
| GET | `/users` | List all users |
| POST | `/users` | Create user |
| PUT | `/users/{id}` | Update user (username/password/role/status) |
| DELETE | `/users/{id}` | Delete user |

### Users (All roles)
| Method | Endpoint | Description |
|-------:|----------|-------------|
| GET | `/users/me` | View your profile |
| GET | `/users/search?q=` | Search users by name/email |

---

## cURL Examples

### 1) Login
```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

Copy the `token` from the response and export it:

```bash
export TOKEN="paste-jwt-here"
```

### 2) Admin: List users
```bash
curl -s http://localhost:8080/users \
  -H "Authorization: Bearer $TOKEN"
```

### 3) Regular user: Search (allowed)
```bash
curl -s "http://localhost:8080/users/search?q=ada" \
  -H "Authorization: Bearer $TOKEN"
```

### 4) Regular user: Manage users (blocked)
```bash
curl -i http://localhost:8080/users \
  -H "Authorization: Bearer $TOKEN"
```

You will receive **403 Forbidden** if your role is not `admin`.

---

## Troubleshooting

**Login fails with 401**
- Ensure the user is active and the password is correct.
- If you changed the JWT secret, restart the backend.

**Vue shows "Unable to load users"**
- Confirm the backend is running on port 8080.
- Confirm the user role is `admin` for `/users` access.
