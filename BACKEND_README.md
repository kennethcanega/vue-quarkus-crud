# Backend Guide (Quarkus + PostgreSQL + JWT) for Developers New to Quarkus

This guide is written for engineers who are already comfortable with backend development (for example in Spring Boot, .NET, Express, etc.) but are new to Quarkus.

The goal is to explain this backend **file by file**, including runtime/deployment configuration (`docker-compose.yml`, `application.properties`), cryptographic key setup with OpenSSL, and exact authentication/authorization flow.

---

## 1) Libraries used (`backend/pom.xml`) and why

Quarkus uses extensions (dependencies) to add capabilities. In this project:

- **`quarkus-resteasy-reactive-jackson`**
  - Exposes REST endpoints using JAX-RS annotations (`@Path`, `@GET`, `@POST`, etc.).
  - Converts Java objects/records to JSON and back (Jackson).

- **`quarkus-hibernate-orm-panache`**
  - ORM layer for database entities.
  - Panache adds active-record style helpers (`User.findById`, `User.listAll`, `user.persist`).

- **`quarkus-jdbc-postgresql`**
  - PostgreSQL JDBC integration so Hibernate can connect to Postgres.

- **`quarkus-arc`**
  - CDI dependency injection container.
  - Enables `@ApplicationScoped` and constructor injection patterns.

- **`quarkus-smallrye-jwt`**
  - Verifies incoming JWT tokens and integrates with Quarkus security identity.

- **`quarkus-smallrye-jwt-build`**
  - Builds/signs JWT tokens in application code (`Jwt.issuer(...).sign()`).

- **`quarkus-elytron-security-common`**
  - Password hash utilities (bcrypt through `BcryptUtil`).

### If you come from Spring Boot

- `@RestController` ≈ `@Path` JAX-RS resource class.
- Spring Security role checks ≈ `@RolesAllowed`.
- Spring Data repository calls ≈ Panache entity static methods.
- `@Service`/`@Component` DI ≈ CDI beans like `@ApplicationScoped`.

---

## 2) Runtime/deployment file: `docker-compose.yml`

This file spins up local infrastructure and backend service.

## `postgres` service

- Uses image `postgres:16`.
- Creates DB/user/password via env vars:
  - `POSTGRES_DB=userdb`
  - `POSTGRES_USER=postgres`
  - `POSTGRES_PASSWORD=postgres`
- Maps host port `5433` → container `5432`.
  - So local apps connect to `localhost:5433`.
- Uses volume `postgres_data` to persist data across container restarts.

## `backend` service

- Builds from `./backend` using `src/main/docker/Dockerfile.jvm`.
- Exposes backend API on host `8080`.
- Passes datasource env vars:
  - `QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/userdb`
  - `QUARKUS_DATASOURCE_USERNAME=postgres`
  - `QUARKUS_DATASOURCE_PASSWORD=postgres`
- `depends_on: postgres` ensures DB container starts first.

### Why this matters

- Local host execution (`mvn quarkus:dev`) typically uses `application.properties` URL (`localhost:5433`).
- Containerized backend uses internal Docker network URL (`postgres:5432`) via env override.
- Same code, different runtime binding strategy.

---

## 3) Application config file: `backend/src/main/resources/application.properties`

This file defines default backend runtime behavior.

## Database/ORM

- `quarkus.datasource.db-kind=postgresql`
- `quarkus.datasource.username=postgres`
- `quarkus.datasource.password=postgres`
- `quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5433/userdb`
- `quarkus.hibernate-orm.database.generation=update`
- `quarkus.hibernate-orm.log.sql=true`

Interpretation:

- Connect to Postgres on local machine port `5433`.
- Auto-update schema to match entities (good for local dev, usually stricter in prod).
- SQL logging enabled for easier debugging.

## CORS

- `quarkus.http.cors=true`
- `quarkus.http.cors.origins=*`
- `quarkus.http.cors.headers=...`
- `quarkus.http.cors.methods=...`

This allows browser frontend calls from different origins during development.

## JWT/security config

- `mp.jwt.verify.issuer=quarkus-crud`
- `smallrye.jwt.sign.key.location=jwt/privateKey.pem`
- `mp.jwt.verify.publickey.location=jwt/publicKey.pem`
- `smallrye.jwt.new-token.signature-algorithm=RS256`
- `mp.jwt.verify.publickey.algorithm=RS256`

Meaning:

- Tokens created by this service set issuer `quarkus-crud`.
- Private key signs tokens; public key verifies tokens.
- RS256 is explicitly configured for both creation and verification.

---

## 4) Backend codebase walkthrough (per crucial file)

## `backend/src/main/java/com/example/users/User.java`

The core entity (`users` table). Extends `PanacheEntity`, so it inherits `id` and ORM helpers.

Fields:

- `name`, `email` for profile data.
- `username`, `passwordHash`, `role`, `active` for auth/authorization.
- `@JsonIgnore` on `passwordHash` prevents accidental exposure in API responses.

Includes helper:

- `findByUsername(String username)` for login/profile lookups.

## `backend/src/main/java/com/example/users/PasswordUtils.java`

Security helper wrapping bcrypt:

- `hash(plainPassword)` for storage.
- `matches(plainPassword, storedHash)` for verification.

Never compare plain passwords directly.

## `backend/src/main/java/com/example/users/AuthResource.java`

Authentication endpoint class (`/auth`).

- `POST /auth/login` marked `@PermitAll`.
- Validates request payload and credentials.
- Rejects inactive users.
- Creates JWT with:
  - issuer
  - subject = username
  - groups = user role
  - expiration = 8 hours
- Returns `LoginResponse(token, UserResponse)`.

## `backend/src/main/java/com/example/users/UserResource.java`

Main protected business API class (`/users`).

Endpoints:

- `GET /users` (`@RolesAllowed("admin")`) list all users.
- `GET /users/search` (`@RolesAllowed({"admin","user"})`) search by name/email.
- `GET /users/me` (`@RolesAllowed({"admin","user"})`) current profile from token principal.
- `POST /users` (`@RolesAllowed("admin")`, `@Transactional`) create user.
- `PUT /users/{id}` (`@RolesAllowed("admin")`, `@Transactional`) partial update.
- `DELETE /users/{id}` (`@RolesAllowed("admin")`, `@Transactional`) delete user.

Key detail:

- `SecurityIdentity` gives current authenticated principal (`identity.getPrincipal().getName()`), which maps to JWT subject (username).

## DTO/record files under `com/example/users`

- `LoginRequest`, `LoginResponse`
- `CreateUserRequest`, `UpdateUserRequest`
- `UserResponse`, `UserSummary`

Why records/DTOs matter:

- Keep API contract explicit.
- Avoid leaking internal entity fields.
- Enable endpoint-specific response shape (`UserSummary` for lightweight search).

## `backend/src/main/java/com/example/users/UserSeeder.java`

Startup initialization bean:

- Observes `StartupEvent`.
- Ensures `admin/admin` exists and is active with admin role.
- Backfills old users with missing username/role/active/password defaults.
- Runs in transaction so updates persist atomically.

This prevents local environments from starting in an unusable auth state.

---

## 5) OpenSSL key generation: commands and rationale

This backend signs JWTs with RSA key material (asymmetric crypto).

Commands:

```bash
# 1) Generate RSA private key (2048 bits)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out privateKey.pem

# 2) Derive matching public key
openssl pkey -in privateKey.pem -pubout -out publicKey.pem
```

Why these commands:

- **Private key** is secret and used only by this backend to sign JWTs.
- **Public key** can be shared and used by verifiers to validate signatures.
- Asymmetric signing is safer/scalable: verifiers do not need secret signing key.

Where files go in this project:

- `backend/src/main/resources/jwt/privateKey.pem`
- `backend/src/main/resources/jwt/publicKey.pem`

Why not plain strings?

- `Jwt.sign()` in this setup expects real key material.
- Using random plain text as signing key can fail or be insecure.

---

## 6) Authentication processing (step-by-step)

When frontend calls `POST /auth/login`:

1. Request body arrives as `LoginRequest`.
2. Backend validates non-null fields.
3. `User.findByUsername(...)` fetches user.
4. `PasswordUtils.matches(...)` checks bcrypt hash.
5. If user missing/inactive/wrong password → `401 Unauthorized`.
6. Backend builds JWT (`iss`, `sub`, `groups`, expiry) and signs with private key.
7. Response returns token + sanitized user payload.

Frontend stores token and sends `Authorization: Bearer <token>` on later requests.

---

## 7) Authorization processing (step-by-step)

For a protected endpoint (example `GET /users`):

1. Incoming Bearer token extracted by Quarkus security.
2. Signature verified with configured public key.
3. Issuer and algorithm validated (`application.properties`).
4. Security identity created (principal + groups).
5. `@RolesAllowed("admin")` evaluated.
6. If role missing → access denied before endpoint logic.
7. If role present → resource method executes.

This means security checks happen before your business code runs.

---

## 8) Data flow examples in this backend

## Login and profile

- `/auth/login` returns token and user.
- `/users/me` reads principal from JWT subject and loads matching user.

## Admin CRUD

- Create/update/delete endpoints are transactional.
- Mutations change entity state and ORM flushes to Postgres.

## Search

- `/users/search?q=...` performs case-insensitive query against name/email.
- Returns `UserSummary` list to keep payload focused.

---

## 9) How to quickly understand this codebase as a new Quarkus dev

Recommended order:

1. `docker-compose.yml` (runtime wiring)
2. `backend/pom.xml` (capabilities/extensions)
3. `application.properties` (security/db behavior)
4. `User.java` + DTO records (data model and API contracts)
5. `AuthResource.java` (login/token creation)
6. `UserResource.java` (role-protected business APIs)
7. `UserSeeder.java` (startup guarantees)

If you can trace how a JWT created in `AuthResource` becomes a `SecurityIdentity` used by `@RolesAllowed` in `UserResource`, you have mastered the central Quarkus security flow in this project.
