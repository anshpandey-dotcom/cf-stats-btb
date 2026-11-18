import { useCallback, useEffect, useState } from "react";

import { api } from "../api/client";
import { allTasks } from "../api/local";
import { isNative } from "../lib/native";
import {
  reminderPermission,
  remindersDisabled,
  requestReminderPermission,
  rescheduleReminders,
  sendTestReminder,
  setRemindersDisabled,
} from "../lib/reminders";

export type PushState = "unsupported" | "denied" | "off" | "on";

const supported =
  typeof window !== "undefined" &&
  "serviceWorker" in navigator &&
  "PushManager" in window &&
  "Notification" in window &&
  window.isSecureContext;

function timezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
}

function decodeKey(value: string): Uint8Array<ArrayBuffer> {
  const padded = value + "=".repeat((4 - (value.length % 4)) % 4);
  const raw = atob(padded.replace(/-/g, "+").replace(/_/g, "/"));
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let index = 0; index < raw.length; index += 1) {
    bytes[index] = raw.charCodeAt(index);
  }
  return bytes;
}

async function registration(): Promise<ServiceWorkerRegistration> {
  return navigator.serviceWorker.ready;
}

async function sync(subscription: PushSubscription) {
  const json = subscription.toJSON();
  await api.post("/api/push/subscribe", {
    endpoint: json.endpoint,
    keys: json.keys,
    timezone: timezone(),
  });
}

export function usePush() {
  const native = isNative();
  const [state, setState] = useState<PushState>(native || supported ? "off" : "unsupported");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (native) {
      reminderPermission()
        .then((permission) => {
          const on = permission === "granted" && !remindersDisabled();
          setState(on ? "on" : permission === "denied" ? "denied" : "off");
          if (on) void rescheduleReminders(allTasks());
        })
        .catch(() => undefined);
      return;
    }
    if (!supported) return;
    if (Notification.permission === "denied") {
      setState("denied");
      return;
    }

    let active = true;
    registration()
      .then((reg) => reg.pushManager.getSubscription())
      .then(async (subscription) => {
        if (!active || !subscription) return;
        setState("on");
        await sync(subscription).catch(() => undefined);
      })
      .catch(() => undefined);

    return () => {
      active = false;
    };
  }, [native]);

  const enable = useCallback(async () => {
    if (native) {
      setBusy(true);
      try {
        setRemindersDisabled(false);
        const permission = await requestReminderPermission();
        setState(permission === "granted" ? "on" : "denied");
        if (permission === "granted") await rescheduleReminders(allTasks());
      } finally {
        setBusy(false);
      }
      return;
    }
    if (!supported) return;
    setBusy(true);
    try {
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        setState(permission === "denied" ? "denied" : "off");
        return;
      }

      const reg = await registration();
      const { publicKey } = await api.get<{ publicKey: string }>("/api/push/key");
      const subscription =
        (await reg.pushManager.getSubscription()) ??
        (await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: decodeKey(publicKey),
        }));
      await sync(subscription);
      setState("on");
    } finally {
      setBusy(false);
    }
  }, [native]);

  const disable = useCallback(async () => {
    if (native) {
      setRemindersDisabled(true);
      await rescheduleReminders([]);
      setState("off");
      return;
    }
    if (!supported) return;
    setBusy(true);
    try {
      const reg = await registration();
      const subscription = await reg.pushManager.getSubscription();
      if (subscription) {
        await api.post("/api/push/unsubscribe", { endpoint: subscription.endpoint });
        await subscription.unsubscribe();
      }
      setState("off");
    } finally {
      setBusy(false);
    }
  }, [native]);

  const sendTest = useCallback(
    () => (native ? sendTestReminder() : api.post<{ delivered: number }>("/api/push/test")),
    [native],
  );

  return { state, busy, enable, disable, sendTest };
}
