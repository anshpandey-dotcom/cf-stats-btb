import { useAllTasks } from "../hooks/useAllTasks";
import { isNative } from "../lib/native";
import { XP_PER_LEVEL, xpInfo } from "../lib/progress";
import { PixelBar } from "./PixelBar";

export function XpCard() {
  const tasks = useAllTasks();
  if (!isNative()) return null;

  const { level, into, xp } = xpInfo(tasks);
  return (
    <section className="xp-card" aria-label="Experience">
      <div className="xp-head">
        <span className="xp-level">LEVEL {level}</span>
        <span className="xp-count">
          {into}/{XP_PER_LEVEL} XP
        </span>
      </div>
      <PixelBar value={into} max={XP_PER_LEVEL} label="Experience to next level" />
      <p className="xp-note">
        {xp === 0
          ? "Finish tasks to earn XP. Hard tasks give the most."
          : "Hard tasks +30, medium +20, easy +10."}
      </p>
    </section>
  );
}
