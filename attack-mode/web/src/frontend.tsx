import { StrictMode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { App } from "./App";
import { isNative } from "./lib/native";
import { initTheme } from "./lib/theme";

initTheme();

if (isNative()) {
  document.addEventListener("contextmenu", (event) => {
    const target = event.target as HTMLElement | null;
    if (!target?.closest("input, textarea")) event.preventDefault();
  });

  void (async () => {
    await import("./lib/extras");
    const backup = await import("./lib/backup");
    const sheets = await import("./lib/sheetSave");
    const reminders = await import("./lib/reminders");
    const { allTasks } = await import("./api/local");

    if (await backup.syncBackup()) {
      window.location.reload();
      return;
    }

    await reminders.listenForTaps();
    await reminders.rescheduleReminders(allTasks());
    const widgets = await import("./lib/widgets");
    const nudgeNative = await import("./lib/nudgeNative");
    await widgets.refreshWidgets();
    void nudgeNative.syncUnlockNudges();
    await backup.askForBackupAccess();
    sheets.scheduleAutoSave(1500);
    let remindTimer: ReturnType<typeof setTimeout> | undefined;
    let widgetTimer: ReturnType<typeof setTimeout> | undefined;
    window.addEventListener("attack:data-changed", () => {
      sheets.scheduleAutoSave();
      clearTimeout(widgetTimer);
      widgetTimer = setTimeout(() => {
        void widgets.syncWidgets();
        void nudgeNative.syncUnlockNudges();
      }, 800);
      clearTimeout(remindTimer);
      remindTimer = setTimeout(() => void reminders.rescheduleReminders(allTasks()), 1200);
    });

    const { App: NativeApp } = await import("@capacitor/app");
    void NativeApp.addListener("backButton", () => {
      const depth = (window.history.state as { idx?: number } | null)?.idx ?? 0;
      if (depth > 0) window.history.back();
      else if (window.location.pathname !== "/today") window.location.replace("/today");
      else void NativeApp.exitApp();
    });

    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState !== "visible") return;
      void backup.syncBackup().then((restored) => {
        if (restored) window.location.reload();
        else sheets.scheduleAutoSave(1500);
      });
      void widgets.refreshWidgets();
    });
  })();
}

const elem = document.getElementById("root")!;
const app = (
  <StrictMode>
    <App />
  </StrictMode>
);

type HotData = { root?: Root };
((import.meta.hot.data as HotData).root ??= createRoot(elem)).render(app);
