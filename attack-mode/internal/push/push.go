package push

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/http"
	"strings"
	"time"

	webpush "github.com/SherClockHolmes/webpush-go"

	"attackmode/internal/store"
)

const (
	settingPublicKey  = "vapid_public"
	settingPrivateKey = "vapid_private"
	staleAfter        = 2 * time.Hour
	tickEvery         = 20 * time.Second
)

// Service sends Web Push reminders for upcoming tasks.
type Service struct {
	store   *store.Store
	public  string
	private string
	subject string
	client  *http.Client
}

// New loads the VAPID key pair from the database, generating it on first run.
func New(ctx context.Context, st *store.Store, subject string) (*Service, error) {
	public, err := st.GetSetting(ctx, settingPublicKey)
	if err != nil {
		return nil, err
	}
	private, err := st.GetSetting(ctx, settingPrivateKey)
	if err != nil {
		return nil, err
	}

	if public == "" || private == "" {
		private, public, err = webpush.GenerateVAPIDKeys()
		if err != nil {
			return nil, fmt.Errorf("generate vapid keys: %w", err)
		}
		if err := st.SetSetting(ctx, settingPublicKey, public); err != nil {
			return nil, err
		}
		if err := st.SetSetting(ctx, settingPrivateKey, private); err != nil {
			return nil, err
		}
	}

	if subject == "" {
		subject = "mailto:attackmode@example.com"
	}

	return &Service{
		store:   st,
		public:  public,
		private: private,
		subject: subject,
		client:  &http.Client{Timeout: 15 * time.Second},
	}, nil
}

// PublicKey is the VAPID application server key shared with browsers.
func (s *Service) PublicKey() string {
	return s.public
}

// Payload is the notification content delivered to the service worker.
type Payload struct {
	Title  string `json:"title"`
	Body   string `json:"body"`
	Tag    string `json:"tag"`
	URL    string `json:"url"`
	TaskID int64  `json:"taskId,omitempty"`
}

// Broadcast delivers a payload to every subscription and prunes dead ones.
func (s *Service) Broadcast(ctx context.Context, payload Payload) (int, error) {
	subs, err := s.store.ListSubscriptions(ctx)
	if err != nil {
		return 0, err
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return 0, fmt.Errorf("encode payload: %w", err)
	}

	delivered := 0
	for _, sub := range subs {
		response, err := webpush.SendNotificationWithContext(ctx, body, &webpush.Subscription{
			Endpoint: sub.Endpoint,
			Keys:     webpush.Keys{P256dh: sub.P256dh, Auth: sub.Auth},
		}, &webpush.Options{
			HTTPClient:      s.client,
			Subscriber:      s.subject,
			VAPIDPublicKey:  s.public,
			VAPIDPrivateKey: s.private,
			TTL:             3600,
			Urgency:         webpush.UrgencyHigh,
		})
		if err != nil {
			log.Printf("push send: %v", err)
			continue
		}
		response.Body.Close()

		switch {
		case response.StatusCode == http.StatusNotFound || response.StatusCode == http.StatusGone:
			_ = s.store.DeleteSubscription(ctx, sub.Endpoint)
		case response.StatusCode >= 200 && response.StatusCode < 300:
			delivered++
		default:
			log.Printf("push send: status %d", response.StatusCode)
		}
	}
	return delivered, nil
}

// Run checks for due reminders until the context is cancelled.
func (s *Service) Run(ctx context.Context) {
	ticker := time.NewTicker(tickEvery)
	defer ticker.Stop()

	s.check(ctx, time.Now())
	for {
		select {
		case <-ctx.Done():
			return
		case now := <-ticker.C:
			s.check(ctx, now)
		}
	}
}

func (s *Service) check(ctx context.Context, now time.Time) {
	checkCtx, cancel := context.WithTimeout(ctx, 2*time.Minute)
	defer cancel()

	subs, err := s.store.ListSubscriptions(checkCtx)
	if err != nil || len(subs) == 0 {
		return
	}
	loc, err := time.LoadLocation(subs[0].Timezone)
	if err != nil {
		loc = time.UTC
	}

	local := now.In(loc)
	from := local.AddDate(0, 0, -1).Format("2006-01-02")
	to := local.AddDate(0, 0, 1).Format("2006-01-02")

	tasks, err := s.store.PendingReminders(checkCtx, from, to)
	if err != nil {
		log.Printf("reminders: %v", err)
		return
	}

	for _, task := range tasks {
		due, ok := Moment(task, loc)
		if !ok {
			continue
		}
		fireAt := due.Add(-time.Duration(task.RemindBefore) * time.Minute)
		if now.Before(fireAt) {
			continue
		}
		if now.After(due.Add(staleAfter)) {
			_ = s.store.MarkNotified(checkCtx, task.ID)
			continue
		}

		if _, err := s.Broadcast(checkCtx, ReminderPayload(task, due, now)); err != nil {
			log.Printf("reminders: %v", err)
			continue
		}
		if err := s.store.MarkNotified(checkCtx, task.ID); err != nil {
			log.Printf("reminders: %v", err)
		}
	}
}

// Moment resolves the task date and time in the given location.
func Moment(task store.Task, loc *time.Location) (time.Time, bool) {
	moment, err := time.ParseInLocation("2006-01-02 15:04", task.Date+" "+task.Time, loc)
	if err != nil {
		return time.Time{}, false
	}
	return moment, true
}

// ReminderPayload builds the notification shown for a task.
func ReminderPayload(task store.Task, due, now time.Time) Payload {
	title := "Starting now: " + shorten(task.What, 60)
	if minutes := int(math.Ceil(due.Sub(now).Minutes())); minutes > 0 {
		title = fmt.Sprintf("In %d min: %s", minutes, shorten(task.What, 60))
	}

	details := []string{due.Format("15:04")}
	if task.HowMuch != "" {
		details = append(details, task.HowMuch)
	}
	if task.Where != "" {
		details = append(details, "at "+task.Where)
	}

	return Payload{
		Title:  title,
		Body:   strings.Join(details, " · "),
		Tag:    fmt.Sprintf("task-%d", task.ID),
		URL:    "/day/" + task.Date,
		TaskID: task.ID,
	}
}

func shorten(text string, limit int) string {
	runes := []rune(strings.Join(strings.Fields(text), " "))
	if len(runes) <= limit {
		return string(runes)
	}
	return string(runes[:limit-1]) + "…"
}
