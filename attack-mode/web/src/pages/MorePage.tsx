import { AlarmClock, BarChart3, BellRing, BookMarked, CalendarDays, History, LineChart, Settings, Sunrise, Target, type LucideIcon } from "lucide-react";
import { Link } from "react-router-dom";

const ITEMS: { to: string; label: string; hint: string; icon: LucideIcon }[] = [
  { to: "/tomorrow", label: "Tomorrow", hint: "Plan the next day before today ends", icon: Sunrise },
  { to: "/insights", label: "Insights", hint: "Charts, best days and what moves your day", icon: LineChart },
  { to: "/woop", label: "WOOP", hint: "Turn a wish into an if-then plan", icon: Target },
  { to: "/manual", label: "Company manual", hint: "Your rules and daily compliance", icon: BookMarked },
  { to: "/nudges", label: "Nudges", hint: "Random reminders, also when you unlock your phone", icon: BellRing },
  { to: "/reminders", label: "Reminders", hint: "Fixed-time alarms that ring when the app is closed", icon: AlarmClock },
  { to: "/history", label: "History", hint: "Every day and journal entry", icon: History },
  { to: "/calendar", label: "Calendar", hint: "Every day at a glance", icon: CalendarDays },
  { to: "/stats", label: "Calibration", hint: "Perceived vs actual difficulty", icon: BarChart3 },
  { to: "/settings", label: "Settings", hint: "Planning targets, habits and export", icon: Settings },
];

export function MorePage() {
  return (
    <div className="page more-page">
      <header className="page-header">
        <span className="eyebrow">Everything else</span>
        <h1>More</h1>
      </header>
      <ul className="more-list">
        {ITEMS.map((item) => (
          <li key={item.to}>
            <Link to={item.to} className="more-link">
              <item.icon size={22} aria-hidden="true" />
              <span>
                <b>{item.label}</b>
                <small>{item.hint}</small>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
