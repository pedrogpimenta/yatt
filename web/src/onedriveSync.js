import { PublicClientApplication, InteractionRequiredAuthError } from '@azure/msal-browser'
import * as offlineStorage from './offlineStorage.js'
import {
  applyRemotePreferences,
  getPreferencesUpdatedAt,
  preferences,
  setPreferencesUpdatedAt
} from './preferences.js'
import {
  buildKdfParams,
  decryptPayload,
  deriveKeyFromPassphrase,
  encryptPayload,
  exportKeyToBase64,
  generateSalt,
  importKeyFromBase64
} from './cloudCrypto.js'
import { getDeviceId, isCloudMode, setCloudMode } from './authState.js'

const GRAPH_BASE = 'https://graph.microsoft.com/v1.0'
const FILE_NAME = 'yatt-sync.json'
const SCOPES = ['Files.ReadWrite.AppFolder', 'User.Read', 'offline_access']

const KEY_STORAGE = 'yatt_onedrive_key'
const KDF_STORAGE = 'yatt_onedrive_kdf'
const STATUS_STORAGE = 'yatt_onedrive_status'

let msalClient = null
let syncInProgress = false
let autoSyncTimer = null
let scheduledSync = null

function getClientId() {
  return import.meta.env.VITE_ONEDRIVE_CLIENT_ID
}

function getMsalClient() {
  if (msalClient) return msalClient
  const clientId = getClientId()
  if (!clientId) {
    throw new Error('OneDrive client ID is not configured')
  }
  msalClient = new PublicClientApplication({
    auth: {
      clientId,
      authority: 'https://login.microsoftonline.com/common',
      redirectUri: window.location.origin
    },
    cache: {
      cacheLocation: 'localStorage'
    }
  })
  return msalClient
}

function getStoredStatus() {
  try {
    const raw = localStorage.getItem(STATUS_STORAGE)
    return raw ? JSON.parse(raw) : {}
  } catch (err) {
    return {}
  }
}

function setStoredStatus(next) {
  localStorage.setItem(STATUS_STORAGE, JSON.stringify(next))
}

async function loginOneDrive() {
  const client = getMsalClient()
  const result = await client.loginPopup({ scopes: SCOPES })
  if (result?.account) {
    client.setActiveAccount(result.account)
  }
  return result.account
}

async function getAccessToken() {
  const client = getMsalClient()
  let account = client.getActiveAccount()
  if (!account) {
    const accounts = client.getAllAccounts()
    account = accounts[0] || null
  }
  if (!account) {
    throw new Error('No OneDrive account connected')
  }
  try {
    const response = await client.acquireTokenSilent({ scopes: SCOPES, account })
    return response.accessToken
  } catch (err) {
    if (err instanceof InteractionRequiredAuthError) {
      const response = await client.acquireTokenPopup({ scopes: SCOPES })
      return response.accessToken
    }
    throw err
  }
}

async function graphRequest(path, token, options = {}) {
  const res = await fetch(`${GRAPH_BASE}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      ...options.headers
    }
  })
  if (res.status === 404) return null
  if (!res.ok) {
    const message = await res.text()
    throw new Error(message || 'OneDrive request failed')
  }
  return res.json()
}

async function fetchRemoteMetadata(token) {
  return graphRequest(`/me/drive/special/approot:/${FILE_NAME}`, token)
}

async function downloadRemoteContent(downloadUrl) {
  const res = await fetch(downloadUrl)
  if (!res.ok) {
    throw new Error('Failed to download OneDrive data')
  }
  return res.text()
}

async function uploadRemoteContent(token, content, etag = null) {
  const res = await fetch(`${GRAPH_BASE}/me/drive/special/approot:/${FILE_NAME}:/content`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...(etag ? { 'If-Match': etag } : {})
    },
    body: content
  })
  if (!res.ok) {
    const message = await res.text()
    throw new Error(message || 'Failed to upload OneDrive data')
  }
  return res.json()
}

async function loadStoredKey() {
  const raw = localStorage.getItem(KEY_STORAGE)
  if (!raw) return null
  return importKeyFromBase64(raw)
}

async function storeKey(key) {
  const base64 = await exportKeyToBase64(key)
  localStorage.setItem(KEY_STORAGE, base64)
}

function getStoredKdf() {
  try {
    const raw = localStorage.getItem(KDF_STORAGE)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function storeKdf(kdf) {
  localStorage.setItem(KDF_STORAGE, JSON.stringify(kdf))
}

function normalizeTimestamp(value) {
  if (!value) return null
  const time = Date.parse(value)
  if (Number.isNaN(time)) return null
  return new Date(time).toISOString()
}

function normalizeTimer(timer) {
  const updatedAt =
    normalizeTimestamp(timer.updated_at) ||
    normalizeTimestamp(timer.updatedAt) ||
    normalizeTimestamp(timer.end_time) ||
    normalizeTimestamp(timer.start_time) ||
    new Date().toISOString()
  return { ...timer, updated_at: updatedAt }
}

function normalizeProject(project) {
  const updatedAt =
    normalizeTimestamp(project.updated_at) ||
    normalizeTimestamp(project.updatedAt) ||
    new Date().toISOString()
  return { ...project, updated_at: updatedAt }
}

function normalizeDeletion(entry) {
  const deletedAt = normalizeTimestamp(entry.deleted_at) || new Date().toISOString()
  return { id: entry.id, deleted_at: deletedAt }
}

function mergeRecords(localItems, remoteItems, localDeletes, remoteDeletes) {
  const itemMap = new Map()
  const putLatest = (item) => {
    const id = String(item.id)
    const existing = itemMap.get(id)
    if (!existing) {
      itemMap.set(id, item)
      return
    }
    const existingTime = Date.parse(existing.updated_at || '')
    const incomingTime = Date.parse(item.updated_at || '')
    if (Number.isNaN(existingTime) || incomingTime >= existingTime) {
      itemMap.set(id, item)
    }
  }

  localItems.forEach(putLatest)
  remoteItems.forEach(putLatest)

  const deletionMap = new Map()
  const mergeDeletion = (entry) => {
    const id = String(entry.id)
    const existing = deletionMap.get(id)
    if (!existing) {
      deletionMap.set(id, entry)
      return
    }
    const existingTime = Date.parse(existing.deleted_at || '')
    const incomingTime = Date.parse(entry.deleted_at || '')
    if (Number.isNaN(existingTime) || incomingTime >= existingTime) {
      deletionMap.set(id, entry)
    }
  }

  localDeletes.forEach(mergeDeletion)
  remoteDeletes.forEach(mergeDeletion)

  for (const [id, deletion] of deletionMap.entries()) {
    const item = itemMap.get(id)
    if (!item) continue
    const itemTime = Date.parse(item.updated_at || '')
    const deleteTime = Date.parse(deletion.deleted_at || '')
    if (!Number.isNaN(deleteTime) && (Number.isNaN(itemTime) || deleteTime >= itemTime)) {
      itemMap.delete(id)
    } else if (!Number.isNaN(itemTime) && itemTime > deleteTime) {
      deletionMap.delete(id)
    }
  }

  return {
    items: Array.from(itemMap.values()),
    deletions: Array.from(deletionMap.values())
  }
}

async function buildLocalPayload() {
  const [timers, projects, deletedTimers, deletedProjects] = await Promise.all([
    offlineStorage.getAllTimers(),
    offlineStorage.getAllProjects(),
    offlineStorage.getDeletedTimers(),
    offlineStorage.getDeletedProjects()
  ])

  return {
    version: 1,
    syncedAt: new Date().toISOString(),
    deviceId: getDeviceId(),
    timers: (timers || []).map(normalizeTimer),
    projects: (projects || []).map(normalizeProject),
    deleted: {
      timers: (deletedTimers || []).map(normalizeDeletion),
      projects: (deletedProjects || []).map(normalizeDeletion)
    },
    preferences: { ...preferences },
    preferencesUpdatedAt: getPreferencesUpdatedAt() || new Date().toISOString()
  }
}

function mergePayloads(localPayload, remotePayload) {
  const localTimers = (localPayload.timers || []).map(normalizeTimer)
  const remoteTimers = (remotePayload?.timers || []).map(normalizeTimer)
  const localProjects = (localPayload.projects || []).map(normalizeProject)
  const remoteProjects = (remotePayload?.projects || []).map(normalizeProject)

  const timerMerge = mergeRecords(
    localTimers,
    remoteTimers,
    (localPayload.deleted?.timers || []).map(normalizeDeletion),
    (remotePayload?.deleted?.timers || []).map(normalizeDeletion)
  )
  const projectMerge = mergeRecords(
    localProjects,
    remoteProjects,
    (localPayload.deleted?.projects || []).map(normalizeDeletion),
    (remotePayload?.deleted?.projects || []).map(normalizeDeletion)
  )

  const localPrefsUpdatedAt = getPreferencesUpdatedAt()
  const remotePrefsUpdatedAt = remotePayload?.preferencesUpdatedAt
  let mergedPreferences = { ...preferences }
  let mergedPreferencesUpdatedAt = localPrefsUpdatedAt || new Date().toISOString()
  let appliedRemote = false

  if (remotePayload?.preferences && remotePrefsUpdatedAt) {
    if (!localPrefsUpdatedAt || Date.parse(remotePrefsUpdatedAt) > Date.parse(localPrefsUpdatedAt)) {
      applyRemotePreferences(remotePayload.preferences, remotePrefsUpdatedAt)
      mergedPreferences = { ...preferences }
      mergedPreferencesUpdatedAt = remotePrefsUpdatedAt
      appliedRemote = true
    }
  }

  if (!appliedRemote && !localPrefsUpdatedAt) {
    setPreferencesUpdatedAt(mergedPreferencesUpdatedAt)
  }

  return {
    payload: {
      version: 1,
      syncedAt: new Date().toISOString(),
      deviceId: getDeviceId(),
      timers: timerMerge.items,
      projects: projectMerge.items,
      deleted: {
        timers: timerMerge.deletions,
        projects: projectMerge.deletions
      },
      preferences: mergedPreferences,
      preferencesUpdatedAt: mergedPreferencesUpdatedAt
    },
    mergedTimers: timerMerge.items,
    mergedProjects: projectMerge.items,
    mergedDeletedTimers: timerMerge.deletions,
    mergedDeletedProjects: projectMerge.deletions
  }
}

async function applyMergedLocalData(mergeResult) {
  await Promise.all([
    offlineStorage.saveTimers(mergeResult.mergedTimers),
    offlineStorage.saveProjects(mergeResult.mergedProjects),
    offlineStorage.setDeletedTimers(mergeResult.mergedDeletedTimers),
    offlineStorage.setDeletedProjects(mergeResult.mergedDeletedProjects)
  ])
}

export async function connectOneDrive(passphrase) {
  if (!passphrase || passphrase.length < 8) {
    throw new Error('Passphrase must be at least 8 characters')
  }
  await loginOneDrive()

  const token = await getAccessToken()
  const metadata = await fetchRemoteMetadata(token)
  let remotePayload = null

  if (metadata?.['@microsoft.graph.downloadUrl']) {
    const content = await downloadRemoteContent(metadata['@microsoft.graph.downloadUrl'])
    const encrypted = JSON.parse(content)
    const kdf = encrypted.kdf
    if (!kdf?.salt || !kdf?.iterations) {
      throw new Error('Missing encryption parameters for OneDrive data')
    }
    const key = await deriveKeyFromPassphrase(passphrase, atobToBytes(kdf.salt), kdf.iterations)
    remotePayload = await decryptPayload(encrypted, key)
    await storeKey(key)
    storeKdf(kdf)
  } else {
    const salt = generateSalt()
    const kdf = buildKdfParams(salt)
    const key = await deriveKeyFromPassphrase(passphrase, salt, kdf.iterations)
    await storeKey(key)
    storeKdf(kdf)
  }

  const localPayload = await buildLocalPayload()
  const mergeResult = mergePayloads(localPayload, remotePayload)
  await applyMergedLocalData(mergeResult)

  const key = await loadStoredKey()
  const kdf = getStoredKdf()
  if (!key || !kdf) {
    throw new Error('Failed to initialize encryption key')
  }
  const encrypted = await encryptPayload(mergeResult.payload, key, kdf)
  const uploadResult = await uploadRemoteContent(token, JSON.stringify(encrypted))
  setStoredStatus({
    lastEtag: uploadResult?.eTag || null,
    lastSyncAt: new Date().toISOString()
  })
  await offlineStorage.clearSyncQueue()
  setCloudMode('onedrive')
  startAutoSync()
}

function atobToBytes(base64) {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

export async function syncNow() {
  if (!isCloudMode()) {
    return { success: false, synced: 0 }
  }
  if (!navigator.onLine) {
    return { success: false, synced: 0 }
  }
  if (syncInProgress) {
    return { success: false, synced: 0 }
  }
  syncInProgress = true
  try {
    const token = await getAccessToken()
    const [pendingCount, localPayload] = await Promise.all([
      offlineStorage.getPendingSyncCount(),
      buildLocalPayload()
    ])
    const status = getStoredStatus()
    const metadata = await fetchRemoteMetadata(token)
    let remotePayload = null
    let remoteEtag = metadata?.eTag || null
    if (metadata?.['@microsoft.graph.downloadUrl']) {
      if (status.lastEtag && status.lastEtag === remoteEtag && pendingCount === 0) {
        return { success: true, synced: 0 }
      }
      const content = await downloadRemoteContent(metadata['@microsoft.graph.downloadUrl'])
      const encrypted = JSON.parse(content)
      const key = await loadStoredKey()
      if (!key) {
        throw new Error('Missing encryption key for OneDrive sync')
      }
      remotePayload = await decryptPayload(encrypted, key)
    }

    const mergeResult = mergePayloads(localPayload, remotePayload)
    await applyMergedLocalData(mergeResult)

    const key = await loadStoredKey()
    const kdf = getStoredKdf()
    if (!key || !kdf) {
      throw new Error('Missing encryption settings for OneDrive sync')
    }
    const encrypted = await encryptPayload(mergeResult.payload, key, kdf)
    const uploadResult = await uploadRemoteContent(token, JSON.stringify(encrypted), remoteEtag)
    remoteEtag = uploadResult?.eTag || remoteEtag
    setStoredStatus({
      lastEtag: remoteEtag,
      lastSyncAt: new Date().toISOString()
    })
    await offlineStorage.clearSyncQueue()
    const syncedCount = pendingCount > 0 || remotePayload ? Math.max(1, pendingCount) : 0
    return { success: true, synced: syncedCount }
  } catch (err) {
    console.error('OneDrive sync failed:', err)
    return { success: false, synced: 0 }
  } finally {
    syncInProgress = false
  }
}

export function markDirty() {
  if (!isCloudMode()) return
  if (scheduledSync) return
  scheduledSync = setTimeout(async () => {
    scheduledSync = null
    await syncNow()
  }, 2000)
}

export function startAutoSync() {
  if (autoSyncTimer) return
  autoSyncTimer = setInterval(() => {
    syncNow()
  }, 60000)
}

export function stopAutoSync() {
  if (autoSyncTimer) {
    clearInterval(autoSyncTimer)
    autoSyncTimer = null
  }
  if (scheduledSync) {
    clearTimeout(scheduledSync)
    scheduledSync = null
  }
}

export function getLastSyncAt() {
  return getStoredStatus().lastSyncAt || null
}

export async function getPendingCount() {
  if (!isCloudMode()) return 0
  return offlineStorage.getPendingSyncCount()
}

export async function disconnect() {
  stopAutoSync()
  localStorage.removeItem(KEY_STORAGE)
  localStorage.removeItem(KDF_STORAGE)
  localStorage.removeItem(STATUS_STORAGE)
}
