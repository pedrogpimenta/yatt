// Simple preferences storage using localStorage
// Works in both web and Capacitor

export const Preferences = {
  async get(key) {
    try {
      return localStorage.getItem(`yatt_${key}`)
    } catch {
      return null
    }
  },

  async set(key, value) {
    try {
      localStorage.setItem(`yatt_${key}`, value)
    } catch {
      // ignore
    }
  },

  async remove(key) {
    try {
      localStorage.removeItem(`yatt_${key}`)
    } catch {
      // ignore
    }
  }
}
