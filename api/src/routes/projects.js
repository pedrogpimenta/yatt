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

// Update a project
router.patch('/:id', (req, res) => {
  try {
    const project = db.prepare('SELECT id, user_id FROM projects WHERE id = ? AND user_id = ?').get(req.params.id, req.userId);
    if (!project) {
      return res.status(404).json({ error: 'Project not found' });
    }

    const name = normalizeRequiredText(req.body?.name);
    const type = normalizeOptionalText(req.body?.type);
    const clientName = normalizeOptionalText(req.body?.clientName);
    const clientId = normalizeOptionalId(req.body?.clientId);
    const hasClientIdKey = Object.prototype.hasOwnProperty.call(req.body || {}, 'clientId');
    const hasClientNameKey = Object.prototype.hasOwnProperty.call(req.body || {}, 'clientName');

    if (name !== null && !name) {
      return res.status(400).json({ error: 'Project name is required' });
    }

    let resolvedClientId = null;
    let resolvedClientName = null;
    let updateClient = false;

    if (hasClientIdKey && clientId !== null) {
      const client = db.prepare('SELECT id, name FROM clients WHERE id = ? AND user_id = ?').get(clientId, req.userId);
      if (!client) {
        return res.status(400).json({ error: 'Client not found' });
      }
      resolvedClientId = client.id;
      resolvedClientName = client.name;
      updateClient = true;
    } else if (hasClientIdKey && clientId === null) {
      updateClient = true;
    } else if (hasClientNameKey) {
      if (clientName) {
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
      updateClient = true;
    }

    const updates = [];
    const params = [];
    const hasTypeKey = Object.prototype.hasOwnProperty.call(req.body || {}, 'type');

    if (name !== null) {
      updates.push('name = ?');
      params.push(name);
    }
    if (hasTypeKey) {
      updates.push('type = ?');
      params.push(type);
    }
    if (updateClient) {
      updates.push('client_id = ?');
      params.push(resolvedClientId);
    }

    if (updates.length > 0) {
      params.push(req.params.id, req.userId);
      db.prepare(`
        UPDATE projects
        SET ${updates.join(', ')}
        WHERE id = ? AND user_id = ?
      `).run(...params);
    }

    const updated = db.prepare(`
      SELECT p.id, p.name, p.type, p.client_id, c.name as client_name
      FROM projects p
      LEFT JOIN clients c ON c.id = p.client_id AND c.user_id = p.user_id
      WHERE p.id = ?
    `).get(req.params.id);

    if (updated && updateClient && resolvedClientName !== undefined) {
      updated.client_name = resolvedClientName ?? updated.client_name;
    }

    res.json(updated);
  } catch (err) {
    console.error('Update project error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Delete a project (timers keep project_id but it becomes orphaned; we clear it)
router.delete('/:id', (req, res) => {
  try {
    const project = db.prepare('SELECT id, user_id FROM projects WHERE id = ? AND user_id = ?').get(req.params.id, req.userId);
    if (!project) {
      return res.status(404).json({ error: 'Project not found' });
    }

    db.prepare('UPDATE timers SET project_id = NULL WHERE project_id = ?').run(req.params.id);
    db.prepare('DELETE FROM projects WHERE id = ? AND user_id = ?').run(req.params.id, req.userId);

    res.status(204).send();
  } catch (err) {
    console.error('Delete project error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
