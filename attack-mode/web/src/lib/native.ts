interface CapacitorGlobal {
  isNativePlatform?: () => boolean;
}

// Detected once at startup, before any Capacitor module can replace the global.
const native = Boolean(
  (window as unknown as { Capacitor?: CapacitorGlobal }).Capacitor?.isNativePlatform?.(),
);

export function isNative(): boolean {
  return native;
}
