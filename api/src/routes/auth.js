const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const db = require('../db');
const { JWT_SECRET, authMiddleware } = require('../middleware/auth');

const router = express.Router();

function normalizeDayStartHour(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 0 || parsed > 23) {
    return null;
  }
  return parsed;
}

// Register
router.post('/register', async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    if (password.length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters' });
    }

    const existingUser = db.prepare('SELECT id FROM users WHERE email = ?').get(email);
    if (existingUser) {
      return res.status(400).json({ error: 'Email already registered' });
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    const result = db.prepare('INSERT INTO users (email, password) VALUES (?, ?)').run(email, hashedPassword);

    const token = jwt.sign({ userId: result.lastInsertRowid }, JWT_SECRET, { expiresIn: '7d' });

    res.status(201).json({ token, userId: result.lastInsertRowid });
  } catch (err) {
    console.error('Register error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Login
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    const user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
    if (!user) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const validPassword = await bcrypt.compare(password, user.password);
    if (!validPassword) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const token = jwt.sign({ userId: user.id }, JWT_SECRET, { expiresIn: '7d' });

    res.json({ token, userId: user.id });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get current user (protected)
router.get('/me', authMiddleware, (req, res) => {
  try {
    const user = db.prepare('SELECT id, email, created_at, day_start_hour FROM users WHERE id = ?').get(req.userId);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }
    res.json({
      id: user.id,
      email: user.email,
      created_at: user.created_at,
      dayStartHour: user.day_start_hour ?? 0
    });
  } catch (err) {
    console.error('Get user error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get user preferences (protected)
router.get('/preferences', authMiddleware, (req, res) => {
  try {
    const user = db.prepare(
      'SELECT day_start_hour, daily_goal_enabled, default_daily_goal_hours, include_weekend_goals FROM users WHERE id = ?'
    ).get(req.userId);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }
    res.json({
      dayStartHour: user.day_start_hour ?? 0,
      dailyGoalEnabled: !!(user.daily_goal_enabled ?? 0),
      defaultDailyGoalHours: Number(user.default_daily_goal_hours ?? 8),
      includeWeekendGoals: !!(user.include_weekend_goals ?? 0)
    });
  } catch (err) {
    console.error('Get preferences error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

function normalizeDefaultDailyGoalHours(value) {
  if (value === null || value === undefined || value === '') return null;
  const parsed = Number(value);
  if (Number.isNaN(parsed) || parsed < 0 || parsed > 24) return null;
  return parsed;
}

// Update user preferences (protected)
router.patch('/preferences', authMiddleware, (req, res) => {
  try {
    const updates = [];
    const params = [];

    if (Object.prototype.hasOwnProperty.call(req.body || {}, 'dayStartHour')) {
      const dayStartHour = normalizeDayStartHour(req.body.dayStartHour);
      if (dayStartHour === null) {
        return res.status(400).json({ error: 'dayStartHour must be an integer between 0 and 23' });
      }
      updates.push('day_start_hour = ?');
      params.push(dayStartHour);
    }

    if (typeof req.body?.dailyGoalEnabled === 'boolean') {
      updates.push('daily_goal_enabled = ?');
      params.push(req.body.dailyGoalEnabled ? 1 : 0);
    }
    const defaultHours = normalizeDefaultDailyGoalHours(req.body?.defaultDailyGoalHours);
    if (defaultHours !== null) {
      updates.push('default_daily_goal_hours = ?');
      params.push(defaultHours);
    }
    if (typeof req.body?.includeWeekendGoals === 'boolean') {
      updates.push('include_weekend_goals = ?');
      params.push(req.body.includeWeekendGoals ? 1 : 0);
    }

    if (updates.length === 0) {
      const user = db.prepare(
        'SELECT day_start_hour, daily_goal_enabled, default_daily_goal_hours, include_weekend_goals FROM users WHERE id = ?'
      ).get(req.userId);
      if (!user) return res.status(404).json({ error: 'User not found' });
      return res.json({
        dayStartHour: user.day_start_hour ?? 0,
        dailyGoalEnabled: !!(user.daily_goal_enabled ?? 0),
        defaultDailyGoalHours: Number(user.default_daily_goal_hours ?? 8),
        includeWeekendGoals: !!(user.include_weekend_goals ?? 0)
      });
    }

    params.push(req.userId);
    const result = db.prepare(
      `UPDATE users SET ${updates.join(', ')} WHERE id = ?`
    ).run(...params);
    if (result.changes === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    const user = db.prepare(
      'SELECT day_start_hour, daily_goal_enabled, default_daily_goal_hours, include_weekend_goals FROM users WHERE id = ?'
    ).get(req.userId);
    res.json({
      dayStartHour: user.day_start_hour ?? 0,
      dailyGoalEnabled: !!(user.daily_goal_enabled ?? 0),
      defaultDailyGoalHours: Number(user.default_daily_goal_hours ?? 8),
      includeWeekendGoals: !!(user.include_weekend_goals ?? 0)
    });
  } catch (err) {
    console.error('Update preferences error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get daily goal overrides for a date range (protected)
// Query params: from=YYYY-MM-DD, to=YYYY-MM-DD
router.get('/daily-goals', authMiddleware, (req, res) => {
  try {
    const from = req.query.from;
    const to = req.query.to;
    if (!from || !to) {
      return res.status(400).json({ error: 'Query params from and to (YYYY-MM-DD) are required' });
    }
    const rows = db.prepare(
      'SELECT date, hours FROM daily_goals WHERE user_id = ? AND date >= ? AND date <= ? ORDER BY date'
    ).all(req.userId, from, to);
    const goals = {};
    rows.forEach((row) => { goals[row.date] = row.hours; });
    res.json(goals);
  } catch (err) {
    console.error('Get daily goals error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Set daily goal for a specific date (protected)
router.put('/daily-goals/:date', authMiddleware, (req, res) => {
  try {
    const dateStr = req.params.date;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
      return res.status(400).json({ error: 'Date must be YYYY-MM-DD' });
    }
    const hours = normalizeDefaultDailyGoalHours(req.body?.hours);
    if (hours === null) {
      return res.status(400).json({ error: 'hours must be a number between 0 and 24' });
    }
    db.prepare(
      'INSERT INTO daily_goals (user_id, date, hours) VALUES (?, ?, ?) ON CONFLICT(user_id, date) DO UPDATE SET hours = excluded.hours'
    ).run(req.userId, dateStr, hours);
    res.json({ date: dateStr, hours });
  } catch (err) {
    console.error('Set daily goal error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Clear daily goal override for a date (protected)
router.delete('/daily-goals/:date', authMiddleware, (req, res) => {
  try {
    const dateStr = req.params.date;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
      return res.status(400).json({ error: 'Date must be YYYY-MM-DD' });
    }
    db.prepare('DELETE FROM daily_goals WHERE user_id = ? AND date = ?').run(req.userId, dateStr);
    res.status(204).send();
  } catch (err) {
    console.error('Delete daily goal error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Change password (protected)
router.post('/change-password', authMiddleware, async (req, res) => {
  try {
    const { currentPassword, newPassword } = req.body;

    if (!currentPassword || !newPassword) {
      return res.status(400).json({ error: 'Current and new password are required' });
    }

    if (newPassword.length < 6) {
      return res.status(400).json({ error: 'New password must be at least 6 characters' });
    }

    const user = db.prepare('SELECT * FROM users WHERE id = ?').get(req.userId);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    const validPassword = await bcrypt.compare(currentPassword, user.password);
    if (!validPassword) {
      return res.status(401).json({ error: 'Current password is incorrect' });
    }

    const hashedPassword = await bcrypt.hash(newPassword, 10);
    db.prepare('UPDATE users SET password = ? WHERE id = ?').run(hashedPassword, req.userId);

    res.json({ message: 'Password changed successfully' });
  } catch (err) {
    console.error('Change password error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
