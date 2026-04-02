import * as offlineStorage from './offlineStorage.js'
import { preferences } from './preferences.js'
import * as dropboxStorage from './dropboxStorage.js'

const API_BASE = '/api'

// Connection state
let isOnline = navigator.onLine
let onlineListeners = new Set()
let syncInProgress = false
let authListeners = new Set()

const SESSION_EXPIRED_MESSAGE = 'Your session expired. Please sign in again.'

// Local-only mode (no account)
const LOCAL_MODE_KEY = 'yatt_local_mode'
const DEVICE_ID_KEY = 'yatt_device_id'

function isLocalMode() {
  return localStorage.getItem(LOCAL_MODE_KEY) === 'true'
}

function isDropboxMode() {
  return dropboxStorage.isDropboxMode()
}

// --- Dropbox sync ---

let dropboxSyncTimeout = null

function scheduleSyncToDropbox() {
  if (dropboxSyncTimeout) clearTimeout(dropboxSyncTimeout)
  dropboxSyncTimeout = setTimeout(() => {
    dropboxSyncTimeout = null
    performDropboxSync().catch(console.error)
  }, 1500)
}

async function performDropboxSync() {
  const [timers, projects] = await Promise.all([
    offlineStorage.getAllTimers(),
    offlineStorage.getAllProjects(),
  ])
  const dailyGoals = JSON.parse(localStorage.getItem('yatt_daily_goals') || '{}')
  await dropboxStorage.uploadData({
    version: 1,
    updated_at: new Date().toISOString(),
    timers,
    projects,
    daily_goals: dailyGoals,
  })
}

async function loadDropboxData() {
  const data = await dropboxStorage.downloadData()
  if (!data) return
  if (data.timers) await offlineStorage.saveTimers(data.timers)
  if (data.projects) await offlineStorage.saveProjects(data.projects)
  if (data.daily_goals) localStorage.setItem('yatt_daily_goals', JSON.stringify(data.daily_goals))
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

function isNetworkError(err) {
  return err?.message === 'Failed to fetch' || err?.name === 'TypeError'
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
  if (isLocalMode()) return 'local_mode'
  if (isDropboxMode()) return 'dropbox_mode'
  return localStorage.getItem('token')
}

function setToken(token) {
  localStorage.setItem('token', token)
}

function clearToken() {
  localStorage.removeItem('token')
  setLocalMode(false)
}

function notifyAuthExpired(message = SESSION_EXPIRED_MESSAGE) {
  const hadStoredToken = !!localStorage.getItem('token')
  disconnectWebSocket()
  clearToken()

  if (hadStoredToken) {
    authListeners.forEach(listener => listener({ message }))
  }
}

function addAuthListener(listener) {
  authListeners.add(listener)
}

function removeAuthListener(listener) {
  authListeners.delete(listener)
}

async function logout() {
  await offlineStorage.clearAllData()
  localStorage.removeItem('token')
  localStorage.removeItem(LOCAL_MODE_KEY)
  localStorage.removeItem(DEVICE_ID_KEY)
  dropboxStorage.disconnect()
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
    let authError = null

    try {
      authError = await res.json()
    } catch {
      authError = null
    }

    const errorMessage = authError?.error || 'Unauthorized'

    if (errorMessage === 'No token provided' || errorMessage === 'Invalid token') {
      notifyAuthExpired()
      throw new Error(SESSION_EXPIRED_MESSAGE)
    }

    throw new Error(errorMessage)
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
    if (isNetworkError(err)) {
      isOnline = false
      notifyOnlineStatus(false)
      return null
    }
    throw err
  }
}

async function validateSession() {
  if (isLocalMode() || !localStorage.getItem('token') || !isOnline) {
    return null
  }

  try {
    return await request('/auth/me')
  } catch (err) {
    if (isNetworkError(err)) {
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
        } else if (operation.type === 'stop' && err.message.includes('Timer already stopped')) {
          // Timer was already stopped (e.g. by another device/tab) - desired state achieved
          await offlineStorage.removeSyncQueueItem(operation.id)
        }
        // Continue with other operations
      }
    }

    // Refresh timers and projects from server after sync
    if (syncedCount > 0) {
      try {
        const serverTimers = await request('/timers')
        await offlineStorage.saveTimers(serverTimers)
      } catch (err) {
        console.error('Failed to refresh timers after sync:', err)
      }
      try {
        const serverProjects = await request('/projects')
        await offlineStorage.saveProjects(serverProjects)
      } catch (err) {
        console.error('Failed to refresh projects after sync:', err)
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
        } else if (data.type === 'auth' && data.status === 'error') {
          notifyAuthExpired()
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
    ws.onclose = null
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
  addAuthListener,
  removeAuthListener,
  validateSession,

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
    if (isLocalMode() || isDropboxMode()) return Promise.resolve(null)
    return tryRequest('/auth/preferences')
  },

  updateUserPreferences(prefs) {
    if (isLocalMode() || isDropboxMode()) return Promise.resolve(null)
    return tryRequest('/auth/preferences', {
      method: 'PATCH',
      body: JSON.stringify(prefs)
    })
  },

  async getDailyGoals(from, to) {
    if (isLocalMode()) {
      try {
        const raw = localStorage.getItem('yatt_daily_goals')
        const all = raw ? JSON.parse(raw) : {}
        const result = {}
        for (const date of Object.keys(all)) {
          if (date >= from && date <= to) result[date] = all[date]
        }
        return result
      } catch (e) {
        return {}
      }
    }
    const result = await tryRequest(`/auth/daily-goals?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
    return result || {}
  },

  async setDailyGoal(date, hours) {
    const d = typeof date === 'string' ? new Date(date + 'T12:00:00') : date
    const dateStr = typeof date === 'string' ? date : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    if (isLocalMode() || isDropboxMode()) {
      const raw = localStorage.getItem('yatt_daily_goals') || '{}'
      const all = JSON.parse(raw)
      all[dateStr] = hours
      localStorage.setItem('yatt_daily_goals', JSON.stringify(all))
      if (isDropboxMode()) scheduleSyncToDropbox()
      return { date: dateStr, hours }
    }
    const result = await tryRequest(`/auth/daily-goals/${dateStr}`, {
      method: 'PUT',
      body: JSON.stringify({ hours })
    })
    return result || { date: dateStr, hours }
  },

  async clearDailyGoal(date) {
    const d = typeof date === 'string' ? new Date(date + 'T12:00:00') : date
    const dateStr = typeof date === 'string' ? date : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    if (isLocalMode() || isDropboxMode()) {
      const raw = localStorage.getItem('yatt_daily_goals') || '{}'
      const all = JSON.parse(raw)
      delete all[dateStr]
      localStorage.setItem('yatt_daily_goals', JSON.stringify(all))
      if (isDropboxMode()) scheduleSyncToDropbox()
      return
    }
    await tryRequest(`/auth/daily-goals/${dateStr}`, { method: 'DELETE' })
  },

  async getProjects() {
    if (isLocalMode() || isDropboxMode()) {
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
    if (isLocalMode() || isDropboxMode()) {
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

    if (isLocalMode() || isDropboxMode()) {
      const localProject = {
        id: offlineStorage.generateProjectId(),
        name: payload.name,
        type: payload.type,
        client_id: null,
        client_name: payload.clientName || null
      }
      await offlineStorage.saveProject(localProject)
      if (isDropboxMode()) scheduleSyncToDropbox()
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
    if (isLocalMode() || isDropboxMode()) {
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
      if (isDropboxMode()) scheduleSyncToDropbox()
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
    if (isLocalMode() || isDropboxMode()) {
      await offlineStorage.deleteProject(id)
      if (isDropboxMode()) scheduleSyncToDropbox()
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

  forgotPassword(email) {
    return request('/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email })
    })
  },

  resetPassword(token, newPassword) {
    return request('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ token, newPassword })
    })
  },

  // Timer methods with offline support
  async getTimers() {
    if (isLocalMode() || isDropboxMode()) {
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
    if (isLocalMode() || isDropboxMode()) {
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

    if (isLocalMode() || isDropboxMode()) {
      const localId = offlineStorage.generateLocalId()
      const localTimer = {
        id: localId,
        ...timerData,
        end_time: timerData.end_time || null
      }
      await offlineStorage.saveTimer(localTimer)
      if (isDropboxMode()) scheduleSyncToDropbox()
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

    if (isLocalMode() || isDropboxMode()) {
      if (isDropboxMode()) scheduleSyncToDropbox()
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

    if (isLocalMode() || isDropboxMode()) {
      if (isDropboxMode()) scheduleSyncToDropbox()
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

    if (isLocalMode() || isDropboxMode()) {
      if (isDropboxMode()) scheduleSyncToDropbox()
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
  getPreferencesForSync() {
    return {
      dateFormat: preferences.dateFormat,
      timeFormat: preferences.timeFormat,
      dayStartHour: preferences.dayStartHour,
      dailyGoalEnabled: preferences.dailyGoalEnabled,
      defaultDailyGoalHours: preferences.defaultDailyGoalHours,
      includeWeekendGoals: preferences.includeWeekendGoals
    }
  },

  async createSyncSession() {
    const [timers, projects] = await Promise.all([
      offlineStorage.getAllTimers(),
      offlineStorage.getAllProjects()
    ])
    const deviceId = getDeviceId()
    const prefs = this.getPreferencesForSync()
    const res = await fetch(`${API_BASE}/sync/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceId, timers, projects: projects || [], preferences: prefs })
    })
    
    if (!res.ok) {
      const data = await res.json()
      throw new Error(data.error || 'Failed to create sync session')
    }
    
    return await res.json()
  },

  async joinSyncSession(syncCode) {
    const [timers, projects] = await Promise.all([
      offlineStorage.getAllTimers(),
      offlineStorage.getAllProjects()
    ])
    const deviceId = getDeviceId()
    const prefs = this.getPreferencesForSync()
    const res = await fetch(`${API_BASE}/sync/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ syncCode, deviceId, timers, projects: projects || [], preferences: prefs })
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

  async completeSyncImport(timers, projects = null, preferencesPayload = null) {
    // Merge timers into local storage
    const existingTimers = await offlineStorage.getAllTimers()
    const existingIds = new Set(existingTimers.map(t => t.id))

    for (const timer of timers) {
      if (existingIds.has(timer.id)) {
        const newId = offlineStorage.generateLocalId()
        await offlineStorage.saveTimer({ ...timer, id: newId })
      } else {
        await offlineStorage.saveTimer(timer)
      }
    }

    // Merge projects if provided
    if (Array.isArray(projects) && projects.length > 0) {
      const existingProjects = await offlineStorage.getAllProjects()
      const existingProjectIds = new Set(existingProjects.map((p) => String(p.id)))
      for (const project of projects) {
        if (!existingProjectIds.has(String(project.id))) {
          await offlineStorage.saveProject(project)
        }
      }
    }

    // Preferences are applied by the caller (DeviceSync) so the reactive preferences object updates
    return { preferencesPayload }
  },

  // Dropbox — client-side sync; server is a stateless OAuth proxy only
  getDropboxAuthUrl() {
    // No auth required — can be called from the login screen
    return fetch(`${API_BASE}/dropbox/auth-url`).then(r => r.json())
  },

  isDropboxMode,
  loadDropboxData,

  // OneDrive sync
  getOnedriveStatus() {
    return request('/onedrive/status')
  },

  getOnedriveAuthUrl() {
    return request('/onedrive/auth-url', { method: 'POST' })
  },

  onedriveExport() {
    return request('/onedrive/export', { method: 'POST' })
  },

  onedriveImport() {
    return request('/onedrive/import', { method: 'POST' })
  },

  onedriveDisconnect() {
    return request('/onedrive/disconnect', { method: 'DELETE' })
  },

  // Local mode helpers
  isLocalMode,
  setLocalMode,
  getDeviceId,

  // Logout (clears all local data and Dropbox tokens)
  logout
}
