interface PixelBarProps {
  value: number;
  max: number;
  blocks?: number;
  tone?: "accent" | "good";
  label: string;
}

export function PixelBar({ value, max, blocks = 20, tone = "accent", label }: PixelBarProps) {
  const filled = max <= 0 ? 0 : Math.min(blocks, Math.round((value / max) * blocks));
  return (
    <div
      className={`pixel-bar pixel-bar-${tone}`}
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={Math.min(value, max)}
    >
      {Array.from({ length: blocks }, (_, index) => (
        <i key={index} className={index < filled ? "on" : ""} />
      ))}
    </div>
  );
}
