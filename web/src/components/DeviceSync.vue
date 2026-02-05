<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { api } from '../api.js'
import { preferences } from '../preferences.js'
import * as offlineStorage from '../offlineStorage.js'

const emit = defineEmits(['close', 'synced'])

const syncType = ref('online') // 'online', 'offline'
const mode = ref('choose') // 'choose', 'share', 'scan', 'export', 'import'
const syncCode = ref('')
const manualCode = ref('')
const qrCanvas = ref(null)
const error = ref('')
const success = ref('')
const loading = ref(false)
const polling = ref(false)
let pollInterval = null

// Offline sync
const exportData = ref('')
const importData = ref('')
const copied = ref(false)

// QR Code generation using Canvas API (no external library needed)
function generateQRCode(text) {
  // We'll use a simple URL-based QR code service as a fallback,
  // or generate using a minimal QR library
  // For simplicity, we'll display the code prominently and use a QR API
  return `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(text)}`
}

// Offline export/import functions
async function generateExport() {
  mode.value = 'export'
  loading.value = true
  error.value = ''
  
  try {
    const [timers, projects] = await Promise.all([
      offlineStorage.getAllTimers(),
      offlineStorage.getAllProjects()
    ])
    const payload = {
      timers,
      projects: projects || [],
      preferences: {
        dateFormat: preferences.dateFormat,
        timeFormat: preferences.timeFormat,
        dayStartHour: preferences.dayStartHour,
        dailyGoalEnabled: preferences.dailyGoalEnabled,
        defaultDailyGoalHours: preferences.defaultDailyGoalHours,
        includeWeekendGoals: preferences.includeWeekendGoals
      }
    }
    const data = JSON.stringify(payload)
    exportData.value = btoa(unescape(encodeURIComponent(data)))
  } catch (err) {
    error.value = 'Failed to export data: ' + err.message
    mode.value = 'choose'
  } finally {
    loading.value = false
  }
}

async function copyExportData() {
  try {
    await navigator.clipboard.writeText(exportData.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch (err) {
    error.value = 'Failed to copy to clipboard'
  }
}

async function handleImport() {
  const data = importData.value.trim()
  if (!data) {
    error.value = 'Please paste the export data'
    return
  }
  
  loading.value = true
  error.value = ''
  
  try {
    const json = decodeURIComponent(escape(atob(data)))
    const parsed = JSON.parse(json)
    
    let timers = []
    let projects = []
    let prefs = null
    
    if (Array.isArray(parsed)) {
      // Legacy format: raw array of timers
      timers = parsed
    } else if (parsed && typeof parsed === 'object' && Array.isArray(parsed.timers)) {
      timers = parsed.timers
      if (Array.isArray(parsed.projects)) projects = parsed.projects
      if (parsed.preferences && typeof parsed.preferences === 'object') prefs = parsed.preferences
    } else {
      throw new Error('Invalid data format')
    }
    
    await api.completeSyncImport(timers, projects, prefs)
    if (prefs) {
      if (prefs.dateFormat != null) preferences.dateFormat = prefs.dateFormat
      if (prefs.timeFormat != null) preferences.timeFormat = prefs.timeFormat
      if (prefs.dayStartHour != null) preferences.dayStartHour = prefs.dayStartHour
      if (typeof prefs.dailyGoalEnabled === 'boolean') preferences.dailyGoalEnabled = prefs.dailyGoalEnabled
      if (typeof prefs.defaultDailyGoalHours === 'number') preferences.defaultDailyGoalHours = prefs.defaultDailyGoalHours
      if (typeof prefs.includeWeekendGoals === 'boolean') preferences.includeWeekendGoals = prefs.includeWeekendGoals
    }
    success.value = `Imported ${timers.length} timer(s)${projects.length ? `, ${projects.length} project(s)` : ''} successfully!`
    setTimeout(() => {
      emit('synced')
    }, 1500)
  } catch (err) {
    if (err.message.includes('Invalid') || err.message.includes('JSON')) {
      error.value = 'Invalid export data. Please check and try again.'
    } else {
      error.value = 'Failed to import: ' + err.message
    }
  } finally {
    loading.value = false
  }
}

function resetMode() {
  mode.value = 'choose'
  error.value = ''
  success.value = ''
  exportData.value = ''
  importData.value = ''
  copied.value = false
  stopPolling()
}

async function startSharing() {
  mode.value = 'share'
  loading.value = true
  error.value = ''

  try {
    const result = await api.createSyncSession()
    syncCode.value = result.syncCode
    startPolling()
  } catch (err) {
    error.value = err.message
    mode.value = 'choose'
  } finally {
    loading.value = false
  }
}

function startPolling() {
  polling.value = true
  pollInterval = setInterval(async () => {
  try {
    const status = await api.getSyncStatus(syncCode.value)
      if (status.status === 'joined' && status.timers) {
        await api.completeSyncImport(status.timers, status.projects, status.preferences)
        if (status.preferences) {
          if (status.preferences.dateFormat != null) preferences.dateFormat = status.preferences.dateFormat
          if (status.preferences.timeFormat != null) preferences.timeFormat = status.preferences.timeFormat
          if (status.preferences.dayStartHour != null) preferences.dayStartHour = status.preferences.dayStartHour
          if (typeof status.preferences.dailyGoalEnabled === 'boolean') preferences.dailyGoalEnabled = status.preferences.dailyGoalEnabled
          if (typeof status.preferences.defaultDailyGoalHours === 'number') preferences.defaultDailyGoalHours = status.preferences.defaultDailyGoalHours
          if (typeof status.preferences.includeWeekendGoals === 'boolean') preferences.includeWeekendGoals = status.preferences.includeWeekendGoals
        }
        success.value = 'Sync complete! Timers have been merged.'
        stopPolling()
        setTimeout(() => {
          emit('synced')
        }, 2000)
      }
    } catch (err) {
      // Session might have expired
      if (err.message.includes('not found')) {
        error.value = 'Sync session expired. Please try again.'
        stopPolling()
      }
    }
  }, 2000)
}

function stopPolling() {
  polling.value = false
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

async function joinSession() {
  const code = manualCode.value.trim().toUpperCase()
  if (!code) {
    error.value = 'Please enter a sync code'
    return
  }

  loading.value = true
  error.value = ''

  try {
    const result = await api.joinSyncSession(code)
    if (result.timers) {
      await api.completeSyncImport(result.timers, result.projects, result.preferences)
      if (result.preferences) {
        if (result.preferences.dateFormat != null) preferences.dateFormat = result.preferences.dateFormat
        if (result.preferences.timeFormat != null) preferences.timeFormat = result.preferences.timeFormat
        if (result.preferences.dayStartHour != null) preferences.dayStartHour = result.preferences.dayStartHour
        if (typeof result.preferences.dailyGoalEnabled === 'boolean') preferences.dailyGoalEnabled = result.preferences.dailyGoalEnabled
        if (typeof result.preferences.defaultDailyGoalHours === 'number') preferences.defaultDailyGoalHours = result.preferences.defaultDailyGoalHours
        if (typeof result.preferences.includeWeekendGoals === 'boolean') preferences.includeWeekendGoals = result.preferences.includeWeekendGoals
      }
    }
    success.value = 'Sync complete! Timers have been merged.'
    setTimeout(() => {
      emit('synced')
    }, 2000)
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function handleKeydown(e) {
  if (e.key === 'Escape') {
    e.stopPropagation()
    emit('close')
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown, true)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown, true)
  stopPolling()
})
</script>

<template>
  <div class="sync-overlay" @click.self="emit('close')">
    <div class="sync-modal">
      <div class="sync-header">
        <h2>Sync Devices</h2>
        <button class="close-btn" @click="emit('close')">&times;</button>
      </div>

      <div class="sync-content">
        <!-- Sync Type Toggle -->
        <div v-if="mode === 'choose'" class="sync-type-toggle">
          <button 
            :class="['toggle-btn', { active: syncType === 'online' }]"
            @click="syncType = 'online'"
          >
            Online Sync
          </button>
          <button 
            :class="['toggle-btn', { active: syncType === 'offline' }]"
            @click="syncType = 'offline'"
          >
            Offline Export
          </button>
        </div>

        <!-- Choose Mode - Online -->
        <div v-if="mode === 'choose' && syncType === 'online'" class="mode-choose">
          <p class="description">
            Sync your timers between devices without creating an account. 
            Data is transferred through the server.
          </p>

          <div class="mode-buttons">
            <button @click="startSharing" class="mode-btn share-btn">
              <span class="icon">📤</span>
              <span class="text">
                <strong>Share from this device</strong>
                <small>Show QR code for another device to scan</small>
              </span>
            </button>

            <button @click="mode = 'scan'" class="mode-btn scan-btn">
              <span class="icon">📥</span>
              <span class="text">
                <strong>Receive on this device</strong>
                <small>Enter code from another device</small>
              </span>
            </button>
          </div>
        </div>

        <!-- Choose Mode - Offline -->
        <div v-if="mode === 'choose' && syncType === 'offline'" class="mode-choose">
          <p class="description">
            Export or import your timers as text. 100% private - data never touches the server.
          </p>

          <div class="mode-buttons">
            <button @click="generateExport" class="mode-btn share-btn">
              <span class="icon">📋</span>
              <span class="text">
                <strong>Export data</strong>
                <small>Copy data to share manually</small>
              </span>
            </button>

            <button @click="mode = 'import'" class="mode-btn scan-btn">
              <span class="icon">📥</span>
              <span class="text">
                <strong>Import data</strong>
                <small>Paste data from another device</small>
              </span>
            </button>
          </div>
        </div>

        <!-- Share Mode - Show QR Code -->
        <div v-else-if="mode === 'share'" class="mode-share">
          <div v-if="loading" class="loading">
            Creating sync session...
          </div>

          <template v-else-if="syncCode && !success">
            <p class="description">
              Scan this QR code with another device, or enter the code manually:
            </p>

            <div class="qr-container">
              <img 
                :src="generateQRCode(syncCode)" 
                alt="QR Code" 
                class="qr-image"
              />
            </div>

            <div class="code-display">
              <span class="code">{{ syncCode }}</span>
            </div>

            <div class="status" v-if="polling">
              <span class="pulse"></span>
              Waiting for another device to connect...
            </div>
          </template>

          <div v-if="success" class="success-message">
            {{ success }}
          </div>

          <button v-if="!success" @click="resetMode" class="back-btn">
            Cancel
          </button>
        </div>

        <!-- Scan/Enter Code Mode -->
        <div v-else-if="mode === 'scan'" class="mode-scan">
          <p class="description">
            Enter the 6-character code shown on the other device:
          </p>

          <div class="code-input-container">
            <input
              v-model="manualCode"
              type="text"
              class="code-input"
              placeholder="XXXXXX"
              maxlength="6"
              @keyup.enter="joinSession"
              :disabled="loading"
            />
          </div>

          <button 
            @click="joinSession" 
            class="join-btn"
            :disabled="loading || !manualCode.trim()"
          >
            {{ loading ? 'Connecting...' : 'Connect & Sync' }}
          </button>

          <div v-if="success" class="success-message">
            {{ success }}
          </div>

          <button v-if="!success" @click="resetMode" class="back-btn">
            Back
          </button>
        </div>

        <!-- Export Mode (Offline) -->
        <div v-else-if="mode === 'export'" class="mode-export">
          <div v-if="loading" class="loading">
            Generating export...
          </div>

          <template v-else-if="exportData && !success">
            <p class="description">
              Copy this text and send it to the other device (via email, messenger, etc.):
            </p>

            <div class="export-container">
              <textarea 
                :value="exportData" 
                readonly 
                class="export-textarea"
                @click="$event.target.select()"
              ></textarea>
            </div>

            <button @click="copyExportData" class="join-btn">
              {{ copied ? 'Copied!' : 'Copy to Clipboard' }}
            </button>
          </template>

          <button v-if="!success" @click="resetMode" class="back-btn">
            Back
          </button>
        </div>

        <!-- Import Mode (Offline) -->
        <div v-else-if="mode === 'import'" class="mode-import">
          <p class="description">
            Paste the export data from the other device:
          </p>

          <div class="export-container">
            <textarea 
              v-model="importData" 
              class="export-textarea"
              placeholder="Paste export data here..."
              :disabled="loading"
            ></textarea>
          </div>

          <button 
            @click="handleImport" 
            class="join-btn"
            :disabled="loading || !importData.trim()"
          >
            {{ loading ? 'Importing...' : 'Import Data' }}
          </button>

          <div v-if="success" class="success-message">
            {{ success }}
          </div>

          <button v-if="!success" @click="resetMode" class="back-btn">
            Back
          </button>
        </div>

        <!-- Error Display -->
        <p v-if="error" class="error">{{ error }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sync-overlay {
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

.sync-modal {
  background: var(--bg-primary);
  border-radius: 12px;
  width: 90%;
  max-width: 400px;
  max-height: 90vh;
  overflow-y: auto;
}

.sync-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
}

.sync-header h2 {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
}

.close-btn {
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

.sync-content {
  padding: 1.5rem;
}

.description {
  color: var(--text-secondary);
  font-size: 0.875rem;
  line-height: 1.5;
  margin-bottom: 1.5rem;
  text-align: center;
}

.mode-buttons {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
}

.mode-btn:hover {
  border-color: var(--accent-color);
  background: var(--bg-tertiary);
}

.mode-btn .icon {
  font-size: 1.5rem;
}

.mode-btn .text {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.mode-btn .text strong {
  color: var(--text-primary);
  font-size: 0.9375rem;
}

.mode-btn .text small {
  color: var(--text-muted);
  font-size: 0.75rem;
}

.qr-container {
  display: flex;
  justify-content: center;
  margin-bottom: 1rem;
}

.qr-image {
  width: 200px;
  height: 200px;
  border-radius: 8px;
  background: white;
  padding: 8px;
}

.code-display {
  text-align: center;
  margin-bottom: 1.5rem;
}

.code {
  font-family: monospace;
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: 0.25em;
  color: var(--accent-color);
  background: var(--bg-secondary);
  padding: 0.75rem 1.5rem;
  border-radius: 8px;
}

.status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: var(--text-secondary);
  font-size: 0.875rem;
  margin-bottom: 1rem;
}

.pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-color);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

.code-input-container {
  display: flex;
  justify-content: center;
  margin-bottom: 1rem;
}

.code-input {
  font-family: monospace;
  font-size: 1.5rem;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-align: center;
  text-transform: uppercase;
  width: 200px;
  padding: 0.75rem;
  background: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
}

.code-input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.code-input::placeholder {
  color: var(--text-muted);
}

.join-btn {
  width: 100%;
  padding: 0.875rem;
  background: var(--accent-color);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  margin-bottom: 1rem;
}

.join-btn:hover:not(:disabled) {
  background: var(--accent-hover);
}

.join-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.back-btn {
  width: 100%;
  padding: 0.75rem;
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-size: 0.875rem;
  cursor: pointer;
}

.back-btn:hover {
  border-color: var(--border-light);
  color: var(--text-primary);
}

.loading {
  text-align: center;
  color: var(--text-muted);
  padding: 2rem;
}

.error {
  color: var(--danger-color);
  font-size: 0.875rem;
  text-align: center;
  margin-top: 1rem;
}

.success-message {
  color: var(--success-color);
  font-size: 0.875rem;
  text-align: center;
  padding: 1rem;
  background: var(--bg-secondary);
  border-radius: 8px;
  margin-bottom: 1rem;
}

.sync-type-toggle {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
  background: var(--bg-secondary);
  padding: 0.25rem;
  border-radius: 8px;
}

.toggle-btn {
  flex: 1;
  padding: 0.625rem 1rem;
  background: transparent;
  border: none;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-btn:hover {
  color: var(--text-primary);
}

.toggle-btn.active {
  background: var(--bg-primary);
  color: var(--text-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.export-container {
  margin-bottom: 1rem;
}

.export-textarea {
  width: 100%;
  height: 120px;
  padding: 0.75rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-family: monospace;
  font-size: 0.75rem;
  resize: vertical;
  word-break: break-all;
}

.export-textarea:focus {
  outline: none;
  border-color: var(--accent-color);
}

.export-textarea::placeholder {
  color: var(--text-muted);
}

.export-textarea[readonly] {
  cursor: pointer;
}
</style>
