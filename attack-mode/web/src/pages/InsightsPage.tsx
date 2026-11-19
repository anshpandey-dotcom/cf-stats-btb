import { useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { PageHeader } from "../components/PageHeader";
import { ErrorState, PageLoader } from "../components/States";
import { useStats } from "../hooks/useTasks";
import { useToday } from "../hooks/useToday";
import { useExtras } from "../lib/extras";
import { shiftDay, weekdayOf } from "../lib/habits";

const RANGES = [14, 30, 90] as const;
const LETTERS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const tooltipStyle = {
  background: "#16161a",
  border: "1px solid #2e2e35",
  borderRadius: 10,
  color: "#f5f5f7",
  fontSize: 12,
};

interface Factor {
  label: string;
  yes: number | null;
  no: number | null;
  yesDays: number;
  noDays: number;
}

function average(values: number[]): number | null {
  return values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length;
}

export function InsightsPage() {
  const today = useToday();
  const extras = useExtras();
  const [range, setRange] = useState<(typeof RANGES)[number]>(30);
  const from = shiftDay(today, -(range - 1));
  const stats = useStats(from, today);

  const days = useMemo(() => {
    const map = new Map((stats.data?.daily ?? []).map((day) => [day.date, day]));
    return map;
  }, [stats.data]);

  const completion = useMemo(() => {
    const rows: { date: string; label: string; rate: number | null; pd: number | null; ad: number | null }[] = [];
    for (let offset = range - 1; offset >= 0; offset -= 1) {
      const date = shiftDay(today, -offset);
      const day = days.get(date);
      rows.push({
        date,
        label: `${Number(date.slice(8))}/${Number(date.slice(5, 7))}`,
        rate: day && day.planned > 0 ? Math.round((day.completed / day.planned) * 100) : null,
        pd: day?.avgPd ?? null,
        ad: day?.avgAd ?? null,
      });
    }
    return rows;
  }, [days, range, today]);

  const weekday = useMemo(() => {
    const buckets: number[][] = [[], [], [], [], [], [], []];
    for (const row of completion) if (row.rate !== null) buckets[weekdayOf(row.date)]!.push(row.rate);
    return buckets.map((values, day) => ({ label: LETTERS[day]!, rate: Math.round(average(values) ?? 0), days: values.length }));
  }, [completion]);

  const factors = useMemo<Factor[]>(() => {
    const rateOf = (date: string) => completion.find((row) => row.date === date)?.rate ?? null;
    const defs: { label: string; pick: (date: string) => boolean | null }[] = [
      {
        label: "Slept 7+ hours",
        pick: (date) => {
          const value = extras.journal[date]?.factors.sleep;
          return value === undefined ? null : value >= 7;
        },
      },
      {
        label: "Good sleep quality (4+)",
        pick: (date) => {
          const value = extras.journal[date]?.factors.sleepQuality;
          return value === undefined ? null : value >= 4;
        },
      },
      {
        label: "Good diet (4+)",
        pick: (date) => {
          const value = extras.journal[date]?.factors.diet;
          return value === undefined ? null : value >= 4;
        },
      },
      {
        label: "Active 30+ minutes",
        pick: (date) => {
          const value = extras.journal[date]?.factors.activity;
          return value === undefined ? null : value >= 30;
        },
      },
    ];

    return defs.map((def) => {
      const yes: number[] = [];
      const no: number[] = [];
      for (const row of completion) {
        const rate = rateOf(row.date);
        const group = def.pick(row.date);
        if (rate === null || group === null) continue;
        (group ? yes : no).push(rate);
      }
      return { label: def.label, yes: average(yes), no: average(no), yesDays: yes.length, noDays: no.length };
    });
  }, [completion, extras.journal]);

  if (stats.isLoading) return <PageLoader label="Loading insights…" />;
  if (stats.isError) return <ErrorState title="Could not load insights" message="Try again in a moment." onRetry={() => void stats.refetch()} />;

  const summary = stats.data!.summary;
  const target = extras.prefs.targetDifficulty;
  const hasData = summary.planned > 0;

  return (
    <div className="page insights-page">
      <PageHeader eyebrow="Track, reflect, adjust" title="Insights" subtitle="What your numbers say about how you work." />

      <div className="mode-pick" role="group" aria-label="Range">
        {RANGES.map((value) => (
          <button key={value} type="button" className={range === value ? "on" : ""} aria-pressed={range === value} onClick={() => setRange(value)}>
            {value} days
          </button>
        ))}
      </div>

      <section className="stat-tiles" aria-label="Summary">
        <div className="stat-tile">
          <b>{summary.completed}</b>
          <span>Done</span>
        </div>
        <div className="stat-tile">
          <b>{Math.round(summary.completionRate * 100)}%</b>
          <span>Completion</span>
        </div>
        <div className="stat-tile">
          <b>{summary.avgAd === null ? "–" : summary.avgAd.toFixed(1)}</b>
          <span>Avg AD</span>
        </div>
        <div className="stat-tile">
          <b>{summary.gap === null ? "–" : `${summary.gap > 0 ? "+" : ""}${summary.gap.toFixed(1)}`}</b>
          <span>AD vs PD</span>
        </div>
      </section>

      {!hasData ? (
        <div className="notice">Plan and finish a few tasks to see your charts.</div>
      ) : (
        <>
          <section className="panel" aria-label="Completion">
            <h2 className="panel-title">Daily completion</h2>
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={completion} margin={{ top: 8, right: 6, bottom: 0, left: 4 }}>
                  <CartesianGrid stroke="#1f1f24" vertical={false} />
                  <XAxis dataKey="label" tickLine={false} axisLine={false} interval="preserveStartEnd" />
                  <YAxis domain={[0, 100]} tickLine={false} axisLine={false} width={46} ticks={[0, 50, 100]} unit="%" />
                  <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "#16161a" }} formatter={(value) => [`${value}%`, "Done"]} />
                  <ReferenceLine y={extras.prefs.winThreshold} stroke="#32d583" strokeDasharray="4 4" />
                  <Bar dataKey="rate" fill="#ff6b3d" radius={[5, 5, 0, 0]} isAnimationActive={false} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <p className="panel-note">The green line is your win threshold ({extras.prefs.winThreshold}%).</p>
          </section>

          <section className="panel" aria-label="Difficulty">
            <h2 className="panel-title">Perceived vs actual difficulty</h2>
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={completion} margin={{ top: 8, right: 6, bottom: 0, left: 4 }}>
                  <CartesianGrid stroke="#1f1f24" vertical={false} />
                  <XAxis dataKey="label" tickLine={false} axisLine={false} interval="preserveStartEnd" />
                  <YAxis domain={[0, 10]} tickLine={false} axisLine={false} width={46} ticks={[0, 5, 10]} />
                  <Tooltip contentStyle={tooltipStyle} formatter={(value, name) => [Number(value).toFixed(1), name === "pd" ? "Perceived" : "Actual"]} />
                  <ReferenceLine y={target} stroke="#f5b942" strokeDasharray="4 4" />
                  <Line type="monotone" dataKey="pd" stroke="#8e8e93" strokeWidth={2} dot={false} connectNulls isAnimationActive={false} />
                  <Line type="monotone" dataKey="ad" stroke="#ff6b3d" strokeWidth={2.5} dot={false} connectNulls isAnimationActive={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
            <p className="panel-note">
              Grey is how hard it felt before, orange is how hard it was. The yellow line is your {target}/10 target.
            </p>
          </section>

          <section className="panel" aria-label="Weekday">
            <h2 className="panel-title">Best days of the week</h2>
            <div className="chart-box">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={weekday} margin={{ top: 8, right: 6, bottom: 0, left: 4 }}>
                  <CartesianGrid stroke="#1f1f24" vertical={false} />
                  <XAxis dataKey="label" tickLine={false} axisLine={false} />
                  <YAxis domain={[0, 100]} tickLine={false} axisLine={false} width={46} ticks={[0, 50, 100]} unit="%" />
                  <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "#16161a" }} formatter={(value) => [`${value}%`, "Avg done"]} />
                  <Bar dataKey="rate" fill="#5ea8ff" radius={[5, 5, 0, 0]} isAnimationActive={false} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>
        </>
      )}

      <section className="panel" aria-label="Factors">
        <h2 className="panel-title">What moves your day</h2>
        <p className="panel-note">Average completion on days with and without each habit. Needs journal days.</p>
        <ul className="factor-list">
          {factors.map((factor) => {
            const ready = factor.yes !== null && factor.no !== null && factor.yesDays >= 2 && factor.noDays >= 2;
            const diff = ready ? Math.round((factor.yes ?? 0) - (factor.no ?? 0)) : null;
            return (
              <li key={factor.label}>
                <div className="factor-line">
                  <span>{factor.label}</span>
                  <b className={diff === null ? "" : diff >= 0 ? "good" : "bad"}>
                    {diff === null ? "Not enough days" : `${diff >= 0 ? "+" : ""}${diff} pts`}
                  </b>
                </div>
                {ready ? (
                  <div className="factor-bars">
                    <div style={{ width: `${Math.round(factor.yes ?? 0)}%` }} className="with">
                      With {Math.round(factor.yes ?? 0)}%
                    </div>
                    <div style={{ width: `${Math.round(factor.no ?? 0)}%` }} className="without">
                      Without {Math.round(factor.no ?? 0)}%
                    </div>
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      </section>
    </div>
  );
}
