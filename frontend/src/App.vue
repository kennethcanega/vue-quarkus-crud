<template>
  <div class="app-shell">
    <header class="top-bar">
      <div class="brand">
        <div class="brand-mark">QD</div>
        <div>
          <h1>Quarkus Directory</h1>
          <p>Professional user operations with secure role-based access.</p>
        </div>
      </div>
      <div class="auth-actions" v-if="isAuthenticated">
        <span class="pill pill-neutral">{{ currentUser?.username }}</span>
        <button class="secondary" @click="handleLogout">Logout</button>
      </div>
    </header>

    <nav v-if="isAuthenticated" class="menu">
      <RouterLink to="/profile">My Profile</RouterLink>
      <RouterLink to="/search">Directory Search</RouterLink>
      <RouterLink v-if="isAdmin" to="/users">Administration</RouterLink>
    </nav>

    <main class="container">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { initializeAuth, isAdmin, isAuthenticated, logout, state } from './services/auth';

const router = useRouter();
const currentUser = computed(() => state.user);

const handleLogout = async () => {
  await logout();
  router.push('/login');
};

onMounted(async () => {
  await initializeAuth();
});
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f4f7fc 0%, #edf2f9 100%);
  color: #10233c;
  font-family: 'Inter', 'Segoe UI', sans-serif;
}

.container {
  max-width: 1100px;
  width: 100%;
  margin: 0 auto;
  padding: 2rem 1.5rem 2.5rem;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  background: rgba(11, 32, 58, 0.95);
  color: #f8fbff;
  box-shadow: 0 14px 32px rgba(11, 32, 58, 0.25);
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.9rem;
}

.brand h1 {
  margin: 0;
  font-size: 1.45rem;
}

.brand p {
  margin: 0.2rem 0 0;
  color: #b7c9e6;
  font-size: 0.9rem;
}

.brand-mark {
  width: 2.3rem;
  height: 2.3rem;
  border-radius: 0.65rem;
  display: grid;
  place-content: center;
  font-weight: 700;
  background: linear-gradient(145deg, #4f8ef7, #1f5fc8);
}

.menu {
  display: flex;
  gap: 0.8rem;
  padding: 0.8rem 1.5rem;
  background: #ffffff;
  border-bottom: 1px solid #d8e0ec;
}

.menu a {
  text-decoration: none;
  color: #1d3557;
  font-weight: 600;
  border-radius: 999px;
  padding: 0.45rem 0.95rem;
}

.menu a.router-link-active {
  background: #e7f0ff;
  color: #1f5fc8;
}

.auth-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

:global(.card) {
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 12px 28px rgba(15, 39, 68, 0.08);
  border: 1px solid #deE7f2;
  padding: 1.4rem;
  margin-bottom: 1.1rem;
}

:global(.field) {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 1rem;
}

:global(.grid) {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

:global(.actions) {
  display: flex;
  gap: 0.65rem;
  flex-wrap: wrap;
}

:global(input),
:global(select) {
  border: 1px solid #c7d5e6;
  border-radius: 10px;
  padding: 0.64rem 0.75rem;
  font-size: 0.95rem;
  color: #10233c;
  background: #fbfdff;
}

:global(button) {
  border: none;
  padding: 0.6rem 0.95rem;
  border-radius: 10px;
  background: linear-gradient(150deg, #2669de, #1d54b1);
  color: #ffffff;
  font-weight: 600;
  cursor: pointer;
}

:global(button.secondary) {
  background: #e6edf7;
  color: #1b365d;
}

:global(button.danger) {
  background: linear-gradient(150deg, #d94242, #bb2b2b);
}

:global(table) {
  width: 100%;
  border-collapse: collapse;
}

:global(th),
:global(td) {
  text-align: left;
  padding: 0.75rem 0.5rem;
  border-bottom: 1px solid #e0e7f1;
}

:global(.status) {
  color: #5a6f8d;
}

:global(.status.error) {
  color: #c62828;
}

:global(.pill) {
  display: inline-flex;
  align-items: center;
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  font-size: 0.83rem;
  font-weight: 600;
}

:global(.pill-success) {
  background: #ddf8e8;
  color: #17673a;
}

:global(.pill-warning) {
  background: #fff0ca;
  color: #8a5a12;
}

:global(.pill-neutral) {
  background: #e7edf6;
  color: #264870;
}

:global(.muted) {
  color: #5d7393;
}
</style>
