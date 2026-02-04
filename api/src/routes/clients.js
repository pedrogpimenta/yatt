const express = require('express');
const db = require('../db');
const { authMiddleware } = require('../middleware/auth');

const router = express.Router();

router.use(authMiddleware);

function normalizeRequiredText(value) {
  if (value === null || value === undefined) {
    return null;
  }
  const trimmed = String(value).trim();
  return trimmed ? trimmed : null;
}

// Get all clients for current user
router.get('/', (req, res) => {
  try {
    const clients = db.prepare(`
      SELECT id, name
      FROM clients
      WHERE user_id = ?
      ORDER BY name COLLATE NOCASE ASC
    `).all(req.userId);

    res.json(clients);
  } catch (err) {
    console.error('Get clients error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Create a new client
router.post('/', (req, res) => {
  try {
    const name = normalizeRequiredText(req.body?.name);
    if (!name) {
      return res.status(400).json({ error: 'Client name is required' });
    }

    const existingClient = db.prepare('SELECT id, name FROM clients WHERE user_id = ? AND name = ?').get(req.userId, name);
    if (existingClient) {
      return res.json(existingClient);
    }

    const result = db.prepare('INSERT INTO clients (user_id, name) VALUES (?, ?)').run(req.userId, name);
    res.status(201).json({ id: result.lastInsertRowid, name });
  } catch (err) {
    console.error('Create client error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
