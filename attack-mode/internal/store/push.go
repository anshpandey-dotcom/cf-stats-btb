package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"
)

// PushSubscription is one browser push endpoint.
type PushSubscription struct {
	Endpoint string `json:"endpoint"`
	P256dh   string `json:"p256dh"`
	Auth     string `json:"auth"`
	Timezone string `json:"timezone"`
}

// SaveSubscription stores or refreshes a push subscription.
func (s *Store) SaveSubscription(ctx context.Context, sub PushSubscription) error {
	sub.Endpoint = strings.TrimSpace(sub.Endpoint)
	if sub.Endpoint == "" || sub.P256dh == "" || sub.Auth == "" {
		return validationError("A complete push subscription is required.")
	}
	if !strings.HasPrefix(sub.Endpoint, "https://") {
		return validationError("Push endpoint must use https.")
	}
	if _, err := time.LoadLocation(sub.Timezone); err != nil || sub.Timezone == "" {
		sub.Timezone = "UTC"
	}

	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO push_subscriptions (endpoint, p256dh, auth, timezone, created_at)
		VALUES (?, ?, ?, ?, ?)
		ON CONFLICT(endpoint) DO UPDATE SET
			p256dh = excluded.p256dh,
			auth = excluded.auth,
			timezone = excluded.timezone`,
		sub.Endpoint, sub.P256dh, sub.Auth, sub.Timezone, formatTime(time.Now().UTC()),
	)
	if err != nil {
		return fmt.Errorf("save subscription: %w", err)
	}
	return nil
}

// DeleteSubscription removes a push subscription by endpoint.
func (s *Store) DeleteSubscription(ctx context.Context, endpoint string) error {
	if _, err := s.db.ExecContext(ctx, `DELETE FROM push_subscriptions WHERE endpoint = ?`, endpoint); err != nil {
		return fmt.Errorf("delete subscription: %w", err)
	}
	return nil
}

// ListSubscriptions returns every stored subscription, newest first.
func (s *Store) ListSubscriptions(ctx context.Context) ([]PushSubscription, error) {
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT endpoint, p256dh, auth, timezone FROM push_subscriptions ORDER BY created_at DESC`,
	)
	if err != nil {
		return nil, fmt.Errorf("list subscriptions: %w", err)
	}
	defer rows.Close()

	subs := make([]PushSubscription, 0)
	for rows.Next() {
		var sub PushSubscription
		if err := rows.Scan(&sub.Endpoint, &sub.P256dh, &sub.Auth, &sub.Timezone); err != nil {
			return nil, fmt.Errorf("scan subscription: %w", err)
		}
		subs = append(subs, sub)
	}
	return subs, rows.Err()
}

// GetSetting reads a stored setting, returning an empty string when unset.
func (s *Store) GetSetting(ctx context.Context, key string) (string, error) {
	var value string
	err := s.db.QueryRowContext(ctx, `SELECT value FROM settings WHERE key = ?`, key).Scan(&value)
	if errors.Is(err, sql.ErrNoRows) {
		return "", nil
	}
	if err != nil {
		return "", fmt.Errorf("get setting: %w", err)
	}
	return value, nil
}

// SetSetting stores a setting value.
func (s *Store) SetSetting(ctx context.Context, key, value string) error {
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO settings (key, value) VALUES (?, ?)
		ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
		key, value,
	)
	if err != nil {
		return fmt.Errorf("set setting: %w", err)
	}
	return nil
}

// PendingReminders returns open, un-notified timed tasks dated within the
// given range. The caller decides which of them are actually due.
func (s *Store) PendingReminders(ctx context.Context, fromDate, toDate string) ([]Task, error) {
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT `+taskColumns+` FROM tasks
		WHERE task_date BETWEEN ? AND ? AND remind_time != '' AND notified = 0 AND completed = 0
		ORDER BY task_date, remind_time`,
		fromDate, toDate,
	)
	if err != nil {
		return nil, fmt.Errorf("pending reminders: %w", err)
	}
	defer rows.Close()

	tasks := make([]Task, 0)
	for rows.Next() {
		task, err := scanTask(rows)
		if err != nil {
			return nil, fmt.Errorf("scan reminder: %w", err)
		}
		tasks = append(tasks, task)
	}
	return tasks, rows.Err()
}

// MarkNotified flags a task as already reminded.
func (s *Store) MarkNotified(ctx context.Context, id int64) error {
	if _, err := s.db.ExecContext(ctx, `UPDATE tasks SET notified = 1 WHERE id = ?`, id); err != nil {
		return fmt.Errorf("mark notified: %w", err)
	}
	return nil
}
