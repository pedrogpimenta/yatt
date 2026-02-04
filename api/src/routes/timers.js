const express = require('express');
const db = require('../db');
const { authMiddleware } = require('../middleware/auth');

const router = express.Router();

// All routes require authentication
router.use(authMiddleware);

// Helper to broadcast timer updates
function broadcast(req, event, data) {
  const broadcastToUser = req.app.get('broadcastToUser');
  if (broadcastToUser) {
    broadcastToUser(req.userId, { type: 'timer', event, data });
  }
}

// Get all timers for current user
router.get('/', (req, res) => {
  try {
    const timers = db.prepare(`
      SELECT * FROM timers 
      WHERE user_id = ? 
      ORDER BY start_time DESC
    `).all(req.userId);

    res.json(timers);
  } catch (err) {
    console.error('Get timers error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get unique tags for current user (sorted by most recently used)
router.get('/tags', (req, res) => {
  try {
    const tags = db.prepare(`
      SELECT tag, MAX(start_time) as last_used
      FROM timers 
      WHERE user_id = ? AND tag IS NOT NULL AND tag != ''
      GROUP BY tag
      ORDER BY last_used DESC
      LIMIT 50
    `).all(req.userId);

    res.json(tags.map(t => t.tag));
  } catch (err) {
    console.error('Get tags error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get single timer
router.get('/:id', (req, res) => {
  try {
    const timer = db.prepare('SELECT * FROM timers WHERE id = ? AND user_id = ?').get(req.params.id, req.userId);

    if (!timer) {
      return res.status(404).json({ error: 'Timer not found' });
    }

    res.json(timer);
  } catch (err) {
    console.error('Get timer error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Start a new timer
router.post('/', (req, res) => {
  try {
    const { start_time, end_time, tag, project_id } = req.body;
    const startTime = start_time || new Date().toISOString();
    let projectId = null;

    if (project_id !== null && project_id !== undefined && project_id !== '') {
      const parsedProjectId = Number(project_id);
      if (!Number.isInteger(parsedProjectId)) {
        return res.status(400).json({ error: 'Invalid project_id' });
      }
      const project = db.prepare('SELECT id FROM projects WHERE id = ? AND user_id = ?').get(parsedProjectId, req.userId);
      if (!project) {
        return res.status(400).json({ error: 'Project not found' });
      }
      projectId = parsedProjectId;
    }

    const result = db.prepare(`
      INSERT INTO timers (user_id, start_time, end_time, tag, project_id) 
      VALUES (?, ?, ?, ?, ?)
    `).run(req.userId, startTime, end_time || null, tag || null, projectId);

    const timer = db.prepare('SELECT * FROM timers WHERE id = ?').get(result.lastInsertRowid);

    broadcast(req, 'created', timer);
    res.status(201).json(timer);
  } catch (err) {
    console.error('Create timer error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Update timer (stop it or modify)
router.patch('/:id', (req, res) => {
  try {
    const timer = db.prepare('SELECT * FROM timers WHERE id = ? AND user_id = ?').get(req.params.id, req.userId);

    if (!timer) {
      return res.status(404).json({ error: 'Timer not found' });
    }

    const { start_time, end_time, tag, project_id } = req.body;

    let projectId;
    if (Object.prototype.hasOwnProperty.call(req.body, 'project_id')) {
      if (project_id === null || project_id === '') {
        projectId = null;
      } else {
        const parsedProjectId = Number(project_id);
        if (!Number.isInteger(parsedProjectId)) {
          return res.status(400).json({ error: 'Invalid project_id' });
        }
        const project = db.prepare('SELECT id FROM projects WHERE id = ? AND user_id = ?').get(parsedProjectId, req.userId);
        if (!project) {
          return res.status(400).json({ error: 'Project not found' });
        }
        projectId = parsedProjectId;
      }
    }

    const updates = [];
    const params = [];

    if (start_time !== undefined) {
      updates.push('start_time = ?');
      params.push(start_time);
    }
    if (end_time !== undefined) {
      updates.push('end_time = ?');
      params.push(end_time);
    }
    if (tag !== undefined) {
      updates.push('tag = ?');
      params.push(tag);
    }
    if (Object.prototype.hasOwnProperty.call(req.body, 'project_id')) {
      updates.push('project_id = ?');
      params.push(projectId);
    }

    if (updates.length > 0) {
      params.push(req.params.id, req.userId);
      db.prepare(`
        UPDATE timers 
        SET ${updates.join(', ')}
        WHERE id = ? AND user_id = ?
      `).run(...params);
    }

    const updatedTimer = db.prepare('SELECT * FROM timers WHERE id = ?').get(req.params.id);

    broadcast(req, 'updated', updatedTimer);
    res.json(updatedTimer);
  } catch (err) {
    console.error('Update timer error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Stop current running timer
router.post('/:id/stop', (req, res) => {
  try {
    const timer = db.prepare('SELECT * FROM timers WHERE id = ? AND user_id = ?').get(req.params.id, req.userId);

    if (!timer) {
      return res.status(404).json({ error: 'Timer not found' });
    }

    if (timer.end_time) {
      return res.status(400).json({ error: 'Timer already stopped' });
    }

    const endTime = new Date().toISOString();
    db.prepare('UPDATE timers SET end_time = ? WHERE id = ?').run(endTime, req.params.id);

    const updatedTimer = db.prepare('SELECT * FROM timers WHERE id = ?').get(req.params.id);

    broadcast(req, 'stopped', updatedTimer);
    res.json(updatedTimer);
  } catch (err) {
    console.error('Stop timer error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Delete timer
router.delete('/:id', (req, res) => {
  try {
    const timer = db.prepare('SELECT * FROM timers WHERE id = ? AND user_id = ?').get(req.params.id, req.userId);

    if (!timer) {
      return res.status(404).json({ error: 'Timer not found' });
    }

    db.prepare('DELETE FROM timers WHERE id = ?').run(req.params.id);

    broadcast(req, 'deleted', { id: timer.id });
    res.status(204).send();
  } catch (err) {
    console.error('Delete timer error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
