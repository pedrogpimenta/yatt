<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { api } from '../api.js'
import { startTimerNotification, stopTimerNotification, requestPermissions } from '../notifications.js'
import TimerItem from './TimerItem.vue'
import WeeklyCalendar from './WeeklyCalendar.vue'
import TagInput from './TagInput.vue'

const timers = ref([])
const loading = ref(true)
const error = ref('')
const newTag = ref('')
const tags = ref([])
const viewMode = ref('list') // 'list' or 'calendar'
const selectedTimer = ref(null)

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
    
    // Sync tag input with running timer's tag
    if (runningTimer.value) {
      newTag.value = runningTimer.value.tag || ''
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

async function fetchTags() {
  try {
    tags.value = await api.getTags()
  } catch (err) {
    console.error('Failed to fetch tags:', err)
  }
}

async function toggleTimer() {
  error.value = ''
  try {
    if (isRunning.value) {
      await api.stopTimer(runningTimer.value.id)
      await stopTimerNotification()
      newTag.value = ''
    } else {
      await requestPermissions()
      await api.createTimer({ tag: newTag.value || null })
      // Refresh tags after creating a timer with a potentially new tag
      if (newTag.value && !tags.value.includes(newTag.value)) {
        fetchTags()
      }
    }
    await fetchTimers()
  } catch (err) {
    error.value = err.message
  }
}

async function updateRunningTag() {
  if (!runningTimer.value) return
  error.value = ''
  try {
    await api.updateTimer(runningTimer.value.id, { tag: newTag.value || null })
    // Refresh tags if it's a new one
    if (newTag.value && !tags.value.includes(newTag.value)) {
      fetchTags()
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
    selectedTimer.value = null
  } catch (err) {
    error.value = err.message
  }
}

function selectTimerFromCalendar(timer) {
  selectedTimer.value = timer
}

function closeSelectedTimer() {
  selectedTimer.value = null
}

onMounted(() => {
  fetchTimers()
  fetchTags()
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
    <div class="tag-input-section">
      <TagInput 
        v-model="newTag"
        :tags="tags"
        :placeholder="isRunning ? 'Change tag...' : 'Tag (optional)'"
        @submit="isRunning ? updateRunningTag() : toggleTimer()"
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

    <!-- View Toggle -->
    <div class="view-toggle">
      <button 
        @click="viewMode = 'list'" 
        :class="{ active: viewMode === 'list' }"
      >
        List
      </button>
      <button 
        @click="viewMode = 'calendar'" 
        :class="{ active: viewMode === 'calendar' }"
      >
        Calendar
      </button>
    </div>

    <!-- Timer List View -->
    <div class="timer-list" v-if="viewMode === 'list'">
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

    <!-- Calendar View -->
    <div class="calendar-view" v-if="viewMode === 'calendar'">
      <WeeklyCalendar 
        :timers="timers" 
        :currentElapsed="currentElapsed"
        @select="selectTimerFromCalendar"
      />
    </div>

    <!-- Selected Timer Modal (for calendar view) -->
    <div class="timer-modal-overlay" v-if="selectedTimer" @click.self="closeSelectedTimer">
      <div class="timer-modal">
        <button class="close-btn" @click="closeSelectedTimer">&times;</button>
        <TimerItem 
          :timer="selectedTimer"
          @update="handleUpdate"
          @delete="handleDelete"
        />
      </div>
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

.tag-input-section {
  width: 100%;
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

.view-toggle {
  display: flex;
  gap: 0.5rem;
  justify-content: center;
}

.view-toggle button {
  padding: 0.5rem 1.5rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
}

.view-toggle button:active {
  border-color: var(--border-light);
  color: var(--text-primary);
}

.view-toggle button.active {
  background: var(--accent-color);
  border-color: var(--accent-color);
  color: #fff;
}

.calendar-view {
  margin-top: 1rem;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  background: var(--bg-secondary);
}

.timer-modal-overlay {
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

.timer-modal {
  background: var(--bg-primary);
  border-radius: 12px;
  padding: 1.5rem;
  width: 90%;
  max-width: 400px;
  position: relative;
}

.close-btn {
  position: absolute;
  top: 0.5rem;
  right: 0.75rem;
  background: none;
  border: none;
  font-size: 1.5rem;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.close-btn:active {
  color: var(--text-primary);
}
</style>
