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
import TagInput from './TagInput.vue'
import { formatProjectLabel } from '../projects.js'

const props = defineProps({
  timer: Object,
  projects: {
    type: Array,
    default: () => []
  },
  clients: {
    type: Array,
    default: () => []
  },
  tags: {
    type: Array,
    default: () => []
  },
  /** When set (running timer), parent updates this every second so duration ticks */
  liveElapsedMs: {
    type: Number,
    default: null
  },
  onCreateProject: {
    type: Function,
    default: null
  },
  startInEditMode: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update', 'delete', 'cancel', 'openCreateForm'])

function handleKeydown(e) {
  if (e.key === 'Escape' && isEditing.value) {
    e.stopPropagation()
    cancelEdit()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown, true)
  if (props.startInEditMode && props.timer) {
    startEdit()
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown, true)
})

const isEditing = ref(false)
const editTag = ref('')
const editDescription = ref('')
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
const editingTime = ref(false)
const editDuration = ref('')
/** When user opened edit for a running timer; used to add time spent editing to duration on save (like sidebar) */
const editStartedAt = ref(0)

// Hidden date picker refs
const startDatePicker = ref(null)
const endDatePicker = ref(null)
const hiddenStartDate = ref('')
const hiddenEndDate = ref('')

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
    const hours = parseInt(parts[0], 10)
    const minutes = parseInt(parts[1], 10)
    if (isNaN(hours) || isNaN(minutes)) return null
    return (hours * 3600 + minutes * 60) * 1000
  } else if (parts.length === 3) {
    const hours = parseInt(parts[0], 10)
    const minutes = parseInt(parts[1], 10)
    const seconds = parseInt(parts[2], 10)
    if (isNaN(hours) || isNaN(minutes) || isNaN(seconds)) return null
    return (hours * 3600 + minutes * 60 + seconds) * 1000
  }
  return null
}

// Duration from form start/end (for display when editing)
const formDurationMs = computed(() => {
  const startParts = parseDateInput(editStartDate.value)
  const startTimeParts = parseTimeInput(editStartTime.value)
  if (!startParts || !startTimeParts) return null
  const startMs = new Date(startParts.year, startParts.month, startParts.day, startTimeParts.hours, startTimeParts.minutes).getTime()
  if (!editEndDate.value || !editEndTime.value) return Date.now() - startMs
  const endParts = parseDateInput(editEndDate.value)
  const endTimeParts = parseTimeInput(editEndTime.value)
  if (!endParts || !endTimeParts) return null
  const endMs = new Date(endParts.year, endParts.month, endParts.day, endTimeParts.hours, endTimeParts.minutes).getTime()
  return Math.max(0, endMs - startMs)
})

function toggleEditingTime() {
  editingTime.value = !editingTime.value
}

function onDurationInput() {
  const durationMs = parseHHmmss(editDuration.value)
  if (durationMs === null) return
  const startParts = parseDateInput(editStartDate.value)
  const startTimeParts = parseTimeInput(editStartTime.value)
  if (!startParts || !startTimeParts) return
  const startMs = new Date(startParts.year, startParts.month, startParts.day, startTimeParts.hours, startTimeParts.minutes).getTime()
  const endMs = startMs + durationMs
  const endDate = new Date(endMs)
  editEndDate.value = formatDateForInput(endDate)
  editEndTime.value = formatTimeForInput(endDate)
  hiddenEndDate.value = toISODate(endDate)
}

function toISODate(isoString) {
  const date = new Date(isoString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function startEdit() {
  editTag.value = props.timer.tag || ''
  editDescription.value = props.timer.description || ''
  editProjectId.value = props.timer.project_id ?? null
  editStartDate.value = formatDateForInput(props.timer.start_time)
  hiddenStartDate.value = toISODate(props.timer.start_time)
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
  const ms = props.timer.end_time
    ? new Date(props.timer.end_time).getTime() - new Date(props.timer.start_time).getTime()
    : Date.now() - new Date(props.timer.start_time).getTime()
  editDuration.value = formatHHmmss(ms)
  if (!props.timer.end_time) editStartedAt.value = Date.now()
  editError.value = ''
  editingTime.value = false
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
  editingTime.value = false
  editError.value = ''
  emit('cancel')
}

function saveEdit() {
  editError.value = ''
  let startDateTime
  let endTime = null

  if (!props.timer.end_time) {
    // Running timer: keep it running; add time spent editing to duration (like sidebar)
    const durationMs = parseHHmmss(editDuration.value)
    if (durationMs === null) {
      editError.value = 'Invalid duration. Use HH:mm:ss or HH:mm'
      return
    }
    const timeSinceEditStarted = Date.now() - editStartedAt.value
    const totalElapsedMs = durationMs + timeSinceEditStarted
    startDateTime = new Date(Date.now() - totalElapsedMs)
    endTime = null
  } else {
    // Stopped timer: use start/end from form
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
    startDateTime = new Date(startDateParts.year, startDateParts.month, startDateParts.day, startTimeParts.hours, startTimeParts.minutes)
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
      if (endDateTime <= startDateTime) {
        editError.value = 'End must be after start'
        return
      }
      endTime = endDateTime.toISOString()
    }
  }

  emit('update', props.timer.id, {
    tag: editTag.value || null,
    description: editDescription.value?.trim() || null,
    start_time: startDateTime.toISOString(),
    end_time: endTime,
    project_id: editProjectId.value ?? null
  })
  editingTime.value = false
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
            <span class="timer-project" v-if="projectDisplay">{{ projectDisplay }}</span>
            <p class="timer-description" v-if="timer.description">{{ timer.description }}</p>
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
        {{ formatDuration(liveElapsedMs ?? duration) }}
      </div>
    </template>

    <!-- Edit Mode (same content and styles as timer editor modal) -->
    <template v-else>
      <div
        class="selected-timer-display"
        :class="{ running: isRunning, 'editing-time': editingTime }"
        @click="!editingTime && toggleEditingTime()"
      >
        <button type="button" class="selected-timer-edit-btn" @click.stop="toggleEditingTime" title="Edit time">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
        </button>
        <template v-if="!editingTime">
          <div class="selected-timer-elapsed">{{ formatHHmmss(liveElapsedMs ?? formDurationMs ?? duration) }}</div>
          <div class="selected-timer-time-range">
            {{ editStartDate }} {{ editStartTime }}
            <span class="selected-timer-range-sep">–</span>
            <template v-if="editEndDate && editEndTime">{{ editEndDate }} {{ editEndTime }}</template>
            <span v-else class="selected-timer-running-label">Running</span>
          </div>
          <div class="selected-timer-meta" v-if="editTag">{{ editTag }}</div>
          <div class="selected-timer-meta" v-if="editProjectId != null && findProjectById(editProjectId)">{{ formatProjectLabel(findProjectById(editProjectId)) }}</div>
          <div class="selected-timer-meta description" v-if="editDescription">{{ editDescription }}</div>
        </template>
        <template v-else>
          <div class="selected-timer-inline-edit">
            <input
              v-model="editDuration"
              type="text"
              class="selected-timer-duration-inline"
              placeholder="HH:mm:ss"
              @input="onDurationInput"
              @click.stop
            />
            <div class="selected-timer-inline-datetime">
              <div class="selected-timer-datetime">
                <div class="date-input-wrapper">
                  <input v-model="editStartDate" type="text" :placeholder="getDatePlaceholder()" @input="onStartDateInput" @click.stop />
                  <button type="button" class="date-picker-btn" @click.stop="openStartDatePicker"><svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg></button>
                  <input ref="startDatePicker" type="date" v-model="hiddenStartDate" @change="onHiddenStartDateChange" class="hidden-date-picker" />
                </div>
                <input v-model="editStartTime" type="text" :placeholder="getTimePlaceholder()" @click.stop />
              </div>
              <div class="selected-timer-datetime">
                <div class="date-input-wrapper">
                  <input v-model="editEndDate" type="text" :placeholder="getDatePlaceholder()" @input="onEndDateInput" @click.stop />
                  <button type="button" class="date-picker-btn" @click.stop="openEndDatePicker"><svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg></button>
                  <input ref="endDatePicker" type="date" v-model="hiddenEndDate" @change="onHiddenEndDateChange" class="hidden-date-picker" />
                </div>
                <input v-model="editEndTime" type="text" :placeholder="getTimePlaceholder()" @click.stop />
              </div>
            </div>
          </div>
        </template>
      </div>
      <form class="selected-timer-form" @submit.prevent="saveEdit">
        <div class="form-group">
          <label>Tag</label>
          <TagInput
            v-model="editTag"
            :tags="tags"
            placeholder="Tag (optional)"
            @submit="saveEdit"
          />
        </div>
        <div class="form-group">
          <label>Description</label>
          <textarea
            v-model="editDescription"
            placeholder="Optional description..."
            rows="2"
            class="selected-timer-description-input"
          />
        </div>
        <div class="form-group">
          <label>Project</label>
          <ProjectSelector
            v-model="editProjectId"
            :projects="projects"
            :clients="clients"
            :onCreate="onCreateProject"
            placeholder="Project (optional)"
            @open-create-form="emit('openCreateForm')"
          />
        </div>
        <p v-if="editError" class="form-error">{{ editError }}</p>
        <div class="selected-timer-actions">
          <button type="button" @click="deleteTimer" class="selected-timer-delete-btn">Delete</button>
          <div class="selected-timer-actions-right">
            <button type="button" @click="cancelEdit" class="selected-timer-cancel-btn">Cancel</button>
            <button type="submit" class="selected-timer-save-btn">Save</button>
          </div>
        </div>
      </form>
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

.timer-description {
  font-size: 0.8125rem;
  color: var(--text-muted);
  margin: 0.25rem 0 0;
  white-space: pre-wrap;
  word-break: break-word;
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

/* Edit mode – same content and styles as timer editor modal */
.selected-timer-display {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 1.25rem 1.5rem;
  margin-bottom: 1.25rem;
  text-align: center;
  position: relative;
  cursor: pointer;
}

.selected-timer-display.editing-time {
  cursor: default;
  text-align: left;
}

.selected-timer-display.running {
  border-color: var(--timer-border);
  background: var(--timer-bg);
}

.selected-timer-edit-btn {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  padding: 0.35rem;
  background: transparent;
  border: none;
  border-radius: 6px;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.selected-timer-edit-btn:hover {
  color: var(--accent-color);
  background: var(--bg-tertiary);
}

.selected-timer-time-range {
  font-size: 0.8125rem;
  color: var(--text-secondary);
  margin-top: 0.5rem;
}

.selected-timer-range-sep {
  margin: 0 0.35rem;
  color: var(--text-muted);
}

.selected-timer-running-label {
  color: var(--accent-color);
}

.selected-timer-inline-edit {
  min-width: 0;
}

.selected-timer-duration-inline {
  display: block;
  width: 100%;
  margin-bottom: 0.75rem;
  padding: 0.5rem 0.75rem;
  font-size: 1.25rem;
  font-variant-numeric: tabular-nums;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-family: inherit;
  box-sizing: border-box;
}

.selected-timer-duration-inline:focus {
  outline: none;
  border-color: var(--accent-color);
}

.selected-timer-inline-datetime {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.selected-timer-elapsed {
  font-size: 2.25rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
  letter-spacing: 0.02em;
}

.selected-timer-display.running .selected-timer-elapsed {
  color: var(--accent-color);
}

.selected-timer-meta {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin-top: 0.375rem;
}

.selected-timer-meta.description {
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid var(--border-color);
}

.selected-timer-form {
  min-width: 0;
}

.selected-timer-form .form-group {
  margin-bottom: 0.75rem;
}

.selected-timer-form .form-group label {
  display: block;
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.25rem;
}

.selected-timer-datetime {
  display: flex;
  gap: 0.5rem;
  min-width: 0;
}

.selected-timer-datetime > .date-input-wrapper {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  position: relative;
}

.selected-timer-datetime > .date-input-wrapper input[type="text"] {
  flex: 1;
  min-width: 0;
  width: 0;
  padding: 0.625rem 2.5rem 0.625rem 0.75rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 0.9375rem;
  font-family: inherit;
  box-sizing: border-box;
}

.selected-timer-datetime > .date-input-wrapper input[type="text"]:focus {
  outline: none;
  border-color: var(--accent-color);
}

.selected-timer-datetime > input[type="text"] {
  flex: 0 1 auto;
  min-width: 0;
  width: 5.5rem;
  max-width: 50%;
  padding: 0.625rem 0.75rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 0.9375rem;
  font-family: inherit;
  box-sizing: border-box;
}

.selected-timer-datetime > input[type="text"]:focus {
  outline: none;
  border-color: var(--accent-color);
}

.selected-timer-form .selected-timer-description-input {
  width: 100%;
  padding: 0.625rem 0.75rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 0.9375rem;
  font-family: inherit;
  resize: vertical;
  min-height: 4rem;
  line-height: 1.4;
}

.selected-timer-form .selected-timer-description-input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.selected-timer-form .selected-timer-description-input::placeholder {
  color: var(--text-muted);
}

.selected-timer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-color);
}

.selected-timer-actions-right {
  display: flex;
  gap: 0.75rem;
}

.selected-timer-delete-btn {
  background: transparent;
  border: 1px solid var(--danger-color);
  color: var(--danger-color);
  padding: 0.5rem 1rem;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
}

.selected-timer-delete-btn:hover {
  background: var(--danger-color);
  color: #fff;
}

.selected-timer-cancel-btn {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  padding: 0.625rem 1.25rem;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
}

.selected-timer-cancel-btn:hover {
  border-color: var(--border-light);
  color: var(--text-primary);
}

.selected-timer-save-btn {
  background: var(--accent-color);
  border: none;
  color: #fff;
  padding: 0.625rem 1.25rem;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
}

.selected-timer-save-btn:hover {
  background: var(--accent-hover);
}

.selected-timer-form .form-error {
  color: var(--danger-color);
  font-size: 0.875rem;
  margin: 0.5rem 0 0 0;
}

.hidden-date-picker {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
  pointer-events: none;
}

.date-input-wrapper {
  flex: 1;
  position: relative;
  display: flex;
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
</style>
