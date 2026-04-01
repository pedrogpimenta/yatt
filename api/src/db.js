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
    daily_goal_enabled INTEGER NOT NULL DEFAULT 0,
    default_daily_goal_hours REAL NOT NULL DEFAULT 8,
    include_weekend_goals INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  );

  CREATE TABLE IF NOT EXISTS daily_goals (
    user_id INTEGER NOT NULL,
    date TEXT NOT NULL,
    hours REAL NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, date),
    FOREIGN KEY (user_id) REFERENCES users(id)
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

  CREATE TABLE IF NOT EXISTS device_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    token TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'android',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, token),
    FOREIGN KEY (user_id) REFERENCES users(id)
  );

  CREATE INDEX IF NOT EXISTS idx_clients_user_id ON clients(user_id);
  CREATE INDEX IF NOT EXISTS idx_projects_user_id ON projects(user_id);
  CREATE INDEX IF NOT EXISTS idx_projects_client_id ON projects(client_id);
  CREATE INDEX IF NOT EXISTS idx_timers_user_id ON timers(user_id);
  CREATE INDEX IF NOT EXISTS idx_timers_start_time ON timers(start_time);
  CREATE INDEX IF NOT EXISTS idx_daily_goals_user_id ON daily_goals(user_id);
  CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON device_tokens(user_id);
  CREATE INDEX IF NOT EXISTS idx_device_tokens_token ON device_tokens(token);

  CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    token TEXT UNIQUE NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
  );

  CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token ON password_reset_tokens(token);
`);

// Ensure new columns exist for existing databases
const userColumns = db.prepare(`PRAGMA table_info(users);`).all();
const hasDayStartHour = userColumns.some((column) => column.name === 'day_start_hour');
if (!hasDayStartHour) {
  db.exec('ALTER TABLE users ADD COLUMN day_start_hour INTEGER NOT NULL DEFAULT 0;');
}
const hasDailyGoalEnabled = userColumns.some((column) => column.name === 'daily_goal_enabled');
if (!hasDailyGoalEnabled) {
  db.exec('ALTER TABLE users ADD COLUMN daily_goal_enabled INTEGER NOT NULL DEFAULT 0;');
}
const hasDefaultDailyGoalHours = userColumns.some((column) => column.name === 'default_daily_goal_hours');
if (!hasDefaultDailyGoalHours) {
  db.exec('ALTER TABLE users ADD COLUMN default_daily_goal_hours REAL NOT NULL DEFAULT 8;');
}
const hasIncludeWeekendGoals = userColumns.some((column) => column.name === 'include_weekend_goals');
if (!hasIncludeWeekendGoals) {
  db.exec('ALTER TABLE users ADD COLUMN include_weekend_goals INTEGER NOT NULL DEFAULT 0;');
}

const timerColumns = db.prepare(`PRAGMA table_info(timers);`).all();
const hasProjectId = timerColumns.some((column) => column.name === 'project_id');
if (!hasProjectId) {
  db.exec('ALTER TABLE timers ADD COLUMN project_id INTEGER;');
}
const hasDescription = timerColumns.some((column) => column.name === 'description');
if (!hasDescription) {
  db.exec('ALTER TABLE timers ADD COLUMN description TEXT;');
}

db.exec('CREATE INDEX IF NOT EXISTS idx_timers_project_id ON timers(project_id);');

module.exports = db;
