ALTER TABLE tasks ADD COLUMN remind_time TEXT NOT NULL DEFAULT '';
ALTER TABLE tasks ADD COLUMN remind_before INTEGER NOT NULL DEFAULT 10 CHECK (remind_before BETWEEN 0 AND 1440);
ALTER TABLE tasks ADD COLUMN notified INTEGER NOT NULL DEFAULT 0 CHECK (notified IN (0, 1));

CREATE INDEX IF NOT EXISTS idx_tasks_reminders ON tasks (task_date, notified, completed);

CREATE TABLE IF NOT EXISTS push_subscriptions (
    endpoint TEXT PRIMARY KEY,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    timezone TEXT NOT NULL DEFAULT 'UTC',
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
