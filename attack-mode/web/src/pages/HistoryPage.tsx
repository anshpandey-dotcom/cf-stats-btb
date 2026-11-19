import { useMemo, useState } from "react";
import { BookOpen, Image as ImageIcon } from "lucide-react";
import { Link } from "react-router-dom";

import { PageHeader } from "../components/PageHeader";
import { EmptyState, ErrorState, PageLoader } from "../components/States";
import { useStats } from "../hooks/useTasks";
import { useExtras, entryHasContent } from "../lib/extras";
import { weekdayOf } from "../lib/habits";

const DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];

export function HistoryPage() {
  const stats = useStats();
  const extras = useExtras();
  const [query, setQuery] = useState("");

  const rows = useMemo(() => {
    const planned = new Map((stats.data?.daily ?? []).map((day) => [day.date, day]));
    const dates = new Set<string>(planned.keys());
    Object.keys(extras.journal).forEach((date) => {
      if (entryHasContent(extras.journal[date])) dates.add(date);
    });
    return [...dates]
      .sort()
      .reverse()
      .map((date) => ({
        date,
        day: planned.get(date),
        journal: entryHasContent(extras.journal[date]),
        saved: Boolean(extras.saved[date]),
      }));
  }, [stats.data, extras.journal, extras.saved]);

  if (stats.isLoading) return <PageLoader label="Loading history…" />;
  if (stats.isError) return <ErrorState title="Could not load history" message="Try again in a moment." onRetry={() => void stats.refetch()} />;

  const filtered = rows.filter((row) => !query.trim() || row.date.includes(query.trim()));
  const groups = new Map<string, typeof rows>();
  for (const row of filtered) {
    const key = `${MONTHS[Number(row.date.slice(5, 7)) - 1]} ${row.date.slice(0, 4)}`;
    groups.set(key, [...(groups.get(key) ?? []), row]);
  }

  return (
    <div className="page history-page">
      <PageHeader eyebrow="Everything you've written" title="History" subtitle="Every planned day and journal entry, newest first." />

      <input
        className="search-input"
        type="search"
        inputMode="numeric"
        value={query}
        placeholder="Filter by date, e.g. 2026-11"
        onChange={(event) => setQuery(event.target.value)}
        aria-label="Filter by date"
      />

      {filtered.length === 0 ? (
        <EmptyState title="Nothing here yet" message="Plan a day or write a journal entry and it shows up here." />
      ) : (
        [...groups.entries()].map(([month, list]) => (
          <section key={month} aria-label={month}>
            <h2 className="group-title">{month}</h2>
            <ul className="history-list">
              {list.map((row) => (
                <li key={row.date}>
                  <Link to={`/day/${row.date}`} className="history-row">
                    <div className="history-date">
                      <b>{Number(row.date.slice(8))}</b>
                      <span>{DAYS[weekdayOf(row.date)]}</span>
                    </div>
                    <div className="history-main">
                      {row.day ? (
                        <>
                          <b>
                            {row.day.completed}/{row.day.planned} done
                          </b>
                          <span>
                            PD {row.day.avgPd === null ? "–" : row.day.avgPd.toFixed(1)} · AD{" "}
                            {row.day.avgAd === null ? "–" : row.day.avgAd.toFixed(1)}
                          </span>
                        </>
                      ) : (
                        <b>No tasks</b>
                      )}
                    </div>
                    <div className="history-flags">
                      {row.journal ? <BookOpen size={16} aria-label="Journal entry" /> : null}
                      {row.saved ? <ImageIcon size={16} aria-label="Sheet saved" /> : null}
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}
    </div>
  );
}
