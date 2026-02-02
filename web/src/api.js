const API_BASE = '/api'

function getToken() {
  return localStorage.getItem('token')
}

function setToken(token) {
  localStorage.setItem('token', token)
}

function clearToken() {
  localStorage.removeItem('token')
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

  changePassword(currentPassword, newPassword) {
    return request('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword })
    })
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
