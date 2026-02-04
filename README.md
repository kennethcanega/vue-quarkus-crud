# Vue + Quarkus + Postgres CRUD (Experimental)

This repo contains a minimal CRUD stack:

- **Frontend:** Vue 3 + Vite
- **Backend:** Java 21 + Quarkus (REST + Hibernate Panache)
- **Database:** PostgreSQL (Docker container)

The UI allows you to **create, list, update, and delete** users.

---

## Architecture Overview

```
frontend (Vue)  -->  backend (Quarkus)  -->  postgres
```

- Vue talks to Quarkus via HTTP (`/users` REST endpoints).
- Quarkus uses Hibernate ORM Panache to map a `User` entity to the `users` table.
- Postgres runs in Docker for a repeatable local environment.

---

## Step-by-Step: How This Experimental Project Was Built

### 1) Create the repository structure

```
.
├── backend
├── frontend
└── docker-compose.yml
```

We split the backend and frontend into separate folders to keep build tools isolated and easy to manage.

### 2) Backend: Quarkus + Java 21 CRUD API

**Key files created:**

- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/src/main/java/com/example/users/User.java`
- `backend/src/main/java/com/example/users/UserResource.java`
- `backend/src/main/docker/Dockerfile.jvm`

#### 2.1 Maven configuration (`pom.xml`)

We use Quarkus 3.9.2 and Java 21. Dependencies:

- `quarkus-resteasy-reactive-jackson`: JSON REST API
- `quarkus-hibernate-orm-panache`: simplified ORM layer
- `quarkus-jdbc-postgresql`: Postgres driver

**Reasoning:** These are the minimal Quarkus extensions to expose a REST API and persist data in Postgres.

#### 2.2 Application configuration (`application.properties`)

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=postgres
quarkus.datasource.password=postgres
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/userdb
quarkus.hibernate-orm.database.generation=update
quarkus.http.cors=true
```

**Reasoning:**
- `database.generation=update` auto-creates the table for development.
- CORS is enabled so the Vue UI can call the API from another port.

#### 2.3 User entity (`User.java`)

```java
@Entity
@Table(name = "users")
public class User extends PanacheEntity {
    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String email;
}
```

**Reasoning:** Panache gives us a ready-made `id` and CRUD helpers. `email` is unique to avoid duplicates.

#### 2.4 REST resource (`UserResource.java`)

Endpoints:

- `GET /users` — list
- `POST /users` — create
- `PUT /users/{id}` — update
- `DELETE /users/{id}` — delete

**Reasoning:** These match standard CRUD operations and are easy for the Vue UI to call.

#### 2.5 Backend Dockerfile

We use the Quarkus JVM Dockerfile to run the built application in a container.

---

### 3) Database: PostgreSQL container

`docker-compose.yml` defines a Postgres service:

```yaml
postgres:
  image: postgres:16
  environment:
    POSTGRES_DB: userdb
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: postgres
```

**Reasoning:** Docker ensures the database is reproducible with no manual install.

---

### 4) Frontend: Vue 3 + Vite CRUD UI

**Key files created:**

- `frontend/package.json`
- `frontend/vite.config.js`
- `frontend/index.html`
- `frontend/src/main.js`
- `frontend/src/App.vue`

#### 4.1 Dependencies

- `vue` — UI framework
- `axios` — HTTP client
- `vite` — development/build tooling

**Reasoning:** This is a lightweight, modern Vue stack with a fast dev server.

#### 4.2 UI and CRUD logic (`App.vue`)

- Form for create/update
- Table for list
- Buttons for edit/delete
- Calls the Quarkus API using Axios

Environment variable:

```
VITE_API_BASE_URL=http://localhost:8080
```

**Reasoning:** This makes the API base URL configurable without changing code.

---

## How To Run (Step-by-Step)

### Prerequisites

- Java 21
- Maven 3.9+
- Node.js 18+
- Docker + Docker Compose

### 1) Start Postgres and build/run backend

```bash
cd backend
mvn package
cd ..
docker-compose up --build
```

The backend will be available at: `http://localhost:8080`

### 2) Start the Vue frontend

```bash
cd frontend
npm install
npm run dev
```

Open: `http://localhost:5173`

---

## API Reference

| Method | Endpoint        | Description        |
|-------:|------------------|--------------------|
| GET    | `/users`         | List all users     |
| POST   | `/users`         | Create a user      |
| PUT    | `/users/{id}`    | Update a user      |
| DELETE | `/users/{id}`    | Delete a user      |

---

## What Was Configured (and Why)

- **CORS enabled in Quarkus**: Allows the Vue app (port 5173) to call the API (port 8080).
- **Hibernate `update` strategy**: Automatically creates/updates the DB table during development.
- **Dockerized Postgres**: Ensures consistent local development without manual DB setup.

---

## Notes

- This project is experimental and minimal by design.
- For production, use migrations (e.g., Flyway), environment-based configs, and secure credentials.
