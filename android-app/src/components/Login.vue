<script setup>
import { ref } from 'vue'
import { api } from '../api.js'

const emit = defineEmits(['login'])

const email = ref('')
const password = ref('')
const error = ref('')
const isRegistering = ref(false)
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  loading.value = true

  try {
    if (isRegistering.value) {
      await api.register(email.value, password.value)
    } else {
      await api.login(email.value, password.value)
    }
    emit('login')
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <h2>{{ isRegistering ? 'Create Account' : 'Sign In' }}</h2>
    
    <form @submit.prevent="handleSubmit">
      <div class="field">
        <label for="email">Email</label>
        <input 
          id="email"
          v-model="email" 
          type="email" 
          required
          autocomplete="email"
          placeholder="you@example.com"
        />
      </div>
      
      <div class="field">
        <label for="password">Password</label>
        <input 
          id="password"
          v-model="password" 
          type="password" 
          required
          autocomplete="current-password"
          placeholder="••••••••"
          minlength="6"
        />
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <button type="submit" class="submit-btn" :disabled="loading">
        {{ loading ? 'Please wait...' : (isRegistering ? 'Register' : 'Login') }}
      </button>
    </form>

    <p class="toggle">
      {{ isRegistering ? 'Already have an account?' : "Don't have an account?" }}
      <button @click="isRegistering = !isRegistering" class="link-btn">
        {{ isRegistering ? 'Sign in' : 'Register' }}
      </button>
    </p>
  </div>
</template>

<style scoped>
.login {
  max-width: 320px;
  margin: 2rem auto;
}

h2 {
  text-align: center;
  margin-bottom: 2rem;
  font-weight: 500;
}

form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

label {
  font-size: 0.875rem;
  color: var(--text-secondary);
}

input {
  padding: 0.875rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 1rem;
}

input:focus {
  outline: none;
  border-color: var(--accent-color);
}

input::placeholder {
  color: var(--text-muted);
}

.error {
  color: var(--danger-color);
  font-size: 0.875rem;
  text-align: center;
}

.submit-btn {
  padding: 1rem;
  background: var(--accent-color);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
  margin-top: 0.5rem;
}

.submit-btn:active:not(:disabled) {
  background: var(--accent-hover);
}

.submit-btn:disabled {
  opacity: 0.6;
}

.toggle {
  text-align: center;
  margin-top: 1.5rem;
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.link-btn {
  background: none;
  border: none;
  color: var(--accent-color);
  font-size: 0.875rem;
  text-decoration: underline;
}
</style>
