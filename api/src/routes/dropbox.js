const express = require('express');

const router = express.Router();

const CLIENT_ID = process.env.DROPBOX_CLIENT_ID;
const CLIENT_SECRET = process.env.DROPBOX_CLIENT_SECRET;

function getRedirectUri() {
  return `${process.env.APP_URL}/api/dropbox/callback`;
}

function isConfigured() {
  return !!(CLIENT_ID && CLIENT_SECRET && process.env.APP_URL);
}

// In-memory state store for CSRF protection: state -> expiresAt
const pendingStates = new Map();
setInterval(() => {
  const now = Date.now();
  for (const [key, value] of pendingStates.entries()) {
    if (value < now) pendingStates.delete(key);
  }
}, 60000);

// GET /dropbox/configured - Check if Dropbox is configured (no auth needed)
router.get('/configured', (req, res) => {
  res.json({ configured: isConfigured() });
});

// GET /dropbox/auth-url - No auth required; any visitor can initiate OAuth
router.get('/auth-url', (req, res) => {
  if (!isConfigured()) {
    return res.status(503).json({ error: 'Dropbox not configured on this server' });
  }

  const state = Math.random().toString(36).substr(2, 16);
  pendingStates.set(state, Date.now() + 5 * 60 * 1000);

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    response_type: 'code',
    redirect_uri: getRedirectUri(),
    token_access_type: 'offline',
    state,
  });

  res.json({ url: `https://www.dropbox.com/oauth2/authorize?${params}` });
});

// GET /dropbox/callback - Exchanges code for tokens, passes them to the client via URL
router.get('/callback', async (req, res) => {
  const { code, state, error } = req.query;
  const appUrl = process.env.APP_URL;

  if (error) {
    return res.redirect(`${appUrl}?dropbox_error=${encodeURIComponent(error)}`);
  }

  const expiresAt = pendingStates.get(state);
  if (!expiresAt || expiresAt < Date.now()) {
    return res.redirect(`${appUrl}?dropbox_error=invalid_state`);
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
    const expiresAtMs = Date.now() + (data.expires_in || 14400) * 1000;

    // Pass tokens to client via URL — client stores them in localStorage and immediately cleans the URL
    const redirect = new URLSearchParams({
      dropbox_token: data.access_token,
      dropbox_refresh: data.refresh_token,
      dropbox_expires: String(expiresAtMs),
    });

    res.redirect(`${appUrl}?${redirect}`);
  } catch (err) {
    res.redirect(`${appUrl}?dropbox_error=token_exchange_failed`);
  }
});

// POST /dropbox/refresh - Stateless token refresh proxy (no server-side storage)
router.post('/refresh', async (req, res) => {
  const { refreshToken } = req.body;
  if (!refreshToken) return res.status(400).json({ error: 'refreshToken required' });

  try {
    const params = new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: refreshToken,
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
    });

    const response = await fetch('https://api.dropboxapi.com/oauth2/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params,
    });

    if (!response.ok) return res.status(401).json({ error: 'Refresh failed' });

    const data = await response.json();
    res.json({
      accessToken: data.access_token,
      expiresAt: Date.now() + (data.expires_in || 14400) * 1000,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
