# Time Command KDE Widget

KDE Plasma applet for the [Time Command](https://github.com/your-org/yatt) API. Shows the day’s timer total and a play/stop button in the taskbar; opening it shows the current timer, editable start date, tag, project, description, and day/week totals. Works offline and syncs when online.

## Install

```bash
./install.sh
```

Then: right-click the panel → **Add Widgets** → search for **Time Command**.

## Configure

Open the widget (click the time in the taskbar) and set **Auth Token** at the top:

- Paste your JWT from the web app (e.g. from browser localStorage, key `token`).
- Click **Save**. The token is stored in the widget and used for all API requests.

The API URL is fixed to `https://time-server.command.pimenta.pt` and is not configurable.

## Usage

- **Compact (taskbar):** Day total time + play/stop. Click the time to open the full view.
- **Full view:** Current timer, editable start date (click “Started …”), tag, project, description, Start/Stop, today and this week totals. Refresh/Sync and Configure in the header.
- **Offline:** Timers are stored locally; when the connection is back, pending changes sync automatically. A small icon indicates offline or pending sync.
