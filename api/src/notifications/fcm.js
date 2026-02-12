const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');
const db = require('../db');

const TOKEN_BATCH_SIZE = 500;
const ANDROID_PLATFORM = 'android';
const INVALID_TOKEN_ERRORS = new Set([
  'messaging/invalid-registration-token',
  'messaging/registration-token-not-registered'
]);

let cachedMessaging = null;
let initAttempted = false;

function isFcmEnabled() {
  if (!Object.prototype.hasOwnProperty.call(process.env, 'FCM_ENABLED')) {
    return true;
  }
  return String(process.env.FCM_ENABLED).toLowerCase() !== 'false';
}

function loadServiceAccountFromEnv() {
  const jsonEnv = process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (jsonEnv) {
    try {
      return JSON.parse(jsonEnv);
    } catch (err) {
      try {
        const decoded = Buffer.from(jsonEnv, 'base64').toString('utf8');
        return JSON.parse(decoded);
      } catch (decodeErr) {
        console.error('FCM JSON env var is invalid:', decodeErr);
        return null;
      }
    }
  }

  const jsonPath = process.env.FCM_SERVICE_ACCOUNT_PATH;
  if (!jsonPath) {
    return null;
  }

  try {
    const resolvedPath = path.isAbsolute(jsonPath) ? jsonPath : path.resolve(jsonPath);
    const raw = fs.readFileSync(resolvedPath, 'utf8');
    return JSON.parse(raw);
  } catch (err) {
    console.error('Failed to load FCM service account file:', err);
    return null;
  }
}

function getMessaging() {
  if (!isFcmEnabled()) {
    return null;
  }

  if (cachedMessaging) {
    return cachedMessaging;
  }

  if (initAttempted) {
    return null;
  }

  initAttempted = true;
  const serviceAccount = loadServiceAccountFromEnv();
  if (!serviceAccount) {
    console.warn('FCM disabled: no service account configured. Set FCM_SERVICE_ACCOUNT_PATH or FCM_SERVICE_ACCOUNT_JSON.');
    return null;
  }

  try {
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
    cachedMessaging = admin.messaging();
    const projectId = serviceAccount.project_id || '(unknown)';
    console.log('FCM enabled: Firebase Admin initialized for project', projectId);
    return cachedMessaging;
  } catch (err) {
    console.error('Failed to initialize Firebase Admin:', err);
    return null;
  }
}

function buildTimerData(event, timer) {
  return {
    type: 'timer',
    event,
    timerId: timer?.id ? String(timer.id) : '',
    startTime: timer?.start_time ? String(timer.start_time) : '',
    endTime: timer?.end_time ? String(timer.end_time) : '',
    projectId: timer?.project_id ? String(timer.project_id) : '',
    tag: timer?.tag ? String(timer.tag) : '',
    description: timer?.description ? String(timer.description) : ''
  };
}

function loadUserTokens(userId) {
  return db.prepare(
    'SELECT token FROM device_tokens WHERE user_id = ? AND platform = ?'
  ).all(userId, ANDROID_PLATFORM).map((row) => row.token);
}

function removeInvalidTokens(userId, tokens) {
  if (!tokens.length) return;
  const placeholders = tokens.map(() => '?').join(', ');
  db.prepare(
    `DELETE FROM device_tokens WHERE user_id = ? AND token IN (${placeholders})`
  ).run(userId, ...tokens);
}

async function sendTimerEvent({ userId, event, timer }) {
  const messaging = getMessaging();
  if (!messaging) {
    return { skipped: 'disabled' };
  }

  const tokens = loadUserTokens(userId).filter(Boolean);
  if (tokens.length === 0) {
    console.log('FCM: no device tokens for user', userId, '- timer', event, 'not pushed');
    return { skipped: 'no_tokens' };
  }

  const data = buildTimerData(event, timer);
  const invalidTokens = [];

  for (let i = 0; i < tokens.length; i += TOKEN_BATCH_SIZE) {
    const batch = tokens.slice(i, i + TOKEN_BATCH_SIZE);
    const response = await messaging.sendMulticast({
      tokens: batch,
      data,
      android: { priority: 'high' }
    });

    response.responses.forEach((result, index) => {
      if (!result.success && result.error) {
        const code = result.error?.code || '';
        console.warn('FCM send failed:', code, result.error?.message || result.error);
        if (INVALID_TOKEN_ERRORS.has(code)) {
          invalidTokens.push(batch[index]);
        }
      }
    });
  }

  removeInvalidTokens(userId, invalidTokens);
  console.log('FCM: timer', event, 'sent to', tokens.length, 'device(s), invalid:', invalidTokens.length);
  return { sent: tokens.length, invalid: invalidTokens.length };
}

/** Call at startup to initialize FCM and log status (enabled/disabled). */
function initAndLogFcmStatus() {
  const hasEnabled = Object.prototype.hasOwnProperty.call(process.env, 'FCM_ENABLED');
  const hasPath = Object.prototype.hasOwnProperty.call(process.env, 'FCM_SERVICE_ACCOUNT_PATH') && process.env.FCM_SERVICE_ACCOUNT_PATH;
  const hasJson = Object.prototype.hasOwnProperty.call(process.env, 'FCM_SERVICE_ACCOUNT_JSON') && process.env.FCM_SERVICE_ACCOUNT_JSON;
  console.log('FCM env: FCM_ENABLED=' + (hasEnabled ? process.env.FCM_ENABLED : '(not set)') +
    ', FCM_SERVICE_ACCOUNT_PATH=' + (hasPath ? 'set' : 'not set') +
    ', FCM_SERVICE_ACCOUNT_JSON=' + (hasJson ? 'set' : 'not set'));

  if (!isFcmEnabled()) {
    console.log('FCM disabled: FCM_ENABLED is false');
    return;
  }
  const messaging = getMessaging();
  if (!messaging) {
    console.log('FCM disabled: could not initialize (check FCM_SERVICE_ACCOUNT_PATH or FCM_SERVICE_ACCOUNT_JSON)');
  }
}

module.exports = { sendTimerEvent, initAndLogFcmStatus };
