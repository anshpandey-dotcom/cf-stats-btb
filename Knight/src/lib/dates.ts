const pad = (n: number) => String(n).padStart(2, '0');

export function keyFromDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function dateFromKey(key: string): Date {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d, 12, 0, 0, 0);
}

export function addDays(key: string, amount: number): string {
  const date = dateFromKey(key);
  date.setDate(date.getDate() + amount);
  return keyFromDate(date);
}

export function todayKey(offsetDays = 0, now: Date = new Date()): string {
  return addDays(keyFromDate(now), offsetDays);
}

export function formatLong(key: string): string {
  return dateFromKey(key).toLocaleDateString('en-US', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}

export function formatShort(key: string): string {
  return dateFromKey(key).toLocaleDateString('en-US', { day: 'numeric', month: 'short' });
}

export function formatClock(hour: number, minute: number): string {
  const suffix = hour >= 12 ? 'PM' : 'AM';
  const h = hour % 12 === 0 ? 12 : hour % 12;
  return `${h}:${pad(minute)} ${suffix}`;
}
