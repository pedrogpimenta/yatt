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
    const action = isRegistering.value ? api.register : api.login
    const data = await action(email.value, password.value)
    api.setToken(data.token)
    emit('login')
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function useWithoutAccount() {
  api.setLocalMode(true)
  emit('login')
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

    <div class="divider">
      <span>or</span>
    </div>

    <button @click="useWithoutAccount" class="local-btn">
      Use without account
    </button>
    <p class="local-hint">
      Data stays on this device. You can sync with other devices via QR code.
    </p>
  </div>
</template>

<style scoped>
.login {
  max-width: 360px;
  margin: 0 auto;
  padding: 2rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100vh;
}

h2 {
  text-align: center;
  margin-bottom: 0.5rem;
  font-weight: 600;
  font-size: 1.5rem;
}

.login::before {
  content: 'Time Command';
  display: block;
  text-align: center;
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--accent-color);
  margin-bottom: 2rem;
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
  padding: 0.75rem;
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
  padding: 0.875rem;
  background: var(--accent-color);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
  margin-top: 0.5rem;
}

.submit-btn:hover:not(:disabled) {
  background: var(--accent-hover);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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

.link-btn:hover {
  color: var(--accent-hover);
}

.divider {
  display: flex;
  align-items: center;
  margin: 1.5rem 0;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-color);
}

.divider span {
  padding: 0 1rem;
  color: var(--text-muted);
  font-size: 0.875rem;
}

.local-btn {
  width: 100%;
  padding: 0.875rem;
  background: var(--bg-secondary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
}

.local-btn:hover {
  background: var(--bg-tertiary);
  border-color: var(--border-light);
}

.local-hint {
  text-align: center;
  margin-top: 0.75rem;
  color: var(--text-muted);
  font-size: 0.75rem;
  line-height: 1.4;
}
</style>
