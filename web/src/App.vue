<script setup>
import { ref, onMounted } from 'vue'
import { api } from './api.js'
import Login from './components/Login.vue'
import Timer from './components/Timer.vue'
import Settings from './components/Settings.vue'

const isLoggedIn = ref(!!api.getToken())
const showSettings = ref(false)

function handleLogin() {
  isLoggedIn.value = true
}

function handleLogout() {
  api.clearToken()
  api.disconnectWebSocket()
  isLoggedIn.value = false
  showSettings.value = false
}

function openSettings() {
  showSettings.value = true
}

function closeSettings() {
  showSettings.value = false
}

onMounted(() => {
  isLoggedIn.value = !!api.getToken()
})
</script>

<template>
  <div class="app">
    <header>
      <h1>YATT</h1>
      <button v-if="isLoggedIn" @click="openSettings" class="settings-btn">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="3"></circle>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
        </svg>
      </button>
    </header>
    <main>
      <Login v-if="!isLoggedIn" @login="handleLogin" />
      <Timer v-else />
    </main>
    
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
  max-width: 600px;
  margin: 0 auto;
  padding: 1rem;
}

header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 0;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 2rem;
}

header h1 {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
}

.settings-btn {
  background: transparent;
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
  padding: 0.5rem;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.settings-btn:hover {
  border-color: var(--border-color);
  color: var(--text-primary);
}

.settings-btn svg {
  display: block;
}

button {
  cursor: pointer;
  font-family: inherit;
}

input {
  font-family: inherit;
}
</style>
