# Frontend Guide (Vue 3 + Vue Router + Axios)

This file explains **how the frontend works**, with the goal of helping a programmer who is new to Vue understand the project structure and become productive quickly.

---

## 1) Mental model: how this app starts

Think of the frontend as a pipeline:

1. `index.html` provides a DOM node (`#app`).
2. `src/main.js` creates the Vue app.
3. `App.vue` is the root UI shell (header/nav/content area).
4. `router.js` decides which page component to render inside `<RouterView />`.
5. Each page (`LoginView`, `ProfileView`, `SearchView`, `ManageUsersView`) handles one use-case.
6. `services/auth.js` centralizes authentication state and API access.

If you keep this flow in mind, most Vue code in this project becomes easy to follow.

---

## 2) File-by-file architecture

## Entry point: `frontend/src/main.js`

```js
createApp(App).use(router).mount('#app');
```

Equivalent idea in other ecosystems:

- **React**: `createRoot(...).render(...)`
- **Angular**: bootstrap module
- **Server apps**: create app, attach middleware, mount routes

What happens here:

- `App.vue` becomes the root component.
- `router` plugin is installed so route components and `<RouterLink/>`/`<RouterView/>` work.
- The app mounts into `<div id="app"></div>`.

---

## Root component: `frontend/src/App.vue`

`App.vue` is a layout shell, not a “page” itself.

Key Vue concepts demonstrated:

- **Template directives**
  - `v-if="isAuthenticated"` → conditionally render sections.
  - `@click="handleLogout"` → event binding.
  - `{{ currentUser?.username }}` → interpolation (reactive values rendered in HTML).
- **Router primitives**
  - `<RouterLink to="/profile">` → declarative navigation.
  - `<RouterView />` → where the current route component is injected.
- **Reactivity and derived state**
  - `currentUser = computed(() => state.user)` creates reactive, derived data.

The component also runs initialization logic:

- In `onMounted`, if a token exists but user profile is missing (e.g., page refresh), it calls `loadProfile()`.
- `handleLogout()` clears auth and redirects to `/login`.

### Why this matters

If you understand `App.vue`, you understand the app shell pattern in Vue:

- global frame (header/nav)
- route outlet
- top-level session behavior

---

## Routing + authorization: `frontend/src/router.js`

Routes are defined as plain objects:

- `/login` (guest only)
- `/profile` (authenticated)
- `/search` (authenticated)
- `/users` (authenticated + admin)

The important part is the **global navigation guard**:

```js
router.beforeEach(async (to) => { ... })
```

This function runs before every route transition. It enforces access control using route metadata:

- `requiresAuth`
- `requiresGuest`
- `requiresAdmin`

Flow:

1. If token exists but profile not loaded, fetch profile.
2. If route needs auth and user is not logged in → redirect to `/login`.
3. If route is guest-only and user is logged in → redirect to `/profile`.
4. If route requires admin and user is not admin → redirect to `/search`.

### Why this matters

This is where frontend access control lives. Backend must still enforce permissions, but this gives good UX and keeps users away from invalid screens.

---

## Auth + API service: `frontend/src/services/auth.js`

This file is the frontend's mini auth store + API gateway.

## Reactive session state

```js
const state = reactive({ token: ..., user: null })
```

- `state.token` is initialized from `localStorage`.
- `state.user` holds current user profile.

Because it is reactive, components using this state update automatically when values change.

## Axios instance

`api = axios.create({ baseURL: ... })`

- Shared client for all API calls.
- `api.interceptors.request.use(...)` injects `Authorization: Bearer <token>` if token exists.

This is much cleaner than manually attaching headers in every request.

## Session helpers

- `setSession(token, user)` → update reactive state + persist token.
- `clearSession()` → remove both reactive and persistent auth state.

## Public auth functions

- `login(username, password)` → `POST /auth/login`, then save session.
- `logout()` → clear session.
- `loadProfile()` → `GET /users/me` to restore current user after refresh.

## Derived state

- `isAuthenticated = computed(() => Boolean(state.token))`
- `isAdmin = computed(() => state.user?.role === 'admin')`

These are reactive booleans used by components and router guards.

---

## 3) Understanding each view component

All page components use `<script setup>`, the Composition API style where top-level bindings are directly available in templates.

### `frontend/src/views/LoginView.vue`

Purpose: authenticate user.

- `form` is a reactive object (`username`, `password`).
- `handleLogin` calls `login()` from auth service.
- On success: route to `/profile`.
- On error: show user-friendly error.

Vue patterns shown:

- `v-model` for two-way input binding.
- `@submit.prevent` to stop full-page submit and run JS handler.
- `ref('')` for scalar reactive state (`errorMessage`).

---

### `frontend/src/views/ProfileView.vue`

Purpose: display currently authenticated user details.

- Uses `onMounted(fetchProfile)`.
- Calls `api.get('/users/me')`.
- Controls a `loading` state for UI feedback.

Vue patterns shown:

- Async lifecycle hooks.
- Conditional rendering (`v-if`, `v-else-if`) for loading/data states.

---

### `frontend/src/views/SearchView.vue`

Purpose: query users by name/email.

- `query`, `results`, `loading`, `hasSearched` are refs.
- `handleSearch` requests `GET /users/search?q=<query>`.
- Template shows status text for searching and empty result states.

Vue patterns shown:

- State machine style UI (idle/searching/results/empty).
- `v-for` list rendering with stable keys.

---

### `frontend/src/views/ManageUsersView.vue`

Purpose: admin CRUD operations.

Main states:

- `users` list
- `loading`
- `errorMessage`
- `editingId`
- `form` object

Main functions:

- `fetchUsers()` → load all users.
- `handleSubmit()` → create or update depending on `editingId`.
- `startEdit(user)` → fill form from selected row.
- `toggleStatus(user)` → block/reactivate.
- `removeUser(id)` → delete user.
- `resetForm()` → return form to create mode.

Key pattern: one form supports both **create** and **edit**.

- If `editingId` is null → create (`POST /users`).
- If set → update (`PUT /users/:id`).

This is a common enterprise UI pattern and worth mastering.

---

## 4) Vue concepts used in this codebase (quick translation)

If you know other frameworks, map these concepts:

- `ref(value)` → reactive box for primitives/arrays/objects.
  - Access/write in script via `.value`.
  - In templates, `.value` is auto-unwrapped.
- `reactive(object)` → deeply reactive object (no `.value` for fields).
- `computed(fn)` → cached derived value based on reactive dependencies.
- `onMounted(fn)` → lifecycle hook after component mounts.
- `v-model` → syntactic sugar for input value + update event.
- `v-if / v-else-if / v-else` → conditional template blocks.
- `v-for="item in items" :key="item.id"` → list rendering.
- `<script setup>` → concise Composition API component syntax.

---

## 5) End-to-end request flow example

Example: user logs in.

1. User types credentials in `LoginView` (`v-model` updates `form`).
2. Submit triggers `handleLogin`.
3. `handleLogin` calls `login(username, password)` from `auth.js`.
4. Backend returns token + user.
5. `setSession` updates reactive `state` and localStorage.
6. Router navigates to `/profile`.
7. `App.vue` and guarded routes react automatically because `isAuthenticated` changed.
8. Future `api` calls include Bearer token via interceptor.

If you can explain this flow from memory, you're already thinking in Vue app architecture terms.

---

## 6) How to become proficient using this project

Practical drills (high value):

1. **Trace reactivity manually**
   - Add `console.log` in `auth.js` (`setSession`, `clearSession`, `loadProfile`) and observe UI updates.
2. **Add one protected route**
   - Create `ReportsView.vue`, route meta `requiresAuth: true`, nav link in `App.vue`.
3. **Extract composables**
   - Move user CRUD logic from `ManageUsersView.vue` into `useUsers.js` (a composition function).
4. **Improve error handling**
   - Normalize Axios errors in one helper and use consistent error messaging across views.
5. **Type-safe next step**
   - Migrate selected files to TypeScript (`.ts` / `<script setup lang="ts">`) to strengthen maintainability.

These exercises reinforce Vue fundamentals: reactivity, routing, compositional architecture, and stateful UI.

---

## 7) Common pitfalls and how this code addresses them

- **Losing auth on refresh**
  - Token persists in localStorage; profile is reloaded through `loadProfile()`.
- **Forgotten auth headers**
  - Axios interceptor adds token automatically.
- **Unauthorized page access**
  - Router guard checks route metadata before navigation.
- **Template complexity growth**
  - Views are split by use-case (login, profile, search, admin management).

---

## 8) Suggested next refactors (when you want to level up)

- Use Pinia for larger, explicit global state management.
- Add reusable UI components (base button, form field, status banner).
- Add unit tests for auth service and router guard behavior.
- Add loading/error wrappers to standardize async UI patterns.
- Add optimistic updates for faster-feeling admin operations.

---

## 9) Quick glossary

- **Component**: self-contained UI + behavior unit (`*.vue`).
- **Composition API**: Vue API style using functions (`ref`, `reactive`, `computed`, hooks).
- **Router guard**: function that decides whether navigation is allowed.
- **Reactive state**: data that auto-updates the UI when it changes.
- **Interceptor**: request/response middleware around HTTP client calls.

---

If you read this once, then walk through each referenced file while running the app, you should be able to reason about new features confidently and implement non-trivial changes in Vue.
