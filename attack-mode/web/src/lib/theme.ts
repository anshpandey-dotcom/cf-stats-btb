import { SystemBars, SystemBarsStyle } from "@capacitor/core";

import { isNative } from "./native";

export type ResolvedTheme = "black";

export function resolveTheme(): ResolvedTheme {
  return "black";
}

export function initTheme() {
  document.documentElement.dataset.theme = "black";
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", "#000000");

  if (isNative()) {
    void SystemBars.setStyle({ style: SystemBarsStyle.Dark }).catch(() => undefined);
  }
}
