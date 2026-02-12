# YATT Android App (Native)

This is the native Android app for YATT, built with Kotlin and Jetpack Compose.

## Prerequisites

- Android Studio (Giraffe or newer)
- Android SDK
- JDK 17

## Open in Android Studio

1. Open the `android-app/` folder in Android Studio.
2. Let Gradle sync.
3. Run the app on a device or emulator.

## Configuration

On first launch, open Settings and set the API base URL (for example, `http://10.0.2.2:3000` for the emulator, or your LAN IP for a physical device).

### Push notifications (FCM)

To receive timer push notifications (e.g. when a timer is started or stopped on another device), complete these steps:

**1. Firebase project (Android app)**

- Open [Firebase Console](https://console.firebase.google.com/) and create a project (or use an existing one).
- Add an Android app with package name **`org.yatt.app`** (must match `applicationId` in `app/build.gradle.kts`).
- Download **`google-services.json`** and place it in **`android-app/app/`** (next to `build.gradle.kts`).  
  Without this file, the Google Services plugin is not applied and FCM will not be available.

**2. API server (Firebase Admin)**

- In the same Firebase project: **Project settings** → **Service accounts** → **Generate new private key**.
- Save the JSON key file somewhere secure (e.g. `api/firebase-service-account.json` — add it to `.gitignore`).
- Configure the API with one of:
  - **`FCM_SERVICE_ACCOUNT_PATH`** = path to that JSON file, or
  - **`FCM_SERVICE_ACCOUNT_JSON`** = raw JSON string or base64-encoded JSON (useful for hosted envs).
- Restart the API so it can initialize Firebase Admin. If no service account is set, FCM is disabled and the API logs: *"FCM disabled: no service account configured."*

**3. Behaviour**

- The app registers its FCM token with the API on **login** and when **opening the app** while logged in; it unregisters on **logout**.
- **Verifying FCM:** See [docs/FCM_VERIFICATION.md](../docs/FCM_VERIFICATION.md) for step-by-step checks on both the app and the API.
- The API sends FCM **data** messages (no user-visible notification) when a timer is **started** or **stopped** from another client (e.g. web). The Android app uses them to update the running-timer notification.
- Requests from the Android app send **`X-Client-Platform: android`**, so the API does **not** send FCM for the same user’s actions from this device.

## Features

- Native Material 3 UI
- Login, register, and local mode
- Start/stop timers with tags
- Edit timers and add past entries
- List and calendar views
- Today and weekly totals
- Offline queue with sync
- Device-to-device sync (online and offline export)
- CSV export
- Running timer notification
