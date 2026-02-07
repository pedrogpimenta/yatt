# YATT - Yet Another Time Tracker

A self-hosted time tracking application with a web frontend, Android app, and KDE Plasma widget.

## Architecture

- **API** (`api/`): Node.js/Express REST API with SQLite database and WebSocket support
- **Web** (`web/`): Vue 3 web application
- **Android App** (`android-app/`): Native Android application (Kotlin + Jetpack Compose)
- **KDE Widget** (`kde-widget/`): Plasma widget for KDE desktop

## Prerequisites

- Node.js 20+
- npm
- Docker & Docker Compose (for production)
- Android Studio (for Android app development)
- KDE Plasma 6 (for the widget)

## Quick Start (Development)

### 1. Clone and Setup Environment

```bash
git clone <repo-url> yatt
cd yatt

# Create environment file
cp .env.example .env
# Edit .env and set a secure JWT_SECRET
```

### 2. Start the API

```bash
cd api
npm install
npm run dev
```

The API runs on `http://localhost:3000`.

### 3. Start the Web Frontend

In a new terminal:

```bash
cd web
npm install
npm run dev
```

The web app runs on `http://localhost:5173`.

### 4. Create a User Account

Register via the web interface or directly via API:

```bash
curl -X POST http://localhost:3000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "you@example.com", "password": "your_password"}'
```

## Production Deployment

### Using Docker Compose

This is the recommended method for production.

#### 1. Configure Environment

```bash
cp .env.example .env
# Edit .env with a strong JWT_SECRET
nano .env
```

```env
JWT_SECRET=your-very-long-and-secure-random-string-here
```

#### 2. Build and Run

```bash
docker compose up -d --build
```

This starts:
- API on port 3000
- Web on port 5173

#### 3. Production Web Build (Recommended)

For production, you should serve the web app as static files behind a reverse proxy (nginx, Caddy, etc.) instead of using Vite's dev server.

Build the static files:

```bash
cd web
npm run build
# Output is in web/dist/
```

Example nginx configuration:

```nginx
server {
    listen 80;
    server_name yatt.example.com;

    # Serve static web files
    location / {
        root /path/to/yatt/web/dist;
        try_files $uri $uri/ /index.html;
    }

    # Proxy API requests
    location /api/ {
        proxy_pass http://localhost:3000/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### 4. Data Persistence

The Docker setup uses a named volume `api-data` for the SQLite database. To backup:

```bash
docker compose exec api cat /app/data/yatt.db > backup.db
```

To restore:

```bash
docker compose cp backup.db api:/app/data/yatt.db
docker compose restart api
```

## Android App

### Development Setup

Open the `android-app/` folder in Android Studio and let Gradle sync.

#### Configure API URL

Open Settings in the app and set the API base URL.

For local development with an emulator, use `http://10.0.2.2:3000` instead of `localhost`.

#### Running on Device/Emulator

From Android Studio, run the app on your device or emulator.

### Building a Release APK

1. Open the project in Android Studio
2. Go to Build > Generate Signed Bundle/APK
3. Follow the wizard to create a signed APK

## KDE Plasma Widget

### Installation

```bash
cd kde-widget
./install.sh
```

Restart Plasma shell:

```bash
# Wayland
systemctl --user restart plasma-plasmashell

# X11
kquitapp6 plasmashell && kstart plasmashell
```

### Configuration

1. Right-click your panel > Add Widgets
2. Search for "YATT" and add it
3. Right-click the widget > Configure
4. Set your API URL (e.g., `http://localhost:3000` or your production URL)
5. Get a token by logging into the web app, then use browser DevTools to copy the token from localStorage, or generate one via API

### Uninstallation

```bash
cd kde-widget
./uninstall.sh
```

## API Authentication

The API uses JWT tokens. To get a token:

```bash
# Login
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "you@example.com", "password": "your_password"}'
```

Response:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": { "id": 1, "email": "you@example.com" }
}
```

Use this token in the Authorization header:

```bash
curl http://localhost:3000/timers \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /auth/register | Register new user |
| POST | /auth/login | Login and get token |
| GET | /auth/confirm-email | Confirm email address |
| POST | /auth/request-password-reset | Send password reset email |
| GET | /auth/reset-password | Reset password form |
| POST | /auth/reset-password | Reset password with token |
| GET | /timers | Get all timers |
| POST | /timers | Create new timer |
| GET | /timers/:id | Get single timer |
| PATCH | /timers/:id | Update timer |
| POST | /timers/:id/stop | Stop running timer |
| DELETE | /timers/:id | Delete timer |

## WebSocket

The API supports WebSocket connections for real-time updates. Connect to the same port as the API and authenticate:

```javascript
const ws = new WebSocket('ws://localhost:3000')
ws.onopen = () => {
  ws.send(JSON.stringify({ type: 'auth', token: 'YOUR_TOKEN' }))
}
ws.onmessage = (event) => {
  const data = JSON.parse(event.data)
  if (data.type === 'timer') {
    // Timer was created/updated/deleted
  }
}
```

## Environment Variables

### API

| Variable | Default | Description |
|----------|---------|-------------|
| JWT_SECRET | (required) | Secret key for JWT signing |
| PORT | 3000 | API server port |
| DB_PATH | ./data/yatt.db | SQLite database path |
| APP_NAME | YATT | App name used in email templates |
| PUBLIC_API_BASE_URL | http://localhost:3000 | Base URL for email links |
| SMTP_HOST | (required for email) | SMTP server host |
| SMTP_PORT | 465 | SMTP server port |
| SMTP_SECURE | true | Use TLS for SMTP |
| SMTP_USER | (required for email) | SMTP username |
| SMTP_PASS | (required for email) | SMTP password |
| SMTP_FROM | SMTP_USER | From address for emails |
| EMAIL_CONFIRMATION_TTL_HOURS | 48 | Confirmation token TTL (hours) |
| PASSWORD_RESET_TTL_HOURS | 2 | Reset token TTL (hours) |
| REQUIRE_EMAIL_CONFIRMATION | false | Block login until email confirmed |

## Email Confirmation and Password Reset

When SMTP is configured, the API sends confirmation and reset emails automatically.

```bash
# Request a password reset email
curl -X POST http://localhost:3000/auth/request-password-reset \
  -H "Content-Type: application/json" \
  -d '{"email": "you@example.com"}'

# Reset password with a token from the email
curl -X POST http://localhost:3000/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token": "TOKEN_FROM_EMAIL", "newPassword": "new-secret"}'
```

## Troubleshooting

### API won't start

- Ensure `better-sqlite3` compiled correctly. On Linux, you may need build tools:
  ```bash
  sudo apt install python3 make g++
  # or on Arch
  sudo pacman -S python make gcc
  ```

### Android app can't connect to API

- For emulator: Use `10.0.2.2` instead of `localhost`
- For physical device: Use your computer's local IP address
- Ensure the API is accessible from the network
- Check that `cleartext` traffic is enabled in `capacitor.config.ts` for HTTP

### KDE widget not appearing

- Restart Plasma shell after installation
- Check widget logs: `journalctl --user -f` while interacting with the widget
- Verify the API URL and token in widget settings

## Development Tips

### Hot Reload

- **API**: Uses Node.js `--watch` flag for auto-reload
- **Web**: Vite provides HMR out of the box
- **Android**: Run `npm run build && npm run cap:sync` after changes, then rebuild in Android Studio
- **KDE Widget**: Re-run `./install.sh` and restart Plasma

### Database Location

- Development: `api/data/yatt.db`
- Docker: `/app/data/yatt.db` (in the `api-data` volume)

You can browse the SQLite database with any SQLite client:

```bash
sqlite3 api/data/yatt.db
```

## License

MIT
