import { StrictMode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { App } from "./App";
import { isNative } from "./lib/native";

if (isNative()) {
  void import("./lib/reminders").then(async (reminders) => {
    const { allTasks } = await import("./api/local");
    await reminders.listenForTaps();
    await reminders.rescheduleReminders(allTasks());
  });
}

const elem = document.getElementById("root")!;
const app = (
  <StrictMode>
    <App />
  </StrictMode>
);

type HotData = { root?: Root };
((import.meta.hot.data as HotData).root ??= createRoot(elem)).render(app);
