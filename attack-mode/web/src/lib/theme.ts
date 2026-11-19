import { SystemBars, SystemBarsStyle } from "@capacitor/core";
import { useEffect } from "react";

import { getExtras, useExtras, type ThemeMode } from "./extras";
import { isNative } from "./native";

const BLACK = "#000000";
const PAPER = "#f5f1e8";

export type ResolvedTheme = "light" | "black";

export function resolveTheme(mode: ThemeMode): ResolvedTheme {
  if (mode === "auto") {
    return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "black" : "light";
  }
  return mode;
}

export function applyTheme(mode: ThemeMode) {
  const resolved = resolveTheme(mode);
  document.documentElement.dataset.theme = resolved;

  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", resolved === "black" ? BLACK : PAPER);

  if (isNative()) {
    void SystemBars.setStyle({
      style: resolved === "black" ? SystemBarsStyle.Dark : SystemBarsStyle.Light,
    }).catch(() => undefined);
  }
}

export function initTheme() {
  applyTheme(getExtras().theme);
  window.matchMedia?.("(prefers-color-scheme: dark)").addEventListener("change", () => {
    applyTheme(getExtras().theme);
  });
}

export function useThemeEffect() {
  const { theme } = useExtras();
  useEffect(() => {
    applyTheme(theme);
  }, [theme]);
}
