import { Check, Minus, X } from "lucide-react";

import type { CheckState } from "../lib/habits";

interface CheckCellProps {
  state: CheckState | undefined;
  color: string;
  label: string;
  onPress: () => void;
  size?: "md" | "sm";
}

export function CheckCell({ state, color, label, onPress, size = "md" }: CheckCellProps) {
  return (
    <button
      type="button"
      className={`check-cell check-${state ?? "empty"} check-${size}`}
      style={{ "--habit": color } as React.CSSProperties}
      onClick={onPress}
      aria-label={label}
      aria-pressed={state === "y"}
    >
      {state === "y" ? <Check size={size === "sm" ? 14 : 20} strokeWidth={3} /> : null}
      {state === "n" ? <X size={size === "sm" ? 14 : 18} strokeWidth={3} /> : null}
      {state === "s" ? <Minus size={size === "sm" ? 14 : 18} strokeWidth={3} /> : null}
    </button>
  );
}
