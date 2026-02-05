<template>
  <div class="app-shell">
    <header class="top-bar">
      <div>
        <h1>Team Directory</h1>
        <p>Secure user management with role-based access.</p>
      </div>
      <div class="auth-actions" v-if="isAuthenticated">
        <span class="pill pill-neutral">{{ currentUser?.username }}</span>
        <button class="secondary" @click="handleLogout">Logout</button>
      </div>
    </header>

    <nav v-if="isAuthenticated" class="menu">
      <RouterLink to="/profile">My Profile</RouterLink>
      <RouterLink to="/search">Search</RouterLink>
      <RouterLink v-if="isAdmin" to="/users">Manage Users</RouterLink>
    </nav>

    <main class="container">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { isAdmin, isAuthenticated, loadProfile, logout, state } from './services/auth';

const router = useRouter();
const currentUser = computed(() => state.user);

const handleLogout = () => {
  logout();
  router.push('/login');
};

onMounted(async () => {
  if (isAuthenticated.value && !state.user) {
    await loadProfile();
  }
});
</script>

<style scoped>
:root {
  color-scheme: light;
}

body {
  margin: 0;
  font-family: 'Inter', system-ui, sans-serif;
  background: #f5f7fb;
}

.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 1.5rem;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem;
  background: #0f172a;
  color: #f8fafc;
}

.top-bar h1 {
  margin: 0 0 0.25rem;
  font-size: 1.75rem;
}

.top-bar p {
  margin: 0;
  color: #cbd5f5;
}

.menu {
  display: flex;
  gap: 1rem;
  padding: 0.75rem 1.5rem;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
}

.menu a {
  text-decoration: none;
  color: #1e293b;
  font-weight: 600;
}

.menu a.router-link-active {
  color: #2563eb;
}

.auth-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.card {
  background: #ffffff;
  padding: 1.5rem;
  border-radius: 12px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
  margin-bottom: 1.5rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 1rem;
}

.grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.checkbox {
  align-items: center;
}

input,
select {
  border: 1px solid #d7dde5;
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  font-size: 1rem;
}

.actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

button {
  border: none;
  padding: 0.6rem 1rem;
  border-radius: 8px;
  background: #2563eb;
  color: #ffffff;
  font-weight: 600;
  cursor: pointer;
}

button.secondary {
  background: #e2e8f0;
  color: #1f2937;
}

button.danger {
  background: #dc2626;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 0.75rem 0.5rem;
  border-bottom: 1px solid #e2e8f0;
}

.status {
  color: #64748b;
}

.status.error {
  color: #dc2626;
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: 600;
}

.pill-success {
  background: #dcfce7;
  color: #166534;
}

.pill-warning {
  background: #fef3c7;
  color: #92400e;
}

.pill-neutral {
  background: #e2e8f0;
  color: #1f2937;
}

.auth-card {
  max-width: 420px;
  margin: 2rem auto;
}

.muted {
  color: #64748b;
  margin-bottom: 1rem;
}

.search-form {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.search-results {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 0.75rem;
}

.search-results li {
  background: #f8fafc;
  padding: 0.75rem;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
}

.profile-grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
}

.label {
  color: #94a3b8;
  font-size: 0.85rem;
  margin: 0 0 0.25rem;
}
</style>
