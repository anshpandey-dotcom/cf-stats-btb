import { motion } from "motion/react";

import { tap } from "../lib/feedback";
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
    <motion.button
      type="button"
      className={`check-cell check-${state ?? "empty"} check-${size}`}
      style={{ "--habit": color } as React.CSSProperties}
      onClick={() => {
        tap();
        onPress();
      }}
      whileTap={{ scale: 0.88 }}
      transition={{ type: "spring", stiffness: 500, damping: 26 }}
      aria-label={label}
      aria-pressed={state === "y"}
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="8.6" strokeWidth="1.7" />
        {state === "y" ? <path d="M8.2 12.6l2.7 2.7 5-5.6" strokeWidth="2.1" /> : null}
        {state === "n" ? <path d="M9 9l6 6M15 9l-6 6" strokeWidth="2" /> : null}
        {state === "s" ? <path d="M8.8 12h6.4" strokeWidth="2" /> : null}
      </svg>
    </motion.button>
  );
}
