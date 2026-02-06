// IndexedDB-based offline storage for timers

const DB_NAME = 'yatt-offline'
const DB_VERSION = 2

let db = null

function openDB() {
  return new Promise((resolve, reject) => {
    if (db) {
      resolve(db)
      return
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => {
      db = request.result
      resolve(db)
    }

    request.onupgradeneeded = (event) => {
      const database = event.target.result

      // Store for cached timers
      if (!database.objectStoreNames.contains('timers')) {
        const timerStore = database.createObjectStore('timers', { keyPath: 'id' })
        timerStore.createIndex('localId', 'localId', { unique: false })
      }

      // Store for cached projects
      if (!database.objectStoreNames.contains('projects')) {
        const projectStore = database.createObjectStore('projects', { keyPath: 'id' })
        projectStore.createIndex('name', 'name', { unique: false })
      }

      // Store for pending sync operations
      if (!database.objectStoreNames.contains('syncQueue')) {
        database.createObjectStore('syncQueue', { keyPath: 'id', autoIncrement: true })
      }

      // Store for metadata (last sync time, etc)
      if (!database.objectStoreNames.contains('meta')) {
        database.createObjectStore('meta', { keyPath: 'key' })
      }
    }
  })
}

// Generate a temporary local ID for offline-created timers
export function generateLocalId() {
  return `local_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

export function generateProjectId() {
  return `local_project_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

// Check if an ID is a local (offline) ID
export function isLocalId(id) {
  return typeof id === 'string' && id.startsWith('local_')
}

// Timer operations
export async function getAllTimers() {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['timers'], 'readonly')
    const store = transaction.objectStore('timers')
    const request = store.getAll()

    request.onerror = () => reject(request.error)
    request.onsuccess = () => {
      // Sort by start_time descending
      const timers = request.result.sort((a, b) => 
        new Date(b.start_time) - new Date(a.start_time)
      )
      resolve(timers)
    }
  })
}

export async function getTimer(id) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['timers'], 'readonly')
    const store = transaction.objectStore('timers')
    const request = store.get(id)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result)
  })
}

export async function saveTimer(timer) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['timers'], 'readwrite')
    const store = transaction.objectStore('timers')
    const request = store.put(timer)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(timer)
  })
}

export async function saveTimers(timers) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['timers'], 'readwrite')
    const store = transaction.objectStore('timers')

    // Clear existing timers and add new ones
    const clearRequest = store.clear()
    
    clearRequest.onsuccess = () => {
      let completed = 0
      if (timers.length === 0) {
        resolve()
        return
      }
      
      for (const timer of timers) {
        const request = store.put(timer)
        request.onsuccess = () => {
          completed++
          if (completed === timers.length) {
            resolve()
          }
        }
        request.onerror = () => reject(request.error)
      }
    }
    
    clearRequest.onerror = () => reject(clearRequest.error)
  })
}

export async function deleteTimer(id) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['timers'], 'readwrite')
    const store = transaction.objectStore('timers')
    const request = store.delete(id)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve()
  })
}

// Update local ID to server ID after sync
export async function updateTimerId(localId, serverId, serverData) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['timers'], 'readwrite')
    const store = transaction.objectStore('timers')
    
    // Delete old entry
    const deleteRequest = store.delete(localId)
    
    deleteRequest.onsuccess = () => {
      // Add new entry with server ID
      const newTimer = { ...serverData, id: serverId }
      const putRequest = store.put(newTimer)
      
      putRequest.onerror = () => reject(putRequest.error)
      putRequest.onsuccess = () => resolve(newTimer)
    }
    
    deleteRequest.onerror = () => reject(deleteRequest.error)
  })
}

// Project operations
export async function getAllProjects() {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['projects'], 'readonly')
    const store = transaction.objectStore('projects')
    const request = store.getAll()

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result || [])
  })
}

export async function getProject(id) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['projects'], 'readonly')
    const store = transaction.objectStore('projects')
    const request = store.get(id)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result)
  })
}

export async function saveProject(project) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['projects'], 'readwrite')
    const store = transaction.objectStore('projects')
    const request = store.put(project)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(project)
  })
}

export async function saveProjects(projects) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['projects'], 'readwrite')
    const store = transaction.objectStore('projects')

    const clearRequest = store.clear()

    clearRequest.onsuccess = () => {
      let completed = 0
      if (projects.length === 0) {
        resolve()
        return
      }

      for (const project of projects) {
        const request = store.put(project)
        request.onsuccess = () => {
          completed++
          if (completed === projects.length) {
            resolve()
          }
        }
        request.onerror = () => reject(request.error)
      }
    }

    clearRequest.onerror = () => reject(clearRequest.error)
  })
}

export async function deleteProject(id) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['projects'], 'readwrite')
    const store = transaction.objectStore('projects')
    const request = store.delete(id)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve()
  })
}

// Sync queue operations
export async function addToSyncQueue(operation) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['syncQueue'], 'readwrite')
    const store = transaction.objectStore('syncQueue')
    const request = store.add({
      ...operation,
      timestamp: Date.now()
    })

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result)
  })
}

export async function getSyncQueue() {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['syncQueue'], 'readonly')
    const store = transaction.objectStore('syncQueue')
    const request = store.getAll()

    request.onerror = () => reject(request.error)
    request.onsuccess = () => {
      // Sort by timestamp (oldest first)
      const queue = request.result.sort((a, b) => a.timestamp - b.timestamp)
      resolve(queue)
    }
  })
}

export async function clearSyncQueue() {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['syncQueue'], 'readwrite')
    const store = transaction.objectStore('syncQueue')
    const request = store.clear()

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve()
  })
}

export async function removeSyncQueueItem(id) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['syncQueue'], 'readwrite')
    const store = transaction.objectStore('syncQueue')
    const request = store.delete(id)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve()
  })
}

// Get pending sync count
export async function getPendingSyncCount() {
  const queue = await getSyncQueue()
  return queue.length
}

// Meta operations
export async function setMeta(key, value) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['meta'], 'readwrite')
    const store = transaction.objectStore('meta')
    const request = store.put({ key, value })

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve()
  })
}

export async function getMeta(key) {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['meta'], 'readonly')
    const store = transaction.objectStore('meta')
    const request = store.get(key)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result?.value)
  })
}

const DELETED_TIMERS_KEY = 'deleted_timers'
const DELETED_PROJECTS_KEY = 'deleted_projects'

export async function getDeletedTimers() {
  return (await getMeta(DELETED_TIMERS_KEY)) || []
}

export async function getDeletedProjects() {
  return (await getMeta(DELETED_PROJECTS_KEY)) || []
}

async function setDeletedList(key, list) {
  await setMeta(key, list)
}

function mergeDeletionEntry(existing, incoming) {
  if (!existing) return incoming
  const existingTime = Date.parse(existing.deleted_at || '')
  const incomingTime = Date.parse(incoming.deleted_at || '')
  if (Number.isNaN(existingTime)) return incoming
  if (Number.isNaN(incomingTime)) return existing
  return incomingTime >= existingTime ? incoming : existing
}

async function addDeletedItem(key, id, deletedAt = new Date().toISOString()) {
  const current = (await getMeta(key)) || []
  const byId = new Map(current.map((entry) => [String(entry.id), entry]))
  const next = mergeDeletionEntry(byId.get(String(id)), { id, deleted_at: deletedAt })
  byId.set(String(id), next)
  await setDeletedList(key, Array.from(byId.values()))
}

async function removeDeletedItem(key, id) {
  const current = (await getMeta(key)) || []
  const filtered = current.filter((entry) => String(entry.id) !== String(id))
  await setDeletedList(key, filtered)
}

export async function addDeletedTimer(id, deletedAt) {
  await addDeletedItem(DELETED_TIMERS_KEY, id, deletedAt)
}

export async function addDeletedProject(id, deletedAt) {
  await addDeletedItem(DELETED_PROJECTS_KEY, id, deletedAt)
}

export async function setDeletedTimers(list) {
  await setDeletedList(DELETED_TIMERS_KEY, list || [])
}

export async function setDeletedProjects(list) {
  await setDeletedList(DELETED_PROJECTS_KEY, list || [])
}

export async function removeDeletedTimer(id) {
  await removeDeletedItem(DELETED_TIMERS_KEY, id)
}

export async function removeDeletedProject(id) {
  await removeDeletedItem(DELETED_PROJECTS_KEY, id)
}

// Clear all local data (for logout)
export async function clearAllData() {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['timers', 'projects', 'syncQueue', 'meta'], 'readwrite')
    
    const timersClear = transaction.objectStore('timers').clear()
    const projectsClear = transaction.objectStore('projects').clear()
    const syncQueueClear = transaction.objectStore('syncQueue').clear()
    const metaClear = transaction.objectStore('meta').clear()
    
    transaction.oncomplete = () => resolve()
    transaction.onerror = () => reject(transaction.error)
  })
}

// Get unique tags from local timers
export async function getLocalTags() {
  const timers = await getAllTimers()
  const tagMap = new Map()
  
  for (const timer of timers) {
    if (timer.tag) {
      const existing = tagMap.get(timer.tag)
      if (!existing || new Date(timer.start_time) > new Date(existing)) {
        tagMap.set(timer.tag, timer.start_time)
      }
    }
  }
  
  // Sort by most recently used
  return Array.from(tagMap.entries())
    .sort((a, b) => new Date(b[1]) - new Date(a[1]))
    .map(([tag]) => tag)
}
