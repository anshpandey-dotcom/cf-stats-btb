import { Suspense } from "react";
import {
  BookOpen,
  ListChecks,
  Menu,
  LogOut,
  Sun,
  Timer,
  type LucideIcon,
} from "lucide-react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";

import { NotificationButton } from "./NotificationButton";
import { ProgressPills } from "./ProgressPills";
import { PageLoader } from "./States";
import { useLogout } from "../hooks/useAuth";
import { isNative } from "../lib/native";

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
}

const navItems: NavItem[] = [
  { to: "/today", label: "Today", icon: Sun },
  { to: "/habits", label: "Habits", icon: ListChecks },
  { to: "/focus", label: "Focus", icon: Timer },
  { to: "/journal", label: "Journal", icon: BookOpen },
  { to: "/more", label: "More", icon: Menu },
];

const MORE_PATHS = [
  "/more",
  "/tomorrow",
  "/calendar",
  "/stats",
  "/settings",
  "/insights",
  "/history",
  "/woop",
  "/manual",
  "/reminders",
  "/nudges",
];

function itemActive(to: string, pathname: string): boolean {
  if (to === "/today") return pathname === "/" || pathname.startsWith("/today") || pathname.startsWith("/day/");
  if (to === "/more") return MORE_PATHS.some((path) => pathname.startsWith(path));
  return pathname.startsWith(to);
}

export function AppShell() {
  const logout = useLogout();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const handleLogout = () => {
    logout.mutate(undefined, {
      onSettled: () => navigate("/login", { replace: true }),
    });
  };

  return (
    <div className="app-shell">
      <header className="top-bar">
        <Link to="/today" className="brand" aria-label="Attack Mode home">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-name">Attack Mode</span>
        </Link>

        <nav className="desktop-nav" aria-label="Main navigation">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={`desktop-nav-link${itemActive(item.to, pathname) ? " active" : ""}`}
            >
              <item.icon size={16} aria-hidden="true" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <ProgressPills />
        <NotificationButton />

        {isNative() ? null : (
          <button
            type="button"
            className="icon-button logout-button"
            onClick={handleLogout}
            disabled={logout.isPending}
            aria-label="Log out"
            title="Log out"
          >
            <LogOut size={18} />
          </button>
        )}
      </header>

      <main className="app-main">
        <Suspense fallback={<PageLoader label="Loading…" />}>
          <Outlet />
        </Suspense>
      </main>

      <nav className="bottom-nav" aria-label="Main navigation">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={`bottom-nav-link${itemActive(item.to, pathname) ? " active" : ""}`}
          >
            <item.icon size={20} aria-hidden="true" />
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
