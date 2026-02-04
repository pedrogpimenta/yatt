const Database = require('better-sqlite3');
const path = require('path');

const dbPath = process.env.DB_PATH || path.join(__dirname, '..', 'data', 'yatt.db');
const db = new Database(dbPath);

// Enable WAL mode for better performance
db.pragma('journal_mode = WAL');

// Create tables
db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    day_start_hour INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  );

  CREATE TABLE IF NOT EXISTS clients (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, name),
    FOREIGN KEY (user_id) REFERENCES users(id)
  );

  CREATE TABLE IF NOT EXISTS projects (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    type TEXT,
    client_id INTEGER,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (client_id) REFERENCES clients(id)
  );

  CREATE TABLE IF NOT EXISTS timers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    tag TEXT,
    project_id INTEGER,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
  );

  CREATE INDEX IF NOT EXISTS idx_clients_user_id ON clients(user_id);
  CREATE INDEX IF NOT EXISTS idx_projects_user_id ON projects(user_id);
  CREATE INDEX IF NOT EXISTS idx_projects_client_id ON projects(client_id);
  CREATE INDEX IF NOT EXISTS idx_timers_user_id ON timers(user_id);
  CREATE INDEX IF NOT EXISTS idx_timers_start_time ON timers(start_time);
  CREATE INDEX IF NOT EXISTS idx_timers_project_id ON timers(project_id);
`);

// Ensure new columns exist for existing databases
const userColumns = db.prepare(`PRAGMA table_info(users);`).all();
const hasDayStartHour = userColumns.some((column) => column.name === 'day_start_hour');
if (!hasDayStartHour) {
  db.exec('ALTER TABLE users ADD COLUMN day_start_hour INTEGER NOT NULL DEFAULT 0;');
}

const timerColumns = db.prepare(`PRAGMA table_info(timers);`).all();
const hasProjectId = timerColumns.some((column) => column.name === 'project_id');
if (!hasProjectId) {
  db.exec('ALTER TABLE timers ADD COLUMN project_id INTEGER;');
}

module.exports = db;
