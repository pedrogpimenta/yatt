<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { 
  preferences, 
  formatTime, 
  formatDate,
  formatTimeForInput,
  formatDateForInput,
  parseTimeInput,
  parseDateInput,
  getTimePlaceholder,
  getDatePlaceholder
} from '../preferences.js'
import ProjectSelector from './ProjectSelector.vue'
import { formatProjectLabel } from '../projects.js'

const props = defineProps({
  timer: Object,
  projects: {
    type: Array,
    default: () => []
  },
  onCreateProject: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['update', 'delete'])

function handleKeydown(e) {
  if (e.key === 'Escape' && isEditing.value) {
    e.stopPropagation()
    cancelEdit()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown, true)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown, true)
})

const isEditing = ref(false)
const editTag = ref('')
const editProjectId = ref(null)
const editStartDate = ref('')
const editStartTime = ref('')
const editEndDate = ref('')
const editEndTime = ref('')

const isRunning = computed(() => !props.timer.end_time)

const duration = computed(() => {
  const start = new Date(props.timer.start_time).getTime()
  const end = props.timer.end_time 
    ? new Date(props.timer.end_time).getTime() 
    : Date.now()
  return end - start
})

function findProjectById(id) {
  if (id === null || id === undefined) return null
  return props.projects.find((project) => String(project.id) === String(id)) || null
}

const projectDisplay = computed(() => {
  const project = findProjectById(props.timer.project_id)
  return project ? formatProjectLabel(project) : ''
})

function formatDuration(ms) {
  const totalSeconds = Math.floor(ms / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

// Computed values for reactivity when preferences change
const displayStartTime = computed(() => {
  const _ = preferences.timeFormat // Track dependency
  return formatTime(props.timer.start_time)
})

const displayEndTime = computed(() => {
  const _ = preferences.timeFormat // Track dependency
  return props.timer.end_time ? formatTime(props.timer.end_time) : null
})

const displayDate = computed(() => {
  const _ = preferences.dateFormat // Track dependency
  const date = new Date(props.timer.start_time)
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  
  if (date.toDateString() === today.toDateString()) {
    return 'Today'
  }
  if (date.toDateString() === yesterday.toDateString()) {
    return 'Yesterday'
  }
  return formatDate(props.timer.start_time)
})

const endDateSynced = ref(true)
const editError = ref('')

// Hidden date picker refs
const startDatePicker = ref(null)
const endDatePicker = ref(null)
const hiddenStartDate = ref('')
const hiddenEndDate = ref('')

function toISODate(isoString) {
  const date = new Date(isoString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function startEdit() {
  editTag.value = props.timer.tag || ''
  editProjectId.value = props.timer.project_id ?? null
  // Date uses preference format for display
  editStartDate.value = formatDateForInput(props.timer.start_time)
  hiddenStartDate.value = toISODate(props.timer.start_time)
  // Time uses preference format
  editStartTime.value = formatTimeForInput(props.timer.start_time)
  if (props.timer.end_time) {
    editEndDate.value = formatDateForInput(props.timer.end_time)
    hiddenEndDate.value = toISODate(props.timer.end_time)
    editEndTime.value = formatTimeForInput(props.timer.end_time)
    endDateSynced.value = editEndDate.value === editStartDate.value
  } else {
    editEndDate.value = editStartDate.value
    hiddenEndDate.value = hiddenStartDate.value
    editEndTime.value = ''
    endDateSynced.value = true
  }
  editError.value = ''
  isEditing.value = true
}

function openStartDatePicker() {
  startDatePicker.value?.showPicker()
}

function openEndDatePicker() {
  endDatePicker.value?.showPicker()
}

function onHiddenStartDateChange() {
  if (hiddenStartDate.value) {
    const [year, month, day] = hiddenStartDate.value.split('-').map(Number)
    const date = new Date(year, month - 1, day)
    editStartDate.value = formatDateForInput(date)
    if (endDateSynced.value) {
      hiddenEndDate.value = hiddenStartDate.value
      editEndDate.value = editStartDate.value
    }
  }
}

function onHiddenEndDateChange() {
  if (hiddenEndDate.value) {
    const [year, month, day] = hiddenEndDate.value.split('-').map(Number)
    const date = new Date(year, month - 1, day)
    editEndDate.value = formatDateForInput(date)
    endDateSynced.value = hiddenEndDate.value === hiddenStartDate.value
  }
}

function onStartDateInput() {
  const parsed = parseDateInput(editStartDate.value)
  if (parsed) {
    hiddenStartDate.value = `${parsed.year}-${String(parsed.month + 1).padStart(2, '0')}-${String(parsed.day).padStart(2, '0')}`
    if (endDateSynced.value) {
      hiddenEndDate.value = hiddenStartDate.value
      editEndDate.value = editStartDate.value
    }
  }
}

function onEndDateInput() {
  const parsed = parseDateInput(editEndDate.value)
  if (parsed) {
    hiddenEndDate.value = `${parsed.year}-${String(parsed.month + 1).padStart(2, '0')}-${String(parsed.day).padStart(2, '0')}`
    endDateSynced.value = hiddenEndDate.value === hiddenStartDate.value
  }
}

function cancelEdit() {
  isEditing.value = false
  editError.value = ''
}

function saveEdit() {
  editError.value = ''
  
  if (!editStartDate.value || !editStartTime.value) {
    editError.value = 'Start date and time are required'
    return
  }
  
  const startDateParts = parseDateInput(editStartDate.value)
  if (!startDateParts) {
    editError.value = `Invalid start date. Use ${getDatePlaceholder()}`
    return
  }
  
  const startTimeParts = parseTimeInput(editStartTime.value)
  if (!startTimeParts) {
    editError.value = `Invalid start time. Use ${getTimePlaceholder()}`
    return
  }
  
  const startDateTime = new Date(startDateParts.year, startDateParts.month, startDateParts.day, startTimeParts.hours, startTimeParts.minutes)
  
  let endTime = null
  if (editEndDate.value && editEndTime.value) {
    const endDateParts = parseDateInput(editEndDate.value)
    if (!endDateParts) {
      editError.value = `Invalid end date. Use ${getDatePlaceholder()}`
      return
    }
    
    const endTimeParts = parseTimeInput(editEndTime.value)
    if (!endTimeParts) {
      editError.value = `Invalid end time. Use ${getTimePlaceholder()}`
      return
    }
    
    const endDateTime = new Date(endDateParts.year, endDateParts.month, endDateParts.day, endTimeParts.hours, endTimeParts.minutes)
    endTime = endDateTime.toISOString()
  }
  
  emit('update', props.timer.id, {
    tag: editTag.value || null,
    start_time: startDateTime.toISOString(),
    end_time: endTime,
    project_id: editProjectId.value ?? null
  })
  isEditing.value = false
}

function deleteTimer() {
  if (confirm('Delete this timer?')) {
    emit('delete', props.timer.id)
  }
}
</script>

<template>
  <div class="timer-item" :class="{ running: isRunning, editing: isEditing }">
    <!-- View Mode -->
    <template v-if="!isEditing">
      <div class="timer-main" @click="startEdit">
        <div class="timer-info">
          <div class="timer-labels">
            <span class="timer-tag" v-if="timer.tag">{{ timer.tag }}</span>
            <span class="timer-tag empty" v-else>No tag</span>
            <span class="timer-project" v-if="projectDisplay">{{ projectDisplay }}</span>
          </div>
          <span class="timer-date">{{ displayDate }}</span>
        </div>
        <div class="timer-times">
          <span>{{ displayStartTime }}</span>
          <span class="separator">—</span>
          <span v-if="timer.end_time">{{ displayEndTime }}</span>
          <span v-else class="running-indicator">Running</span>
        </div>
      </div>
      <div class="timer-duration">
        {{ formatDuration(duration) }}
      </div>
    </template>

    <!-- Edit Mode -->
    <template v-else>
      <div class="edit-form">
        <div class="edit-row">
          <label>Tag</label>
          <input v-model="editTag" type="text" placeholder="Tag (optional)" />
        </div>

        <div class="edit-row">
          <label>Project</label>
          <ProjectSelector
            v-model="editProjectId"
            :projects="projects"
            :onCreate="onCreateProject"
            placeholder="Select project..."
          />
        </div>
        
        <div class="edit-row">
          <label>Start</label>
          <div class="datetime-inputs">
            <div class="date-input-wrapper">
              <input v-model="editStartDate" type="text" :placeholder="getDatePlaceholder()" @input="onStartDateInput" required />
              <button type="button" class="date-picker-btn" @click="openStartDatePicker">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                  <line x1="16" y1="2" x2="16" y2="6"></line>
                  <line x1="8" y1="2" x2="8" y2="6"></line>
                  <line x1="3" y1="10" x2="21" y2="10"></line>
                </svg>
              </button>
              <input ref="startDatePicker" type="date" v-model="hiddenStartDate" @change="onHiddenStartDateChange" class="hidden-date-picker" />
            </div>
            <input v-model="editStartTime" type="text" :placeholder="getTimePlaceholder()" required />
          </div>
        </div>
        
        <div class="edit-row">
          <label>End</label>
          <div class="datetime-inputs">
            <div class="date-input-wrapper">
              <input v-model="editEndDate" type="text" :placeholder="getDatePlaceholder()" @input="onEndDateInput" />
              <button type="button" class="date-picker-btn" @click="openEndDatePicker">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                  <line x1="16" y1="2" x2="16" y2="6"></line>
                  <line x1="8" y1="2" x2="8" y2="6"></line>
                  <line x1="3" y1="10" x2="21" y2="10"></line>
                </svg>
              </button>
              <input ref="endDatePicker" type="date" v-model="hiddenEndDate" @change="onHiddenEndDateChange" class="hidden-date-picker" />
            </div>
            <input v-model="editEndTime" type="text" :placeholder="getTimePlaceholder()" />
          </div>
        </div>

        <p v-if="editError" class="edit-error">{{ editError }}</p>

        <div class="edit-actions">
          <button @click="deleteTimer" class="delete-btn">Delete</button>
          <div class="edit-actions-right">
            <button @click="cancelEdit" class="cancel-btn">Cancel</button>
            <button @click="saveEdit" class="save-btn">Save</button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.timer-item {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 1rem;
  margin-bottom: 0.75rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  transition: border-color 0.2s;
}

.timer-item:hover {
  border-color: var(--border-light);
}

.timer-item.running {
  border-color: var(--timer-border);
  background: var(--timer-bg);
}

.timer-item.editing {
  cursor: default;
  flex-direction: column;
  align-items: stretch;
}

.timer-main {
  flex: 1;
  min-width: 0;
}

.timer-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.25rem;
  margin-bottom: 0.25rem;
}

.timer-labels {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.timer-tag {
  font-weight: 500;
  color: var(--text-primary);
}

.timer-tag.empty {
  color: var(--text-muted);
  font-style: italic;
}

.timer-project {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.timer-date {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.timer-times {
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.separator {
  margin: 0 0.5rem;
  color: var(--border-light);
}

.running-indicator {
  color: var(--accent-color);
}

.timer-duration {
  font-weight: 500;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  margin-left: 1rem;
}

/* Edit Form */
.edit-form {
  width: 100%;
}

.edit-row {
  margin-bottom: 0.75rem;
}

.edit-row label {
  display: block;
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-bottom: 0.25rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.edit-row input {
  width: 100%;
  padding: 0.625rem;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 0.875rem;
}

.edit-row input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.datetime-inputs {
  display: flex;
  gap: 0.5rem;
}

.datetime-inputs > input {
  flex: 1;
}

.date-input-wrapper {
  flex: 1;
  position: relative;
  display: flex;
}

.date-input-wrapper input[type="text"] {
  flex: 1;
  padding-right: 2.5rem;
}

.date-picker-btn {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.date-picker-btn:hover {
  color: var(--accent-color);
}

.hidden-date-picker {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
  pointer-events: none;
}

.edit-error {
  color: var(--danger-color);
  font-size: 0.75rem;
  margin: 0.5rem 0 0 0;
}

.edit-actions {
  display: flex;
  justify-content: space-between;
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--border-color);
}

.edit-actions-right {
  display: flex;
  gap: 0.5rem;
}

.edit-actions button {
  padding: 0.5rem 1rem;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
}

.delete-btn {
  background: transparent;
  border: 1px solid var(--danger-color);
  color: var(--danger-color);
}

.delete-btn:hover {
  background: var(--danger-color);
  color: #fff;
}

.cancel-btn {
  background: transparent;
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
}

.cancel-btn:hover {
  border-color: var(--border-color);
  color: var(--text-primary);
}

.save-btn {
  background: var(--accent-color);
  border: none;
  color: #fff;
}

.save-btn:hover {
  background: var(--accent-hover);
}
</style>
