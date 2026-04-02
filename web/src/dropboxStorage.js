// Dropbox storage: the client holds tokens and talks to Dropbox directly.
// The YATT server only proxies OAuth (stateless — it never stores tokens or data).

const DROPBOX_MODE_KEY = 'yatt_dropbox_mode'
const ACCESS_TOKEN_KEY = 'yatt_dropbox_access_token'
const REFRESH_TOKEN_KEY = 'yatt_dropbox_refresh_token'
const TOKEN_EXPIRES_KEY = 'yatt_dropbox_token_expires'
const FILE_PATH = '/yatt-data.json'

export function isDropboxMode() {
  return localStorage.getItem(DROPBOX_MODE_KEY) === 'true'
}

export function connect(accessToken, refreshToken, expiresAt) {
  localStorage.setItem(DROPBOX_MODE_KEY, 'true')
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  localStorage.setItem(TOKEN_EXPIRES_KEY, String(expiresAt))
}

export function disconnect() {
  localStorage.removeItem(DROPBOX_MODE_KEY)
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(TOKEN_EXPIRES_KEY)
}

export async function getValidToken() {
  const accessToken = localStorage.getItem(ACCESS_TOKEN_KEY)
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
  const expiresAt = Number(localStorage.getItem(TOKEN_EXPIRES_KEY) || 0)

  if (!accessToken || !refreshToken) return null

  if (Date.now() >= expiresAt - 60000) {
    try {
      const res = await fetch('/api/dropbox/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!res.ok) {
        disconnect()
        return null
      }
      const data = await res.json()
      localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken)
      localStorage.setItem(TOKEN_EXPIRES_KEY, String(data.expiresAt))
      return data.accessToken
    } catch {
      return null
    }
  }

  return accessToken
}

// Download the data file from Dropbox. Returns null if the file doesn't exist yet.
export async function downloadData() {
  const token = await getValidToken()
  if (!token) return null

  const res = await fetch('https://content.dropboxapi.com/2/files/download', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Dropbox-API-Arg': JSON.stringify({ path: FILE_PATH }),
    },
  })

  if (res.status === 409) return null // Not found — first use
  if (!res.ok) throw new Error('Failed to download from Dropbox')

  return res.json()
}

// Upload the full data snapshot to Dropbox, overwriting any existing file.
export async function uploadData(data) {
  const token = await getValidToken()
  if (!token) return

  const res = await fetch('https://content.dropboxapi.com/2/files/upload', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/octet-stream',
      'Dropbox-API-Arg': JSON.stringify({ path: FILE_PATH, mode: 'overwrite', autorename: false }),
    },
    body: JSON.stringify(data),
  })

  if (!res.ok) throw new Error('Failed to upload to Dropbox')
}
