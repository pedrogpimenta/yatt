<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { api } from '../api.js'
import { 
  preferences, 
  formatDateLabel,
  formatTimeForInput,
  formatDateForInput,
  parseTimeInput,
  parseDateInput,
  getTimePlaceholder,
  getDatePlaceholder,
  getEffectiveDate,
  getEffectiveDateString,
  getEffectiveTodayStart,
  getEffectiveWeekStart
} from '../preferences.js'

function toDateKey(date) {
  const d = typeof date === 'string' ? new Date(date) : date
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
import TimerItem from './TimerItem.vue'
import WeeklyCalendar from './WeeklyCalendar.vue'
import TagInput from './TagInput.vue'
import ProjectSelector from './ProjectSelector.vue'
import { formatProjectLabel } from '../projects.js'

const emit = defineEmits(['openSettings'])

const timers = ref([])
const loading = ref(true)
const error = ref('')
const newTag = ref('')
const newDescription = ref('')
const tags = ref([])
const projects = ref([])
const clients = ref([])
const newProjectId = ref(null)
const viewMode = ref('calendar') // 'list' or 'calendar'
const selectedTimer = ref(null)
const selectedTimerFromCalendar = ref(false)
const filterTag = ref('') // '' means all tags
const dailyGoals = ref({}) // date key -> hours

// Modal stack to track open modals (for Escape key handling)
const modalStack = ref([])

function pushModal(name) {
  modalStack.value.push(name)
}

function popModal(name) {
  const index = modalStack.value.lastIndexOf(name)
  if (index !== -1) {
    modalStack.value.splice(index, 1)
  }
}

function closeTopModal() {
  if (modalStack.value.length === 0) return
  
  const topModal = modalStack.value[modalStack.value.length - 1]
  
  if (topModal === 'manualEntry') {
    closeManualEntry()
  } else if (topModal === 'selectedTimer') {
    closeSelectedTimer()
  } else if (topModal === 'dayGoal') {
    closeDayGoalModal()
  }
}

function handleKeydown(e) {
  if (e.key === 'Escape') {
    closeTopModal()
  }
}

// Manual entry modal
const showManualEntry = ref(false)
const manualEntry = ref({
  startDate: '',
  startTime: '',
  endDate: '',
  endTime: '',
  tag: '',
  description: '',
  projectId: null
})
const manualEndDateSynced = ref(true)
const manualStartDatePicker = ref(null)
const manualEndDatePicker = ref(null)
const hiddenManualStartDate = ref('')
const hiddenManualEndDate = ref('')

function toISODateString(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getDefaultTime(hours) {
  // Create a date with the specified hour to format it according to preferences
  const date = new Date()
  date.setHours(hours, 0, 0, 0)
  return formatTimeForInput(date)
}

function openManualEntry() {
  // Default to today's date
  const today = new Date()
  const isoDate = toISODateString(today)
  const formattedDate = formatDateForInput(today)
  manualEntry.value = {
    startDate: formattedDate,
    startTime: getDefaultTime(9),
    endDate: formattedDate,
    endTime: getDefaultTime(10),
    tag: '',
    description: '',
    projectId: newProjectId.value
  }
  hiddenManualStartDate.value = isoDate
  hiddenManualEndDate.value = isoDate
  manualEndDateSynced.value = true
  error.value = ''
  showManualEntry.value = true
  pushModal('manualEntry')
}

function closeManualEntry() {
  showManualEntry.value = false
  popModal('manualEntry')
}

function openManualStartDatePicker() {
  manualStartDatePicker.value?.showPicker()
}

function openManualEndDatePicker() {
  manualEndDatePicker.value?.showPicker()
}

function onHiddenManualStartDateChange() {
  if (hiddenManualStartDate.value) {
    const [year, month, day] = hiddenManualStartDate.value.split('-').map(Number)
    const date = new Date(year, month - 1, day)
    manualEntry.value.startDate = formatDateForInput(date)
    if (manualEndDateSynced.value) {
      hiddenManualEndDate.value = hiddenManualStartDate.value
      manualEntry.value.endDate = manualEntry.value.startDate
    }
  }
}

function onHiddenManualEndDateChange() {
  if (hiddenManualEndDate.value) {
    const [year, month, day] = hiddenManualEndDate.value.split('-').map(Number)
    const date = new Date(year, month - 1, day)
    manualEntry.value.endDate = formatDateForInput(date)
    manualEndDateSynced.value = hiddenManualEndDate.value === hiddenManualStartDate.value
  }
}

function onManualStartDateInput() {
  const parsed = parseDateInput(manualEntry.value.startDate)
  if (parsed) {
    hiddenManualStartDate.value = `${parsed.year}-${String(parsed.month + 1).padStart(2, '0')}-${String(parsed.day).padStart(2, '0')}`
    if (manualEndDateSynced.value) {
      hiddenManualEndDate.value = hiddenManualStartDate.value
      manualEntry.value.endDate = manualEntry.value.startDate
    }
  }
}

function onManualEndDateInput() {
  const parsed = parseDateInput(manualEntry.value.endDate)
  if (parsed) {
    hiddenManualEndDate.value = `${parsed.year}-${String(parsed.month + 1).padStart(2, '0')}-${String(parsed.day).padStart(2, '0')}`
    manualEndDateSynced.value = hiddenManualEndDate.value === hiddenManualStartDate.value
  }
}

async function saveManualEntry() {
  error.value = ''
  
  const { startDate, startTime, endDate, endTime, tag, description, projectId } = manualEntry.value
  
  if (!startDate || !startTime || !endDate || !endTime) {
    error.value = 'Please fill in all required fields'
    return
  }
  
  const startDateParts = parseDateInput(startDate)
  if (!startDateParts) {
    error.value = `Invalid start date. Use ${getDatePlaceholder()}`
    return
  }
  
  const startTimeParts = parseTimeInput(startTime)
  if (!startTimeParts) {
    error.value = `Invalid start time. Use ${getTimePlaceholder()}`
    return
  }
  
  const endDateParts = parseDateInput(endDate)
  if (!endDateParts) {
    error.value = `Invalid end date. Use ${getDatePlaceholder()}`
    return
  }
  
  const endTimeParts = parseTimeInput(endTime)
  if (!endTimeParts) {
    error.value = `Invalid end time. Use ${getTimePlaceholder()}`
    return
  }
  
  const startDateTime = new Date(startDateParts.year, startDateParts.month, startDateParts.day, startTimeParts.hours, startTimeParts.minutes)
  const endDateTime = new Date(endDateParts.year, endDateParts.month, endDateParts.day, endTimeParts.hours, endTimeParts.minutes)
  
  if (endDateTime <= startDateTime) {
    error.value = 'End time must be after start time'
    return
  }
  
  try {
    await api.createTimer({
      start_time: startDateTime.toISOString(),
      end_time: endDateTime.toISOString(),
      tag: tag || null,
      description: description?.trim() || null,
      project_id: projectId || null
    })
    
    // Refresh tags if it's a new one
    if (tag && !tags.value.includes(tag)) {
      fetchTags()
    }
    
    await fetchTimers()
    await updatePendingSyncCount()
    closeManualEntry()
  } catch (err) {
    error.value = err.message
  }
}

// Offline status
const isOnline = ref(api.getOnlineStatus())
const pendingSyncCount = ref(0)
const syncing = ref(false)

let tickInterval = null

const runningTimer = computed(() => {
  return timers.value.find(t => !t.end_time)
})

const isRunning = computed(() => !!runningTimer.value)

function findProjectById(id) {
  if (id === null || id === undefined) return null
  return projects.value.find((project) => String(project.id) === String(id)) || null
}

const runningProjectLabel = computed(() => {
  if (!runningTimer.value) return ''
  const project = findProjectById(runningTimer.value.project_id)
  return project ? formatProjectLabel(project) : ''
})

const currentElapsed = ref(0)
const isEditingElapsed = ref(false)
const editElapsedValue = ref('')
const editStartedAt = ref(0)

// Start time editing for running timer
const editStartTime = ref('')
const isEditingStartTime = ref(false)

const displayStartTime = computed(() => {
  if (!runningTimer.value) return ''
  return formatTimeForInput(new Date(runningTimer.value.start_time))
})

function startEditStartTime() {
  if (!runningTimer.value) return
  editStartTime.value = displayStartTime.value
  isEditingStartTime.value = true
}

function cancelEditStartTime() {
  isEditingStartTime.value = false
}

async function saveEditStartTime() {
  if (!runningTimer.value) return
  
  const timeParts = parseTimeInput(editStartTime.value)
  if (!timeParts) {
    error.value = `Invalid time format. Use ${getTimePlaceholder()}`
    return
  }
  
  const originalStart = new Date(runningTimer.value.start_time)
  const newStart = new Date(originalStart)
  newStart.setHours(timeParts.hours, timeParts.minutes, 0, 0)
  
  // Don't allow start time in the future
  if (newStart > new Date()) {
    error.value = 'Start time cannot be in the future'
    return
  }
  
  try {
    await api.updateTimer(runningTimer.value.id, { start_time: newStart.toISOString() })
    await fetchTimers()
    isEditingStartTime.value = false
    error.value = ''
    await updatePendingSyncCount()
  } catch (err) {
    error.value = err.message
  }
}


function updateCurrentElapsed() {
  if (runningTimer.value) {
    const start = new Date(runningTimer.value.start_time).getTime()
    currentElapsed.value = Date.now() - start
    if (selectedTimer.value && String(selectedTimer.value.id) === String(runningTimer.value.id)) {
      selectedTimerLiveMs.value = Date.now() - start
    }
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
  
  const timeSinceEditStarted = Date.now() - editStartedAt.value
  const totalElapsedMs = enteredMs + timeSinceEditStarted
  
  const newStartTime = new Date(Date.now() - totalElapsedMs).toISOString()
  
  try {
    await api.updateTimer(runningTimer.value.id, { start_time: newStartTime })
    await fetchTimers()
    isEditingElapsed.value = false
    await updatePendingSyncCount()
  } catch (err) {
    error.value = err.message
  }
}

function onWsMessage(data) {
  if (data.type === 'timer') {
    fetchTimers()
  }
}

function onOnlineStatusChange(online) {
  isOnline.value = online
  if (online) {
    // Trigger sync when back online
    syncPendingChanges()
  }
}

async function updatePendingSyncCount() {
  pendingSyncCount.value = await api.getPendingSyncCount()
}

async function syncPendingChanges() {
  if (syncing.value || !isOnline.value) return
  
  syncing.value = true
  error.value = ''
  
  try {
    const result = await api.attemptSync()
    if (result.synced > 0) {
      await fetchTimers()
      await fetchTags()
      await fetchProjects()
      await fetchClients()
    }
  } catch (err) {
    error.value = 'Sync failed: ' + err.message
  } finally {
    syncing.value = false
    await updatePendingSyncCount()
  }
}

const todayTotal = computed(() => {
  // Track dependencies for reactivity
  const _ = preferences.dayStartHour
  const __ = currentElapsed.value // Triggers recalc for running timers
  
  const todayStart = getEffectiveTodayStart()
  const tomorrowStart = new Date(todayStart)
  tomorrowStart.setDate(tomorrowStart.getDate() + 1)
  
  let total = 0
  const now = Date.now()
  
  for (const timer of timers.value) {
    const timerStart = new Date(timer.start_time).getTime()
    const timerEnd = timer.end_time ? new Date(timer.end_time).getTime() : now
    
    // Calculate overlap between timer and today's window
    const overlapStart = Math.max(timerStart, todayStart.getTime())
    const overlapEnd = Math.min(timerEnd, tomorrowStart.getTime())
    
    if (overlapEnd > overlapStart) {
      total += overlapEnd - overlapStart
    }
  }
  return total
})

const weekTotal = computed(() => {
  // Track dependencies for reactivity
  const _ = preferences.dayStartHour
  const __ = currentElapsed.value // Triggers recalc for running timers
  
  const weekStart = getEffectiveWeekStart()
  
  let total = 0
  const now = Date.now()
  
  for (const timer of timers.value) {
    const timerStart = new Date(timer.start_time).getTime()
    const timerEnd = timer.end_time ? new Date(timer.end_time).getTime() : now
    
    // Calculate overlap between timer and this week's window
    const overlapStart = Math.max(timerStart, weekStart.getTime())
    const overlapEnd = timerEnd
    
    if (overlapEnd > overlapStart) {
      total += overlapEnd - overlapStart
    }
  }
  return total
})

// Daily goal: today and week goals + remaining
const effectiveTodayDate = computed(() => {
  const _ = preferences.dayStartHour
  return getEffectiveDate(new Date())
})
const todayDateKey = computed(() => toDateKey(effectiveTodayDate.value))
const weekStartDate = computed(() => getEffectiveWeekStart())
const todayGoalHours = computed(() => {
  if (!preferences.dailyGoalEnabled) return null
  const d = effectiveTodayDate.value
  const day = d.getDay() // 0 Sun, 6 Sat
  if (!preferences.includeWeekendGoals && (day === 0 || day === 6)) return null
  return dailyGoals.value[todayDateKey.value] ?? preferences.defaultDailyGoalHours ?? 8
})
const weekGoalHours = computed(() => {
  if (!preferences.dailyGoalEnabled) return null
  const includeWeekend = preferences.includeWeekendGoals
  let sum = 0
  const start = new Date(weekStartDate.value)
  for (let i = 0; i < 7; i++) {
    const d = new Date(start)
    d.setDate(d.getDate() + i)
    if (!includeWeekend) {
      const day = d.getDay()
      if (day === 0 || day === 6) continue
    }
    const key = toDateKey(d)
    sum += dailyGoals.value[key] ?? preferences.defaultDailyGoalHours ?? 8
  }
  return sum
})
const todayRemainingMs = computed(() => {
  const goal = todayGoalHours.value
  if (goal == null) return null
  const goalMs = goal * 3600000
  return Math.max(0, goalMs - todayTotal.value)
})
const weekRemainingMs = computed(() => {
  const goal = weekGoalHours.value
  if (goal == null) return null
  const goalMs = goal * 3600000
  return Math.max(0, goalMs - weekTotal.value)
})

const filteredTimers = computed(() => {
  if (!filterTag.value) {
    return timers.value
  }
  return timers.value.filter(t => t.tag === filterTag.value)
})

const filteredTotal = computed(() => {
  const elapsed = currentElapsed.value
  
  let total = 0
  for (const timer of filteredTimers.value) {
    const start = new Date(timer.start_time).getTime()
    if (timer.end_time) {
      total += new Date(timer.end_time).getTime() - start
    } else {
      total += elapsed
    }
  }
  return total
})

const timersByDay = computed(() => {
  // Reference preferences and currentElapsed to track as dependencies for reactivity
  const _ = preferences.dateFormat
  const dayStartHour = preferences.dayStartHour || 0
  const __ = currentElapsed.value // Triggers recalc for running timers
  const now = Date.now()
  
  const groups = []
  let currentDateStr = null
  
  for (const timer of filteredTimers.value) {
    const effectiveDate = getEffectiveDate(timer.start_time)
    const dateStr = effectiveDate.toDateString()
    
    if (dateStr !== currentDateStr) {
      currentDateStr = dateStr
      groups.push({
        date: effectiveDate,
        label: formatDateLabel(effectiveDate),
        timers: [timer]
      })
    } else {
      groups[groups.length - 1].timers.push(timer)
    }
  }
  
  // Calculate total for each day using overlap with day boundaries
  for (const group of groups) {
    // Calculate day boundaries for this group
    const dayStart = new Date(group.date)
    dayStart.setHours(dayStartHour, 0, 0, 0)
    const dayEnd = new Date(dayStart)
    dayEnd.setDate(dayEnd.getDate() + 1)
    
    let dayTotal = 0
    for (const timer of group.timers) {
      const timerStart = new Date(timer.start_time).getTime()
      const timerEnd = timer.end_time ? new Date(timer.end_time).getTime() : now
      
      // Calculate overlap between timer and this day's window
      const overlapStart = Math.max(timerStart, dayStart.getTime())
      const overlapEnd = Math.min(timerEnd, dayEnd.getTime())
      
      if (overlapEnd > overlapStart) {
        dayTotal += overlapEnd - overlapStart
      }
    }
    group.total = dayTotal
  }
  
  return groups
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
      newDescription.value = runningTimer.value.description || ''
      newProjectId.value = runningTimer.value.project_id || null
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

async function fetchProjects() {
  try {
    projects.value = await api.getProjects()
  } catch (err) {
    console.error('Failed to fetch projects:', err)
  }
}

async function fetchClients() {
  try {
    clients.value = await api.getClients()
  } catch (err) {
    console.error('Failed to fetch clients:', err)
  }
}

async function fetchDailyGoals() {
  if (!preferences.dailyGoalEnabled) {
    dailyGoals.value = {}
    return
  }
  const start = getEffectiveWeekStart()
  const from = toDateKey(start)
  const toDate = new Date(start)
  toDate.setDate(toDate.getDate() + 6)
  const to = toDateKey(toDate)
  try {
    dailyGoals.value = await api.getDailyGoals(from, to) || {}
  } catch (err) {
    dailyGoals.value = {}
  }
}

const showDayGoalModal = ref(false)
const dayGoalDate = ref(null)
const dayGoalHoursInput = ref('')
const dayGoalError = ref('')

function openDayGoalModal(date) {
  if (!preferences.dailyGoalEnabled) return
  dayGoalDate.value = date
  const key = toDateKey(date)
  const override = dailyGoals.value[key]
  dayGoalHoursInput.value = override != null ? String(override) : ''
  showDayGoalModal.value = true
  pushModal('dayGoal')
}

function closeDayGoalModal() {
  showDayGoalModal.value = false
  dayGoalDate.value = null
  dayGoalError.value = ''
  popModal('dayGoal')
}

async function saveDayGoal() {
  if (!dayGoalDate.value) return
  dayGoalError.value = ''
  const raw = String(dayGoalHoursInput.value ?? '').trim()
  if (raw === '') {
    try {
      await api.clearDailyGoal(dayGoalDate.value)
      const key = toDateKey(dayGoalDate.value)
      const next = { ...dailyGoals.value }
      delete next[key]
      dailyGoals.value = next
      closeDayGoalModal()
    } catch (err) {
      dayGoalError.value = err.message || 'Failed to clear goal'
    }
    return
  }
  const hours = parseFloat(raw)
  if (isNaN(hours) || hours < 0 || hours > 24) {
    dayGoalError.value = 'Enter a number between 0 and 24'
    return
  }
  try {
    await api.setDailyGoal(dayGoalDate.value, hours)
    const key = toDateKey(dayGoalDate.value)
    dailyGoals.value = { ...dailyGoals.value, [key]: hours }
    closeDayGoalModal()
  } catch (err) {
    dayGoalError.value = err.message || 'Failed to save goal'
  }
}

async function refetch() {
  await fetchTimers()
  await fetchProjects()
  await fetchClients()
  await fetchTags()
  await fetchDailyGoals()
}

defineExpose({ refetch })

async function handleCreateProject(payload) {
  const created = await api.createProject(payload)
  if (created) {
    const existingIndex = projects.value.findIndex((project) => String(project.id) === String(created.id))
    if (existingIndex === -1) {
      projects.value = [...projects.value, created]
    } else {
      projects.value.splice(existingIndex, 1, created)
    }
    await fetchClients()
  }
  return created
}

async function toggleTimer() {
  error.value = ''
  try {
    if (isRunning.value) {
      await api.stopTimer(runningTimer.value.id)
      newTag.value = ''
    } else {
      await api.createTimer({
        tag: newTag.value || null,
        description: newDescription.value?.trim() || null,
        project_id: newProjectId.value || null
      })
      // Refresh tags after creating a timer with a potentially new tag
      if (newTag.value && !tags.value.includes(newTag.value)) {
        fetchTags()
      }
    }
    await fetchTimers()
    await updatePendingSyncCount()
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
    await updatePendingSyncCount()
  } catch (err) {
    error.value = err.message
  }
}

async function updateRunningProject() {
  if (!runningTimer.value) return
  error.value = ''
  try {
    await api.updateTimer(runningTimer.value.id, { project_id: newProjectId.value || null })
    await fetchTimers()
    await updatePendingSyncCount()
  } catch (err) {
    error.value = err.message
  }
}

async function updateRunningDescription() {
  if (!runningTimer.value) return
  error.value = ''
  try {
    await api.updateTimer(runningTimer.value.id, { description: newDescription.value?.trim() || null })
    await fetchTimers()
    await updatePendingSyncCount()
  } catch (err) {
    error.value = err.message
  }
}

function onDescriptionBlur() {
  if (isRunning.value) {
    updateRunningDescription()
  }
}

watch(newProjectId, async (newValue) => {
  if (!runningTimer.value) return
  const currentProjectId = runningTimer.value.project_id ?? null
  const normalizedNewValue = newValue ?? null
  if (String(currentProjectId) === String(normalizedNewValue)) {
    return
  }
  await updateRunningProject()
})

async function handleUpdate(id, data) {
  error.value = ''
  try {
    await api.updateTimer(id, data)
    await fetchTimers()
    closeSelectedTimer()
    await updatePendingSyncCount()
  } catch (err) {
    error.value = err.message
  }
}

async function handleDelete(id) {
  error.value = ''
  try {
    await api.deleteTimer(id)
    await fetchTimers()
    closeSelectedTimer()
    await updatePendingSyncCount()
  } catch (err) {
    error.value = err.message
  }
}

// Selected timer modal (calendar): big timer + start/end + duration-based edit
const selectedTimerEditDuration = ref('')
const selectedTimerEditStartDate = ref('')
const selectedTimerEditStartTime = ref('')
const selectedTimerEditEndDate = ref('')
const selectedTimerEditEndTime = ref('')
const selectedTimerEditTag = ref('')
const selectedTimerEditDescription = ref('')
const selectedTimerEditProjectId = ref(null)
const selectedTimerLiveMs = ref(0)
const selectedTimerHiddenStartDate = ref('')
const selectedTimerHiddenEndDate = ref('')
const selectedTimerStartDatePicker = ref(null)
const selectedTimerEndDatePicker = ref(null)
const selectedTimerEditingTime = ref(false)
/** When user opened the modal for a running timer; used to add time spent editing to duration on save */
const selectedTimerEditStartedAt = ref(0)

const selectedTimerDisplayDurationMs = computed(() => {
  const t = selectedTimer.value
  if (!t) return 0
  if (!t.end_time) return selectedTimerLiveMs.value
  return new Date(t.end_time).getTime() - new Date(t.start_time).getTime()
})

// Duration from form start/end (for display when editing); fallback to timer duration
const selectedTimerFormDurationMs = computed(() => {
  const startParts = parseDateInput(selectedTimerEditStartDate.value)
  const startTimeParts = parseTimeInput(selectedTimerEditStartTime.value)
  if (!startParts || !startTimeParts) return null
  const startMs = new Date(startParts.year, startParts.month, startParts.day, startTimeParts.hours, startTimeParts.minutes).getTime()
  if (!selectedTimerEditEndDate.value || !selectedTimerEditEndTime.value) {
    const t = selectedTimer.value
    if (t?.end_time) return null
    return Date.now() - startMs
  }
  const endParts = parseDateInput(selectedTimerEditEndDate.value)
  const endTimeParts = parseTimeInput(selectedTimerEditEndTime.value)
  if (!endParts || !endTimeParts) return null
  const endMs = new Date(endParts.year, endParts.month, endParts.day, endTimeParts.hours, endTimeParts.minutes).getTime()
  return Math.max(0, endMs - startMs)
})

watch(selectedTimer, (t) => {
  if (!t) return
  const ms = t.end_time
    ? new Date(t.end_time).getTime() - new Date(t.start_time).getTime()
    : Date.now() - new Date(t.start_time).getTime()
  selectedTimerEditDuration.value = formatHHmmss(ms)
  selectedTimerEditStartDate.value = formatDateForInput(t.start_time)
  selectedTimerEditStartTime.value = formatTimeForInput(t.start_time)
  selectedTimerHiddenStartDate.value = toISODateString(new Date(t.start_time))
  if (t.end_time) {
    selectedTimerEditEndDate.value = formatDateForInput(t.end_time)
    selectedTimerEditEndTime.value = formatTimeForInput(t.end_time)
    selectedTimerHiddenEndDate.value = toISODateString(new Date(t.end_time))
  } else {
    selectedTimerEditEndDate.value = ''
    selectedTimerEditEndTime.value = ''
    selectedTimerHiddenEndDate.value = ''
  }
  selectedTimerEditTag.value = t.tag || ''
  selectedTimerEditDescription.value = t.description || ''
  selectedTimerEditProjectId.value = t.project_id ?? null
  if (!t.end_time) {
    selectedTimerLiveMs.value = ms
    selectedTimerEditStartedAt.value = Date.now()
  }
  selectedTimerEditingTime.value = false
})

function selectedTimerOpenStartDatePicker() {
  selectedTimerStartDatePicker.value?.showPicker()
}
function selectedTimerOpenEndDatePicker() {
  selectedTimerEndDatePicker.value?.showPicker()
}
function onSelectedTimerHiddenStartDateChange() {
  if (selectedTimerHiddenStartDate.value) {
    const [y, m, d] = selectedTimerHiddenStartDate.value.split('-').map(Number)
    const date = new Date(y, m - 1, d)
    selectedTimerEditStartDate.value = formatDateForInput(date)
  }
}
function onSelectedTimerHiddenEndDateChange() {
  if (selectedTimerHiddenEndDate.value) {
    const [y, m, d] = selectedTimerHiddenEndDate.value.split('-').map(Number)
    const date = new Date(y, m - 1, d)
    selectedTimerEditEndDate.value = formatDateForInput(date)
  }
}
function onSelectedTimerDurationInput() {
  const durationMs = parseHHmmss(selectedTimerEditDuration.value)
  if (durationMs === null) return
  const startParts = parseDateInput(selectedTimerEditStartDate.value)
  const startTimeParts = parseTimeInput(selectedTimerEditStartTime.value)
  if (!startParts || !startTimeParts) return
  const startMs = new Date(startParts.year, startParts.month, startParts.day, startTimeParts.hours, startTimeParts.minutes).getTime()
  const endMs = startMs + durationMs
  const endDate = new Date(endMs)
  selectedTimerEditEndDate.value = formatDateForInput(endDate)
  selectedTimerEditEndTime.value = formatTimeForInput(endDate)
  selectedTimerHiddenEndDate.value = toISODateString(endDate)
}

function onSelectedTimerStartDateInput() {
  const parsed = parseDateInput(selectedTimerEditStartDate.value)
  if (parsed) {
    selectedTimerHiddenStartDate.value = `${parsed.year}-${String(parsed.month + 1).padStart(2, '0')}-${String(parsed.day).padStart(2, '0')}`
  }
}
function onSelectedTimerEndDateInput() {
  const parsed = parseDateInput(selectedTimerEditEndDate.value)
  if (parsed) {
    selectedTimerHiddenEndDate.value = `${parsed.year}-${String(parsed.month + 1).padStart(2, '0')}-${String(parsed.day).padStart(2, '0')}`
  }
}

function selectTimerFromCalendar(timer) {
  selectedTimer.value = timer
  selectedTimerFromCalendar.value = true
  pushModal('selectedTimer')
}

function closeSelectedTimer() {
  selectedTimer.value = null
  selectedTimerFromCalendar.value = false
  selectedTimerEditingTime.value = false
  popModal('selectedTimer')
}

function toggleSelectedTimerEditTime() {
  selectedTimerEditingTime.value = !selectedTimerEditingTime.value
}

async function handleSelectedTimerSave() {
  const t = selectedTimer.value
  if (!t) return
  error.value = ''
  let startDateTime
  let endTime = null

  if (!t.end_time) {
    // Running timer: keep it running; add time spent editing to duration (like sidebar)
    const durationMs = parseHHmmss(selectedTimerEditDuration.value)
    if (durationMs === null) {
      error.value = 'Invalid duration. Use HH:mm:ss or HH:mm'
      return
    }
    const timeSinceEditStarted = Date.now() - selectedTimerEditStartedAt.value
    const totalElapsedMs = durationMs + timeSinceEditStarted
    startDateTime = new Date(Date.now() - totalElapsedMs)
    endTime = null
  } else {
    // Stopped timer: use start/end from form
    const startDateParts = parseDateInput(selectedTimerEditStartDate.value)
    const startTimeParts = parseTimeInput(selectedTimerEditStartTime.value)
    if (!startDateParts || !startTimeParts) {
      error.value = `Invalid start. Use ${getDatePlaceholder()} and ${getTimePlaceholder()}`
      return
    }
    startDateTime = new Date(startDateParts.year, startDateParts.month, startDateParts.day, startTimeParts.hours, startTimeParts.minutes)
    if (selectedTimerEditEndDate.value && selectedTimerEditEndTime.value) {
      const endDateParts = parseDateInput(selectedTimerEditEndDate.value)
      const endTimeParts = parseTimeInput(selectedTimerEditEndTime.value)
      if (!endDateParts || !endTimeParts) {
        error.value = `Invalid end. Use ${getDatePlaceholder()} and ${getTimePlaceholder()}`
        return
      }
      const endDateTime = new Date(endDateParts.year, endDateParts.month, endDateParts.day, endTimeParts.hours, endTimeParts.minutes)
      if (endDateTime <= startDateTime) {
        error.value = 'End must be after start'
        return
      }
      endTime = endDateTime.toISOString()
    }
  }

  try {
    await api.updateTimer(t.id, {
      start_time: startDateTime.toISOString(),
      end_time: endTime,
      tag: selectedTimerEditTag.value || null,
      description: selectedTimerEditDescription.value?.trim() || null,
      project_id: selectedTimerEditProjectId.value ?? null
    })
    await fetchTimers()
    selectedTimerEditingTime.value = false
    closeSelectedTimer()
    await updatePendingSyncCount()
  } catch (err) {
    error.value = err.message
  }
}

async function handleSelectedTimerDelete() {
  const t = selectedTimer.value
  if (!t || !confirm('Delete this timer?')) return
  error.value = ''
  try {
    await api.deleteTimer(t.id)
    await fetchTimers()
    closeSelectedTimer()
    await updatePendingSyncCount()
  } catch (err) {
    error.value = err.message
  }
}

watch(() => [preferences.dailyGoalEnabled, preferences.includeWeekendGoals], () => {
  fetchDailyGoals()
}, { immediate: false })

onMounted(() => {
  fetchTimers()
  fetchTags()
  fetchProjects()
  fetchClients()
  fetchDailyGoals()
  updatePendingSyncCount()
  tickInterval = setInterval(() => {
    updateCurrentElapsed()
  }, 1000)
  
  api.addWsListener(onWsMessage)
  api.addOnlineListener(onOnlineStatusChange)
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  if (tickInterval) {
    clearInterval(tickInterval)
  }
  api.removeWsListener(onWsMessage)
  api.removeOnlineListener(onOnlineStatusChange)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="layout">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <h1 class="logo">Time Command</h1>
        <div class="header-actions">
          <!-- Offline/Sync indicator -->
          <div class="sync-status" :class="{ offline: !isOnline, syncing: syncing }">
            <button 
              v-if="pendingSyncCount > 0 && isOnline" 
              @click="syncPendingChanges" 
              class="sync-btn"
              :disabled="syncing"
              :title="syncing ? 'Syncing...' : `Sync ${pendingSyncCount} pending changes`"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" :class="{ spinning: syncing }">
                <polyline points="23 4 23 10 17 10"></polyline>
                <polyline points="1 20 1 14 7 14"></polyline>
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
              </svg>
              <span class="sync-count">{{ pendingSyncCount }}</span>
            </button>
            <div v-else-if="!isOnline" class="offline-indicator" title="Offline - changes will sync when online">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="1" y1="1" x2="23" y2="23"></line>
                <path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55"></path>
                <path d="M5 12.55a10.94 10.94 0 0 1 5.17-2.39"></path>
                <path d="M10.71 5.05A16 16 0 0 1 22.58 9"></path>
                <path d="M1.42 9a15.91 15.91 0 0 1 4.7-2.88"></path>
                <path d="M8.53 16.11a6 6 0 0 1 6.95 0"></path>
                <line x1="12" y1="20" x2="12.01" y2="20"></line>
              </svg>
              <span v-if="pendingSyncCount > 0" class="pending-badge">{{ pendingSyncCount }}</span>
            </div>
          </div>
          <button @click="emit('openSettings')" class="settings-btn" title="Settings">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="3"></circle>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
            </svg>
          </button>
        </div>
      </div>

      <div class="sidebar-content">
        <!-- Current Timer Display -->
        <div class="current-timer-section">
          <div class="current-timer" :class="{ running: isRunning }">
            <div class="elapsed" v-if="!isEditingElapsed" @click="isRunning && startEditElapsed()">
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
                <button @click="cancelEditElapsed" class="btn-secondary btn-sm">Cancel</button>
                <button @click="saveEditElapsed" class="btn-primary btn-sm">Save</button>
              </div>
            </div>
            <div class="current-tag" v-if="runningTimer?.tag">{{ runningTimer.tag }}</div>
            <div class="current-project" v-if="runningProjectLabel">{{ runningProjectLabel }}</div>
            <div class="current-description" v-if="runningTimer?.description">{{ runningTimer.description }}</div>
            <div class="current-status" v-if="!isRunning">Stopped</div>
            <div class="started-at" v-if="isRunning && !isEditingStartTime" @click="startEditStartTime">
              Started {{ displayStartTime }}
            </div>
            <div class="started-at-edit" v-if="isRunning && isEditingStartTime">
              <span class="started-label">Started</span>
              <input 
                v-model="editStartTime" 
                type="text" 
                :placeholder="getTimePlaceholder()"
                @keyup.enter="saveEditStartTime"
                @keyup.escape="cancelEditStartTime"
                autofocus
              />
              <button @click="cancelEditStartTime" class="btn-icon" title="Cancel">✕</button>
              <button @click="saveEditStartTime" class="btn-icon btn-icon-primary" title="Save">✓</button>
            </div>
          </div>
        </div>

        <!-- Project Selector -->
        <div class="project-input-section">
          <ProjectSelector
            v-model="newProjectId"
            :projects="projects"
            :clients="clients"
            :onCreate="handleCreateProject"
            :placeholder="isRunning ? 'Change project...' : 'Project (optional)'"
            @open-create-form="fetchClients"
          />
        </div>

        <!-- Description -->
        <div class="description-input-section">
          <textarea
            id="running-description"
            v-model="newDescription"
            placeholder="Description (optional)"
            rows="1"
            class="sidebar-input description-input"
            @blur="onDescriptionBlur"
          />
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

        <!-- Start/Stop Button -->
        <button 
          @click="toggleTimer" 
          class="toggle-button"
          :class="{ running: isRunning }"
        >
          <svg v-if="!isRunning" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
            <polygon points="5 3 19 12 5 21 5 3"></polygon>
          </svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="4" width="4" height="16"></rect>
            <rect x="14" y="4" width="4" height="16"></rect>
          </svg>
          {{ isRunning ? 'Stop' : 'Start' }}
        </button>

        <!-- Add Manual Entry Button -->
        <button @click="openManualEntry" class="manual-entry-button">
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          Add Past Entry
        </button>

        <p v-if="error" class="error">{{ error }}</p>

        <!-- Stats -->
        <div class="stats">
          <div class="stat">
            <span class="stat-value">{{ formatDuration(todayTotal) }}</span>
            <span v-if="todayRemainingMs !== null" class="stat-remaining">
              {{ todayRemainingMs > 0 ? formatDuration(todayRemainingMs) + ' left' : 'goal reached' }}
            </span>
            <span class="stat-label">Today</span>
          </div>
          <div class="stat">
            <span class="stat-value">{{ formatDuration(weekTotal) }}</span>
            <span v-if="weekRemainingMs !== null" class="stat-remaining">
              {{ weekRemainingMs > 0 ? formatDuration(weekRemainingMs) + ' left' : 'goal reached' }}
            </span>
            <span class="stat-label">This Week</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <!-- View Toggle -->
      <div class="content-header">
        <div class="view-toggle">
          <button 
            @click="viewMode = 'calendar'" 
            :class="{ active: viewMode === 'calendar' }"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="16" y1="2" x2="16" y2="6"></line>
              <line x1="8" y1="2" x2="8" y2="6"></line>
              <line x1="3" y1="10" x2="21" y2="10"></line>
            </svg>
            Calendar
          </button>
          <button 
            @click="viewMode = 'list'" 
            :class="{ active: viewMode === 'list' }"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="8" y1="6" x2="21" y2="6"></line>
              <line x1="8" y1="12" x2="21" y2="12"></line>
              <line x1="8" y1="18" x2="21" y2="18"></line>
              <line x1="3" y1="6" x2="3.01" y2="6"></line>
              <line x1="3" y1="12" x2="3.01" y2="12"></line>
              <line x1="3" y1="18" x2="3.01" y2="18"></line>
            </svg>
            List
          </button>
        </div>
      </div>

      <!-- Calendar View -->
      <div class="content-body" v-if="viewMode === 'calendar'">
        <WeeklyCalendar 
          :key="`calendar-${preferences.dateFormat}-${preferences.timeFormat}`"
          :timers="timers"
          :projects="projects"
          :currentElapsed="currentElapsed"
          :daily-goal-enabled="preferences.dailyGoalEnabled"
          :day-goal-for-date="(d) => {
            if (!preferences.includeWeekendGoals && (d.getDay() === 0 || d.getDay() === 6)) return null
            return dailyGoals[toDateKey(d)] ?? preferences.defaultDailyGoalHours
          }"
          @select="selectTimerFromCalendar"
          @day-goal-click="openDayGoalModal"
        />
      </div>

      <!-- List View -->
      <div class="content-body list-view" v-else>
        <div class="list-header">
          <div class="filter-section">
            <label for="tag-filter">Filter by tag:</label>
            <select id="tag-filter" v-model="filterTag" class="tag-filter-select">
              <option value="">All tags</option>
              <option v-for="tag in tags" :key="tag" :value="tag">{{ tag }}</option>
            </select>
            <button v-if="filterTag" @click="filterTag = ''" class="clear-filter-btn" title="Clear filter">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
              Clear
            </button>
          </div>
          <div class="filtered-total">
            <span class="filtered-total-value">{{ formatDuration(filteredTotal) }}</span>
            <span class="filtered-total-label">Total{{ filterTag ? ` (${filterTag})` : '' }}</span>
          </div>
        </div>
        <p v-if="loading" class="loading">Loading...</p>
        <p v-else-if="filteredTimers.length === 0" class="empty">{{ filterTag ? 'No timers with this tag' : 'No timers yet' }}</p>
        <div class="timer-list">
          <template v-for="group in timersByDay" :key="group.date.toISOString()">
            <div class="day-separator" :class="{ 'day-separator-goal': preferences.dailyGoalEnabled }">
              <span class="day-label">{{ group.label }}</span>
              <span class="day-total">{{ formatDuration(group.total) }}</span>
              <button
                v-if="preferences.dailyGoalEnabled"
                type="button"
                class="day-goal-btn"
                title="Set goal for this day"
                @click="openDayGoalModal(group.date)"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
              </button>
            </div>
            <TimerItem 
              v-for="timer in group.timers" 
              :key="`${timer.id}-${preferences.dateFormat}-${preferences.timeFormat}`" 
              :timer="timer"
              :projects="projects"
              :clients="clients"
              :tags="tags"
              :live-elapsed-ms="runningTimer && String(timer.id) === String(runningTimer.id) ? currentElapsed : null"
              :onCreateProject="handleCreateProject"
              @update="handleUpdate"
              @delete="handleDelete"
              @open-create-form="fetchClients"
            />
          </template>
        </div>
      </div>
    </main>

    <!-- Selected Timer Modal (calendar: big timer, click to edit time) -->
    <div class="timer-modal-overlay" v-if="selectedTimer" @click.self="closeSelectedTimer">
      <div class="timer-modal selected-timer-modal">
        <button class="close-btn" @click="closeSelectedTimer">&times;</button>
        <div
          class="selected-timer-display"
          :class="{ running: !selectedTimer.end_time, 'editing-time': selectedTimerEditingTime }"
          @click="!selectedTimerEditingTime && toggleSelectedTimerEditTime()"
        >
          <button type="button" class="selected-timer-edit-btn" @click.stop="toggleSelectedTimerEditTime" title="Edit time">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          </button>
          <template v-if="!selectedTimerEditingTime">
            <div class="selected-timer-elapsed">{{ formatHHmmss(!selectedTimer.end_time ? selectedTimerDisplayDurationMs : (selectedTimerFormDurationMs ?? selectedTimerDisplayDurationMs)) }}</div>
            <div class="selected-timer-time-range">
              {{ selectedTimerEditStartDate }} {{ selectedTimerEditStartTime }}
              <span class="selected-timer-range-sep">–</span>
              <template v-if="selectedTimerEditEndDate && selectedTimerEditEndTime">{{ selectedTimerEditEndDate }} {{ selectedTimerEditEndTime }}</template>
              <span v-else class="selected-timer-running-label">Running</span>
            </div>
            <div class="selected-timer-meta" v-if="selectedTimer.tag">{{ selectedTimer.tag }}</div>
            <div class="selected-timer-meta" v-if="selectedTimer.project_id && findProjectById(selectedTimer.project_id)">{{ formatProjectLabel(findProjectById(selectedTimer.project_id)) }}</div>
            <div class="selected-timer-meta description" v-if="selectedTimer.description">{{ selectedTimer.description }}</div>
          </template>
          <template v-else>
            <div class="selected-timer-inline-edit">
              <input
                v-model="selectedTimerEditDuration"
                type="text"
                class="selected-timer-duration-inline"
                placeholder="HH:mm:ss"
                @input="onSelectedTimerDurationInput"
                @click.stop
              />
              <div class="selected-timer-inline-datetime">
                <div class="selected-timer-datetime">
                  <div class="date-input-wrapper">
                    <input v-model="selectedTimerEditStartDate" type="text" :placeholder="getDatePlaceholder()" @input="onSelectedTimerStartDateInput" @click.stop />
                    <button type="button" class="date-picker-btn" @click.stop="selectedTimerOpenStartDatePicker"><svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg></button>
                    <input ref="selectedTimerStartDatePicker" type="date" v-model="selectedTimerHiddenStartDate" @change="onSelectedTimerHiddenStartDateChange" class="hidden-date-picker" />
                  </div>
                  <input v-model="selectedTimerEditStartTime" type="text" :placeholder="getTimePlaceholder()" @click.stop />
                </div>
                <div class="selected-timer-datetime">
                  <div class="date-input-wrapper">
                    <input v-model="selectedTimerEditEndDate" type="text" :placeholder="getDatePlaceholder()" @input="onSelectedTimerEndDateInput" @click.stop />
                    <button type="button" class="date-picker-btn" @click.stop="selectedTimerOpenEndDatePicker"><svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg></button>
                    <input ref="selectedTimerEndDatePicker" type="date" v-model="selectedTimerHiddenEndDate" @change="onSelectedTimerHiddenEndDateChange" class="hidden-date-picker" />
                  </div>
                  <input v-model="selectedTimerEditEndTime" type="text" :placeholder="getTimePlaceholder()" @click.stop />
                </div>
              </div>
            </div>
          </template>
        </div>
        <form class="selected-timer-form" @submit.prevent="handleSelectedTimerSave">
          <div class="form-group">
            <label>Tag</label>
            <TagInput
              v-model="selectedTimerEditTag"
              :tags="tags"
              placeholder="Tag (optional)"
              @submit="handleSelectedTimerSave"
            />
          </div>
          <div class="form-group">
            <label>Description</label>
            <textarea
              v-model="selectedTimerEditDescription"
              placeholder="Optional description..."
              rows="2"
              class="selected-timer-description-input"
            />
          </div>
          <div class="form-group">
            <label>Project</label>
            <ProjectSelector
              v-model="selectedTimerEditProjectId"
              :projects="projects"
              :clients="clients"
              :onCreate="handleCreateProject"
              placeholder="Project (optional)"
              @open-create-form="fetchClients"
            />
          </div>
          <p v-if="error" class="form-error">{{ error }}</p>
          <div class="selected-timer-actions">
            <button type="button" @click="handleSelectedTimerDelete" class="selected-timer-delete-btn">Delete</button>
            <div class="selected-timer-actions-right">
              <button type="button" @click="closeSelectedTimer" class="selected-timer-cancel-btn">Cancel</button>
              <button type="submit" class="selected-timer-save-btn">Save</button>
            </div>
          </div>
        </form>
      </div>
    </div>

    <!-- Day goal modal (Escape closes via handleKeydown / closeTopModal) -->
    <div class="timer-modal-overlay" v-if="showDayGoalModal && dayGoalDate" @click.self="closeDayGoalModal">
      <div class="timer-modal day-goal-modal">
        <button class="close-btn" @click="closeDayGoalModal">&times;</button>
        <h3 class="modal-title">Goal for {{ formatDateLabel(dayGoalDate) }}</h3>
        <form @submit.prevent="saveDayGoal" class="day-goal-form">
          <div class="form-group">
            <label>Hours (leave empty to use default)</label>
            <input
              v-model="dayGoalHoursInput"
              type="number"
              min="0"
              max="24"
              step="0.5"
              class="day-goal-input"
              :placeholder="'Default: ' + preferences.defaultDailyGoalHours"
            />
          </div>
          <p v-if="dayGoalError" class="form-error">{{ dayGoalError }}</p>
          <div class="form-actions">
            <button type="button" class="btn-secondary" @click="closeDayGoalModal">Cancel</button>
            <button type="submit" class="btn-primary">Save</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Manual Entry Modal -->
    <div class="timer-modal-overlay" v-if="showManualEntry" @click.self="closeManualEntry">
      <div class="timer-modal manual-entry-modal">
        <button class="close-btn" @click="closeManualEntry">&times;</button>
        <h3 class="modal-title">Add Past Entry</h3>
        
        <form @submit.prevent="saveManualEntry" class="manual-entry-form">
          <div class="form-group">
            <label>Start</label>
            <div class="datetime-inputs">
              <div class="date-input-wrapper">
                <input 
                  type="text" 
                  v-model="manualEntry.startDate"
                  :placeholder="getDatePlaceholder()"
                  @input="onManualStartDateInput"
                  required
                />
                <button type="button" class="date-picker-btn" @click="openManualStartDatePicker">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                    <line x1="16" y1="2" x2="16" y2="6"></line>
                    <line x1="8" y1="2" x2="8" y2="6"></line>
                    <line x1="3" y1="10" x2="21" y2="10"></line>
                  </svg>
                </button>
                <input ref="manualStartDatePicker" type="date" v-model="hiddenManualStartDate" @change="onHiddenManualStartDateChange" class="hidden-date-picker" />
              </div>
              <input 
                type="text" 
                v-model="manualEntry.startTime"
                :placeholder="getTimePlaceholder()"
                required
              />
            </div>
          </div>
          
          <div class="form-group">
            <label>End</label>
            <div class="datetime-inputs">
              <div class="date-input-wrapper">
                <input 
                  type="text" 
                  v-model="manualEntry.endDate"
                  :placeholder="getDatePlaceholder()"
                  @input="onManualEndDateInput"
                  required
                />
                <button type="button" class="date-picker-btn" @click="openManualEndDatePicker">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                    <line x1="16" y1="2" x2="16" y2="6"></line>
                    <line x1="8" y1="2" x2="8" y2="6"></line>
                    <line x1="3" y1="10" x2="21" y2="10"></line>
                  </svg>
                </button>
                <input ref="manualEndDatePicker" type="date" v-model="hiddenManualEndDate" @change="onHiddenManualEndDateChange" class="hidden-date-picker" />
              </div>
              <input 
                type="text" 
                v-model="manualEntry.endTime"
                :placeholder="getTimePlaceholder()"
                required
              />
            </div>
          </div>
          
          <div class="form-group">
            <label>Tag (optional)</label>
            <TagInput 
              v-model="manualEntry.tag"
              :tags="tags"
              placeholder="Add a tag..."
            />
          </div>

          <div class="form-group">
            <label>Description (optional)</label>
            <textarea v-model="manualEntry.description" placeholder="Optional description..." rows="2" class="manual-description-input"></textarea>
          </div>

          <div class="form-group">
            <label>Project (optional)</label>
            <ProjectSelector
              v-model="manualEntry.projectId"
              :projects="projects"
              :clients="clients"
              :onCreate="handleCreateProject"
              placeholder="Select a project..."
              @open-create-form="fetchClients"
            />
          </div>
          
          <p v-if="error" class="form-error">{{ error }}</p>
          
          <div class="form-actions">
            <button type="button" @click="closeManualEntry" class="btn-secondary">Cancel</button>
            <button type="submit" class="btn-primary">Add Entry</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  width: 100%;
}

/* Sidebar */
.sidebar {
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: sticky;
  top: 0;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
}

.logo {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--accent-color);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.sync-status {
  display: flex;
  align-items: center;
}

.sync-btn {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  background: var(--accent-color);
  border: none;
  color: #fff;
  padding: 0.375rem 0.625rem;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s, opacity 0.2s;
}

.sync-btn:hover:not(:disabled) {
  background: var(--accent-hover);
}

.sync-btn:disabled {
  opacity: 0.7;
  cursor: default;
}

.sync-btn svg.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.sync-count {
  font-variant-numeric: tabular-nums;
}

.offline-indicator {
  display: flex;
  align-items: center;
  position: relative;
  color: var(--text-muted);
  padding: 0.375rem;
}

.offline-indicator svg {
  color: #f59e0b;
}

.pending-badge {
  position: absolute;
  top: -2px;
  right: -2px;
  background: #f59e0b;
  color: #fff;
  font-size: 0.625rem;
  font-weight: 600;
  min-width: 14px;
  height: 14px;
  padding: 0 4px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.settings-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 0.5rem;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.settings-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.sidebar-content {
  flex: 1;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  overflow-y: auto;
}

/* Current Timer */
.current-timer-section {
  text-align: center;
}

.current-timer {
  padding: 1.5rem;
  background: var(--bg-primary);
  border-radius: 16px;
  border: 2px solid var(--border-color);
  transition: border-color 0.3s, background 0.3s;
}

.current-timer.running {
  border-color: var(--success-color);
  background: var(--timer-bg);
}

.elapsed {
  font-size: 2.5rem;
  font-weight: 300;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  cursor: default;
  transition: color 0.2s;
}

.current-timer.running .elapsed {
  color: var(--accent-color);
  cursor: pointer;
}

.current-timer.running .elapsed:hover {
  opacity: 0.8;
}

.elapsed-edit {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

.elapsed-input {
  font-size: 2rem;
  font-weight: 300;
  font-variant-numeric: tabular-nums;
  text-align: center;
  width: 100%;
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

.current-tag {
  margin-top: 0.5rem;
  color: var(--text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
}

.current-project {
  margin-top: 0.25rem;
  color: var(--text-muted);
  font-size: 0.8125rem;
}

.current-description {
  margin-top: 0.25rem;
  color: var(--text-muted);
  font-size: 0.8125rem;
  white-space: pre-wrap;
  word-break: break-word;
}

.current-status {
  margin-top: 0.25rem;
  font-size: 0.75rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* Started At */
.started-at {
  margin-top: 0.5rem;
  font-size: 0.75rem;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  transition: background 0.2s, color 0.2s;
}

.started-at:hover {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.started-at-edit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  margin-top: 0.5rem;
}

.started-label {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.started-at-edit input {
  width: 70px;
  padding: 0.25rem 0.375rem;
  font-size: 0.75rem;
  text-align: center;
  background: var(--bg-secondary);
  border: 1px solid var(--accent-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-family: inherit;
}

.started-at-edit input:focus {
  outline: none;
}

.btn-icon {
  width: 24px;
  height: 24px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-secondary);
  font-size: 0.625rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-icon:hover {
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.btn-icon-primary {
  background: var(--accent-color);
  border-color: var(--accent-color);
  color: #fff;
}

.btn-icon-primary:hover {
  background: var(--accent-hover);
}

/* Tag Input */
.tag-input-section {
  width: 100%;
}

/* Project Selector */
.project-input-section {
  width: 100%;
}

.description-input-section {
  width: 100%;
}

/* Shared sidebar input style – same as project/tag inputs */
.sidebar-content .sidebar-input {
  width: 100%;
  padding: 0.75rem 1rem;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-primary);
  font-size: 0.875rem;
  box-sizing: border-box;
}

.sidebar-content .sidebar-input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.sidebar-content .sidebar-input::placeholder {
  color: var(--text-muted);
}

.description-input {
  font-family: inherit;
  resize: vertical;
  min-height: calc(1.5em + 1.5rem);
  line-height: 1.5;
}

.manual-description-input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 0.875rem;
  font-family: inherit;
  resize: vertical;
  min-height: 52px;
}

/* Toggle Button */
.toggle-button {
  width: 100%;
  padding: 1rem;
  font-size: 1.125rem;
  font-weight: 600;
  border: none;
  border-radius: 12px;
  background: var(--success-color);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  transition: transform 0.1s, background 0.2s;
}

.toggle-button:hover {
  background: var(--success-hover);
}

.toggle-button:active {
  transform: scale(0.98);
}

.toggle-button.running {
  background: var(--danger-color);
}

.toggle-button.running:hover {
  background: var(--danger-hover);
}

/* Buttons */
.btn-sm {
  padding: 0.375rem 0.75rem;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 500;
}

.btn-primary {
  background: var(--accent-color);
  border: none;
  color: #fff;
}

.btn-primary:hover {
  background: var(--accent-hover);
}

.btn-secondary {
  background: transparent;
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
}

.btn-secondary:hover {
  border-color: var(--border-color);
  color: var(--text-primary);
}

/* Stats */
.stats {
  display: flex;
  gap: 0.75rem;
  margin-top: auto;
}

.stat {
  flex: 1;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 1rem;
  text-align: center;
}

.stat-value {
  display: block;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.stat-label {
  display: block;
  font-size: 0.625rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-top: 0.25rem;
}

.stat-remaining {
  display: block;
  font-size: 0.6875rem;
  color: var(--text-muted);
  margin-top: 0.125rem;
}

.error {
  color: var(--danger-color);
  text-align: center;
  font-size: 0.875rem;
}

/* Main Content */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
  overflow: hidden;
}

.content-header {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.view-toggle {
  display: flex;
  gap: 0.25rem;
  background: var(--bg-secondary);
  padding: 0.25rem;
  border-radius: 8px;
}

.view-toggle button {
  padding: 0.5rem 1rem;
  background: transparent;
  border: none;
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  transition: all 0.2s;
}

.view-toggle button:hover {
  color: var(--text-primary);
}

.view-toggle button.active {
  background: var(--bg-primary);
  color: var(--text-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.content-body {
  flex: 1;
  overflow: auto;
}

.content-body.list-view {
  padding: 1.5rem;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  max-width: 800px;
}

.filter-section {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.filter-section label {
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.tag-filter-select {
  padding: 0.5rem 0.75rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 0.875rem;
  min-width: 150px;
}

.tag-filter-select:focus {
  outline: none;
  border-color: var(--accent-color);
}

.clear-filter-btn {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5rem 0.75rem;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.clear-filter-btn:hover {
  background: var(--bg-tertiary);
  border-color: var(--border-light);
  color: var(--text-primary);
}

.filtered-total {
  text-align: right;
}

.filtered-total-value {
  display: block;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.filtered-total-label {
  display: block;
  font-size: 0.625rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.timer-list {
  max-width: 800px;
}

.day-separator {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 0.5rem 0;
  margin-top: 0.5rem;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 0.75rem;
}

.day-label {
  color: var(--text-muted);
}

.day-total {
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary);
}

.day-separator-goal {
  padding-right: 2rem;
}

.day-goal-btn {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  padding: 0.25rem;
  background: transparent;
  border: none;
  border-radius: 4px;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.day-goal-btn:hover {
  background: var(--bg-tertiary);
  color: var(--accent-color);
}

.day-separator {
  position: relative;
}

.day-separator:first-child {
  margin-top: 0;
}

.loading, .empty {
  text-align: center;
  color: var(--text-muted);
  padding: 3rem;
}

/* Modal */
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
  min-width: 360px;
  max-width: 90%;
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

.close-btn:hover {
  color: var(--text-primary);
}

/* Selected Timer Modal (big timer + duration edit like sidebar) */
.selected-timer-modal {
  min-width: 320px;
  max-width: min(420px, 90vw);
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
.selected-timer-form {
  min-width: 0;
}

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

.selected-timer-status {
  font-size: 0.75rem;
  color: var(--accent-color);
  margin-top: 0.25rem;
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

.selected-timer-form .elapsed-input {
  width: 100%;
  padding: 0.625rem 0.75rem;
  font-size: 1.125rem;
  font-variant-numeric: tabular-nums;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-family: inherit;
}

.selected-timer-form .elapsed-input:focus {
  outline: none;
  border-color: var(--accent-color);
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

/* Manual Entry Button */
.manual-entry-button {
  width: 100%;
  padding: 0.75rem;
  font-size: 0.875rem;
  font-weight: 500;
  border: 1px dashed var(--border-color);
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  transition: all 0.2s;
}

.manual-entry-button:hover {
  border-color: var(--accent-color);
  color: var(--accent-color);
  background: var(--bg-tertiary);
}

/* Manual Entry Modal */
.manual-entry-modal {
  min-width: 400px;
}

.modal-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 1.5rem;
}

.manual-entry-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.form-group label {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.form-group input[type="date"],
.form-group input[type="text"] {
  padding: 0.625rem 0.75rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 0.9375rem;
  font-family: inherit;
}

.form-group input[type="date"]:focus,
.form-group input[type="text"]:focus {
  outline: none;
  border-color: var(--accent-color);
}

/* Day goal modal – input matches sidebar/project/tag inputs */
.day-goal-modal .day-goal-form {
  margin-top: 1rem;
}
.day-goal-modal .day-goal-input {
  width: 100%;
  padding: 0.75rem 1rem;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-primary);
  font-size: 0.875rem;
  font-family: inherit;
  box-sizing: border-box;
}
.day-goal-modal .day-goal-input:focus {
  outline: none;
  border-color: var(--accent-color);
}
.day-goal-modal .day-goal-input::placeholder {
  color: var(--text-muted);
}
.day-goal-modal .form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1rem;
}
.day-goal-modal .form-actions .btn-primary {
  background: var(--accent-color);
  border: none;
  color: #fff;
}
.day-goal-modal .form-actions .btn-primary:hover {
  background: var(--accent-hover);
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

.form-error {
  color: var(--danger-color);
  font-size: 0.875rem;
  margin: 0;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.form-actions .btn-primary,
.form-actions .btn-secondary {
  padding: 0.625rem 1.25rem;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
}

.form-actions .btn-primary {
  background: var(--accent-color);
  border: none;
  color: #fff;
}

.form-actions .btn-primary:hover {
  background: var(--accent-hover);
}

.form-actions .btn-secondary {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.form-actions .btn-secondary:hover {
  border-color: var(--border-light);
  color: var(--text-primary);
}

/* Responsive */
@media (max-width: 900px) {
  .layout {
    flex-direction: column;
  }
  
  .sidebar {
    width: 100%;
    min-width: 100%;
    height: auto;
    position: relative;
    border-right: none;
    border-bottom: 1px solid var(--border-color);
  }
  
  .sidebar-content {
    padding: 1rem;
    gap: 1rem;
  }
  
  .current-timer {
    padding: 1rem;
  }
  
  .elapsed {
    font-size: 2rem;
  }
  
  .stats {
    margin-top: 0;
  }
  
  .main-content {
    height: auto;
    min-height: 50vh;
  }
  
  .list-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }
  
  .filtered-total {
    text-align: left;
  }
  
  .manual-entry-modal {
    min-width: auto;
    width: 90%;
    max-width: 400px;
  }
  
  .datetime-inputs {
    flex-direction: column;
    gap: 0.5rem;
  }
}
</style>
