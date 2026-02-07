const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const db = require('../db');
const { JWT_SECRET, authMiddleware } = require('../middleware/auth');
const { isEmailConfigured, sendConfirmationEmail, sendPasswordResetEmail } = require('../email');

const router = express.Router();

const EMAIL_CONFIRMATION_TTL_HOURS = Number(process.env.EMAIL_CONFIRMATION_TTL_HOURS || 48);
const PASSWORD_RESET_TTL_HOURS = Number(process.env.PASSWORD_RESET_TTL_HOURS || 2);
const REQUIRE_EMAIL_CONFIRMATION = /^(true|1|yes|on)$/i.test(process.env.REQUIRE_EMAIL_CONFIRMATION || '');

function addHours(hours) {
  return new Date(Date.now() + hours * 60 * 60 * 1000).toISOString();
}

function prefersHtml(req) {
  const accept = req.headers.accept || '';
  return accept.includes('text/html');
}

function sendHtmlPage(res, status, title, bodyHtml) {
  const html = [
    '<!doctype html>',
    '<html>',
    '<head>',
    '<meta charset="utf-8">',
    `<title>${title}</title>`,
    '<style>',
    'body { font-family: Arial, sans-serif; margin: 2rem; color: #111827; }',
    'h1 { font-size: 1.25rem; margin-bottom: 1rem; }',
    'p { margin: 0.5rem 0; }',
    'form { margin-top: 1rem; }',
    'label { display: block; margin: 0.75rem 0 0.25rem; }',
    'input { padding: 0.5rem; width: 100%; max-width: 320px; }',
    'button { margin-top: 1rem; padding: 0.5rem 1rem; }',
    '.notice { background: #f9fafb; padding: 1rem; border-radius: 8px; }',
    '</style>',
    '</head>',
    '<body>',
    `<h1>${title}</h1>`,
    `<div class="notice">${bodyHtml}</div>`,
    '</body>',
    '</html>'
  ].join('');
  res.status(status).set('Content-Type', 'text/html; charset=utf-8').send(html);
}

function sendApiResponse(req, res, status, message, isError = false) {
  if (prefersHtml(req)) {
    const title = isError ? 'Action failed' : 'Success';
    return sendHtmlPage(res, status, title, `<p>${message}</p>`);
  }
  if (isError) {
    return res.status(status).json({ error: message });
  }
  return res.status(status).json({ message });
}

function generateToken() {
  return crypto.randomBytes(32).toString('hex');
}

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

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

    if (REQUIRE_EMAIL_CONFIRMATION && !isEmailConfigured()) {
      return res.status(503).json({ error: 'Email service is not configured' });
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    const result = db.prepare('INSERT INTO users (email, password) VALUES (?, ?)').run(email, hashedPassword);

    const userId = result.lastInsertRowid;
    let confirmationSent = false;
    let confirmationToken = null;

    if (isEmailConfigured()) {
      confirmationToken = generateToken();
      const confirmationTokenHash = hashToken(confirmationToken);
      const confirmationExpiresAt = addHours(EMAIL_CONFIRMATION_TTL_HOURS);
      const sentAt = new Date().toISOString();
      db.prepare(
        'UPDATE users SET email_confirmation_token = ?, email_confirmation_expires_at = ?, email_confirmation_sent_at = ? WHERE id = ?'
      ).run(confirmationTokenHash, confirmationExpiresAt, sentAt, userId);
      try {
        await sendConfirmationEmail({ to: email, token: confirmationToken });
        confirmationSent = true;
      } catch (emailErr) {
        console.error('Failed to send confirmation email:', emailErr);
        if (REQUIRE_EMAIL_CONFIRMATION) {
          db.prepare('DELETE FROM users WHERE id = ?').run(userId);
          return res.status(500).json({ error: 'Failed to send confirmation email' });
        }
      }
    }

    const token = REQUIRE_EMAIL_CONFIRMATION
      ? null
      : jwt.sign({ userId }, JWT_SECRET, { expiresIn: '7d' });

    res.status(201).json({
      token,
      userId,
      emailConfirmationRequired: REQUIRE_EMAIL_CONFIRMATION,
      confirmationSent
    });
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

    if (REQUIRE_EMAIL_CONFIRMATION && !user.email_confirmed) {
      return res.status(403).json({ error: 'Email not confirmed' });
    }

    const token = jwt.sign({ userId: user.id }, JWT_SECRET, { expiresIn: '7d' });

    res.json({ token, userId: user.id, emailConfirmed: !!user.email_confirmed });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Confirm email address
router.get('/confirm-email', async (req, res) => {
  try {
    const token = String(req.query.token || '').trim();
    if (!token) {
      return sendApiResponse(req, res, 400, 'Confirmation token is required', true);
    }

    const tokenHash = hashToken(token);
    const now = new Date().toISOString();
    const user = db.prepare(
      'SELECT id, email_confirmed, email_confirmation_expires_at FROM users WHERE email_confirmation_token = ?'
    ).get(tokenHash);

    if (!user || !user.email_confirmation_expires_at) {
      return sendApiResponse(req, res, 400, 'Invalid confirmation token', true);
    }

    if (user.email_confirmation_expires_at < now) {
      return sendApiResponse(req, res, 400, 'Confirmation token has expired', true);
    }

    if (!user.email_confirmed) {
      db.prepare(
        'UPDATE users SET email_confirmed = 1, email_confirmed_at = ?, email_confirmation_token = NULL, email_confirmation_expires_at = NULL WHERE id = ?'
      ).run(now, user.id);
    } else {
      db.prepare(
        'UPDATE users SET email_confirmation_token = NULL, email_confirmation_expires_at = NULL WHERE id = ?'
      ).run(user.id);
    }

    return sendApiResponse(req, res, 200, 'Email confirmed successfully');
  } catch (err) {
    console.error('Confirm email error:', err);
    return sendApiResponse(req, res, 500, 'Server error', true);
  }
});

// Request password reset email
router.post('/request-password-reset', async (req, res) => {
  try {
    const { email } = req.body;
    if (!email) {
      return res.status(400).json({ error: 'Email is required' });
    }

    if (!isEmailConfigured()) {
      return res.status(503).json({ error: 'Email service is not configured' });
    }

    const user = db.prepare('SELECT id, email FROM users WHERE email = ?').get(email);

    if (user) {
      const resetToken = generateToken();
      const resetTokenHash = hashToken(resetToken);
      const resetExpiresAt = addHours(PASSWORD_RESET_TTL_HOURS);
      const sentAt = new Date().toISOString();

      db.prepare(
        'UPDATE users SET password_reset_token = ?, password_reset_expires_at = ?, password_reset_sent_at = ? WHERE id = ?'
      ).run(resetTokenHash, resetExpiresAt, sentAt, user.id);

      try {
        await sendPasswordResetEmail({ to: user.email, token: resetToken });
      } catch (emailErr) {
        console.error('Failed to send password reset email:', emailErr);
      }
    }

    res.json({ message: 'If an account exists, a reset email has been sent' });
  } catch (err) {
    console.error('Request password reset error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Render reset password form
router.get('/reset-password', (req, res) => {
  try {
    const token = String(req.query.token || '').trim();
    if (!token) {
      return sendHtmlPage(res, 400, 'Reset password', '<p>Reset token is required.</p>');
    }

    const tokenHash = hashToken(token);
    const now = new Date().toISOString();
    const user = db.prepare(
      'SELECT id, password_reset_expires_at FROM users WHERE password_reset_token = ?'
    ).get(tokenHash);

    if (!user || !user.password_reset_expires_at || user.password_reset_expires_at < now) {
      if (!prefersHtml(req)) {
        return res.status(400).json({ error: 'Reset token is invalid or expired' });
      }
      return sendHtmlPage(
        res,
        400,
        'Reset password',
        '<p>Reset token is invalid or expired. Please request a new reset email.</p>'
      );
    }

    if (!prefersHtml(req)) {
      return res.json({ message: 'Reset token is valid' });
    }

    const body = [
      '<p>Enter your new password below.</p>',
      '<form method="POST">',
      `<input type="hidden" name="token" value="${token}">`,
      '<label for="newPassword">New password</label>',
      '<input id="newPassword" name="newPassword" type="password" minlength="6" required>',
      '<label for="confirmPassword">Confirm new password</label>',
      '<input id="confirmPassword" name="confirmPassword" type="password" minlength="6" required>',
      '<button type="submit">Reset password</button>',
      '</form>'
    ].join('');

    return sendHtmlPage(res, 200, 'Reset password', body);
  } catch (err) {
    console.error('Reset password form error:', err);
    return sendHtmlPage(res, 500, 'Reset password', '<p>Server error.</p>');
  }
});

// Reset password with token
router.post('/reset-password', async (req, res) => {
  try {
    const { token, newPassword, confirmPassword } = req.body || {};
    if (!token || !newPassword) {
      return res.status(400).json({ error: 'Token and new password are required' });
    }

    if (confirmPassword && confirmPassword !== newPassword) {
      return res.status(400).json({ error: 'Passwords do not match' });
    }

    if (newPassword.length < 6) {
      return res.status(400).json({ error: 'New password must be at least 6 characters' });
    }

    const tokenHash = hashToken(String(token).trim());
    const now = new Date().toISOString();
    const user = db.prepare(
      'SELECT id FROM users WHERE password_reset_token = ? AND password_reset_expires_at >= ?'
    ).get(tokenHash, now);

    if (!user) {
      return res.status(400).json({ error: 'Reset token is invalid or expired' });
    }

    const hashedPassword = await bcrypt.hash(newPassword, 10);
    db.prepare(
      'UPDATE users SET password = ?, password_reset_token = NULL, password_reset_expires_at = NULL, password_reset_sent_at = NULL WHERE id = ?'
    ).run(hashedPassword, user.id);

    res.json({ message: 'Password reset successfully' });
  } catch (err) {
    console.error('Reset password error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get current user (protected)
router.get('/me', authMiddleware, (req, res) => {
  try {
    const user = db.prepare(
      'SELECT id, email, created_at, day_start_hour, email_confirmed, email_confirmed_at FROM users WHERE id = ?'
    ).get(req.userId);
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }
    res.json({
      id: user.id,
      email: user.email,
      created_at: user.created_at,
      dayStartHour: user.day_start_hour ?? 0,
      emailConfirmed: !!user.email_confirmed,
      emailConfirmedAt: user.email_confirmed_at || null
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
