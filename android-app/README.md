# YATT Android App

A Capacitor-based Android app for YATT time tracking.

## Prerequisites

- Node.js 18+
- Android Studio
- Android SDK

## Quick Setup

```bash
npm install
npm run setup
npx cap open android
```

Then build and run from Android Studio.

## Manual Setup

1. Install dependencies:

```bash
npm install
```

2. Build the web app:

```bash
npm run build
```

3. Add Android platform:

```bash
npx cap add android
```

4. Run setup script (copies native plugin):

```bash
node setup-android.js
```

5. Sync and open in Android Studio:

```bash
npx cap sync
npx cap open android
```

6. In Android Studio, run on device/emulator.

## Development

To update the app after making changes:

```bash
npm run build
npx cap sync
```

## Configuration

On first launch, tap the gear icon to set your API server URL (e.g., `http://192.168.1.100:3000`).

Make sure your phone can reach the API server (same network, or expose via internet).

## Features

- Start/stop timers with optional tags
- Persistent notification showing elapsed time when timer is running
- View and edit timer history
- Today and weekly totals
- Dark theme
