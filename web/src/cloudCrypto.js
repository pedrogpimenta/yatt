const KDF_ITERATIONS = 200000
const SALT_LENGTH = 16
const IV_LENGTH = 12
const FORMAT = 'yatt-onedrive-v1'

function bytesToBase64(bytes) {
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary)
}

function base64ToBytes(base64) {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

export function generateSalt() {
  return crypto.getRandomValues(new Uint8Array(SALT_LENGTH))
}

export function buildKdfParams(saltBytes, iterations = KDF_ITERATIONS) {
  return {
    name: 'PBKDF2',
    hash: 'SHA-256',
    iterations,
    salt: bytesToBase64(saltBytes)
  }
}

export async function deriveKeyFromPassphrase(passphrase, saltBytes, iterations = KDF_ITERATIONS) {
  const encoder = new TextEncoder()
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    encoder.encode(passphrase),
    'PBKDF2',
    false,
    ['deriveKey']
  )
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: saltBytes,
      iterations,
      hash: 'SHA-256'
    },
    keyMaterial,
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt', 'decrypt']
  )
}

export async function exportKeyToBase64(key) {
  const raw = await crypto.subtle.exportKey('raw', key)
  return bytesToBase64(new Uint8Array(raw))
}

export async function importKeyFromBase64(base64) {
  const raw = base64ToBytes(base64)
  return crypto.subtle.importKey('raw', raw, 'AES-GCM', true, ['encrypt', 'decrypt'])
}

export async function encryptPayload(payload, key, kdfParams) {
  const encoder = new TextEncoder()
  const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH))
  const plaintext = encoder.encode(JSON.stringify(payload))
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    plaintext
  )
  return {
    format: FORMAT,
    version: 1,
    kdf: kdfParams,
    cipher: {
      name: 'AES-GCM',
      iv: bytesToBase64(iv),
      text: bytesToBase64(new Uint8Array(ciphertext))
    }
  }
}

export async function decryptPayload(encrypted, key) {
  if (!encrypted || encrypted.format !== FORMAT) {
    throw new Error('Invalid encrypted payload format')
  }
  const iv = base64ToBytes(encrypted.cipher?.iv || '')
  const text = base64ToBytes(encrypted.cipher?.text || '')
  const plaintext = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv },
    key,
    text
  )
  const decoder = new TextDecoder()
  const json = decoder.decode(plaintext)
  return JSON.parse(json)
}

export function getFormat() {
  return FORMAT
}
