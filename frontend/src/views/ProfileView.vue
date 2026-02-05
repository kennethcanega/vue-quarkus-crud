<template>
  <section class="card">
    <h2>My Profile</h2>
    <div v-if="loading" class="status">Loading...</div>
    <div v-else-if="profile" class="profile-grid">
      <div>
        <p class="label">Name</p>
        <p>{{ profile.name }}</p>
      </div>
      <div>
        <p class="label">Email</p>
        <p>{{ profile.email }}</p>
      </div>
      <div>
        <p class="label">Username</p>
        <p>{{ profile.username }}</p>
      </div>
      <div>
        <p class="label">Role</p>
        <p class="pill" :class="profile.role === 'admin' ? 'pill-success' : 'pill-neutral'">{{ profile.role }}</p>
      </div>
      <div>
        <p class="label">Status</p>
        <p class="pill" :class="profile.active ? 'pill-success' : 'pill-warning'">
          {{ profile.active ? 'Active' : 'Blocked' }}
        </p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { api } from '../services/auth';

const profile = ref(null);
const loading = ref(false);

const fetchProfile = async () => {
  loading.value = true;
  try {
    const response = await api.get('/users/me');
    profile.value = response.data;
  } finally {
    loading.value = false;
  }
};

onMounted(fetchProfile);
</script>
