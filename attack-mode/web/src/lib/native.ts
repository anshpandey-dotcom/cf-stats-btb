interface CapacitorGlobal {
  isNativePlatform?: () => boolean;
}

// Detected once at startup, before any Capacitor module can replace the global.
const native =
  typeof window !== "undefined" &&
  Boolean((window as unknown as { Capacitor?: CapacitorGlobal }).Capacitor?.isNativePlatform?.());

if (native) {
  document.documentElement.classList.add("native");
}

export function isNative(): boolean {
  return native;
}
