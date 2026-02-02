<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  timer: Object
})

const emit = defineEmits(['update', 'delete'])

const isEditing = ref(false)
const editTag = ref('')
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

function formatDuration(ms) {
  const totalSeconds = Math.floor(ms / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function formatTime(isoString) {
  const date = new Date(isoString)
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function formatDate(isoString) {
  const date = new Date(isoString)
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  
  if (date.toDateString() === today.toDateString()) {
    return 'Today'
  }
  if (date.toDateString() === yesterday.toDateString()) {
    return 'Yesterday'
  }
  return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

function toLocalDateString(isoString) {
  const date = new Date(isoString)
  return date.toISOString().split('T')[0]
}

function toLocalTimeString(isoString) {
  const date = new Date(isoString)
  return date.toTimeString().slice(0, 5)
}

function startEdit() {
  editTag.value = props.timer.tag || ''
  editStartDate.value = toLocalDateString(props.timer.start_time)
  editStartTime.value = toLocalTimeString(props.timer.start_time)
  if (props.timer.end_time) {
    editEndDate.value = toLocalDateString(props.timer.end_time)
    editEndTime.value = toLocalTimeString(props.timer.end_time)
  } else {
    editEndDate.value = ''
    editEndTime.value = ''
  }
  isEditing.value = true
}

function cancelEdit() {
  isEditing.value = false
}

function saveEdit() {
  const startTime = new Date(`${editStartDate.value}T${editStartTime.value}`).toISOString()
  let endTime = null
  
  if (editEndDate.value && editEndTime.value) {
    endTime = new Date(`${editEndDate.value}T${editEndTime.value}`).toISOString()
  }
  
  emit('update', props.timer.id, {
    tag: editTag.value || null,
    start_time: startTime,
    end_time: endTime
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
          <span class="timer-tag" v-if="timer.tag">{{ timer.tag }}</span>
          <span class="timer-tag empty" v-else>No tag</span>
          <span class="timer-date">{{ formatDate(timer.start_time) }}</span>
        </div>
        <div class="timer-times">
          <span>{{ formatTime(timer.start_time) }}</span>
          <span class="separator">—</span>
          <span v-if="timer.end_time">{{ formatTime(timer.end_time) }}</span>
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
          <label>Start</label>
          <div class="datetime-inputs">
            <input v-model="editStartDate" type="date" required />
            <input v-model="editStartTime" type="time" required />
          </div>
        </div>
        
        <div class="edit-row">
          <label>End</label>
          <div class="datetime-inputs">
            <input v-model="editEndDate" type="date" />
            <input v-model="editEndTime" type="time" />
          </div>
        </div>

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
}

.timer-item.running {
  border-color: var(--timer-border);
  background: var(--timer-bg);
}

.timer-item.editing {
  flex-direction: column;
  align-items: stretch;
}

.timer-main {
  flex: 1;
  min-width: 0;
}

.timer-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.25rem;
}

.timer-tag {
  font-weight: 500;
  color: var(--text-primary);
}

.timer-tag.empty {
  color: var(--text-muted);
  font-style: italic;
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

.datetime-inputs input {
  flex: 1;
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

.delete-btn:active {
  background: var(--danger-color);
  color: #fff;
}

.cancel-btn {
  background: transparent;
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
}

.cancel-btn:active {
  border-color: var(--border-color);
  color: var(--text-primary);
}

.save-btn {
  background: var(--accent-color);
  border: none;
  color: #fff;
}

.save-btn:active {
  background: var(--accent-hover);
}
</style>
