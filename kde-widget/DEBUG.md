# Debugging the YATT KDE widget

When the widget is stuck or not syncing, run it in a terminal to see logs.

## 1. Run the widget in a terminal (see all logs)

From the repo root:

```bash
plasmoidviewer -a org.kde.yatt -l kde-widget/package
```

Or from `kde-widget/`:

```bash
plasmoidviewer -a org.kde.yatt -l package
```

Logs appear in the terminal. You’ll see lines like:

- `YATT: fetchTimers started` / `fetchTimers success` / `fetchTimers failed`
- `YATT: checkOnlineStatus result status=200 isOnline=true`
- `YATT: Response status: 200 for https://...`
- `YATT: Sync skipped (offline)` or `YATT: Starting sync, N operations pending`
- `YATT: Loading safety timeout` (if a request hung)

## 2. If it’s “stuck”

1. **Click Refresh** in the widget. It now **forces** a new fetch and a sync attempt even when the widget thinks it’s offline.
2. If nothing happens for ~15 seconds, the **loading safety** timer resets `loading`, so the next Refresh will run.
3. Check the terminal for:
   - `status=0` or network errors → API unreachable (URL, firewall, HTTPS).
   - `status=401` → bad or expired token; get a new token from the web app.
   - `JSON parse error` → API returned non-JSON (error page, proxy).

## 3. After fixing

Close plasmoidviewer and use the widget normally on the panel. The same logging runs in the background; to see it again, run `plasmoidviewer` as above.
