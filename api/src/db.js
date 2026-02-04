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

  CREATE TABLE IF NOT EXISTS timers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    tag TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
  );

  CREATE INDEX IF NOT EXISTS idx_timers_user_id ON timers(user_id);
  CREATE INDEX IF NOT EXISTS idx_timers_start_time ON timers(start_time);
`);

// Ensure new columns exist for existing databases
const userColumns = db.prepare(`PRAGMA table_info(users);`).all();
const hasDayStartHour = userColumns.some((column) => column.name === 'day_start_hour');
if (!hasDayStartHour) {
  db.exec('ALTER TABLE users ADD COLUMN day_start_hour INTEGER NOT NULL DEFAULT 0;');
}

module.exports = db;
