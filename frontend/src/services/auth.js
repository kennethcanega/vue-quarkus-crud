import { computed, reactive } from 'vue';
import axios from 'axios';

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const refreshIntervalMs = Number(import.meta.env.VITE_AUTH_REFRESH_INTERVAL_MS || 5000);

const state = reactive({
  token: localStorage.getItem('authToken') || '',
  user: null
});

let refreshTimerId = null;
let refreshInFlight = null;

const api = axios.create({
  baseURL: apiBase,
  withCredentials: true
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

const stopRefreshScheduler = () => {
  if (refreshTimerId) {
    window.clearInterval(refreshTimerId);
    refreshTimerId = null;
  }
};

const refreshToken = async () => {
  if (!state.token) {
    return null;
  }
  if (!refreshInFlight) {
    refreshInFlight = api
      .post('/auth/refresh')
      .then((response) => {
        setSession(response.data.token, response.data.user);
        return response.data;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
};

const forceLogout = async () => {
  stopRefreshScheduler();
  clearSession();
};

const startRefreshScheduler = () => {
  stopRefreshScheduler();
  if (!state.token || Number.isNaN(refreshIntervalMs) || refreshIntervalMs <= 0) {
    return;
  }
  refreshTimerId = window.setInterval(async () => {
    try {
      await refreshToken();
    } catch (error) {
      await forceLogout();
    }
  }, refreshIntervalMs);
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && state.token && !originalRequest?._retry) {
      try {
        originalRequest._retry = true;
        await refreshToken();
        originalRequest.headers.Authorization = `Bearer ${state.token}`;
        return api(originalRequest);
      } catch (refreshError) {
        await forceLogout();
      }
    }
    return Promise.reject(error);
  }
);

const login = async (username, password) => {
  const response = await api.post('/auth/login', { username, password });
  setSession(response.data.token, response.data.user);
  startRefreshScheduler();
  return response.data.user;
};

const logout = async () => {
  try {
    await api.post('/auth/logout');
  } finally {
    await forceLogout();
  }
};

const loadProfile = async () => {
  if (!state.token) {
    return null;
  }
  const response = await api.get('/users/me');
  state.user = response.data;
  return response.data;
};

const initializeAuth = async () => {
  if (!state.token) {
    return;
  }
  startRefreshScheduler();
  try {
    await refreshToken();
    await loadProfile();
  } catch (error) {
    await forceLogout();
  }
};

const isAuthenticated = computed(() => Boolean(state.token));
const isAdmin = computed(() => state.user?.role === 'admin');

export {
  api,
  forceLogout,
  initializeAuth,
  isAdmin,
  isAuthenticated,
  loadProfile,
  login,
  logout,
  refreshIntervalMs,
  startRefreshScheduler,
  state,
  stopRefreshScheduler
};
