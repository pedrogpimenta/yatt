const express = require('express');
const crypto = require('crypto');

const router = express.Router();

// In-memory store for sync sessions (in production, use Redis or similar)
// Sessions expire after 10 minutes
const syncSessions = new Map();

// Clean up expired sessions periodically
setInterval(() => {
  const now = Date.now();
  for (const [code, session] of syncSessions.entries()) {
    if (now - session.createdAt > 10 * 60 * 1000) {
      syncSessions.delete(code);
    }
  }
}, 60 * 1000);

// Generate a short, readable sync code
function generateSyncCode() {
  // Generate 6 character alphanumeric code (uppercase for readability)
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // Exclude confusing chars like 0/O, 1/I
  let code = '';
  const bytes = crypto.randomBytes(6);
  for (let i = 0; i < 6; i++) {
    code += chars[bytes[i] % chars.length];
  }
  return code;
}

// Create a new sync session
router.post('/create', (req, res) => {
  try {
    const { deviceId, timers } = req.body;

    if (!deviceId) {
      return res.status(400).json({ error: 'Device ID is required' });
    }

    // Generate unique sync code
    let syncCode;
    do {
      syncCode = generateSyncCode();
    } while (syncSessions.has(syncCode));

    // Store session
    syncSessions.set(syncCode, {
      createdAt: Date.now(),
      initiator: {
        deviceId,
        timers: timers || []
      },
      joiner: null,
      status: 'waiting' // waiting, joined, completed
    });

    res.status(201).json({ 
      syncCode,
      expiresIn: 600 // 10 minutes in seconds
    });
  } catch (err) {
    console.error('Create sync session error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Join an existing sync session
router.post('/join', (req, res) => {
  try {
    const { syncCode, deviceId, timers } = req.body;

    if (!syncCode || !deviceId) {
      return res.status(400).json({ error: 'Sync code and device ID are required' });
    }

    const session = syncSessions.get(syncCode.toUpperCase());
    
    if (!session) {
      return res.status(404).json({ error: 'Sync session not found or expired' });
    }

    if (session.status !== 'waiting') {
      return res.status(400).json({ error: 'Sync session already used' });
    }

    if (session.initiator.deviceId === deviceId) {
      return res.status(400).json({ error: 'Cannot sync with yourself' });
    }

    // Store joiner data
    session.joiner = {
      deviceId,
      timers: timers || []
    };
    session.status = 'joined';

    // Return the initiator's timers to the joiner
    res.json({
      status: 'joined',
      timers: session.initiator.timers,
      message: 'Sync successful! Timers from the other device are included.'
    });
  } catch (err) {
    console.error('Join sync session error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Check sync session status (for polling by initiator)
router.get('/status/:syncCode', (req, res) => {
  try {
    const { syncCode } = req.params;
    const session = syncSessions.get(syncCode.toUpperCase());

    if (!session) {
      return res.status(404).json({ error: 'Sync session not found or expired' });
    }

    const response = {
      status: session.status,
      createdAt: session.createdAt,
      expiresAt: session.createdAt + 10 * 60 * 1000
    };

    // If joined, include the joiner's timers for the initiator
    if (session.status === 'joined' && session.joiner) {
      response.timers = session.joiner.timers;
      // Mark as completed and schedule deletion
      session.status = 'completed';
      setTimeout(() => {
        syncSessions.delete(syncCode.toUpperCase());
      }, 60 * 1000); // Delete after 1 minute
    }

    res.json(response);
  } catch (err) {
    console.error('Get sync status error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

// Cancel a sync session
router.delete('/:syncCode', (req, res) => {
  try {
    const { syncCode } = req.params;
    const deleted = syncSessions.delete(syncCode.toUpperCase());

    if (!deleted) {
      return res.status(404).json({ error: 'Sync session not found' });
    }

    res.status(204).send();
  } catch (err) {
    console.error('Delete sync session error:', err);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;
