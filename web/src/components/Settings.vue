<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { api } from '../api.js'
import { preferences } from '../preferences.js'
import * as offlineStorage from '../offlineStorage.js'
import DeviceSync from './DeviceSync.vue'

const emit = defineEmits(['close', 'logout'])

function handleKeydown(e) {
  if (e.key === 'Escape') {
    e.stopPropagation()
    if (showLogoutConfirm.value) {
      showLogoutConfirm.value = false
    } else if (showSyncModal.value) {
      showSyncModal.value = false
    } else {
      emit('close')
    }
  }
}

const user = ref(null)
const loading = ref(true)
const error = ref('')
const success = ref('')

// Local mode detection
const isLocalMode = computed(() => api.isLocalMode())
const showSyncModal = ref(false)
const showLogoutConfirm = ref(false)

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
  if (isLocalMode.value) {
    loading.value = false
    return
  }
  try {
    user.value = await api.getMe()
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

watch(() => preferences.dayStartHour, async (newValue, oldValue) => {
  if (isLocalMode.value) {
    return
  }
  if (newValue === oldValue || typeof newValue !== 'number') {
    return
  }
  try {
    await api.updateUserPreferences({ dayStartHour: newValue })
  } catch (err) {
    error.value = err.message
  }
})

function handleSynced() {
  showSyncModal.value = false
  success.value = 'Devices synced successfully!'
  // Reload to show the new timers
  setTimeout(() => {
    window.location.reload()
  }, 500)
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

// CSV Export
function formatDurationForCSV(ms) {
  if (!ms || ms < 0) return ''
  const hours = Math.floor(ms / 3600000)
  const minutes = Math.floor((ms % 3600000) / 60000)
  const seconds = Math.floor((ms % 60000) / 1000)
  return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function escapeCSV(value) {
  if (value === null || value === undefined) return ''
  const str = String(value)
  if (str.includes(',') || str.includes('"') || str.includes('\n')) {
    return `"${str.replace(/"/g, '""')}"`
  }
  return str
}

async function downloadCSV() {
  try {
    const timers = await offlineStorage.getAllTimers()
    
    const headers = ['ID', 'Tag', 'Start Time', 'End Time', 'Duration']
    const rows = [headers.join(',')]
    
    timers.sort((a, b) => new Date(b.start_time) - new Date(a.start_time))
    
    for (const timer of timers) {
      const start = new Date(timer.start_time)
      const end = timer.end_time ? new Date(timer.end_time) : null
      const duration = end ? end.getTime() - start.getTime() : null
      
      const row = [
        escapeCSV(timer.id),
        escapeCSV(timer.tag || ''),
        escapeCSV(timer.start_time),
        escapeCSV(timer.end_time || ''),
        escapeCSV(formatDurationForCSV(duration))
      ]
      rows.push(row.join(','))
    }
    
    const csv = rows.join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `yatt-export-${new Date().toISOString().split('T')[0]}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = 'Failed to export CSV: ' + err.message
  }
}

function handleLogout() {
  if (isLocalMode.value) {
    showLogoutConfirm.value = true
  } else {
    emit('logout')
  }
}

function confirmLogout() {
  showLogoutConfirm.value = false
  emit('logout')
}

function cancelLogout() {
  showLogoutConfirm.value = false
}

onMounted(() => {
  fetchUser()
  window.addEventListener('keydown', handleKeydown, true)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown, true)
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
        <!-- Display Preferences -->
        <section class="settings-section">
          <h3>Display</h3>
          <div class="preference-row">
            <span class="label">Date Format</span>
            <select v-model="preferences.dateFormat" class="preference-select">
              <option value="dd/mm/yyyy">DD/MM/YYYY</option>
              <option value="mm/dd/yyyy">MM/DD/YYYY</option>
            </select>
          </div>
          <div class="preference-row">
            <span class="label">Time Format</span>
            <select v-model="preferences.timeFormat" class="preference-select">
              <option value="24h">24-hour (14:30)</option>
              <option value="12h">12-hour (2:30 PM)</option>
            </select>
          </div>
          <div class="preference-row">
            <span class="label">Day Starts At</span>
            <select v-model.number="preferences.dayStartHour" class="preference-select">
              <option v-for="hour in 24" :key="hour - 1" :value="hour - 1">
                {{ String(hour - 1).padStart(2, '0') }}:00{{ hour - 1 === 0 ? ' (midnight)' : '' }}
              </option>
            </select>
          </div>
          <p class="preference-hint">
            Set when your "day" starts for time tracking. Useful if you often work past midnight.
          </p>
        </section>

        <!-- Local Mode Info & Sync -->
        <section v-if="isLocalMode" class="settings-section">
          <h3>Local Mode</h3>
          <p class="section-description">
            You're using the app without an account. Data is stored locally on this device.
          </p>
          <button @click="showSyncModal = true" class="sync-btn">
            Sync with another device
          </button>
        </section>

        <!-- Export Data -->
        <section class="settings-section">
          <h3>Export Data</h3>
          <p class="section-description">
            Download your timer data as a CSV file for use in spreadsheets.
          </p>
          <button @click="downloadCSV" class="export-btn">
            Download CSV
          </button>
        </section>

        <!-- Account Info (only for logged in users) -->
        <section v-if="!isLocalMode" class="settings-section">
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

        <!-- Auth Token (only for logged in users) -->
        <section v-if="!isLocalMode" class="settings-section">
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

        <!-- Change Password (only for logged in users) -->
        <section v-if="!isLocalMode" class="settings-section">
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
          <button @click="handleLogout" class="logout-btn">
            {{ isLocalMode ? 'Exit Local Mode' : 'Logout' }}
          </button>
        </section>
      </div>

    </div>
  </div>

  <!-- Sync Modal (outside settings overlay) -->
  <DeviceSync 
    v-if="showSyncModal" 
    @close="showSyncModal = false"
    @synced="handleSynced"
  />

  <!-- Logout Confirmation Modal -->
  <div v-if="showLogoutConfirm" class="confirm-overlay" @click.self="cancelLogout">
    <div class="confirm-modal">
      <h3>Exit Local Mode?</h3>
      <p class="confirm-warning">
        All your timers and data stored on this device will be permanently deleted. 
        This action cannot be undone.
      </p>
      <p class="confirm-hint">
        If you want to keep your data, sync with another device first or create an account.
      </p>
      <div class="confirm-actions">
        <button @click="cancelLogout" class="btn-cancel">Cancel</button>
        <button @click="confirmLogout" class="btn-danger">Delete All Data & Exit</button>
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

.preference-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.preference-row:last-child {
  margin-bottom: 0;
}

.preference-row .label {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.preference-select {
  padding: 0.5rem 0.75rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 0.875rem;
  cursor: pointer;
}

.preference-select:focus {
  outline: none;
  border-color: var(--accent-color);
}

.preference-hint {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-top: 0.75rem;
  line-height: 1.4;
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

.sync-btn {
  width: 100%;
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

.sync-btn:hover {
  background: var(--accent-hover);
}

.export-btn {
  width: 100%;
  padding: 0.75rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  margin-top: 0.5rem;
}

.export-btn:hover {
  background: var(--bg-tertiary);
  border-color: var(--border-light);
}

/* Confirmation Modal */
.confirm-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.confirm-modal {
  background: var(--bg-primary);
  border-radius: 12px;
  padding: 1.5rem;
  width: 90%;
  max-width: 400px;
}

.confirm-modal h3 {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 1rem;
}

.confirm-warning {
  color: var(--danger-color);
  font-size: 0.875rem;
  line-height: 1.5;
  margin-bottom: 0.75rem;
}

.confirm-hint {
  color: var(--text-muted);
  font-size: 0.8125rem;
  line-height: 1.5;
  margin-bottom: 1.5rem;
}

.confirm-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
}

.btn-cancel {
  padding: 0.625rem 1rem;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
}

.btn-cancel:hover {
  border-color: var(--border-light);
  color: var(--text-primary);
}

.btn-danger {
  padding: 0.625rem 1rem;
  background: var(--danger-color);
  border: none;
  border-radius: 6px;
  color: #fff;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
}

.btn-danger:hover {
  background: var(--danger-hover);
}
</style>
