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
