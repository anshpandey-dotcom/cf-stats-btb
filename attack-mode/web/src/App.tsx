import { lazy, useEffect } from "react";
import { QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";

import { ApiError } from "./api/client";
import { AppShell } from "./components/AppShell";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { ToastProvider } from "./components/Toast";
import { DayPage } from "./pages/DayPage";
import { FocusPage } from "./pages/FocusPage";
import { HabitDetailPage } from "./pages/HabitDetailPage";
import { HistoryPage } from "./pages/HistoryPage";
import { InsightsPage } from "./pages/InsightsPage";
import { ManualPage } from "./pages/ManualPage";
import { HabitsPage } from "./pages/HabitsPage";
import { JournalPage } from "./pages/JournalPage";
import { LoginPage } from "./pages/LoginPage";
import { MorePage } from "./pages/MorePage";
import { NudgesPage } from "./pages/NudgesPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { RemindersPage } from "./pages/RemindersPage";
import { SettingsPage } from "./pages/SettingsPage";
import { WoopPage } from "./pages/WoopPage";
import "./index.css";

const CalendarPage = lazy(() =>
  import("./pages/CalendarPage").then((module) => ({ default: module.CalendarPage })),
);
const StatsPage = lazy(() =>
  import("./pages/StatsPage").then((module) => ({ default: module.StatsPage })),
);

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status < 500) {
          return false;
        }
        return failureCount < 1;
      },
    },
    mutations: {
      retry: 0,
    },
  },
});

function AuthExpiryListener() {
  const client = useQueryClient();

  useEffect(() => {
    const handleExpiry = () => {
      client.setQueryData(["session"], false);
      client.removeQueries({ predicate: (query) => query.queryKey[0] !== "session" });
    };

    window.addEventListener("auth:expired", handleExpiry);
    return () => window.removeEventListener("auth:expired", handleExpiry);
  }, [client]);

  return null;
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <BrowserRouter>
          <AuthExpiryListener />
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<AppShell />}>
                <Route index element={<Navigate to="/today" replace />} />
                <Route path="today" element={<DayPage mode="today" />} />
                <Route path="tomorrow" element={<DayPage mode="tomorrow" />} />
                <Route path="day/:date" element={<DayPage mode="date" />} />
                <Route path="habits" element={<HabitsPage />} />
                <Route path="habits/:id" element={<HabitDetailPage />} />
                <Route path="more" element={<MorePage />} />
                <Route path="insights" element={<InsightsPage />} />
                <Route path="history" element={<HistoryPage />} />
                <Route path="woop" element={<WoopPage />} />
                <Route path="manual" element={<ManualPage />} />
                <Route path="reminders" element={<RemindersPage />} />
                <Route path="nudges" element={<NudgesPage />} />
                <Route path="settings" element={<SettingsPage />} />
                <Route path="focus" element={<FocusPage />} />
                <Route path="journal" element={<JournalPage />} />
                <Route path="calendar" element={<CalendarPage />} />
                <Route path="stats" element={<StatsPage />} />
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Route>
          </Routes>
        </BrowserRouter>
      </ToastProvider>
    </QueryClientProvider>
  );
}
