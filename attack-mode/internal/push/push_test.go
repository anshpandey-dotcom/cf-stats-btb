package push

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"attackmode/internal/store"
)

func TestKeysPersist(t *testing.T) {
	st, err := store.Open(filepath.Join(t.TempDir(), "t.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer st.Close()

	first, err := New(context.Background(), st, "")
	if err != nil {
		t.Fatal(err)
	}
	second, err := New(context.Background(), st, "")
	if err != nil {
		t.Fatal(err)
	}
	if first.PublicKey() == "" || first.PublicKey() != second.PublicKey() {
		t.Fatal("vapid key should be generated once and reused")
	}
}

func TestMomentAndPayload(t *testing.T) {
	loc, _ := time.LoadLocation("Asia/Kolkata")
	task := store.Task{ID: 7, Date: "2026-10-23", Time: "09:30", What: "Write chapter", HowMuch: "1500 words", Where: "Desk"}

	due, ok := Moment(task, loc)
	if !ok || due.UTC().Format("15:04") != "04:00" {
		t.Fatalf("due = %v ok = %v", due, ok)
	}

	payload := ReminderPayload(task, due, due.Add(-10*time.Minute))
	if payload.Title != "In 10 min: Write chapter" || payload.URL != "/day/2026-10-23" {
		t.Fatalf("unexpected payload: %+v", payload)
	}
	if payload.Body != "09:30 · 1500 words · at Desk" {
		t.Fatalf("unexpected body: %q", payload.Body)
	}
}
