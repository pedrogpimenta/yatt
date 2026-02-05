<script setup>
import { ref, onMounted } from 'vue'
import { api } from './api.js'
import { preferences } from './preferences.js'
import Login from './components/Login.vue'
import Timer from './components/Timer.vue'
import Settings from './components/Settings.vue'

const isLoggedIn = ref(!!api.getToken())
const showSettings = ref(false)
const timerRef = ref(null)

async function loadUserPreferences() {
  if (!api.getToken() || api.isLocalMode()) {
    return
  }
  try {
    const userPreferences = await api.getUserPreferences()
    if (userPreferences && typeof userPreferences.dayStartHour === 'number') {
      preferences.dayStartHour = userPreferences.dayStartHour
    }
  } catch (err) {
    console.error('Failed to load user preferences:', err)
  }
}

async function handleLogin() {
  isLoggedIn.value = true
  await loadUserPreferences()
}

async function handleLogout() {
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

onMounted(() => {
  isLoggedIn.value = !!api.getToken()
  if (isLoggedIn.value) {
    loadUserPreferences()
  }
})
</script>

<template>
  <div class="app" :class="{ 'logged-in': isLoggedIn }">
    <Login v-if="!isLoggedIn" @login="handleLogin" />
    <Timer ref="timerRef" v-else @openSettings="openSettings" />
    
    <!-- Settings Modal -->
    <Settings 
      v-if="showSettings" 
      @close="closeSettings" 
      @logout="handleLogout"
    />
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

button {
  cursor: pointer;
  font-family: inherit;
}

input {
  font-family: inherit;
}
</style>
