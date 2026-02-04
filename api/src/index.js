const express = require('express');
const cors = require('cors');
const http = require('http');
const { WebSocketServer } = require('ws');
const jwt = require('jsonwebtoken');
const authRoutes = require('./routes/auth');
const timerRoutes = require('./routes/timers');
const syncRoutes = require('./routes/sync');
const projectRoutes = require('./routes/projects');
const clientRoutes = require('./routes/clients');
const { JWT_SECRET } = require('./middleware/auth');

const app = express();
const server = http.createServer(app);
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());

// WebSocket server
const wss = new WebSocketServer({ server });

// Store connected clients by userId
const clients = new Map();

wss.on('connection', (ws, req) => {
  let userId = null;

  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message);
      
      // Handle authentication
      if (data.type === 'auth') {
        try {
          const decoded = jwt.verify(data.token, JWT_SECRET);
          userId = decoded.userId;
          
          // Store client connection
          if (!clients.has(userId)) {
            clients.set(userId, new Set());
          }
          clients.get(userId).add(ws);
          
          ws.send(JSON.stringify({ type: 'auth', status: 'ok' }));
        } catch (err) {
          ws.send(JSON.stringify({ type: 'auth', status: 'error', message: 'Invalid token' }));
        }
      }
    } catch (err) {
      // Ignore invalid messages
    }
  });

  ws.on('close', () => {
    if (userId && clients.has(userId)) {
      clients.get(userId).delete(ws);
      if (clients.get(userId).size === 0) {
        clients.delete(userId);
      }
    }
  });

  // Send ping every 30 seconds to keep connection alive
  const pingInterval = setInterval(() => {
    if (ws.readyState === ws.OPEN) {
      ws.ping();
    }
  }, 30000);

  ws.on('close', () => clearInterval(pingInterval));
});

// Function to broadcast updates to a user's connected clients
function broadcastToUser(userId, data) {
  if (clients.has(userId)) {
    const message = JSON.stringify(data);
    clients.get(userId).forEach((ws) => {
      if (ws.readyState === ws.OPEN) {
        ws.send(message);
      }
    });
  }
}

// Make broadcast function available to routes
app.set('broadcastToUser', broadcastToUser);

// Routes
app.use('/auth', authRoutes);
app.use('/timers', timerRoutes);
app.use('/sync', syncRoutes);
app.use('/projects', projectRoutes);
app.use('/clients', clientRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`YATT API running on port ${PORT}`);
});
