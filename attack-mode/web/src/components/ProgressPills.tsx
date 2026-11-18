import { useEffect } from "react";
import { Flame } from "lucide-react";

import { useAllTasks } from "../hooks/useAllTasks";
import { useToday } from "../hooks/useToday";
import { getExtras, updateExtras } from "../lib/extras";
import { isNative } from "../lib/native";
import { streakInfo, xpInfo } from "../lib/progress";
import { useToast } from "./Toast";

export function ProgressPills() {
  const tasks = useAllTasks();
  const today = useToday();
  const { showToast } = useToast();
  const { level } = xpInfo(tasks);

  useEffect(() => {
    if (!isNative()) return;
    const extras = getExtras();
    if (level <= extras.lastLevel) return;
    if (extras.savedAt) showToast(`LEVEL UP! You reached level ${level}.`, "success");
    updateExtras((draft) => {
      draft.lastLevel = level;
    });
  }, [level, showToast]);

  if (!isNative()) return null;

  const { current } = streakInfo(tasks, today);

  return (
    <div className="progress-pills" aria-label="Your progress">
      <span className="pill pill-level" title="Level">
        LV {level}
      </span>
      <span className="pill pill-streak" title="Day streak">
        <Flame size={12} aria-hidden="true" />
        {current}
      </span>
    </div>
  );
}
