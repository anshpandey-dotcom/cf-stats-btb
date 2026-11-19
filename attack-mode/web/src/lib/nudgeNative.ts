import { registerPlugin } from "@capacitor/core";

import { getExtras } from "./extras";
import { isNative } from "./native";
import type { Nudge } from "./nudges";
import { unlockConfig } from "./nudges";

interface NudgesPlugin {
  configure(options: { json: string }): Promise<{ unlock: boolean }>;
  showNow(options: { title: string; body: string; url: string }): Promise<void>;
}

const Nudges = registerPlugin<NudgesPlugin>("Nudges");

export async function syncUnlockNudges(): Promise<boolean> {
  if (!isNative()) return false;
  try {
    return (await Nudges.configure({ json: unlockConfig(getExtras().nudges) })).unlock;
  } catch (error) {
    console.warn("Could not update unlock nudges:", error);
    return false;
  }
}

export async function testNudge(nudge: Nudge): Promise<void> {
  const pool = nudge.messages.length > 0 ? nudge.messages : ["Check your tasks."];
  const body = pool[Math.floor(Math.random() * pool.length)]!;
  await Nudges.showNow({ title: nudge.name || "Attack Mode", body, url: nudge.url || "/today" });
}
