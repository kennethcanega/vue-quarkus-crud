<template>
  <div class="container">
    <header>
      <h1>Users</h1>
      <p>Simple CRUD powered by Vue 3 + Quarkus + Postgres.</p>
    </header>

    <section class="card">
      <h2>{{ editingId ? 'Update User' : 'Create User' }}</h2>
      <form @submit.prevent="handleSubmit">
        <div class="field">
          <label for="name">Name</label>
          <input id="name" v-model="form.name" required placeholder="Jane Doe" />
        </div>
        <div class="field">
          <label for="email">Email</label>
          <input id="email" v-model="form.email" required type="email" placeholder="jane@example.com" />
        </div>
        <div class="actions">
          <button type="submit">{{ editingId ? 'Save' : 'Add' }}</button>
          <button type="button" class="secondary" @click="resetForm">Clear</button>
        </div>
      </form>
    </section>

    <section class="card">
      <h2>Existing Users</h2>
      <div v-if="loading" class="status">Loading...</div>
      <div v-else-if="errorMessage" class="status error">{{ errorMessage }}</div>
      <div v-else-if="users.length === 0" class="status">No users yet.</div>
      <table v-else>
        <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>{{ user.name }}</td>
            <td>{{ user.email }}</td>
            <td>
              <button class="secondary" @click="startEdit(user)">Edit</button>
              <button class="danger" @click="removeUser(user.id)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import axios from 'axios';

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const users = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const editingId = ref(null);
const form = reactive({
  name: '',
  email: ''
});

const fetchUsers = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    const response = await axios.get(`${apiBase}/users`);
    users.value = response.data;
  } catch (error) {
    errorMessage.value = 'Unable to load users. Check the backend connection.';
  } finally {
    loading.value = false;
  }
};

const handleSubmit = async () => {
  errorMessage.value = '';
  try {
    if (editingId.value) {
      await axios.put(`${apiBase}/users/${editingId.value}`, {
        name: form.name,
        email: form.email
      });
    } else {
      await axios.post(`${apiBase}/users`, {
        name: form.name,
        email: form.email
      });
    }
    await fetchUsers();
    resetForm();
  } catch (error) {
    errorMessage.value = 'Unable to save user. Check the backend connection.';
  }
};

const startEdit = (user) => {
  editingId.value = user.id;
  form.name = user.name;
  form.email = user.email;
};

const removeUser = async (id) => {
  errorMessage.value = '';
  try {
    await axios.delete(`${apiBase}/users/${id}`);
    await fetchUsers();
    if (editingId.value === id) {
      resetForm();
    }
  } catch (error) {
    errorMessage.value = 'Unable to delete user. Check the backend connection.';
  }
};

const resetForm = () => {
  editingId.value = null;
  form.name = '';
  form.email = '';
};

onMounted(fetchUsers);
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

.container {
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 1.5rem 3rem;
}

header {
  margin-bottom: 2rem;
}

h1 {
  margin: 0 0 0.25rem;
  font-size: 2rem;
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

input {
  border: 1px solid #d7dde5;
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  font-size: 1rem;
}

.actions {
  display: flex;
  gap: 0.75rem;
}

button {
  border: none;
  padding: 0.6rem 1rem;
  border-radius: 8px;
  background: #1d4ed8;
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
</style>
