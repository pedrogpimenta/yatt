<script setup>
import { ref, onMounted } from 'vue'
import { api } from './api.js'
import Login from './components/Login.vue'
import Timer from './components/Timer.vue'

const isLoggedIn = ref(!!api.getToken())

function handleLogin() {
  isLoggedIn.value = true
}

function handleLogout() {
  api.clearToken()
  isLoggedIn.value = false
}

onMounted(() => {
  isLoggedIn.value = !!api.getToken()
})
</script>

<template>
  <div class="app">
    <header>
      <h1>YATT</h1>
      <button v-if="isLoggedIn" @click="handleLogout" class="logout-btn">Logout</button>
    </header>
    <main>
      <Login v-if="!isLoggedIn" @login="handleLogin" />
      <Timer v-else />
    </main>
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

.logout-btn {
  background: transparent;
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
  padding: 0.5rem 1rem;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
}

.logout-btn:hover {
  border-color: var(--border-color);
  color: var(--text-primary);
}

button {
  cursor: pointer;
  font-family: inherit;
}

input {
  font-family: inherit;
}
</style>
