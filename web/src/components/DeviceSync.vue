<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { api } from '../api.js'

const emit = defineEmits(['close', 'synced'])

const mode = ref('choose') // 'choose', 'share', 'scan'
const syncCode = ref('')
const manualCode = ref('')
const qrCanvas = ref(null)
const error = ref('')
const success = ref('')
const loading = ref(false)
const polling = ref(false)
let pollInterval = null

// QR Code generation using Canvas API (no external library needed)
function generateQRCode(text) {
  // We'll use a simple URL-based QR code service as a fallback,
  // or generate using a minimal QR library
  // For simplicity, we'll display the code prominently and use a QR API
  return `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(text)}`
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
        // Import the timers from the other device
        await api.completeSyncImport(status.timers)
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
      await api.completeSyncImport(result.timers)
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
        <!-- Choose Mode -->
        <div v-if="mode === 'choose'" class="mode-choose">
          <p class="description">
            Sync your timers between devices without creating an account. 
            Data is transferred securely through the server.
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

          <button v-if="!success" @click="mode = 'choose'; stopPolling()" class="back-btn">
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

          <button v-if="!success" @click="mode = 'choose'" class="back-btn">
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
</style>
