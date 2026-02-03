<script setup>
import { ref, computed } from 'vue'
import { preferences, formatTime, formatDate } from '../preferences.js'

const props = defineProps({
  timers: Array,
  currentElapsed: Number
})

const emit = defineEmits(['select'])

// Get the Monday of the current week being viewed
const weekOffset = ref(0)

const weekStart = computed(() => {
  const now = new Date()
  const day = now.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  const monday = new Date(now)
  monday.setDate(now.getDate() + mondayOffset + (weekOffset.value * 7))
  monday.setHours(0, 0, 0, 0)
  return monday
})

const weekEnd = computed(() => {
  const end = new Date(weekStart.value)
  end.setDate(end.getDate() + 7)
  return end
})

const days = computed(() => {
  const result = []
  for (let i = 0; i < 7; i++) {
    const date = new Date(weekStart.value)
    date.setDate(date.getDate() + i)
    result.push(date)
  }
  return result
})

const dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

const hours = computed(() => {
  const result = []
  for (let i = 0; i < 24; i++) {
    result.push(i)
  }
  return result
})

const formattedHours = computed(() => {
  // Reference preferences for reactivity
  const _ = preferences.timeFormat
  return hours.value.map(hour => formatHour(hour))
})

// Filter timers for the current week
const weekTimers = computed(() => {
  return props.timers.filter(timer => {
    const start = new Date(timer.start_time)
    const end = timer.end_time ? new Date(timer.end_time) : new Date()
    return start < weekEnd.value && end >= weekStart.value
  })
})

// Get timers for a specific day
function getTimersForDay(dayDate) {
  const dayStart = new Date(dayDate)
  dayStart.setHours(0, 0, 0, 0)
  const dayEnd = new Date(dayDate)
  dayEnd.setHours(23, 59, 59, 999)
  
  return weekTimers.value.filter(timer => {
    const start = new Date(timer.start_time)
    const end = timer.end_time ? new Date(timer.end_time) : new Date()
    return start <= dayEnd && end >= dayStart
  }).map(timer => {
    const timerStart = new Date(timer.start_time)
    const timerEnd = timer.end_time ? new Date(timer.end_time) : new Date()
    
    const displayStart = timerStart < dayStart ? dayStart : timerStart
    const displayEnd = timerEnd > dayEnd ? dayEnd : timerEnd
    
    const startMinutes = displayStart.getHours() * 60 + displayStart.getMinutes()
    const endMinutes = displayEnd.getHours() * 60 + displayEnd.getMinutes()
    
    const top = (startMinutes / (24 * 60)) * 100
    const height = ((endMinutes - startMinutes) / (24 * 60)) * 100
    
    return {
      ...timer,
      top: `${top}%`,
      height: `${Math.max(height, 0.5)}%`,
      isRunning: !timer.end_time,
      displayStart,
      displayEnd
    }
  })
}

function formatDayNumber(date) {
  return date.getDate()
}

function formatHour(hour) {
  if (preferences.timeFormat === '12h') {
    const period = hour >= 12 ? 'PM' : 'AM'
    const hour12 = hour % 12 || 12
    return `${hour12} ${period}`
  }
  return `${String(hour).padStart(2, '0')}:00`
}

function formatTimeDisplay(date) {
  return formatTime(date)
}

function isToday(date) {
  const today = new Date()
  return date.toDateString() === today.toDateString()
}

function prevWeek() {
  weekOffset.value--
}

function nextWeek() {
  weekOffset.value++
}

function goToToday() {
  weekOffset.value = 0
}

function selectTimer(timer) {
  emit('select', timer)
}

const weekLabel = computed(() => {
  // Reference preferences to track as dependency for reactivity
  const _ = preferences.dateFormat
  
  const start = weekStart.value
  const end = new Date(weekStart.value)
  end.setDate(end.getDate() + 6)
  
  return `${formatDate(start)} - ${formatDate(end)}`
})
</script>

<template>
  <div class="weekly-calendar">
    <!-- Navigation -->
    <div class="calendar-nav">
      <button @click="prevWeek" class="nav-btn">&larr;</button>
      <button @click="goToToday" class="today-btn">Today</button>
      <span class="week-label">{{ weekLabel }}</span>
      <button @click="nextWeek" class="nav-btn">&rarr;</button>
    </div>
    
    <!-- Calendar Grid -->
    <div class="calendar-wrapper">
      <!-- Fixed Header -->
      <div class="calendar-header">
        <div class="time-header"></div>
        <div class="days-header">
          <div 
            v-for="(day, index) in days" 
            :key="index" 
            class="day-header"
            :class="{ today: isToday(day) }"
          >
            <span class="day-name">{{ dayNames[index] }}</span>
            <span class="day-date" :class="{ 'is-today': isToday(day) }">{{ formatDayNumber(day) }}</span>
          </div>
        </div>
      </div>
      
      <!-- Scrollable Body -->
      <div class="calendar-body">
        <div class="calendar-grid">
          <!-- Time column -->
          <div class="time-column">
            <div v-for="(label, index) in formattedHours" :key="index" class="hour-label">
              {{ label }}
            </div>
          </div>
          
          <!-- Days grid -->
          <div class="days-grid">
            <!-- Hour rows (background) -->
            <div class="hour-rows">
              <div v-for="hour in hours" :key="hour" class="hour-row"></div>
            </div>
            
            <!-- Day columns with timers -->
            <div class="day-columns">
              <div 
                v-for="(day, index) in days" 
                :key="index" 
                class="day-column"
                :class="{ today: isToday(day) }"
              >
                <!-- Timer blocks -->
                <div 
                  v-for="timer in getTimersForDay(day)" 
                  :key="timer.id"
                  class="timer-block"
                  :class="{ running: timer.isRunning }"
                  :style="{ top: timer.top, height: timer.height }"
                  @click="selectTimer(timer)"
                  :title="`${timer.tag || 'No tag'}\n${formatTimeDisplay(timer.displayStart)} - ${timer.isRunning ? 'Running' : formatTimeDisplay(timer.displayEnd)}`"
                >
                  <span class="timer-tag">{{ timer.tag || 'No tag' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.weekly-calendar {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.calendar-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-primary);
  flex-shrink: 0;
}

.nav-btn {
  padding: 0.5rem 0.75rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  cursor: pointer;
  font-size: 1rem;
}

.nav-btn:hover {
  background: var(--bg-tertiary);
}

.today-btn {
  padding: 0.5rem 1rem;
  background: var(--accent-color);
  border: none;
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 500;
}

.today-btn:hover {
  background: var(--accent-hover);
}

.week-label {
  font-weight: 500;
  color: var(--text-primary);
  min-width: 180px;
  text-align: center;
}

.calendar-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* Fixed Header */
.calendar-header {
  display: flex;
  flex-shrink: 0;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-primary);
  z-index: 10;
}

.time-header {
  width: 60px;
  flex-shrink: 0;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
}

.days-header {
  flex: 1;
  display: flex;
}

.day-header {
  flex: 1;
  min-width: 80px;
  height: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-right: 1px solid var(--border-color);
  padding: 0.5rem;
  background: var(--bg-primary);
}

.day-header:last-child {
  border-right: none;
}

.day-header.today {
  background: var(--timer-bg);
}

.day-name {
  font-size: 0.75rem;
  color: var(--text-muted);
  text-transform: uppercase;
}

.day-date {
  font-size: 1.125rem;
  font-weight: 500;
  color: var(--text-primary);
}

.day-date.is-today {
  background: var(--accent-color);
  color: #fff;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Scrollable Body */
.calendar-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.calendar-grid {
  display: flex;
  min-height: calc(24 * 48px);
}

.time-column {
  width: 60px;
  flex-shrink: 0;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
}

.hour-label {
  height: 48px;
  font-size: 0.75rem;
  color: var(--text-muted);
  text-align: right;
  padding-right: 0.5rem;
  padding-top: 0;
  transform: translateY(-50%);
}

.days-grid {
  flex: 1;
  position: relative;
}

/* Hour rows (horizontal lines) */
.hour-rows {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}

.hour-row {
  height: 48px;
  border-bottom: 1px solid var(--border-color);
}

.hour-row:nth-child(even) {
  background: var(--bg-secondary);
  opacity: 0.5;
}

/* Day columns (vertical divisions) */
.day-columns {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
}

.day-column {
  flex: 1;
  min-width: 80px;
  position: relative;
  border-right: 1px solid var(--border-color);
}

.day-column:last-child {
  border-right: none;
}

.day-column.today::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--accent-color);
  opacity: 0.08;
  pointer-events: none;
  z-index: 0;
}

/* Timer blocks */
.timer-block {
  position: absolute;
  left: 3px;
  right: 3px;
  background: var(--accent-color);
  border-radius: 6px;
  padding: 4px 6px;
  overflow: hidden;
  cursor: pointer;
  opacity: 0.9;
  transition: opacity 0.2s, transform 0.1s;
  z-index: 5;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}

.timer-block:hover {
  opacity: 1;
  transform: scale(1.02);
  z-index: 6;
}

.timer-block.running {
  background: var(--success-color);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.9; }
  50% { opacity: 1; }
}

.timer-tag {
  font-size: 0.75rem;
  font-weight: 500;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}
</style>
