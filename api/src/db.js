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
    email_confirmed INTEGER NOT NULL DEFAULT 0,
    email_confirmed_at DATETIME,
    email_confirmation_token TEXT,
    email_confirmation_expires_at DATETIME,
    email_confirmation_sent_at DATETIME,
    password_reset_token TEXT,
    password_reset_expires_at DATETIME,
    password_reset_sent_at DATETIME,
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

  CREATE INDEX IF NOT EXISTS idx_clients_user_id ON clients(user_id);
  CREATE INDEX IF NOT EXISTS idx_projects_user_id ON projects(user_id);
  CREATE INDEX IF NOT EXISTS idx_projects_client_id ON projects(client_id);
  CREATE INDEX IF NOT EXISTS idx_timers_user_id ON timers(user_id);
  CREATE INDEX IF NOT EXISTS idx_timers_start_time ON timers(start_time);
  CREATE INDEX IF NOT EXISTS idx_daily_goals_user_id ON daily_goals(user_id);
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
const hasEmailConfirmed = userColumns.some((column) => column.name === 'email_confirmed');
if (!hasEmailConfirmed) {
  db.exec('ALTER TABLE users ADD COLUMN email_confirmed INTEGER NOT NULL DEFAULT 0;');
}
const hasEmailConfirmedAt = userColumns.some((column) => column.name === 'email_confirmed_at');
if (!hasEmailConfirmedAt) {
  db.exec('ALTER TABLE users ADD COLUMN email_confirmed_at DATETIME;');
}
const hasEmailConfirmationToken = userColumns.some((column) => column.name === 'email_confirmation_token');
if (!hasEmailConfirmationToken) {
  db.exec('ALTER TABLE users ADD COLUMN email_confirmation_token TEXT;');
}
const hasEmailConfirmationExpiresAt = userColumns.some((column) => column.name === 'email_confirmation_expires_at');
if (!hasEmailConfirmationExpiresAt) {
  db.exec('ALTER TABLE users ADD COLUMN email_confirmation_expires_at DATETIME;');
}
const hasEmailConfirmationSentAt = userColumns.some((column) => column.name === 'email_confirmation_sent_at');
if (!hasEmailConfirmationSentAt) {
  db.exec('ALTER TABLE users ADD COLUMN email_confirmation_sent_at DATETIME;');
}
const hasPasswordResetToken = userColumns.some((column) => column.name === 'password_reset_token');
if (!hasPasswordResetToken) {
  db.exec('ALTER TABLE users ADD COLUMN password_reset_token TEXT;');
}
const hasPasswordResetExpiresAt = userColumns.some((column) => column.name === 'password_reset_expires_at');
if (!hasPasswordResetExpiresAt) {
  db.exec('ALTER TABLE users ADD COLUMN password_reset_expires_at DATETIME;');
}
const hasPasswordResetSentAt = userColumns.some((column) => column.name === 'password_reset_sent_at');
if (!hasPasswordResetSentAt) {
  db.exec('ALTER TABLE users ADD COLUMN password_reset_sent_at DATETIME;');
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
db.exec('CREATE INDEX IF NOT EXISTS idx_users_email_confirmation_token ON users(email_confirmation_token);');
db.exec('CREATE INDEX IF NOT EXISTS idx_users_password_reset_token ON users(password_reset_token);');

module.exports = db;
