import { createApp } from 'vue'
import App from './App.vue'

createApp(App).mount('#app')

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/service-worker.js')
    .then(reg => console.log('Service Worker registered', reg))
    .catch(err => console.error('Service Worker registration failed', err))
}

if ('SyncManager' in window) {
  window.addEventListener('online', async () => {
    try {
      const reg = await navigator.serviceWorker.ready
      await reg.sync.register('check-api-state')
      console.log('Background sync registered')
    } catch (err) {
      console.warn('Background sync registration failed', err)
    }
  })
}
