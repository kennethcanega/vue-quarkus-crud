<template>
  <section class="card auth-card">
    <h2>Login</h2>
    <p class="muted">Default admin: <strong>admin</strong> / <strong>admin</strong></p>
    <form @submit.prevent="handleLogin">
      <div class="field">
        <label for="username">Username</label>
        <input id="username" v-model="form.username" required autocomplete="username" />
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input id="password" type="password" v-model="form.password" required autocomplete="current-password" />
      </div>
      <button type="submit">Sign in</button>
      <p v-if="errorMessage" class="status error">{{ errorMessage }}</p>
    </form>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { login } from '../services/auth';

const router = useRouter();
const errorMessage = ref('');
const form = reactive({
  username: '',
  password: ''
});

const handleLogin = async () => {
  errorMessage.value = '';
  try {
    await login(form.username, form.password);
    router.push('/profile');
  } catch (error) {
    errorMessage.value = 'Invalid credentials or user is inactive.';
  }
};
</script>
