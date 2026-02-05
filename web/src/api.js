import * as offlineStorage from './offlineStorage.js'

const API_BASE = '/api'

// Connection state
let isOnline = navigator.onLine
let onlineListeners = new Set()
let syncInProgress = false

// Local-only mode (no account)
const LOCAL_MODE_KEY = 'yatt_local_mode'
const DEVICE_ID_KEY = 'yatt_device_id'

function isLocalMode() {
  return localStorage.getItem(LOCAL_MODE_KEY) === 'true'
}

function setLocalMode(enabled) {
  if (enabled) {
    localStorage.setItem(LOCAL_MODE_KEY, 'true')
    // Generate a device ID for sync purposes
    if (!localStorage.getItem(DEVICE_ID_KEY)) {
      localStorage.setItem(DEVICE_ID_KEY, generateDeviceId())
    }
  } else {
    localStorage.removeItem(LOCAL_MODE_KEY)
  }
}

function getDeviceId() {
  let deviceId = localStorage.getItem(DEVICE_ID_KEY)
  if (!deviceId) {
    deviceId = generateDeviceId()
    localStorage.setItem(DEVICE_ID_KEY, deviceId)
  }
  return deviceId
}

function generateDeviceId() {
  return `device_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

// Initialize online/offline listeners
window.addEventListener('online', () => {
  isOnline = true
  notifyOnlineStatus(true)
  if (!isLocalMode()) {
    attemptSync()
  }
})

window.addEventListener('offline', () => {
  isOnline = false
  notifyOnlineStatus(false)
})

function notifyOnlineStatus(online) {
  onlineListeners.forEach(listener => listener(online))
}

function addOnlineListener(listener) {
  onlineListeners.add(listener)
  // Immediately notify current status
  listener(isOnline)
}

function removeOnlineListener(listener) {
  onlineListeners.delete(listener)
}

function getOnlineStatus() {
  return isOnline
}

function getToken() {
  // In local mode, return a fake token to indicate "logged in"
  if (isLocalMode()) {
    return 'local_mode'
  }
  return localStorage.getItem('token')
}

function setToken(token) {
  localStorage.setItem('token', token)
}

function clearToken() {
  localStorage.removeItem('token')
  setLocalMode(false)
}

async function logout() {
  // Clear all local data
  await offlineStorage.clearAllData()
  // Clear token and local mode
  localStorage.removeItem('token')
  localStorage.removeItem(LOCAL_MODE_KEY)
  localStorage.removeItem(DEVICE_ID_KEY)
  // Disconnect WebSocket
  disconnectWebSocket()
}

async function request(endpoint, options = {}) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(token && { Authorization: `Bearer ${token}` }),
    ...options.headers
  }

  const res = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers
  })

  if (res.status === 401) {
    clearToken()
    throw new Error('Unauthorized')
  }

  if (res.status === 204) {
    return null
  }

  const data = await res.json()
  
  if (!res.ok) {
    throw new Error(data.error || 'Request failed')
  }

  return data
}

// Try a request, return null if offline/failed
async function tryRequest(endpoint, options = {}) {
  if (!isOnline) {
    return null
  }
  try {
    return await request(endpoint, options)
  } catch (err) {
    // Network error - likely offline
    if (err.message === 'Failed to fetch' || err.name === 'TypeError') {
      isOnline = false
      notifyOnlineStatus(false)
      return null
    }
    throw err
  }
}

// Sync pending operations with the server
async function attemptSync() {
  if (!isOnline || syncInProgress || !getToken()) {
    return { success: false, synced: 0 }
  }

  syncInProgress = true
  let syncedCount = 0

  try {
    const queue = await offlineStorage.getSyncQueue()
    
    // Map to track local ID -> server ID mappings
    const idMappings = new Map()
    
    for (const operation of queue) {
      try {
        let result
        
        // Resolve local IDs to server IDs if needed
        let targetId = operation.timerId
        if (offlineStorage.isLocalId(targetId) && idMappings.has(targetId)) {
          targetId = idMappings.get(targetId)
        }
        
        switch (operation.type) {
          case 'create':
            result = await request('/timers', {
              method: 'POST',
              body: JSON.stringify(operation.data)
            })
            // Store mapping from local ID to server ID
            if (operation.localId && result?.id) {
              idMappings.set(operation.localId, result.id)
              await offlineStorage.updateTimerId(operation.localId, result.id, result)
            }
            break

          case 'update':
            if (!offlineStorage.isLocalId(targetId)) {
              result = await request(`/timers/${targetId}`, {
                method: 'PATCH',
                body: JSON.stringify(operation.data)
              })
            }
            break

          case 'stop':
            if (!offlineStorage.isLocalId(targetId)) {
              result = await request(`/timers/${targetId}/stop`, {
                method: 'POST'
              })
            }
            break

          case 'delete':
            if (!offlineStorage.isLocalId(targetId)) {
              await request(`/timers/${targetId}`, {
                method: 'DELETE'
              })
            }
            break
        }
        
        // Remove from queue after successful sync
        await offlineStorage.removeSyncQueueItem(operation.id)
        syncedCount++
      } catch (err) {
        console.error('Sync operation failed:', operation, err)
        // If it's a 404, the item was deleted on server - remove from queue
        if (err.message.includes('not found')) {
          await offlineStorage.removeSyncQueueItem(operation.id)
        }
        // Continue with other operations
      }
    }

    // Refresh timers from server after sync
    if (syncedCount > 0) {
      try {
        const serverTimers = await request('/timers')
        await offlineStorage.saveTimers(serverTimers)
      } catch (err) {
        console.error('Failed to refresh timers after sync:', err)
      }
    }

    return { success: true, synced: syncedCount }
  } catch (err) {
    console.error('Sync failed:', err)
    return { success: false, synced: syncedCount }
  } finally {
    syncInProgress = false
  }
}

async function getPendingSyncCount() {
  return await offlineStorage.getPendingSyncCount()
}

// WebSocket connection
let ws = null
let wsReconnectTimer = null
let wsListeners = new Set()

function getWsUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  // In dev mode with proxy, connect to same host
  return `${protocol}//${host}/api`
}

function connectWebSocket() {
  // Don't connect WebSocket in local mode
  if (isLocalMode()) return
  
  const token = getToken()
  if (!token || ws?.readyState === WebSocket.OPEN) return

  try {
    ws = new WebSocket(getWsUrl())

    ws.onopen = () => {
      console.log('WebSocket connected')
      ws.send(JSON.stringify({ type: 'auth', token }))
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'auth' && data.status === 'ok') {
          console.log('WebSocket authenticated')
        } else if (data.type === 'timer') {
          console.log('Timer event:', data.event)
          wsListeners.forEach(listener => listener(data))
        }
      } catch (e) {
        console.error('WebSocket message error:', e)
      }
    }

    ws.onclose = () => {
      console.log('WebSocket closed')
      scheduleReconnect()
    }

    ws.onerror = (err) => {
      console.error('WebSocket error:', err)
    }
  } catch (e) {
    console.error('WebSocket connection error:', e)
    scheduleReconnect()
  }
}

function scheduleReconnect() {
  if (wsReconnectTimer) return
  wsReconnectTimer = setTimeout(() => {
    wsReconnectTimer = null
    if (getToken()) {
      connectWebSocket()
    }
  }, 5000)
}

function disconnectWebSocket() {
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
  if (ws) {
    ws.close()
    ws = null
  }
}

function addWsListener(listener) {
  wsListeners.add(listener)
  // Connect if not already connected
  if (getToken() && (!ws || ws.readyState !== WebSocket.OPEN)) {
    connectWebSocket()
  }
}

function removeWsListener(listener) {
  wsListeners.delete(listener)
}

export const api = {
  getToken,
  setToken,
  clearToken,

  // WebSocket methods
  connectWebSocket,
  disconnectWebSocket,
  addWsListener,
  removeWsListener,

  // Online status methods
  addOnlineListener,
  removeOnlineListener,
  getOnlineStatus,
  attemptSync,
  getPendingSyncCount,

  login(email, password) {
    return request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    })
  },

  register(email, password) {
    return request('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    })
  },

  getMe() {
    return request('/auth/me')
  },

  getUserPreferences() {
    if (isLocalMode()) {
      return Promise.resolve(null)
    }
    return tryRequest('/auth/preferences')
  },

  updateUserPreferences(preferences) {
    if (isLocalMode()) {
      return Promise.resolve(null)
    }
    return tryRequest('/auth/preferences', {
      method: 'PATCH',
      body: JSON.stringify(preferences)
    })
  },

  async getProjects() {
    if (isLocalMode()) {
      return await offlineStorage.getAllProjects()
    }

    const result = await tryRequest('/projects')

    if (result !== null) {
      await offlineStorage.saveProjects(result)
      return result
    }

    return await offlineStorage.getAllProjects()
  },

  async getClients() {
    if (isLocalMode()) {
      const projects = await offlineStorage.getAllProjects()
      const byName = new Map()
      for (const p of projects) {
        if (p.client_name && !byName.has(p.client_name)) {
          byName.set(p.client_name, { id: p.client_id, name: p.client_name })
        }
      }
      return Array.from(byName.values()).sort((a, b) =>
        (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' })
      )
    }
    const result = await tryRequest('/clients')
    return result !== null ? result : []
  },

  async createProject(data = {}) {
    const payload = {
      name: data.name,
      type: data.type || null,
      clientName: data.clientName || null,
      clientId: data.clientId || null
    }

    if (isLocalMode()) {
      const localProject = {
        id: offlineStorage.generateProjectId(),
        name: payload.name,
        type: payload.type,
        client_id: null,
        client_name: payload.clientName || null
      }
      await offlineStorage.saveProject(localProject)
      return localProject
    }

    const result = await tryRequest('/projects', {
      method: 'POST',
      body: JSON.stringify(payload)
    })

    if (result !== null) {
      await offlineStorage.saveProject(result)
      return result
    }

    throw new Error('Offline - unable to create project')
  },

  async updateProject(id, data = {}) {
    const payload = {
      name: data.name,
      type: data.type ?? null,
      clientName: data.clientName ?? null,
      clientId: data.clientId ?? null
    }
    if (isLocalMode()) {
      const existing = await offlineStorage.getProject(id)
      if (!existing) throw new Error('Project not found')
      const updated = {
        ...existing,
        name: payload.name ?? existing.name,
        type: Object.prototype.hasOwnProperty.call(data, 'type') ? (payload.type || null) : existing.type
      }
      if (Object.prototype.hasOwnProperty.call(data, 'clientId')) {
        updated.client_id = payload.clientId
        updated.client_name = payload.clientId != null ? existing.client_name : null
      } else if (Object.prototype.hasOwnProperty.call(data, 'clientName')) {
        updated.client_name = payload.clientName || null
      }
      await offlineStorage.saveProject(updated)
      return updated
    }
    const result = await tryRequest(`/projects/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    })
    if (result !== null) {
      await offlineStorage.saveProject(result)
      return result
    }
    throw new Error('Offline - unable to update project')
  },

  async deleteProject(id) {
    if (isLocalMode()) {
      await offlineStorage.deleteProject(id)
      return
    }
    await request(`/projects/${id}`, { method: 'DELETE' })
    await offlineStorage.deleteProject(id)
  },

  changePassword(currentPassword, newPassword) {
    return request('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword })
    })
  },

  // Timer methods with offline support
  async getTimers() {
    // In local mode, always use local storage
    if (isLocalMode()) {
      return await offlineStorage.getAllTimers()
    }

    const result = await tryRequest('/timers')
    
    if (result !== null) {
      // Online - save to local storage and return
      await offlineStorage.saveTimers(result)
      return result
    }
    
    // Offline - return from local storage
    return await offlineStorage.getAllTimers()
  },

  async getTags() {
    // In local mode, always use local storage
    if (isLocalMode()) {
      return await offlineStorage.getLocalTags()
    }

    const result = await tryRequest('/timers/tags')
    
    if (result !== null) {
      return result
    }
    
    // Offline - get tags from local timers
    return await offlineStorage.getLocalTags()
  },

  async createTimer(data = {}) {
    // Add start_time if not provided
    const timerData = {
      ...data,
      start_time: data.start_time || new Date().toISOString()
    }

    // In local mode, always create locally
    if (isLocalMode()) {
      const localId = offlineStorage.generateLocalId()
      const localTimer = {
        id: localId,
        ...timerData,
        end_time: timerData.end_time || null
      }
      await offlineStorage.saveTimer(localTimer)
      return localTimer
    }

    const result = await tryRequest('/timers', {
      method: 'POST',
      body: JSON.stringify(timerData)
    })

    if (result !== null) {
      // Online - save to local storage
      await offlineStorage.saveTimer(result)
      return result
    }

    // Offline - create locally with temporary ID
    const localId = offlineStorage.generateLocalId()
    const localTimer = {
      id: localId,
      ...timerData,
      end_time: timerData.end_time || null
    }

    await offlineStorage.saveTimer(localTimer)
    await offlineStorage.addToSyncQueue({
      type: 'create',
      localId: localId,
      data: timerData
    })

    return localTimer
  },

  async updateTimer(id, data) {
    // Always update local storage first
    const existingTimer = await offlineStorage.getTimer(id)
    if (existingTimer) {
      const updatedTimer = { ...existingTimer, ...data }
      await offlineStorage.saveTimer(updatedTimer)
    }

    // In local mode, just return the updated timer
    if (isLocalMode()) {
      return await offlineStorage.getTimer(id)
    }

    const result = await tryRequest(`/timers/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(data)
    })

    if (result !== null) {
      // Online - save updated timer
      await offlineStorage.saveTimer(result)
      return result
    }

    // Offline - queue the update
    await offlineStorage.addToSyncQueue({
      type: 'update',
      timerId: id,
      data: data
    })

    // Return the locally updated timer
    return await offlineStorage.getTimer(id)
  },

  async stopTimer(id) {
    const endTime = new Date().toISOString()
    
    // Always update local storage first
    const existingTimer = await offlineStorage.getTimer(id)
    if (existingTimer) {
      existingTimer.end_time = endTime
      await offlineStorage.saveTimer(existingTimer)
    }

    // In local mode, just return the updated timer
    if (isLocalMode()) {
      return existingTimer
    }

    const result = await tryRequest(`/timers/${id}/stop`, {
      method: 'POST'
    })

    if (result !== null) {
      await offlineStorage.saveTimer(result)
      return result
    }

    // Offline - queue the stop
    await offlineStorage.addToSyncQueue({
      type: 'stop',
      timerId: id,
      data: { end_time: endTime }
    })

    return existingTimer
  },

  async deleteTimer(id) {
    // Delete locally first
    await offlineStorage.deleteTimer(id)

    // In local mode, we're done
    if (isLocalMode()) {
      return null
    }

    const result = await tryRequest(`/timers/${id}`, {
      method: 'DELETE'
    })

    if (result === null && isOnline === false) {
      // Offline - queue the delete (only for server IDs)
      if (!offlineStorage.isLocalId(id)) {
        await offlineStorage.addToSyncQueue({
          type: 'delete',
          timerId: id
        })
      }
    }

    return null
  },

  // Sync methods for QR code pairing
  async createSyncSession() {
    const timers = await offlineStorage.getAllTimers()
    const deviceId = getDeviceId()
    
    const res = await fetch(`${API_BASE}/sync/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceId, timers })
    })
    
    if (!res.ok) {
      const data = await res.json()
      throw new Error(data.error || 'Failed to create sync session')
    }
    
    return await res.json()
  },

  async joinSyncSession(syncCode) {
    const timers = await offlineStorage.getAllTimers()
    const deviceId = getDeviceId()
    
    const res = await fetch(`${API_BASE}/sync/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ syncCode, deviceId, timers })
    })
    
    if (!res.ok) {
      const data = await res.json()
      throw new Error(data.error || 'Failed to join sync session')
    }
    
    return await res.json()
  },

  async getSyncStatus(syncCode) {
    const res = await fetch(`${API_BASE}/sync/status/${syncCode}`)
    
    if (!res.ok) {
      const data = await res.json()
      throw new Error(data.error || 'Failed to get sync status')
    }
    
    return await res.json()
  },

  async completeSyncImport(timers) {
    // Merge timers into local storage
    const existingTimers = await offlineStorage.getAllTimers()
    const existingIds = new Set(existingTimers.map(t => t.id))
    
    // Add new timers, update existing ones
    for (const timer of timers) {
      // Generate new local ID if it conflicts
      if (existingIds.has(timer.id)) {
        const newId = offlineStorage.generateLocalId()
        await offlineStorage.saveTimer({ ...timer, id: newId })
      } else {
        await offlineStorage.saveTimer(timer)
      }
    }
  },

  // Local mode helpers
  isLocalMode,
  setLocalMode,
  getDeviceId,
  
  // Logout (clears all local data)
  logout
}
