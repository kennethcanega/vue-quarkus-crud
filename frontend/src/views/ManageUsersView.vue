<template>
  <section class="card">
    <h2>{{ editingId ? 'Update User' : 'Create User' }}</h2>
    <form @submit.prevent="handleSubmit">
      <div class="grid">
        <div class="field">
          <label for="name">Name</label>
          <input id="name" v-model="form.name" required />
        </div>
        <div class="field">
          <label for="email">Email</label>
          <input id="email" v-model="form.email" required type="email" />
        </div>
        <div class="field">
          <label for="username">Username</label>
          <input id="username" v-model="form.username" required />
        </div>
        <div class="field">
          <label for="role">Role</label>
          <select id="role" v-model="form.role">
            <option value="user">Regular user</option>
            <option value="admin">Admin</option>
          </select>
        </div>
        <div class="field">
          <label for="password">Password</label>
          <input id="password" v-model="form.password" type="password" :placeholder="editingId ? 'Leave blank to keep' : 'Set password'" />
        </div>
        <div class="field checkbox">
          <label>
            <input type="checkbox" v-model="form.active" />
            Active account
          </label>
        </div>
      </div>
      <div class="actions">
        <button type="submit">{{ editingId ? 'Save changes' : 'Create user' }}</button>
        <button type="button" class="secondary" @click="resetForm">Clear</button>
      </div>
      <p v-if="errorMessage" class="status error">{{ errorMessage }}</p>
    </form>
  </section>

  <section class="card">
    <h2>Manage Users</h2>
    <div v-if="loading" class="status">Loading...</div>
    <div v-else-if="users.length === 0" class="status">No users found.</div>
    <table v-else>
      <thead>
        <tr>
          <th>Name</th>
          <th>Email</th>
          <th>Username</th>
          <th>Role</th>
          <th>Status</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.id">
          <td>{{ user.name }}</td>
          <td>{{ user.email }}</td>
          <td>{{ user.username }}</td>
          <td>{{ user.role }}</td>
          <td>
            <span :class="['pill', user.active ? 'pill-success' : 'pill-warning']">
              {{ user.active ? 'Active' : 'Blocked' }}
            </span>
          </td>
          <td class="actions">
            <button class="secondary" @click="startEdit(user)">Edit</button>
            <button class="secondary" @click="toggleStatus(user)">
              {{ user.active ? 'Block' : 'Reactivate' }}
            </button>
            <button class="danger" @click="removeUser(user.id)">Delete</button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { api } from '../services/auth';

const users = ref([]);
const loading = ref(false);
const errorMessage = ref('');
const editingId = ref(null);
const form = reactive({
  name: '',
  email: '',
  username: '',
  role: 'user',
  password: '',
  active: true
});

const fetchUsers = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    const response = await api.get('/users');
    users.value = response.data;
  } catch (error) {
    errorMessage.value = 'Unable to load users.';
  } finally {
    loading.value = false;
  }
};

const handleSubmit = async () => {
  errorMessage.value = '';
  const payload = {
    name: form.name,
    email: form.email,
    username: form.username,
    role: form.role,
    active: form.active
  };
  if (form.password) {
    payload.password = form.password;
  }
  try {
    if (editingId.value) {
      await api.put(`/users/${editingId.value}`, payload);
    } else {
      await api.post('/users', payload);
    }
    await fetchUsers();
    resetForm();
  } catch (error) {
    errorMessage.value = 'Unable to save user.';
  }
};

const startEdit = (user) => {
  editingId.value = user.id;
  form.name = user.name;
  form.email = user.email;
  form.username = user.username;
  form.role = user.role;
  form.active = user.active;
  form.password = '';
};

const toggleStatus = async (user) => {
  errorMessage.value = '';
  try {
    await api.put(`/users/${user.id}`, { active: !user.active });
    await fetchUsers();
  } catch (error) {
    errorMessage.value = 'Unable to update status.';
  }
};

const removeUser = async (id) => {
  errorMessage.value = '';
  try {
    await api.delete(`/users/${id}`);
    await fetchUsers();
    if (editingId.value === id) {
      resetForm();
    }
  } catch (error) {
    errorMessage.value = 'Unable to delete user.';
  }
};

const resetForm = () => {
  editingId.value = null;
  form.name = '';
  form.email = '';
  form.username = '';
  form.role = 'user';
  form.password = '';
  form.active = true;
};

onMounted(fetchUsers);
</script>
