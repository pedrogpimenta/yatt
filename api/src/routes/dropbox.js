const express = require('express');
const { authMiddleware } = require('../middleware/auth');
const db = require('../db');

const router = express.Router();

const CLIENT_ID = process.env.DROPBOX_CLIENT_ID;
const CLIENT_SECRET = process.env.DROPBOX_CLIENT_SECRET;

function getRedirectUri() {
  return `${process.env.APP_URL}/api/dropbox/callback`;
}

function isConfigured() {
  return !!(CLIENT_ID && CLIENT_SECRET && process.env.APP_URL);
}

// In-memory state store: state -> { userId, expiresAt }
const pendingStates = new Map();
setInterval(() => {
  const now = Date.now();
  for (const [key, value] of pendingStates.entries()) {
    if (value.expiresAt < now) pendingStates.delete(key);
  }
}, 60000);

// Initialize DB table
db.exec(`
  CREATE TABLE IF NOT EXISTS dropbox_tokens (
    user_id INTEGER PRIMARY KEY,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
  );
`);

async function getValidToken(userId) {
  const row = db.prepare('SELECT * FROM dropbox_tokens WHERE user_id = ?').get(userId);
  if (!row) return null;

  if (Date.now() >= row.expires_at - 60000) {
    const params = new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: row.refresh_token,
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
    });

    const response = await fetch('https://api.dropboxapi.com/oauth2/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params,
    });

    if (!response.ok) {
      db.prepare('DELETE FROM dropbox_tokens WHERE user_id = ?').run(userId);
      return null;
    }

    const data = await response.json();
    const expiresAt = Date.now() + (data.expires_in || 14400) * 1000;
    db.prepare(
      'UPDATE dropbox_tokens SET access_token = ?, expires_at = ? WHERE user_id = ?'
    ).run(data.access_token, expiresAt, userId);

    return data.access_token;
  }

  return row.access_token;
}

// GET /dropbox/status
router.get('/status', authMiddleware, (req, res) => {
  const row = db.prepare('SELECT user_id FROM dropbox_tokens WHERE user_id = ?').get(req.userId);
  res.json({ configured: isConfigured(), connected: !!row });
});

// POST /dropbox/auth-url
router.post('/auth-url', authMiddleware, (req, res) => {
  if (!isConfigured()) {
    return res.status(503).json({ error: 'Dropbox not configured on this server' });
  }

  const state = Math.random().toString(36).substr(2, 16);
  pendingStates.set(state, { userId: req.userId, expiresAt: Date.now() + 5 * 60 * 1000 });

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    response_type: 'code',
    redirect_uri: getRedirectUri(),
    token_access_type: 'offline',
    state,
  });

  res.json({ url: `https://www.dropbox.com/oauth2/authorize?${params}` });
});

// GET /dropbox/callback
router.get('/callback', async (req, res) => {
  const { code, state, error } = req.query;
  const appUrl = process.env.APP_URL;

  if (error) {
    return res.redirect(`${appUrl}?dropbox=error&message=${encodeURIComponent(error)}`);
  }

  const pending = pendingStates.get(state);
  if (!pending || pending.expiresAt < Date.now()) {
    return res.redirect(`${appUrl}?dropbox=error&message=invalid_state`);
  }
  pendingStates.delete(state);

  try {
    const params = new URLSearchParams({
      code,
      grant_type: 'authorization_code',
      redirect_uri: getRedirectUri(),
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
    });

    const response = await fetch('https://api.dropboxapi.com/oauth2/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params,
    });

    if (!response.ok) throw new Error('Token exchange failed');

    const data = await response.json();
    const expiresAt = Date.now() + (data.expires_in || 14400) * 1000;

    db.prepare(`
      INSERT INTO dropbox_tokens (user_id, access_token, refresh_token, expires_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(user_id) DO UPDATE SET
        access_token = excluded.access_token,
        refresh_token = excluded.refresh_token,
        expires_at = excluded.expires_at
    `).run(pending.userId, data.access_token, data.refresh_token, expiresAt);

    res.redirect(`${appUrl}?dropbox=connected`);
  } catch (err) {
    res.redirect(`${appUrl}?dropbox=error&message=token_exchange_failed`);
  }
});

// POST /dropbox/export
router.post('/export', authMiddleware, async (req, res) => {
  try {
    const token = await getValidToken(req.userId);
    if (!token) return res.status(401).json({ error: 'Dropbox not connected' });

    const timers = db.prepare('SELECT * FROM timers WHERE user_id = ?').all(req.userId);
    const projects = db.prepare('SELECT * FROM projects WHERE user_id = ?').all(req.userId);
    const clients = db.prepare('SELECT * FROM clients WHERE user_id = ?').all(req.userId);

    const backup = {
      version: 1,
      exported_at: new Date().toISOString(),
      timers,
      projects,
      clients,
    };

    const response = await fetch('https://content.dropboxapi.com/2/files/upload', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/octet-stream',
        'Dropbox-API-Arg': JSON.stringify({
          path: '/yatt-backup.json',
          mode: 'overwrite',
          autorename: false,
        }),
      },
      body: JSON.stringify(backup, null, 2),
    });

    if (!response.ok) throw new Error('Failed to upload to Dropbox');

    res.json({ success: true, timers: timers.length, projects: projects.length });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /dropbox/import
router.post('/import', authMiddleware, async (req, res) => {
  try {
    const token = await getValidToken(req.userId);
    if (!token) return res.status(401).json({ error: 'Dropbox not connected' });

    const response = await fetch('https://content.dropboxapi.com/2/files/download', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Dropbox-API-Arg': JSON.stringify({ path: '/yatt-backup.json' }),
      },
    });

    if (response.status === 409) return res.status(404).json({ error: 'No backup found in Dropbox' });
    if (!response.ok) throw new Error('Failed to download from Dropbox');

    const backup = await response.json();

    const result = db.transaction(() => {
      const clientIdMap = new Map();
      for (const client of backup.clients || []) {
        const existing = db
          .prepare('SELECT id FROM clients WHERE user_id = ? AND name = ?')
          .get(req.userId, client.name);
        if (existing) {
          clientIdMap.set(client.id, existing.id);
        } else {
          const r = db
            .prepare('INSERT INTO clients (user_id, name) VALUES (?, ?)')
            .run(req.userId, client.name);
          clientIdMap.set(client.id, r.lastInsertRowid);
        }
      }

      const projectIdMap = new Map();
      for (const project of backup.projects || []) {
        const clientId = project.client_id ? (clientIdMap.get(project.client_id) ?? null) : null;
        const existing = db
          .prepare('SELECT id FROM projects WHERE user_id = ? AND name = ?')
          .get(req.userId, project.name);
        if (existing) {
          projectIdMap.set(project.id, existing.id);
        } else {
          const r = db
            .prepare('INSERT INTO projects (user_id, name, type, client_id) VALUES (?, ?, ?, ?)')
            .run(req.userId, project.name, project.type, clientId);
          projectIdMap.set(project.id, r.lastInsertRowid);
        }
      }

      let imported = 0;
      for (const timer of backup.timers || []) {
        const existing = db
          .prepare('SELECT id FROM timers WHERE user_id = ? AND start_time = ?')
          .get(req.userId, timer.start_time);
        if (!existing) {
          const projectId = timer.project_id ? (projectIdMap.get(timer.project_id) ?? null) : null;
          db.prepare(
            'INSERT INTO timers (user_id, start_time, end_time, tag, project_id, description) VALUES (?, ?, ?, ?, ?, ?)'
          ).run(req.userId, timer.start_time, timer.end_time, timer.tag, projectId, timer.description);
          imported++;
        }
      }

      return { imported, total: (backup.timers || []).length };
    })();

    res.json({ success: true, ...result });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// DELETE /dropbox/disconnect
router.delete('/disconnect', authMiddleware, (req, res) => {
  db.prepare('DELETE FROM dropbox_tokens WHERE user_id = ?').run(req.userId);
  res.json({ success: true });
});

module.exports = router;
