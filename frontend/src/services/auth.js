import { computed, reactive } from 'vue';
import axios from 'axios';

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const state = reactive({
  token: localStorage.getItem('authToken') || '',
  user: null
});

const api = axios.create({
  baseURL: apiBase
});

api.interceptors.request.use((config) => {
  if (state.token) {
    config.headers.Authorization = `Bearer ${state.token}`;
  }
  return config;
});

const setSession = (token, user) => {
  state.token = token;
  state.user = user;
  localStorage.setItem('authToken', token);
};

const clearSession = () => {
  state.token = '';
  state.user = null;
  localStorage.removeItem('authToken');
};

const login = async (username, password) => {
  const response = await api.post('/auth/login', { username, password });
  setSession(response.data.token, response.data.user);
  return response.data.user;
};

const logout = () => {
  clearSession();
};

const loadProfile = async () => {
  if (!state.token) {
    return null;
  }
  const response = await api.get('/users/me');
  state.user = response.data;
  return response.data;
};

const isAuthenticated = computed(() => Boolean(state.token));
const isAdmin = computed(() => state.user?.role === 'admin');

export { api, state, login, logout, loadProfile, isAuthenticated, isAdmin };
