const express = require('express');
const db = require('../db');
const { authMiddleware } = require('../middleware/auth');

const router = express.Router();

router.use(authMiddleware);

const allowedPlatforms = new Set(['android']);

function normalizeToken(value) {
  if (value === null || value === undefined) return null;
  const trimmed = String(value).trim();
  return trimmed ? trimmed : null;
}

function normalizePlatform(value) {
  if (value === null || value === undefined || value === '') {
    return 'android';
  }
  const normalized = String(value).trim().toLowerCase();
  return normalized || 'android';
}

// Register or refresh a device token for push notifications
router.post('/register', (req, res) => {
  try {
    const token = normalizeToken(req.body?.token);
    const platform = normalizePlatform(req.body?.platform);

    if (!token) {
      return res.status(400).json({ error: 'Device token is required' });
    }

    if (!allowedPlatforms.has(platform)) {
      return res.status(400).json({ error: `Unsupported platform: ${platform}` });
    }

    const now = new Date().toISOString();
    db.prepare(`
      INSERT INTO device_tokens (user_id, token, platform, created_at, last_seen_at)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(user_id, token) DO UPDATE SET
        platform = excluded.platform,
        last_seen_at = excluded.last_seen_at
    `).run(req.userId, token, platform, now, now);

    res.status(201).json({ token, platform });
  } catch (err) {
    console.error('Register device token error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Unregister a device token
router.post('/unregister', (req, res) => {
  try {
    const token = normalizeToken(req.body?.token);
    if (!token) {
      return res.status(400).json({ error: 'Device token is required' });
    }

    db.prepare('DELETE FROM device_tokens WHERE user_id = ? AND token = ?')
      .run(req.userId, token);

    res.status(204).send();
  } catch (err) {
    console.error('Unregister device token error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
