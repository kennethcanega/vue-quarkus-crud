<template>
  <section class="card">
    <h2>User Search</h2>
    <form class="search-form" @submit.prevent="handleSearch">
      <input v-model="query" placeholder="Search by name or email" />
      <button type="submit">Search</button>
    </form>
    <div v-if="loading" class="status">Searching...</div>
    <div v-else-if="results.length === 0 && hasSearched" class="status">No results.</div>
    <ul v-else class="search-results">
      <li v-for="result in results" :key="result.id">
        <strong>{{ result.name }}</strong>
        <span>{{ result.email }}</span>
      </li>
    </ul>
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { api } from '../services/auth';

const query = ref('');
const results = ref([]);
const loading = ref(false);
const hasSearched = ref(false);

const handleSearch = async () => {
  loading.value = true;
  hasSearched.value = true;
  try {
    const response = await api.get('/users/search', { params: { q: query.value } });
    results.value = response.data;
  } finally {
    loading.value = false;
  }
};
</script>
