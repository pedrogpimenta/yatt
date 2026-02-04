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

function normalizeOptionalText(value) {
  if (value === null || value === undefined) {
    return null;
  }
  const trimmed = String(value).trim();
  return trimmed ? trimmed : null;
}

function normalizeOptionalId(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const parsed = Number(value);
  if (!Number.isInteger(parsed)) {
    return null;
  }
  return parsed;
}

// Get all projects for current user
router.get('/', (req, res) => {
  try {
    const projects = db.prepare(`
      SELECT p.id, p.name, p.type, p.client_id, c.name as client_name
      FROM projects p
      LEFT JOIN clients c ON c.id = p.client_id AND c.user_id = p.user_id
      WHERE p.user_id = ?
      ORDER BY p.name COLLATE NOCASE ASC
    `).all(req.userId);

    res.json(projects);
  } catch (err) {
    console.error('Get projects error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Create a new project
router.post('/', (req, res) => {
  try {
    const name = normalizeRequiredText(req.body?.name);
    const type = normalizeOptionalText(req.body?.type);
    const clientName = normalizeOptionalText(req.body?.clientName);
    const clientId = normalizeOptionalId(req.body?.clientId);

    if (!name) {
      return res.status(400).json({ error: 'Project name is required' });
    }

    let resolvedClientId = null;
    let resolvedClientName = null;

    if (clientId !== null) {
      const client = db.prepare('SELECT id, name FROM clients WHERE id = ? AND user_id = ?').get(clientId, req.userId);
      if (!client) {
        return res.status(400).json({ error: 'Client not found' });
      }
      resolvedClientId = client.id;
      resolvedClientName = client.name;
    } else if (clientName) {
      const existingClient = db.prepare('SELECT id, name FROM clients WHERE user_id = ? AND name = ?').get(req.userId, clientName);
      if (existingClient) {
        resolvedClientId = existingClient.id;
        resolvedClientName = existingClient.name;
      } else {
        const result = db.prepare('INSERT INTO clients (user_id, name) VALUES (?, ?)').run(req.userId, clientName);
        resolvedClientId = result.lastInsertRowid;
        resolvedClientName = clientName;
      }
    }

    const result = db.prepare(`
      INSERT INTO projects (user_id, name, type, client_id)
      VALUES (?, ?, ?, ?)
    `).run(req.userId, name, type, resolvedClientId);

    const project = db.prepare(`
      SELECT p.id, p.name, p.type, p.client_id, c.name as client_name
      FROM projects p
      LEFT JOIN clients c ON c.id = p.client_id AND c.user_id = p.user_id
      WHERE p.id = ?
    `).get(result.lastInsertRowid);

    if (project) {
      project.client_name = project.client_name || resolvedClientName;
    }

    res.status(201).json(project);
  } catch (err) {
    console.error('Create project error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
