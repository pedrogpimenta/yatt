<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api } from './api.js'
import * as dropboxStorage from './dropboxStorage.js'
import { preferences } from './preferences.js'
import Login from './components/Login.vue'
import Timer from './components/Timer.vue'
import Settings from './components/Settings.vue'

const isLoggedIn = ref(!!api.getToken())
const showSettings = ref(false)
const sessionExpiredMessage = ref('')
const timerRef = ref(null)
let authCheckInProgress = false

const resetToken = ref(new URLSearchParams(window.location.search).get('reset_token') || '')

const searchParams = new URLSearchParams(window.location.search)

// Dropbox OAuth callback — tokens arrive in the URL, store them and clean up immediately
const dropboxToken = searchParams.get('dropbox_token')
const dropboxRefresh = searchParams.get('dropbox_refresh')
const dropboxExpires = searchParams.get('dropbox_expires')
if (dropboxToken && dropboxRefresh && dropboxExpires) {
  dropboxStorage.connect(dropboxToken, dropboxRefresh, Number(dropboxExpires))
  window.history.replaceState({}, '', window.location.pathname)
}

const dropboxError = searchParams.get('dropbox_error')
if (dropboxError) {
  window.history.replaceState({}, '', window.location.pathname)
}

// OneDrive OAuth callback
const onedriveResult = searchParams.get('onedrive')
if (onedriveResult) {
  window.history.replaceState({}, '', window.location.pathname)
}

const onedriveMessage = ref(
  onedriveResult === 'connected'
    ? 'OneDrive connected successfully!'
    : onedriveResult === 'error'
      ? `OneDrive connection failed: ${searchParams.get('message') || 'unknown error'}`
      : ''
)
const onedriveMessageType = ref(onedriveResult === 'connected' ? 'success' : 'error')

const dropboxErrorMessage = ref(dropboxError ? `Dropbox connection failed: ${dropboxError}` : '')

const dropboxLoading = ref(false)

async function loadUserPreferences() {
  if (!api.getToken() || api.isLocalMode()) {
    return
  }
  try {
    const userPreferences = await api.getUserPreferences()
    if (userPreferences) {
      if (typeof userPreferences.dayStartHour === 'number') {
        preferences.dayStartHour = userPreferences.dayStartHour
      }
      if (typeof userPreferences.dailyGoalEnabled === 'boolean') {
        preferences.dailyGoalEnabled = userPreferences.dailyGoalEnabled
      }
      if (typeof userPreferences.defaultDailyGoalHours === 'number') {
        preferences.defaultDailyGoalHours = userPreferences.defaultDailyGoalHours
      }
      if (typeof userPreferences.includeWeekendGoals === 'boolean') {
        preferences.includeWeekendGoals = userPreferences.includeWeekendGoals
      }
    }
  } catch (err) {
    console.error('Failed to load user preferences:', err)
  }
}

async function handleLogin() {
  sessionExpiredMessage.value = ''
  isLoggedIn.value = true
  await loadUserPreferences()
}

async function handleLogout() {
  sessionExpiredMessage.value = ''
  await api.logout()
  isLoggedIn.value = false
  showSettings.value = false
}

function openSettings() {
  showSettings.value = true
}

function closeSettings() {
  showSettings.value = false
  timerRef.value?.refetch?.()
}

function handleSessionExpired(event = {}) {
  isLoggedIn.value = false
  showSettings.value = false
  sessionExpiredMessage.value = event.message || 'Your session expired. Please sign in again.'
}

async function validateSessionOnFocus() {
  if (authCheckInProgress || !isLoggedIn.value || api.isLocalMode() || !api.getToken()) {
    return
  }

  authCheckInProgress = true

  try {
    await api.validateSession()
  } catch (err) {
    if (
      err.message !== 'Your session expired. Please sign in again.' &&
      err.message !== 'Unauthorized' &&
      err.message !== 'Failed to fetch' &&
      err.name !== 'TypeError'
    ) {
      console.error('Failed to validate session on focus:', err)
    }
  } finally {
    authCheckInProgress = false
  }
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    validateSessionOnFocus()
  }
}

onMounted(async () => {
  api.addAuthListener(handleSessionExpired)
  isLoggedIn.value = !!api.getToken()
  if (isLoggedIn.value) {
    if (api.isDropboxMode()) {
      dropboxLoading.value = true
      try {
        await api.loadDropboxData()
      } catch (e) {
        console.error('Failed to load Dropbox data:', e)
      } finally {
        dropboxLoading.value = false
      }
    } else {
      loadUserPreferences()
    }
  }
  window.addEventListener('focus', validateSessionOnFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  api.removeAuthListener(handleSessionExpired)
  window.removeEventListener('focus', validateSessionOnFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <div class="app" :class="{ 'logged-in': isLoggedIn }">
    <Login v-if="!isLoggedIn" :session-message="sessionExpiredMessage" :reset-token="resetToken" @login="handleLogin" />
    <div v-else-if="dropboxLoading" class="dropbox-loading">Loading from Dropbox...</div>
    <Timer ref="timerRef" v-else @openSettings="openSettings" />
    
    <!-- Settings Modal -->
    <Settings
      v-if="showSettings"
      @close="closeSettings"
      @logout="handleLogout"
    />

    <!-- OAuth result notifications -->
    <div v-if="onedriveMessage" :class="['onedrive-toast', onedriveMessageType]">
      {{ onedriveMessage }}
      <button @click="onedriveMessage = ''">&times;</button>
    </div>
    <div v-if="dropboxErrorMessage" class="onedrive-toast error">
      {{ dropboxErrorMessage }}
      <button @click="dropboxErrorMessage = ''">&times;</button>
    </div>
  </div>
</template>

<style>
:root {
  --bg-primary: #ffffff;
  --bg-secondary: #f5f5f5;
  --bg-tertiary: #e8e8e8;
  --text-primary: #1a1a1a;
  --text-secondary: #666666;
  --text-muted: #999999;
  --border-color: #dddddd;
  --border-light: #eeeeee;
  --accent-color: #4a9eff;
  --accent-hover: #3a8eef;
  --success-color: #22c55e;
  --success-hover: #16a34a;
  --danger-color: #ef4444;
  --danger-hover: #dc2626;
  --timer-bg: #e8f4ff;
  --timer-border: #4a9eff;
  --sidebar-width: 320px;
}

@media (prefers-color-scheme: dark) {
  :root {
    --bg-primary: #0f0f0f;
    --bg-secondary: #1a1a1a;
    --bg-tertiary: #252525;
    --text-primary: #e0e0e0;
    --text-secondary: #888888;
    --text-muted: #555555;
    --border-color: #333333;
    --border-light: #444444;
    --accent-color: #4a9eff;
    --accent-hover: #3a8eef;
    --success-color: #22c55e;
    --success-hover: #16a34a;
    --danger-color: #ef4444;
    --danger-hover: #dc2626;
    --timer-bg: #1a2a3a;
    --timer-border: #4a9eff;
  }
}

* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
  background: var(--bg-primary);
  color: var(--text-primary);
  min-height: 100vh;
}

.app {
  min-height: 100vh;
}

.app.logged-in {
  display: flex;
}

.dropbox-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  color: var(--text-secondary);
  font-size: 1rem;
}

.onedrive-toast {
  position: fixed;
  bottom: 1rem;
  left: 50%;
  transform: translateX(-50%);
  padding: 0.75rem 1.25rem;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.9rem;
  z-index: 9999;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.onedrive-toast.success {
  background: var(--success-color);
  color: #fff;
}

.onedrive-toast.error {
  background: var(--danger-color);
  color: #fff;
}

.onedrive-toast button {
  background: none;
  border: none;
  color: inherit;
  font-size: 1.1rem;
  line-height: 1;
  padding: 0;
  opacity: 0.8;
}

button {
  cursor: pointer;
  font-family: inherit;
}

input {
  font-family: inherit;
}
</style>
