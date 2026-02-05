# Backend Guide (Quarkus + RESTEasy Reactive + Panache + JWT)

This guide explains how the backend works for developers familiar with frameworks like Spring Boot but new to Quarkus.

---

## 1) Backend libraries first: what each one is used for

Dependencies are defined in `backend/pom.xml`.

- **`quarkus-resteasy-reactive-jackson`**
  - Builds JSON REST APIs with JAX-RS annotations (`@Path`, `@GET`, `@POST`, ...).
  - Jackson handles JSON serialization/deserialization.
- **`quarkus-hibernate-orm-panache`**
  - ORM support + Panache active-record style (`User.findById`, `User.listAll`, `user.persist`).
  - Comparable outcome to Spring Data JPA, but different API style.
- **`quarkus-jdbc-postgresql`**
  - PostgreSQL JDBC driver integration.
- **`quarkus-arc`**
  - CDI dependency injection container (`@ApplicationScoped`, constructor injection).
- **`quarkus-smallrye-jwt`**
  - JWT verification and security integration.
- **`quarkus-smallrye-jwt-build`**
  - Programmatic JWT token creation (`Jwt.issuer(...).sign()`).
- **`quarkus-elytron-security-common`**
  - Password hashing/verification utilities (bcrypt used via `BcryptUtil`).

### Spring Boot analogy

- REST controllers (`@RestController`) ≈ JAX-RS resources (`@Path`).
- Spring Security role checks (`@PreAuthorize`) ≈ `@RolesAllowed`.
- JPA repository usage ≈ Panache entity methods.
- `@Component/@Service` DI ≈ CDI scopes like `@ApplicationScoped`.

---

## 2) High-level architecture

Request path:

1. Frontend sends HTTP request with optional Bearer token.
2. Quarkus security verifies JWT signature + issuer + role groups.
3. JAX-RS resource method handles request.
4. Panache ORM talks to PostgreSQL.
5. JSON response is returned.

---

## 3) Project files and responsibilities

### `backend/src/main/resources/application.properties`

Main responsibilities:

- PostgreSQL datasource configuration.
- Hibernate schema strategy (`update`).
- CORS settings for frontend integration.
- JWT issuer and signing/verification key locations.

### `backend/src/main/java/com/example/users/User.java`

Entity model (`users` table):

- profile fields: `name`, `email`
- auth fields: `username`, `passwordHash`, `role`, `active`

Extends `PanacheEntity`, which provides `id` plus active-record helpers.

### DTO/record files

- `LoginRequest`, `LoginResponse`
- `CreateUserRequest`, `UpdateUserRequest`
- `UserResponse`, `UserSummary`

Purpose: isolate API contracts from persistence model and avoid leaking sensitive fields.

### `PasswordUtils.java`

Small helper around bcrypt:

- `hash(password)`
- `matches(password, hash)`

### `AuthResource.java`

Auth endpoint:

- `POST /auth/login` is `@PermitAll`
- validates request, verifies credentials + active status
- creates signed JWT with issuer, subject, role groups, expiry
- returns token + user data

### `UserResource.java`

User APIs:

- `GET /users` (admin)
- `GET /users/search` (admin/user)
- `GET /users/me` (admin/user)
- `POST /users` (admin)
- `PUT /users/{id}` (admin)
- `DELETE /users/{id}` (admin)

Uses:

- `@RolesAllowed` for authorization
- `@Transactional` for write operations
- `SecurityIdentity` to resolve current principal for `/users/me`

### `UserSeeder.java`

Startup data initializer:

- ensures default `admin/admin` account exists
- backfills old/incomplete users with sensible defaults
- runs on startup event in transactional context

---

## 4) Security model in practice

### Login flow

1. Client posts username/password to `/auth/login`.
2. Backend loads user by username.
3. Password compared with bcrypt hash.
4. Inactive users are rejected.
5. JWT created with:
   - `iss=quarkus-crud`
   - `sub=<username>`
   - `groups=[role]`
   - expiration 8 hours

### Authorization flow

For protected endpoints:

- token must be valid (signature + issuer + algorithm)
- required role must exist in JWT `groups`

If not, Quarkus denies access before business logic runs.

---

## 5) Database and transactions

### Panache style used here

- Reads: `User.listAll()`, `User.find(...)`, `User.findById(...)`
- Writes: update managed entity fields in transaction, `persist()`, `deleteById()`

`@Transactional` appears on mutating endpoints and startup seeding method.

### Important behavior

In a transaction, loaded entities are managed. Updating fields is enough; ORM flush persists changes automatically.

---

## 6) JWT key material and configuration

This project uses RSA key files:

- signing private key: `backend/src/main/resources/jwt/privateKey.pem`
- verification public key: `backend/src/main/resources/jwt/publicKey.pem`

Configured via:

- `smallrye.jwt.sign.key.location`
- `mp.jwt.verify.publickey.location`
- `smallrye.jwt.new-token.signature-algorithm=RS256`
- `mp.jwt.verify.publickey.algorithm=RS256`

This is production-style asymmetric JWT signing, not plain shared string secrets.

---

## 7) Typical endpoint behavior map

- **Login (`POST /auth/login`)**
  - 200 on success with token
  - 400 for malformed request
  - 401 for invalid credentials/inactive user

- **List users (`GET /users`)**
  - admin only
  - returns full user list (without password hash)

- **Search (`GET /users/search?q=...`)**
  - admin + user
  - returns lightweight summaries (`id`, `name`, `email`)

- **Profile (`GET /users/me`)**
  - admin + user
  - derives current user from token principal

- **Create/update/delete**
  - admin only
  - create returns `201`, delete returns `204`

---

## 8) Spring Boot to Quarkus translation quick sheet

- `@RestController` + `@RequestMapping` → `@Path` resource + method annotations.
- `@GetMapping/@PostMapping` → `@GET/@POST`.
- `@Service` injection → CDI constructor injection.
- `@Entity` + repository methods → Panache entity methods.
- Spring Security role expressions → `@RolesAllowed`.
- `ApplicationRunner` seeders → observer method with `@Observes StartupEvent`.

---

## 9) How to read this backend efficiently

Suggested order:

1. `pom.xml` (libraries)
2. `application.properties` (runtime behavior)
3. `User` entity + DTO records
4. `AuthResource` (login)
5. `UserResource` (authorization + CRUD)
6. `UserSeeder` (startup defaults)

If you can explain how a JWT from `/auth/login` ends up authorizing `@RolesAllowed` methods in `UserResource`, you already understand the core of this Quarkus backend.
