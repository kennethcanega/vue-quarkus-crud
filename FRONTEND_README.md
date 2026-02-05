# Frontend Guide (Vue 3 + Vue Router + Axios + Vite + npm)

This guide explains how the frontend works for engineers who already know programming but are new to Vue. It focuses on **architecture**, **reactivity**, and **why each tool exists**.

---

## 1) Frontend libraries and what they do

This project’s frontend dependencies are defined in `frontend/package.json`.

### Runtime dependencies

- **`vue`**: core framework for building reactive UI components.
- **`vue-router`**: client-side routing (`/login`, `/profile`, etc.), route guards, and navigation.
- **`axios`**: HTTP client used to call backend APIs.

### Development dependencies

- **`vite`**: dev server + production bundler.
- **`@vitejs/plugin-vue`**: lets Vite understand `.vue` single-file components.

### Why this stack is common

- Vue handles UI state and rendering.
- Router handles screen transitions without page reload.
- Axios handles API requests with interceptors (auth headers).
- Vite keeps development/build fast.

---

## 2) npm basics for this project

`npm` is Node’s package manager and script runner.

In this repo, frontend npm commands are in `frontend/package.json`:

- `npm --prefix frontend install`
  - installs dependencies listed in `dependencies`/`devDependencies`.
- `npm --prefix frontend run dev`
  - starts Vite dev server (`0.0.0.0:5173`).
- `npm --prefix frontend run build`
  - creates production bundle.
- `npm --prefix frontend run preview`
  - serves built bundle locally for validation.

### Quick mental model

- `package.json` = declared dependencies + script aliases.
- `package-lock.json` = exact resolved dependency tree (reproducible installs).
- `node_modules/` = local installed packages.

---

## 3) Startup flow (how the app boots)

Think of startup as a pipeline:

1. `frontend/index.html` exposes `<div id="app"></div>`.
2. `frontend/src/main.js` creates Vue app.
3. Router plugin is installed.
4. `App.vue` renders root layout.
5. `<RouterView />` injects current page component.

`main.js` line:

```js
createApp(App).use(router).mount('#app');
```

Equivalent concept in other frameworks: bootstrapping app root + plugin/middleware registration.

---

## 4) Root layout: `frontend/src/App.vue`

`App.vue` is the shell: header, auth actions, nav, and content area.

### Vue features used

- `v-if` to show nav/actions only when authenticated.
- `@click` event handler for logout.
- `computed(...)` for `currentUser` derived from shared auth state.
- `onMounted(...)` to lazily restore user profile from token.
- `<RouterLink>` and `<RouterView>` from Vue Router.

### Behavior

- If logged in, it shows username and role-aware navigation.
- If logout is clicked, auth state is cleared and route changes to `/login`.

---

## 5) Routing + guards: `frontend/src/router.js`

Routes are metadata-driven:

- `/login` → guest only (`requiresGuest`)
- `/profile` and `/search` → authenticated (`requiresAuth`)
- `/users` → authenticated admin (`requiresAuth`, `requiresAdmin`)

Global guard (`router.beforeEach`) handles:

1. profile bootstrap when token exists but user object is empty
2. guest/auth redirects
3. admin-role redirects

This centralizes navigation authorization in one place.

---

## 6) Auth service and API layer: `frontend/src/services/auth.js`

This file acts like a lightweight store + API client wrapper.

### Shared reactive state

- `state.token` from `localStorage`.
- `state.user` from login response or `/users/me`.

Because `state` is reactive, components update automatically when these fields change.

### Axios setup

`api` is a shared axios instance with base URL from `VITE_API_BASE_URL` (or localhost fallback).

Request interceptor:

- Adds `Authorization: Bearer <token>` if token exists.

### Auth functions

- `login(username, password)`
- `logout()`
- `loadProfile()`

### Derived booleans

- `isAuthenticated`
- `isAdmin`

These are consumed by both components and router guards.

---

## 7) View-by-view walkthrough

All views use `<script setup>` (Composition API style).

### `LoginView.vue`

- Uses `v-model` with reactive `form` object.
- `@submit.prevent` calls async login function.
- On success navigates to `/profile`; otherwise shows error.

### `ProfileView.vue`

- Runs `fetchProfile` on mount.
- Calls `/users/me`.
- Renders loading/data states with `v-if` branches.

### `SearchView.vue`

- Manages `query`, `results`, `loading`, `hasSearched`.
- Calls `/users/search?q=...`.
- Displays empty state only after first search.

### `ManageUsersView.vue` (admin)

- Handles list, create, edit, block/reactivate, delete.
- Single form supports create/edit based on `editingId`.
- Refreshes list after mutations.

---

## 8) Core Vue concepts translated for non-Vue developers

- `ref(x)` → mutable reactive value holder (`.value` in JS).
- `reactive(obj)` → proxy object with reactive fields.
- `computed(fn)` → memoized derived value.
- `onMounted(fn)` → lifecycle hook after mount.
- `v-model` → value binding + change wiring.
- `v-for` + `:key` → list rendering with stable identity.
- `v-if`/`v-else-if`/`v-else` → declarative branching in template.

---

## 9) End-to-end login flow

1. User submits login form.
2. `login()` posts to `/auth/login`.
3. Token + user are saved in reactive state and localStorage.
4. Router redirects to `/profile`.
5. Future API calls include Bearer token via interceptor.
6. Guards and menus react to `isAuthenticated`/`isAdmin`.

---

## 10) How to become proficient faster

Practical exercises:

1. Add a new protected route and view.
2. Extract user CRUD logic into a `useUsers` composable.
3. Add centralized axios error normalizer.
4. Add unit tests for router guard decisions.
5. Migrate one view to TypeScript.

If you can confidently trace auth state changes from `auth.js` to visible UI behavior in `App.vue` and router guards, you’re already operating at a professional Vue level.
