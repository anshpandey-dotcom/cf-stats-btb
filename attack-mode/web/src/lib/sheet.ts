import type { Task } from "../api/types";
import { nextDayGoal } from "../components/NextGoalCard";
import type { JournalEntry } from "./extras";
import { JOURNAL_PROMPTS } from "./extras";
import { encodeIndexedPng, type Rgb } from "./png";
import { streakInfo, xpInfo } from "./progress";

export interface SheetModel {
  date: string;
  tasks: Task[];
  entry: JournalEntry;
  allTasks: Task[];
  today: string;
}

const PAPER: Rgb = [243, 239, 230];
const CARD: Rgb = [255, 253, 248];
const INK: Rgb = [25, 24, 22];
const MUTED: Rgb = [117, 110, 97];
const LINE: Rgb = [185, 174, 156];
const ACCENT: Rgb = [184, 59, 30];
const SOFT: Rgb = [245, 217, 206];
const GOOD: Rgb = [47, 111, 78];

export const SHEET_PALETTE: Rgb[] = [PAPER, CARD, INK, MUTED, LINE, ACCENT, SOFT, GOOD];

const WIDTH = 720;
const MARGIN = 28;
const PIXEL_FONT = "Silkscreen, ui-monospace, monospace";
const BODY_FONT = "ui-sans-serif, system-ui, sans-serif";

function css(color: Rgb): string {
  return `rgb(${color[0]},${color[1]},${color[2]})`;
}

function wrap(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const lines: string[] = [];
  for (const paragraph of text.split(/\r?\n/)) {
    const words = paragraph.split(/\s+/).filter(Boolean);
    let line = "";
    for (const word of words) {
      const test = line ? `${line} ${word}` : word;
      if (ctx.measureText(test).width <= maxWidth || !line) {
        line = test;
      } else {
        lines.push(line);
        line = word;
      }
    }
    lines.push(line);
  }
  return lines;
}

function dateLabel(key: string): string {
  const [year, month, day] = key.split("-").map(Number) as [number, number, number];
  return new Date(Date.UTC(year, month - 1, day))
    .toLocaleDateString("en-US", {
      weekday: "long",
      month: "long",
      day: "numeric",
      year: "numeric",
      timeZone: "UTC",
    })
    .toUpperCase();
}

function taskMeta(task: Task): string {
  return [task.howMuch, [task.time, task.when].filter(Boolean).join(" "), task.where]
    .filter(Boolean)
    .join("  ·  ");
}

export function sheetHash(model: SheetModel): string {
  const text = JSON.stringify([
    model.date,
    model.tasks.map((task) => [
      task.what,
      task.howMuch,
      task.when,
      task.where,
      task.time,
      task.perceivedDifficulty,
      task.actualDifficulty,
      task.completed,
    ]),
    model.entry,
  ]);
  let hash = 0x811c9dc5;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16);
}

function draw(ctx: CanvasRenderingContext2D, model: SheetModel, height: number | null): number {
  const inner = WIDTH - MARGIN * 2;
  const info = xpInfo(model.allTasks);
  const streak = streakInfo(model.allTasks, model.today);
  const goal = nextDayGoal(model.tasks);
  let y = 0;

  const rect = (x: number, top: number, w: number, h: number, fill: Rgb) => {
    ctx.fillStyle = css(fill);
    ctx.fillRect(x, top, w, h);
  };
  const box = (x: number, top: number, w: number, h: number, fill: Rgb, edge: Rgb) => {
    rect(x, top, w, h, edge);
    rect(x + 2, top + 2, w - 4, h - 4, fill);
  };

  if (height !== null) rect(0, 0, WIDTH, height, PAPER);

  rect(0, 0, WIDTH, 92, INK);
  ctx.fillStyle = css(PAPER);
  ctx.font = `700 30px ${PIXEL_FONT}`;
  ctx.textBaseline = "alphabetic";
  ctx.fillText("ATTACK DAY", MARGIN, 46);
  ctx.font = `700 12px ${PIXEL_FONT}`;
  ctx.fillStyle = css(LINE);
  ctx.fillText(dateLabel(model.date), MARGIN, 74);
  ctx.textAlign = "right";
  ctx.fillStyle = css(PAPER);
  ctx.font = `700 14px ${PIXEL_FONT}`;
  ctx.fillText(`LV ${info.level}`, WIDTH - MARGIN, 40);
  ctx.fillStyle = css(LINE);
  ctx.font = `700 11px ${PIXEL_FONT}`;
  ctx.fillText(`STREAK ${streak.current}   XP ${info.xp}`, WIDTH - MARGIN, 66);
  ctx.textAlign = "left";
  y = 92;
  rect(0, y, WIDTH, 6, ACCENT);
  y += 30;

  ctx.fillStyle = css(MUTED);
  ctx.font = `700 12px ${PIXEL_FONT}`;
  const done = model.tasks.filter((task) => task.completed).length;
  ctx.fillText(`TASKS  ${done}/${model.tasks.length}`, MARGIN, y);
  y += 16;

  if (model.tasks.length === 0) {
    ctx.fillStyle = css(MUTED);
    ctx.font = `14px ${BODY_FONT}`;
    ctx.fillText("No tasks on this day.", MARGIN, y + 20);
    y += 40;
  }

  for (const task of model.tasks) {
    ctx.font = `600 16px ${BODY_FONT}`;
    const titleLines = wrap(ctx, task.what, inner - 56 - 168).slice(0, 3);
    const meta = taskMeta(task);
    ctx.font = `13px ${BODY_FONT}`;
    const metaLines = meta ? wrap(ctx, meta, inner - 56 - 168).slice(0, 2) : [];
    const rowHeight = Math.max(52, 20 + titleLines.length * 21 + metaLines.length * 18);

    box(MARGIN, y, inner, rowHeight, CARD, LINE);
    box(MARGIN + 14, y + 14, 22, 22, task.completed ? GOOD : CARD, INK);
    if (task.completed) {
      ctx.fillStyle = css(CARD);
      ctx.font = `700 16px ${PIXEL_FONT}`;
      ctx.fillText("✓", MARGIN + 19, y + 31);
    }

    const textX = MARGIN + 52;
    let ty = y + 26;
    ctx.fillStyle = css(INK);
    ctx.font = `600 16px ${BODY_FONT}`;
    for (const line of titleLines) {
      ctx.fillText(line, textX, ty);
      ty += 21;
    }
    ctx.fillStyle = css(MUTED);
    ctx.font = `13px ${BODY_FONT}`;
    for (const line of metaLines) {
      ctx.fillText(line, textX, ty - 2);
      ty += 18;
    }

    const badgeX = MARGIN + inner - 156;
    const hard = task.perceivedDifficulty >= 7;
    box(badgeX, y + 12, 70, 28, hard ? SOFT : PAPER, hard ? ACCENT : LINE);
    ctx.fillStyle = css(hard ? ACCENT : INK);
    ctx.font = `700 11px ${PIXEL_FONT}`;
    ctx.fillText(`PD ${task.perceivedDifficulty}/10`, badgeX + 8, y + 31);
    box(badgeX + 78, y + 12, 70, 28, PAPER, LINE);
    ctx.fillStyle = css(task.actualDifficulty === null ? MUTED : INK);
    ctx.fillText(
      task.actualDifficulty === null ? "AD --" : `AD ${task.actualDifficulty}/10`,
      badgeX + 86,
      y + 31,
    );
    y += rowHeight + 10;
  }

  y += 10;
  box(MARGIN, y, inner, 46, SOFT, ACCENT);
  ctx.fillStyle = css(ACCENT);
  ctx.font = `700 13px ${PIXEL_FONT}`;
  ctx.fillText("NEXT DAY ATTACK GOAL", MARGIN + 16, y + 29);
  ctx.textAlign = "right";
  ctx.font = `700 22px ${PIXEL_FONT}`;
  ctx.fillText(`${goal ? goal.goal : 7}/10`, MARGIN + inner - 16, y + 31);
  ctx.textAlign = "left";
  y += 46 + 28;

  const factors = model.entry.factors;
  const factorText = [
    factors.sleep !== undefined ? `SLEEP ${factors.sleep}H` : "",
    factors.sleepQuality !== undefined ? `SLEEP QUALITY ${factors.sleepQuality}/5` : "",
    factors.diet !== undefined ? `DIET ${factors.diet}/5` : "",
    factors.activity !== undefined ? `ACTIVITY ${factors.activity} MIN` : "",
  ]
    .filter(Boolean)
    .join("   ");
  if (factorText) {
    ctx.fillStyle = css(MUTED);
    ctx.font = `700 11px ${PIXEL_FONT}`;
    ctx.fillText(factorText, MARGIN, y);
    y += 26;
  }

  const answered = JOURNAL_PROMPTS.map((prompt, index) => ({
    prompt,
    answer: (model.entry.answers[index] ?? "").trim(),
  })).filter((item) => item.answer);
  if (model.entry.note.trim()) answered.push({ prompt: "Note", answer: model.entry.note.trim() });

  if (answered.length > 0) {
    ctx.fillStyle = css(INK);
    ctx.font = `700 14px ${PIXEL_FONT}`;
    ctx.fillText("FEEDBACK JOURNAL", MARGIN, y);
    y += 10;
    rect(MARGIN, y, inner, 2, INK);
    y += 22;

    for (const item of answered) {
      ctx.fillStyle = css(ACCENT);
      ctx.font = `700 10px ${PIXEL_FONT}`;
      ctx.fillText(item.prompt.toUpperCase().slice(0, 64), MARGIN, y);
      y += 20;
      ctx.fillStyle = css(INK);
      ctx.font = `15px ${BODY_FONT}`;
      for (const line of wrap(ctx, item.answer, inner)) {
        ctx.fillText(line, MARGIN, y);
        y += 22;
      }
      y += 12;
    }
  }

  y += 6;
  ctx.fillStyle = css(MUTED);
  ctx.font = `700 9px ${PIXEL_FONT}`;
  ctx.fillText(`ATTACK MODE  ·  ${model.date}`, MARGIN, y + 8);
  return y + 30;
}

export async function renderSheetPng(model: SheetModel): Promise<Uint8Array> {
  if (document.fonts?.load) {
    await Promise.all([
      document.fonts.load(`700 30px Silkscreen`),
      document.fonts.load(`16px ui-sans-serif`),
    ]).catch(() => undefined);
  }

  const measure = document.createElement("canvas");
  measure.width = WIDTH;
  measure.height = 10;
  const height = Math.ceil(draw(measure.getContext("2d")!, model, null));

  const canvas = document.createElement("canvas");
  canvas.width = WIDTH;
  canvas.height = height;
  const ctx = canvas.getContext("2d", { willReadFrequently: true })!;
  draw(ctx, model, height);

  const pixels = ctx.getImageData(0, 0, WIDTH, height);
  return encodeIndexedPng(pixels, SHEET_PALETTE);
}
