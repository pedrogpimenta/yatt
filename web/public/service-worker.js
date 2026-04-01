const SERVICE_WORKER_VERSION = '2026-04-01';

function broadcast(message) {
  return self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    .then((clients) => {
      clients.forEach((client) => client.postMessage(message));
    });
}

function broadcastTimerRefresh(source, extra = {}) {
  return broadcast({
    type: 'refresh-request',
    scope: 'timers',
    source,
    ts: Date.now(),
    ...extra
  });
}

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      await self.clients.claim();
      await broadcast({
        type: 'service-worker-ready',
        version: SERVICE_WORKER_VERSION,
        ts: Date.now()
      });
    })()
  );
});

self.addEventListener('message', (event) => {
  const data = event.data || {};

  if (data.type === 'skip-waiting') {
    self.skipWaiting();
    return;
  }

  if (data.type === 'refresh-request' && data.scope === 'timers') {
    event.waitUntil(
      broadcastTimerRefresh(data.source || 'client-message')
    );
  }
});

self.addEventListener('sync', (event) => {
  if (event.tag === 'check-api-state') {
    event.waitUntil(
      broadcastTimerRefresh('background-sync')
    );
  }
});