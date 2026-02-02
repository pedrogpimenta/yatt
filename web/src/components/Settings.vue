<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const emit = defineEmits(['close', 'logout'])

const user = ref(null)
const loading = ref(true)
const error = ref('')
const success = ref('')

// Password change form
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const changingPassword = ref(false)

// Token visibility
const showToken = ref(false)
const tokenCopied = ref(false)

const token = api.getToken()

async function fetchUser() {
  try {
    user.value = await api.getMe()
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function handleChangePassword() {
  error.value = ''
  success.value = ''
  
  if (!currentPassword.value || !newPassword.value || !confirmPassword.value) {
    error.value = 'All fields are required'
    return
  }
  
  if (newPassword.value !== confirmPassword.value) {
    error.value = 'New passwords do not match'
    return
  }
  
  if (newPassword.value.length < 6) {
    error.value = 'New password must be at least 6 characters'
    return
  }
  
  changingPassword.value = true
  
  try {
    await api.changePassword(currentPassword.value, newPassword.value)
    success.value = 'Password changed successfully'
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (err) {
    error.value = err.message
  } finally {
    changingPassword.value = false
  }
}

async function copyToken() {
  try {
    await navigator.clipboard.writeText(token)
    tokenCopied.value = true
    setTimeout(() => {
      tokenCopied.value = false
    }, 2000)
  } catch (err) {
    error.value = 'Failed to copy token'
  }
}

function handleLogout() {
  emit('logout')
}

onMounted(() => {
  fetchUser()
})
</script>

<template>
  <div class="settings-overlay" @click.self="emit('close')">
    <div class="settings-modal">
      <div class="settings-header">
        <h2>Settings</h2>
        <button class="close-btn" @click="emit('close')">&times;</button>
      </div>
      
      <div class="settings-content">
        <!-- Account Info -->
        <section class="settings-section">
          <h3>Account</h3>
          <div v-if="loading" class="loading">Loading...</div>
          <div v-else-if="user" class="account-info">
            <div class="info-row">
              <span class="label">Email</span>
              <span class="value">{{ user.email }}</span>
            </div>
            <div class="info-row">
              <span class="label">Member since</span>
              <span class="value">{{ new Date(user.created_at).toLocaleDateString() }}</span>
            </div>
          </div>
        </section>

        <!-- Auth Token -->
        <section class="settings-section">
          <h3>Auth Token</h3>
          <p class="section-description">Use this token to authenticate the KDE widget or other clients.</p>
          <div class="token-container">
            <input 
              :type="showToken ? 'text' : 'password'" 
              :value="token" 
              readonly 
              class="token-input"
            />
            <button @click="showToken = !showToken" class="token-btn">
              {{ showToken ? 'Hide' : 'Show' }}
            </button>
            <button @click="copyToken" class="token-btn primary">
              {{ tokenCopied ? 'Copied!' : 'Copy' }}
            </button>
          </div>
        </section>

        <!-- Change Password -->
        <section class="settings-section">
          <h3>Change Password</h3>
          <form @submit.prevent="handleChangePassword" class="password-form">
            <div class="form-group">
              <label>Current Password</label>
              <input 
                v-model="currentPassword" 
                type="password" 
                placeholder="Enter current password"
              />
            </div>
            <div class="form-group">
              <label>New Password</label>
              <input 
                v-model="newPassword" 
                type="password" 
                placeholder="Enter new password"
              />
            </div>
            <div class="form-group">
              <label>Confirm New Password</label>
              <input 
                v-model="confirmPassword" 
                type="password" 
                placeholder="Confirm new password"
              />
            </div>
            <button type="submit" class="submit-btn" :disabled="changingPassword">
              {{ changingPassword ? 'Changing...' : 'Change Password' }}
            </button>
          </form>
        </section>

        <!-- Messages -->
        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="success" class="success">{{ success }}</p>

        <!-- Logout -->
        <section class="settings-section logout-section">
          <button @click="handleLogout" class="logout-btn">Logout</button>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.settings-modal {
  background: var(--bg-primary);
  border-radius: 12px;
  width: 90%;
  max-width: 480px;
  max-height: 90vh;
  overflow-y: auto;
}

.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
}

.settings-header h2 {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.close-btn:hover {
  color: var(--text-primary);
}

.settings-content {
  padding: 1.5rem;
}

.settings-section {
  margin-bottom: 1.5rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid var(--border-color);
}

.settings-section:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.settings-section h3 {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.75rem;
}

.section-description {
  font-size: 0.875rem;
  color: var(--text-muted);
  margin-bottom: 0.75rem;
}

.account-info {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-row .label {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.info-row .value {
  color: var(--text-primary);
  font-weight: 500;
}

.token-container {
  display: flex;
  gap: 0.5rem;
}

.token-input {
  flex: 1;
  padding: 0.625rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 0.75rem;
  font-family: monospace;
}

.token-btn {
  padding: 0.625rem 0.875rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}

.token-btn:hover {
  border-color: var(--border-light);
  color: var(--text-primary);
}

.token-btn.primary {
  background: var(--accent-color);
  border-color: var(--accent-color);
  color: #fff;
}

.token-btn.primary:hover {
  background: var(--accent-hover);
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.form-group label {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.form-group input {
  padding: 0.625rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 0.875rem;
}

.form-group input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.submit-btn {
  padding: 0.75rem;
  background: var(--accent-color);
  border: none;
  border-radius: 6px;
  color: #fff;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  margin-top: 0.5rem;
}

.submit-btn:hover {
  background: var(--accent-hover);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  color: var(--danger-color);
  font-size: 0.875rem;
  text-align: center;
  margin-bottom: 1rem;
}

.success {
  color: var(--success-color);
  font-size: 0.875rem;
  text-align: center;
  margin-bottom: 1rem;
}

.logout-section {
  text-align: center;
}

.logout-btn {
  width: 100%;
  padding: 0.75rem;
  background: transparent;
  border: 1px solid var(--danger-color);
  border-radius: 6px;
  color: var(--danger-color);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
}

.logout-btn:hover {
  background: var(--danger-color);
  color: #fff;
}

.loading {
  color: var(--text-muted);
  font-size: 0.875rem;
}
</style>
