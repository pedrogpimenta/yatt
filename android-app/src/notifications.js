import { registerPlugin, Capacitor } from '@capacitor/core'

const TimerNotification = registerPlugin('TimerNotification')

export async function requestPermissions() {
  console.log('requestPermissions called, platform:', Capacitor.getPlatform())
  if (Capacitor.getPlatform() !== 'android') {
    console.log('Not on Android, skipping permission request')
    return true
  }
  try {
    console.log('Calling TimerNotification.requestPermissions...')
    const result = await TimerNotification.requestPermissions()
    console.log('Permission result:', result)
    return result.granted
  } catch (err) {
    console.error('Permission request error:', err)
    return false
  }
}

export async function startTimerNotification(startTime, tag, dayTotalBase = 0) {
  console.log('startTimerNotification called, platform:', Capacitor.getPlatform())
  if (Capacitor.getPlatform() !== 'android') {
    console.log('Not on Android, skipping notification')
    return
  }
  try {
    const start = new Date(startTime).getTime()
    console.log('Starting notification with startTime:', start, 'tag:', tag, 'dayTotalBase:', dayTotalBase)
    await TimerNotification.start({ startTime: start, tag: tag || null, dayTotalBase })
    console.log('Notification started successfully')
  } catch (err) {
    console.error('Notification start error:', err)
  }
}

export async function stopTimerNotification() {
  console.log('stopTimerNotification called, platform:', Capacitor.getPlatform())
  if (Capacitor.getPlatform() !== 'android') {
    return
  }
  try {
    await TimerNotification.stop()
    console.log('Notification stopped successfully')
  } catch (err) {
    console.error('Notification stop error:', err)
  }
}

export async function syncCredentials(apiUrl, token) {
  if (Capacitor.getPlatform() !== 'android') {
    return
  }
  try {
    await TimerNotification.syncCredentials({ apiUrl, token })
    console.log('Credentials synced to widget')
  } catch (err) {
    console.error('Credential sync error:', err)
  }
}
