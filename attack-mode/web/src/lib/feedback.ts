import { Haptics, ImpactStyle, NotificationType } from "@capacitor/haptics";

import { isNative } from "./native";

export function tap() {
  if (isNative()) void Haptics.impact({ style: ImpactStyle.Light }).catch(() => undefined);
  else if (navigator.vibrate) navigator.vibrate(8);
}

export function success() {
  if (isNative()) void Haptics.notification({ type: NotificationType.Success }).catch(() => undefined);
  else if (navigator.vibrate) navigator.vibrate([12, 40, 12]);
}

export async function celebrate() {
  try {
    const { default: confetti } = await import("canvas-confetti");
    const colors = ["#ff6b3d", "#32d583", "#5ea8ff", "#f5b942", "#ffffff"];
    void confetti({ particleCount: 110, spread: 75, startVelocity: 42, origin: { y: 0.72 }, colors, disableForReducedMotion: true });
    window.setTimeout(() => void confetti({ particleCount: 60, spread: 110, origin: { y: 0.6 }, colors, disableForReducedMotion: true }), 220);
  } catch {
    // Confetti is a nicety only.
  }
  success();
}
