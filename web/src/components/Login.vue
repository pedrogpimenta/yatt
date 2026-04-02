<script setup>
import { ref } from 'vue'
import { api } from '../api.js'

const props = defineProps({
  sessionMessage: {
    type: String,
    default: ''
  },
  resetToken: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['login'])

// view: 'login' | 'register' | 'forgot' | 'reset'
const view = ref(props.resetToken ? 'reset' : 'login')

const email = ref('')
const password = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')
const successMessage = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  loading.value = true

  try {
    const action = view.value === 'register' ? api.register : api.login
    const data = await action(email.value, password.value)
    api.setToken(data.token)
    emit('login')
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function handleForgotPassword() {
  error.value = ''
  successMessage.value = ''
  loading.value = true
  try {
    const data = await api.forgotPassword(email.value)
    successMessage.value = data.message
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function handleResetPassword() {
  error.value = ''
  if (newPassword.value !== confirmPassword.value) {
    error.value = 'Passwords do not match'
    return
  }
  loading.value = true
  try {
    await api.resetPassword(props.resetToken, newPassword.value)
    successMessage.value = 'Password reset successfully. You can now sign in.'
    view.value = 'login'
    // Clean the token from the URL without reloading
    const url = new URL(window.location.href)
    url.searchParams.delete('reset_token')
    window.history.replaceState({}, '', url.toString())
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function switchView(v) {
  view.value = v
  error.value = ''
  successMessage.value = ''
}

function useWithoutAccount() {
  api.setLocalMode(true)
  emit('login')
}

async function connectWithDropbox() {
  loading.value = true
  error.value = ''
  try {
    const { url } = await api.getDropboxAuthUrl()
    window.location.href = url
  } catch (err) {
    error.value = err.message
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="app-title">
      <img src="/favicon.png" alt="" class="app-title-icon" />
      Time Command
    </div>

    <!-- Reset password view (accessed via email link) -->
    <template v-if="view === 'reset'">
      <h2>Set New Password</h2>
      <form @submit.prevent="handleResetPassword">
        <div class="field">
          <label for="new-password">New Password</label>
          <input
            id="new-password"
            v-model="newPassword"
            type="password"
            required
            placeholder="••••••••"
            minlength="6"
          />
        </div>
        <div class="field">
          <label for="confirm-password">Confirm Password</label>
          <input
            id="confirm-password"
            v-model="confirmPassword"
            type="password"
            required
            placeholder="••••••••"
            minlength="6"
          />
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" class="submit-btn" :disabled="loading">
          {{ loading ? 'Please wait...' : 'Reset Password' }}
        </button>
      </form>
    </template>

    <!-- Forgot password view -->
    <template v-else-if="view === 'forgot'">
      <h2>Forgot Password</h2>
      <p class="hint">Enter your email and we'll send you a reset link.</p>
      <form @submit.prevent="handleForgotPassword">
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
        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="successMessage" class="success">{{ successMessage }}</p>
        <button type="submit" class="submit-btn" :disabled="loading || !!successMessage">
          {{ loading ? 'Please wait...' : 'Send Reset Link' }}
        </button>
      </form>
      <p class="toggle">
        <button @click="switchView('login')" class="link-btn">Back to Sign In</button>
      </p>
    </template>

    <!-- Login / Register view -->
    <template v-else>
      <h2>{{ view === 'register' ? 'Create Account' : 'Sign In' }}</h2>
      <p v-if="props.sessionMessage" class="session-message">{{ props.sessionMessage }}</p>
      <p v-if="successMessage" class="success">{{ successMessage }}</p>

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
          {{ loading ? 'Please wait...' : (view === 'register' ? 'Register' : 'Login') }}
        </button>
      </form>

      <p v-if="view === 'login'" class="forgot">
        <button @click="switchView('forgot')" class="link-btn">Forgot password?</button>
      </p>

      <p class="toggle">
        {{ view === 'register' ? 'Already have an account?' : "Don't have an account?" }}
        <button @click="switchView(view === 'register' ? 'login' : 'register')" class="link-btn">
          {{ view === 'register' ? 'Sign in' : 'Register' }}
        </button>
      </p>

      <div class="divider">
        <span>or</span>
      </div>

      <button @click="connectWithDropbox" class="dropbox-btn" :disabled="loading">
        Continue with Dropbox
      </button>
      <p class="local-hint">
        Your data syncs automatically to your own Dropbox. No account on this server needed.
      </p>

      <button @click="useWithoutAccount" class="local-btn" style="margin-top: 0.75rem">
        Use without account
      </button>
      <p class="local-hint">
        Data stays on this device. You can sync with other devices via QR code.
      </p>
    </template>
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

.app-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--accent-color);
  margin-bottom: 2rem;
}

.app-title-icon {
  width: 2.5rem;
  height: 2.5rem;
  object-fit: contain;
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

.session-message {
  margin-bottom: 1rem;
  padding: 0.75rem 1rem;
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.1);
  color: var(--danger-color);
  font-size: 0.875rem;
  text-align: center;
  line-height: 1.4;
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

.dropbox-btn {
  width: 100%;
  padding: 0.875rem;
  background: #0061ff;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
}

.dropbox-btn:hover:not(:disabled) {
  background: #0052d9;
}

.dropbox-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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

.hint {
  text-align: center;
  color: var(--text-secondary);
  font-size: 0.875rem;
  margin-bottom: 1rem;
}

.forgot {
  text-align: center;
  margin-top: 0.75rem;
  font-size: 0.875rem;
}

.success {
  color: #16a34a;
  font-size: 0.875rem;
  text-align: center;
}
</style>
