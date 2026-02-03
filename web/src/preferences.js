import { reactive, watch } from 'vue'

const STORAGE_KEY = 'yatt_preferences'

const defaults = {
  dateFormat: 'dd/mm/yyyy', // 'dd/mm/yyyy' or 'mm/dd/yyyy'
  timeFormat: '24h'         // '24h' or '12h'
}

function loadPreferences() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      return { ...defaults, ...JSON.parse(stored) }
    }
  } catch (e) {
    console.error('Failed to load preferences:', e)
  }
  return { ...defaults }
}

function savePreferences(prefs) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
  } catch (e) {
    console.error('Failed to save preferences:', e)
  }
}

export const preferences = reactive(loadPreferences())

// Auto-save when preferences change
watch(preferences, (newPrefs) => {
  savePreferences(newPrefs)
}, { deep: true })

// Formatting functions that use preferences

export function formatTime(date) {
  if (typeof date === 'string') {
    date = new Date(date)
  }
  
  const hours = date.getHours()
  const minutes = String(date.getMinutes()).padStart(2, '0')
  
  if (preferences.timeFormat === '12h') {
    const period = hours >= 12 ? 'PM' : 'AM'
    const hours12 = hours % 12 || 12
    return `${hours12}:${minutes} ${period}`
  }
  
  return `${String(hours).padStart(2, '0')}:${minutes}`
}

export function formatDate(date) {
  if (typeof date === 'string') {
    date = new Date(date)
  }
  
  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const year = date.getFullYear()
  
  if (preferences.dateFormat === 'mm/dd/yyyy') {
    return `${month}/${day}/${year}`
  }
  
  return `${day}/${month}/${year}`
}

export function formatDateLabel(date) {
  if (typeof date === 'string') {
    date = new Date(date)
  }
  
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  
  if (date.toDateString() === today.toDateString()) {
    return 'Today'
  }
  if (date.toDateString() === yesterday.toDateString()) {
    return 'Yesterday'
  }
  
  const weekday = date.toLocaleDateString('en-GB', { weekday: 'long' })
  return `${weekday}, ${formatDate(date)}`
}

// Format for input fields (editable format)
export function formatTimeForInput(date) {
  if (typeof date === 'string') {
    date = new Date(date)
  }
  
  const hours = date.getHours()
  const minutes = String(date.getMinutes()).padStart(2, '0')
  
  if (preferences.timeFormat === '12h') {
    const period = hours >= 12 ? 'PM' : 'AM'
    const hours12 = hours % 12 || 12
    return `${hours12}:${minutes} ${period}`
  }
  
  return `${String(hours).padStart(2, '0')}:${minutes}`
}

export function formatDateForInput(date) {
  if (typeof date === 'string') {
    date = new Date(date)
  }
  
  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const year = date.getFullYear()
  
  if (preferences.dateFormat === 'mm/dd/yyyy') {
    return `${month}/${day}/${year}`
  }
  
  return `${day}/${month}/${year}`
}

// Parse user input based on preferences
export function parseTimeInput(timeStr) {
  if (!timeStr) return null
  
  timeStr = timeStr.trim().toUpperCase()
  
  if (preferences.timeFormat === '12h') {
    // Parse 12-hour format: "1:30 PM", "12:00 AM", etc.
    const match = timeStr.match(/^(\d{1,2}):(\d{2})\s*(AM|PM)$/i)
    if (!match) return null
    
    let hours = parseInt(match[1], 10)
    const minutes = parseInt(match[2], 10)
    const period = match[3].toUpperCase()
    
    if (hours < 1 || hours > 12 || minutes < 0 || minutes > 59) return null
    
    if (period === 'PM' && hours !== 12) hours += 12
    if (period === 'AM' && hours === 12) hours = 0
    
    return { hours, minutes }
  } else {
    // Parse 24-hour format: "14:30", "09:00", etc.
    const match = timeStr.match(/^(\d{1,2}):(\d{2})$/)
    if (!match) return null
    
    const hours = parseInt(match[1], 10)
    const minutes = parseInt(match[2], 10)
    
    if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) return null
    
    return { hours, minutes }
  }
}

export function parseDateInput(dateStr) {
  if (!dateStr) return null
  
  const parts = dateStr.split('/')
  if (parts.length !== 3) return null
  
  let day, month, year
  
  if (preferences.dateFormat === 'mm/dd/yyyy') {
    month = parseInt(parts[0], 10)
    day = parseInt(parts[1], 10)
    year = parseInt(parts[2], 10)
  } else {
    day = parseInt(parts[0], 10)
    month = parseInt(parts[1], 10)
    year = parseInt(parts[2], 10)
  }
  
  if (isNaN(day) || isNaN(month) || isNaN(year)) return null
  if (day < 1 || day > 31 || month < 1 || month > 12) return null
  
  return { day, month: month - 1, year }
}

// Get placeholder text based on preferences
export function getDatePlaceholder() {
  return preferences.dateFormat === 'mm/dd/yyyy' ? 'MM/DD/YYYY' : 'DD/MM/YYYY'
}

export function getTimePlaceholder() {
  return preferences.timeFormat === '12h' ? '12:00 PM' : 'HH:MM'
}
