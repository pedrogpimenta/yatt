# FCM verification guide

Use this to confirm FCM is set up correctly on both the **API** and the **Android app**, and to see where it fails if it’s not working.

---

## 1. API: Check FCM is enabled

**Start the API** and watch the console:

- **FCM working:** You should see:
  ```text
  FCM enabled: Firebase Admin initialized for project YOUR_PROJECT_ID
  ```
- **FCM disabled:** You’ll see:
  ```text
  FCM disabled: no service account configured. Set FCM_SERVICE_ACCOUNT_PATH or FCM_SERVICE_ACCOUNT_JSON.
  ```
  or an error from Firebase Admin.

**If FCM is disabled:** Set `FCM_SERVICE_ACCOUNT_PATH` (path to the service account JSON from the same Firebase project as the Android app) or `FCM_SERVICE_ACCOUNT_JSON`, then restart the API.

---

## 2. API: Check devices are registered

After logging in on the **Android app** (and optionally opening the app once so it can register the token), call the API with your JWT:

```bash
curl -s -H "Authorization: Bearer YOUR_JWT_TOKEN" https://YOUR_API_URL/devices
```

**Expected:** JSON like `{"count":1,"devices":[{"platform":"android","last_seen_at":"..."}]}`.

- **`count: 0`** → The app has not successfully registered a token. Check the app side (step 3).
- **`count >= 1`** → The API has stored at least one device; push can be sent if FCM is enabled.

---

## 3. Android app: Check token and registration

**With the device connected and the app installed:**

1. **Logcat (Android Studio or `adb logcat`):**
   - Filter by tag: `YattFcmReg` or `YattFcm`.
   - After **login** or **opening the app** you should see:
     - `FCM token registered with API` → token was sent to the API successfully.
   - If you see:
     - `FCM register skipped: no auth token` → not logged in or token not saved.
     - `FCM register skipped: no FCM token (check google-services.json)` → Firebase not configured (missing/wrong `google-services.json` or wrong package name).
     - `FCM register failed: ...` → API returned an error (check status code in the log; 401 = bad/expired JWT).

2. **Confirm `google-services.json`:**
   - File must be in `android-app/app/` (next to `build.gradle.kts`).
   - Package name inside must be `org.yatt.app` (same as `applicationId` in `build.gradle.kts`).
   - It must be from the **same** Firebase project as the service account used by the API.

---

## 4. End-to-end: Trigger a push from another client

FCM is only sent when a timer is **started or stopped from a client that is not the Android app** (so you don’t get a push for your own actions).

1. **On the API:** Start/stop a timer from the **web app** (or another client that does **not** send `X-Client-Platform: android`).
2. **Watch API logs:** You should see either:
   - `FCM: timer started sent to 1 device(s), invalid: 0` (or `stopped`), or
   - `FCM: no device tokens for user X - timer started not pushed` (no devices registered for that user), or
   - `FCM send failed: ...` (invalid token or FCM error).
3. **On the device:** The running-timer notification should update (or disappear if you stopped the timer). In logcat you should see:
   - `YattFcm: FCM onMessageReceived: type=timer event=started` (or `stopped`).

**If the API says “sent” but the app does nothing:**  
- Same Firebase project for both API service account and `google-services.json`.  
- App not killed by battery saver (FCM data messages can be delayed or dropped when the app is heavily restricted).

---

## 5. Checklist

| Check | API | App |
|-------|-----|-----|
| FCM / Firebase configured | `FCM enabled: Firebase Admin initialized...` in API logs | `google-services.json` in `app/`, package `org.yatt.app` |
| Same Firebase project | Service account JSON from Firebase Console → Project settings → Service accounts | Same project as in `google-services.json` |
| Token sent to API | `GET /devices` returns `count >= 1` | Logcat: `FCM token registered with API` |
| Push sent when timer changes elsewhere | API log: `FCM: timer started/stopped sent to N device(s)` | Logcat: `FCM onMessageReceived: type=timer event=...` |

---

## 6. Optional: Inspect the database

If you have access to the API’s SQLite DB:

```bash
sqlite3 path/to/yatt.db "SELECT user_id, platform, last_seen_at FROM device_tokens;"
```

You should see at least one row with `platform = android` for your user after the app has registered.
