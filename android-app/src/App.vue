<script setup>
import { ref, onMounted } from 'vue'
import { api } from './api.js'
import Login from './components/Login.vue'
import Timer from './components/Timer.vue'
import Settings from './components/Settings.vue'

const isLoggedIn = ref(false)
const showSettings = ref(false)
const loading = ref(true)

async function checkLogin() {
  loading.value = true
  await api.getApiUrl()
  isLoggedIn.value = await api.isLoggedIn()
  loading.value = false
}

function handleLogin() {
  isLoggedIn.value = true
}

async function handleLogout() {
  await api.clearToken()
  isLoggedIn.value = false
}

onMounted(() => {
  checkLogin()
})
</script>

<template>
  <div class="app">
    <header>
      <h1>YATT</h1>
      <div class="header-actions">
        <button @click="showSettings = true" class="icon-btn">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
            <path d="M19.14 12.94c.04-.31.06-.63.06-.94 0-.31-.02-.63-.06-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.04.31-.06.63-.06.94s.02.63.06.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
          </svg>
        </button>
        <button v-if="isLoggedIn" @click="handleLogout" class="logout-btn">Logout</button>
      </div>
    </header>

    <main>
      <div v-if="loading" class="loading">Loading...</div>
      <Login v-else-if="!isLoggedIn" @login="handleLogin" />
      <Timer v-else />
    </main>

    <Settings 
      v-if="showSettings" 
      @close="showSettings = false"
      @saved="checkLogin"
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
  -webkit-tap-highlight-color: transparent;
}

.app {
  max-width: 600px;
  margin: 0 auto;
  padding: 1rem;
  padding-top: env(safe-area-inset-top, 1rem);
  padding-bottom: env(safe-area-inset-bottom, 1rem);
}

header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 0;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 1.5rem;
}

header h1 {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.icon-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  padding: 0.5rem;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-btn:hover, .icon-btn:active {
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.logout-btn {
  background: transparent;
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
  padding: 0.5rem 1rem;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
}

.logout-btn:active {
  border-color: var(--border-color);
  color: var(--text-primary);
}

.loading {
  text-align: center;
  color: var(--text-muted);
  padding: 3rem;
}

button {
  cursor: pointer;
  font-family: inherit;
}

input {
  font-family: inherit;
}
</style>
