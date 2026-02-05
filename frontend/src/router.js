import { createRouter, createWebHistory } from 'vue-router';
import LoginView from './views/LoginView.vue';
import ManageUsersView from './views/ManageUsersView.vue';
import ProfileView from './views/ProfileView.vue';
import SearchView from './views/SearchView.vue';
import { isAdmin, isAuthenticated, loadProfile, state } from './services/auth';

const routes = [
  {
    path: '/',
    redirect: () => (isAuthenticated.value ? '/profile' : '/login')
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { requiresGuest: true }
  },
  {
    path: '/profile',
    name: 'profile',
    component: ProfileView,
    meta: { requiresAuth: true }
  },
  {
    path: '/search',
    name: 'search',
    component: SearchView,
    meta: { requiresAuth: true }
  },
  {
    path: '/users',
    name: 'users',
    component: ManageUsersView,
    meta: { requiresAuth: true, requiresAdmin: true }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to) => {
  if (isAuthenticated.value && !state.user) {
    await loadProfile();
  }

  if (to.meta.requiresAuth && !isAuthenticated.value) {
    return { path: '/login' };
  }

  if (to.meta.requiresGuest && isAuthenticated.value) {
    return { path: '/profile' };
  }

  if (to.meta.requiresAdmin && !isAdmin.value) {
    return { path: '/search' };
  }

  return true;
});

export default router;
