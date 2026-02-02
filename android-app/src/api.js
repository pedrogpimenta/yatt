import { Preferences } from './preferences.js'
import { syncCredentials } from './notifications.js'

let API_BASE = 'http://192.168.1.170:3000'

export async function setApiUrl(url) {
  API_BASE = url
  await Preferences.set('apiUrl', url)
  // Sync to widget
  const token = await getToken()
  await syncCredentials(url, token)
  // Reconnect WebSocket with new URL
  disconnectWebSocket()
  if (token) {
    connectWebSocket()
  }
}

export async function getApiUrl() {
  const saved = await Preferences.get('apiUrl')
  if (saved) {
    API_BASE = saved
  }
  // Sync credentials to widget on init
  const token = await getToken()
  await syncCredentials(API_BASE, token)
  return API_BASE
}

async function getToken() {
  return await Preferences.get('token')
}

export async function setToken(token) {
  await Preferences.set('token', token)
  await syncCredentials(API_BASE, token)
}

export async function clearToken() {
  await Preferences.remove('token')
  await syncCredentials(API_BASE, null)
  disconnectWebSocket()
}

export async function isLoggedIn() {
  const token = await getToken()
  return !!token
}

async function request(endpoint, options = {}) {
  const token = await getToken()
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
    await clearToken()
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

// WebSocket connection
let ws = null
let wsReconnectTimer = null
let wsListeners = new Set()

function getWsUrl() {
  return API_BASE.replace('http://', 'ws://').replace('https://', 'wss://')
}

async function connectWebSocket() {
  const token = await getToken()
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

async function scheduleReconnect() {
  if (wsReconnectTimer) return
  wsReconnectTimer = setTimeout(async () => {
    wsReconnectTimer = null
    const token = await getToken()
    if (token) {
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

async function addWsListener(listener) {
  wsListeners.add(listener)
  // Connect if not already connected
  const token = await getToken()
  if (token && (!ws || ws.readyState !== WebSocket.OPEN)) {
    connectWebSocket()
  }
}

function removeWsListener(listener) {
  wsListeners.delete(listener)
}

export const api = {
  setApiUrl,
  getApiUrl,
  setToken,
  clearToken,
  isLoggedIn,

  // WebSocket methods
  connectWebSocket,
  disconnectWebSocket,
  addWsListener,
  removeWsListener,

  async login(email, password) {
    const data = await request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    })
    await setToken(data.token)
    connectWebSocket()
    return data
  },

  async register(email, password) {
    const data = await request('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    })
    await setToken(data.token)
    connectWebSocket()
    return data
  },

  getMe() {
    return request('/auth/me')
  },

  changePassword(currentPassword, newPassword) {
    return request('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword })
    })
  },

  async getToken() {
    return await getToken()
  },

  getTimers() {
    return request('/timers')
  },

  createTimer(data = {}) {
    return request('/timers', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  },

  updateTimer(id, data) {
    return request(`/timers/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(data)
    })
  },

  stopTimer(id) {
    return request(`/timers/${id}/stop`, {
      method: 'POST'
    })
  },

  deleteTimer(id) {
    return request(`/timers/${id}`, {
      method: 'DELETE'
    })
  }
}
