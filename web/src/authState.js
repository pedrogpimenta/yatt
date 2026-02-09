const LOCAL_MODE_KEY = 'yatt_local_mode'
const CLOUD_MODE_KEY = 'yatt_cloud_mode'
const DEVICE_ID_KEY = 'yatt_device_id'

function generateDeviceId() {
  return `device_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

function ensureDeviceId() {
  let deviceId = localStorage.getItem(DEVICE_ID_KEY)
  if (!deviceId) {
    deviceId = generateDeviceId()
    localStorage.setItem(DEVICE_ID_KEY, deviceId)
  }
  return deviceId
}

export function getDeviceId() {
  return ensureDeviceId()
}

export function getAuthMode() {
  const cloudProvider = localStorage.getItem(CLOUD_MODE_KEY)
  if (cloudProvider) return cloudProvider
  if (localStorage.getItem(LOCAL_MODE_KEY) === 'true') return 'local'
  return 'account'
}

export function isCloudMode() {
  return !!localStorage.getItem(CLOUD_MODE_KEY)
}

export function getCloudProvider() {
  return localStorage.getItem(CLOUD_MODE_KEY)
}

export function isLocalMode() {
  return localStorage.getItem(LOCAL_MODE_KEY) === 'true' || isCloudMode()
}

export function setLocalMode(enabled) {
  if (enabled) {
    localStorage.setItem(LOCAL_MODE_KEY, 'true')
    localStorage.removeItem(CLOUD_MODE_KEY)
    ensureDeviceId()
  } else {
    localStorage.removeItem(LOCAL_MODE_KEY)
  }
}

export function setCloudMode(provider) {
  if (provider) {
    localStorage.setItem(CLOUD_MODE_KEY, provider)
    localStorage.removeItem(LOCAL_MODE_KEY)
    ensureDeviceId()
  } else {
    localStorage.removeItem(CLOUD_MODE_KEY)
  }
}

export function clearAuthMode() {
  localStorage.removeItem(LOCAL_MODE_KEY)
  localStorage.removeItem(CLOUD_MODE_KEY)
  localStorage.removeItem(DEVICE_ID_KEY)
}
