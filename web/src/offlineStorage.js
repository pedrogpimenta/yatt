// IndexedDB-based offline storage for timers

const DB_NAME = 'yatt-offline'
const DB_VERSION = 1

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

// Clear all local data (for logout)
export async function clearAllData() {
  const database = await openDB()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(['timers', 'syncQueue', 'meta'], 'readwrite')
    
    const timersClear = transaction.objectStore('timers').clear()
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
