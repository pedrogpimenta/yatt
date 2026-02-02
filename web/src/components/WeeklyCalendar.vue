<script setup>
import { ref, computed } from 'vue'

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

// Filter timers for the current week
const weekTimers = computed(() => {
  return props.timers.filter(timer => {
    const start = new Date(timer.start_time)
    const end = timer.end_time ? new Date(timer.end_time) : new Date()
    // Timer overlaps with this week if it starts before week ends and ends after week starts
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
    // Timer overlaps with this day
    return start <= dayEnd && end >= dayStart
  }).map(timer => {
    const timerStart = new Date(timer.start_time)
    const timerEnd = timer.end_time ? new Date(timer.end_time) : new Date()
    
    // Clamp to day boundaries for display
    const displayStart = timerStart < dayStart ? dayStart : timerStart
    const displayEnd = timerEnd > dayEnd ? dayEnd : timerEnd
    
    // Calculate position as percentage of day (24 hours)
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

function formatDate(date) {
  return date.getDate()
}

function formatMonth(date) {
  return date.toLocaleDateString([], { month: 'short' })
}

function formatHour(hour) {
  return `${String(hour).padStart(2, '0')}:00`
}

function formatTime(date) {
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
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
  const start = weekStart.value
  const end = new Date(weekStart.value)
  end.setDate(end.getDate() + 6)
  
  const startMonth = start.toLocaleDateString([], { month: 'short' })
  const endMonth = end.toLocaleDateString([], { month: 'short' })
  const year = start.getFullYear()
  
  if (startMonth === endMonth) {
    return `${startMonth} ${start.getDate()} - ${end.getDate()}, ${year}`
  }
  return `${startMonth} ${start.getDate()} - ${endMonth} ${end.getDate()}, ${year}`
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
    <div class="calendar-container">
      <!-- Time column -->
      <div class="time-column">
        <div class="day-header"></div>
        <div class="hours">
          <div v-for="hour in hours" :key="hour" class="hour-label">
            {{ formatHour(hour) }}
          </div>
        </div>
      </div>
      
      <!-- Day columns -->
      <div class="days-container">
        <div 
          v-for="(day, index) in days" 
          :key="index" 
          class="day-column"
          :class="{ today: isToday(day) }"
        >
          <div class="day-header">
            <span class="day-name">{{ dayNames[index] }}</span>
            <span class="day-date" :class="{ 'is-today': isToday(day) }">{{ formatDate(day) }}</span>
          </div>
          <div class="day-body">
            <!-- Hour grid lines -->
            <div v-for="hour in hours" :key="hour" class="hour-slot"></div>
            
            <!-- Timer blocks -->
            <div 
              v-for="timer in getTimersForDay(day)" 
              :key="timer.id"
              class="timer-block"
              :class="{ running: timer.isRunning }"
              :style="{ top: timer.top, height: timer.height }"
              @click="selectTimer(timer)"
              :title="`${timer.tag || 'No tag'}\n${formatTime(timer.displayStart)} - ${timer.isRunning ? 'Running' : formatTime(timer.displayEnd)}`"
            >
              <span class="timer-tag">{{ timer.tag || 'No tag' }}</span>
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
  min-height: 500px;
}

.calendar-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 0.75rem;
  border-bottom: 1px solid var(--border-color);
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

.calendar-container {
  display: flex;
  flex: 1;
  overflow: auto;
}

.time-column {
  flex-shrink: 0;
  width: 50px;
  border-right: 1px solid var(--border-color);
}

.time-column .day-header {
  height: 50px;
  border-bottom: 1px solid var(--border-color);
}

.hours {
  position: relative;
}

.hour-label {
  height: 40px;
  font-size: 0.625rem;
  color: var(--text-muted);
  text-align: right;
  padding-right: 0.5rem;
  transform: translateY(-50%);
}

.days-container {
  display: flex;
  flex: 1;
}

.day-column {
  flex: 1;
  min-width: 80px;
  border-right: 1px solid var(--border-color);
}

.day-column:last-child {
  border-right: none;
}

.day-column.today {
  background: var(--timer-bg);
}

.day-header {
  height: 50px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid var(--border-color);
  padding: 0.25rem;
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

.day-body {
  position: relative;
  height: calc(24 * 40px);
}

.hour-slot {
  height: 40px;
  border-bottom: 1px solid var(--border-color);
}

.hour-slot:nth-child(even) {
  background: var(--bg-secondary);
  opacity: 0.3;
}

.timer-block {
  position: absolute;
  left: 2px;
  right: 2px;
  background: var(--accent-color);
  border-radius: 4px;
  padding: 2px 4px;
  overflow: hidden;
  cursor: pointer;
  opacity: 0.9;
  transition: opacity 0.2s;
  z-index: 1;
}

.timer-block:hover {
  opacity: 1;
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
  font-size: 0.625rem;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}
</style>
