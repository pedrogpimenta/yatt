<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { api } from '../api.js'
import { startTimerNotification, stopTimerNotification, requestPermissions } from '../notifications.js'
import TimerItem from './TimerItem.vue'

const timers = ref([])
const loading = ref(true)
const error = ref('')
const newTag = ref('')

let tickInterval = null

const runningTimer = computed(() => {
  return timers.value.find(t => !t.end_time)
})

const isRunning = computed(() => !!runningTimer.value)

const currentElapsed = ref(0)
const isEditingElapsed = ref(false)
const editElapsedValue = ref('')
const editStartedAt = ref(0)

function updateCurrentElapsed() {
  if (runningTimer.value) {
    const start = new Date(runningTimer.value.start_time).getTime()
    currentElapsed.value = Date.now() - start
  } else {
    currentElapsed.value = 0
  }
}

function formatHHmmss(ms) {
  const totalSeconds = Math.floor(ms / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function parseHHmmss(str) {
  const parts = str.split(':')
  if (parts.length === 2) {
    // HH:mm format
    const hours = parseInt(parts[0], 10)
    const minutes = parseInt(parts[1], 10)
    if (isNaN(hours) || isNaN(minutes)) return null
    return (hours * 3600 + minutes * 60) * 1000
  } else if (parts.length === 3) {
    // HH:mm:ss format
    const hours = parseInt(parts[0], 10)
    const minutes = parseInt(parts[1], 10)
    const seconds = parseInt(parts[2], 10)
    if (isNaN(hours) || isNaN(minutes) || isNaN(seconds)) return null
    return (hours * 3600 + minutes * 60 + seconds) * 1000
  }
  return null
}

function startEditElapsed() {
  editElapsedValue.value = formatHHmmss(currentElapsed.value)
  editStartedAt.value = Date.now()
  isEditingElapsed.value = true
}

function cancelEditElapsed() {
  isEditingElapsed.value = false
}

async function saveEditElapsed() {
  const enteredMs = parseHHmmss(editElapsedValue.value)
  
  if (enteredMs === null) {
    error.value = 'Invalid format. Use HH:mm:ss or HH:mm'
    return
  }
  
  // Add time elapsed since we started editing
  const timeSinceEditStarted = Date.now() - editStartedAt.value
  const totalElapsedMs = enteredMs + timeSinceEditStarted
  
  const newStartTime = new Date(Date.now() - totalElapsedMs).toISOString()
  
  try {
    await api.updateTimer(runningTimer.value.id, { start_time: newStartTime })
    await fetchTimers()
    isEditingElapsed.value = false
  } catch (err) {
    error.value = err.message
  }
}

// WebSocket listener for real-time updates
function onWsMessage(data) {
  if (data.type === 'timer') {
    fetchTimers()
  }
}

// Completed time today (excluding running timer)
const todayCompletedTotal = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  
  let total = 0
  for (const timer of timers.value) {
    const start = new Date(timer.start_time)
    if (start >= today && timer.end_time) {
      total += new Date(timer.end_time).getTime() - start.getTime()
    }
  }
  return total
})

const todayTotal = computed(() => {
  return todayCompletedTotal.value + currentElapsed.value
})

const weekTotal = computed(() => {
  const elapsed = currentElapsed.value
  const now = new Date()
  const day = now.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  const monday = new Date(now)
  monday.setDate(now.getDate() + mondayOffset)
  monday.setHours(0, 0, 0, 0)
  
  let total = 0
  for (const timer of timers.value) {
    const start = new Date(timer.start_time)
    if (start >= monday) {
      if (timer.end_time) {
        total += new Date(timer.end_time).getTime() - start.getTime()
      } else {
        total += elapsed
      }
    }
  }
  return total
})

function formatDuration(ms) {
  return formatHHmmss(ms)
}

async function fetchTimers() {
  try {
    timers.value = await api.getTimers()
    updateCurrentElapsed()
    
    // Update notification state
    if (runningTimer.value) {
      await startTimerNotification(
        runningTimer.value.start_time, 
        runningTimer.value.tag,
        todayCompletedTotal.value
      )
    } else {
      await stopTimerNotification()
    }
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function toggleTimer() {
  error.value = ''
  try {
    if (isRunning.value) {
      await api.stopTimer(runningTimer.value.id)
      await stopTimerNotification()
    } else {
      await requestPermissions()
      await api.createTimer({ tag: newTag.value || null })
    }
    await fetchTimers()
  } catch (err) {
    error.value = err.message
  }
}

async function handleUpdate(id, data) {
  error.value = ''
  try {
    await api.updateTimer(id, data)
    await fetchTimers()
  } catch (err) {
    error.value = err.message
  }
}

async function handleDelete(id) {
  error.value = ''
  try {
    await api.deleteTimer(id)
    await fetchTimers()
  } catch (err) {
    error.value = err.message
  }
}

onMounted(() => {
  fetchTimers()
  tickInterval = setInterval(() => {
    updateCurrentElapsed()
  }, 1000)
  
  // Connect to WebSocket for real-time updates
  api.addWsListener(onWsMessage)
})

onUnmounted(() => {
  if (tickInterval) {
    clearInterval(tickInterval)
  }
  api.removeWsListener(onWsMessage)
})
</script>

<template>
  <div class="timer-page">
    <!-- Stats -->
    <div class="stats">
      <div class="stat">
        <span class="stat-label">Today</span>
        <span class="stat-value">{{ formatDuration(todayTotal) }}</span>
      </div>
      <div class="stat">
        <span class="stat-label">This Week</span>
        <span class="stat-value">{{ formatDuration(weekTotal) }}</span>
      </div>
    </div>

    <!-- Current Timer Display -->
    <div class="current-timer" v-if="isRunning">
      <div class="elapsed" v-if="!isEditingElapsed" @click="startEditElapsed">
        {{ formatHHmmss(currentElapsed) }}
      </div>
      <div class="elapsed-edit" v-else>
        <input 
          v-model="editElapsedValue" 
          type="text" 
          class="elapsed-input"
          placeholder="HH:mm:ss"
          @keyup.enter="saveEditElapsed"
          @keyup.escape="cancelEditElapsed"
          autofocus
        />
        <div class="elapsed-actions">
          <button @click="cancelEditElapsed" class="cancel-btn">Cancel</button>
          <button @click="saveEditElapsed" class="save-btn">Save</button>
        </div>
      </div>
      <div class="current-tag" v-if="runningTimer?.tag">{{ runningTimer.tag }}</div>
    </div>

    <!-- Tag Input -->
    <div class="tag-input" v-if="!isRunning">
      <input 
        v-model="newTag" 
        type="text" 
        placeholder="Tag (optional)"
        @keyup.enter="toggleTimer"
      />
    </div>

    <!-- Big Button -->
    <button 
      @click="toggleTimer" 
      class="big-button"
      :class="{ running: isRunning }"
    >
      {{ isRunning ? 'Stop' : 'Start' }}
    </button>

    <p v-if="error" class="error">{{ error }}</p>

    <!-- Timer List -->
    <div class="timer-list">
      <h3>History</h3>
      <p v-if="loading" class="loading">Loading...</p>
      <p v-else-if="timers.length === 0" class="empty">No timers yet</p>
      <TimerItem 
        v-for="timer in timers" 
        :key="timer.id" 
        :timer="timer"
        @update="handleUpdate"
        @delete="handleDelete"
      />
    </div>
  </div>
</template>

<style scoped>
.timer-page {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.stats {
  display: flex;
  gap: 1rem;
}

.stat {
  flex: 1;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 1rem;
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 0.75rem;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
}

.current-timer {
  text-align: center;
  padding: 1rem 0;
}

.elapsed {
  font-size: 3rem;
  font-weight: 300;
  color: var(--accent-color);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: opacity 0.2s;
}

.elapsed:active {
  opacity: 0.8;
}

.elapsed-edit {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

.elapsed-input {
  font-size: 2.5rem;
  font-weight: 300;
  font-variant-numeric: tabular-nums;
  text-align: center;
  width: 10rem;
  padding: 0.5rem;
  background: var(--bg-secondary);
  border: 2px solid var(--accent-color);
  border-radius: 8px;
  color: var(--accent-color);
  font-family: inherit;
}

.elapsed-input:focus {
  outline: none;
}

.elapsed-actions {
  display: flex;
  gap: 0.5rem;
}

.elapsed-actions button {
  padding: 0.375rem 0.75rem;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
}

.elapsed-actions .cancel-btn {
  background: transparent;
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
}

.elapsed-actions .cancel-btn:active {
  border-color: var(--border-color);
  color: var(--text-primary);
}

.elapsed-actions .save-btn {
  background: var(--accent-color);
  border: none;
  color: #fff;
}

.elapsed-actions .save-btn:active {
  background: var(--accent-hover);
}

.current-tag {
  margin-top: 0.5rem;
  color: var(--text-secondary);
  font-size: 1rem;
}

.tag-input input {
  width: 100%;
  padding: 0.875rem 1rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  color: var(--text-primary);
  font-size: 1rem;
  text-align: center;
}

.tag-input input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.tag-input input::placeholder {
  color: var(--text-muted);
}

.big-button {
  width: 100%;
  padding: 1.25rem;
  font-size: 1.25rem;
  font-weight: 600;
  border: none;
  border-radius: 16px;
  background: var(--success-color);
  color: #fff;
  transition: transform 0.1s, background 0.2s;
}

.big-button:active {
  transform: scale(0.98);
  background: var(--success-hover);
}

.big-button.running {
  background: var(--danger-color);
}

.big-button.running:active {
  background: var(--danger-hover);
}

.error {
  color: var(--danger-color);
  text-align: center;
  font-size: 0.875rem;
}

.timer-list {
  margin-top: 1rem;
}

.timer-list h3 {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 1rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.loading, .empty {
  text-align: center;
  color: var(--text-muted);
  padding: 2rem;
}
</style>
