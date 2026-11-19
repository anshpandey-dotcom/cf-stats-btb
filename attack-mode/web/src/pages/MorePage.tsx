import { BarChart3, CalendarDays, Settings, Sunrise, type LucideIcon } from "lucide-react";
import { Link } from "react-router-dom";

const ITEMS: { to: string; label: string; hint: string; icon: LucideIcon }[] = [
  { to: "/tomorrow", label: "Tomorrow", hint: "Plan the next day before today ends", icon: Sunrise },
  { to: "/calendar", label: "Calendar", hint: "Every day at a glance", icon: CalendarDays },
  { to: "/stats", label: "Stats", hint: "Completion and difficulty trends", icon: BarChart3 },
  { to: "/settings", label: "Settings", hint: "Theme, habits and export", icon: Settings },
];

export function MorePage() {
  return (
    <div className="page more-page">
      <header className="page-header">
        <span className="eyebrow">Everything else</span>
        <h1>MORE</h1>
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
