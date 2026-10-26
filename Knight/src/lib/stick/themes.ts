export type Theme = { bg: string; ladder: string; decor: 'none' | 'clouds' | 'stars' | 'sun' | 'hills' | 'balloons' | 'moon' };

export const THEMES: Theme[] = [
  { bg: '#F51414', ladder: '#FFFFFF', decor: 'none' },
  { bg: '#2D8CFF', ladder: '#FFFFFF', decor: 'clouds' },
  { bg: '#7B3FE4', ladder: '#FFFFFF', decor: 'stars' },
  { bg: '#FF8A1F', ladder: '#FFFFFF', decor: 'sun' },
  { bg: '#10A37F', ladder: '#FFFFFF', decor: 'hills' },
  { bg: '#E83E8C', ladder: '#FFFFFF', decor: 'balloons' },
  { bg: '#3B4BDB', ladder: '#FFFFFF', decor: 'moon' },
  { bg: '#22B35A', ladder: '#FFFFFF', decor: 'clouds' },
];

export function themeFor(page: number): Theme {
  const i = ((Math.floor(page) % THEMES.length) + THEMES.length) % THEMES.length;
  return THEMES[i];
}

export function mixColor(a: string, b: string, t: number): string {
  const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16));
  const pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16));
  const m = pa.map((v, i) => Math.round(v + (pb[i] - v) * t));
  return `#${m.map((v) => v.toString(16).padStart(2, '0')).join('')}`;
}
