import { updateExtras } from "./extras";
import type { Nudge } from "./nudges";

export function saveNudge(nudge: Nudge) {
  updateExtras((draft) => {
    const index = draft.nudges.findIndex((item) => item.id === nudge.id);
    if (index >= 0) draft.nudges[index] = nudge;
    else draft.nudges.push(nudge);
    delete draft.nudgeState[nudge.id];
  });
}

export function setNudgeOn(id: string, on: boolean) {
  updateExtras((draft) => {
    const nudge = draft.nudges.find((item) => item.id === id);
    if (nudge) nudge.on = on;
    delete draft.nudgeState[id];
  });
}

export function deleteNudge(id: string) {
  updateExtras((draft) => {
    draft.nudges = draft.nudges.filter((item) => item.id !== id);
    delete draft.nudgeState[id];
  });
}
