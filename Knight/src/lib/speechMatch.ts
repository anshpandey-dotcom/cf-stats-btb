const ONES = [
  'zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten',
  'eleven', 'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen', 'eighteen', 'nineteen',
];
const TENS = ['', '', 'twenty', 'thirty', 'forty', 'fifty', 'sixty', 'seventy', 'eighty', 'ninety'];

function numberWords(n: number): string {
  if (n < 20) return ONES[n];
  if (n < 100) return TENS[Math.floor(n / 10)] + (n % 10 ? ` ${ONES[n % 10]}` : '');
  if (n < 1000) {
    const rest = n % 100;
    return `${ONES[Math.floor(n / 100)]} hundred${rest ? ` ${numberWords(rest)}` : ''}`;
  }
  return String(n);
}

export function tokenize(text: string): string[] {
  const cleaned = text
    .toLowerCase()
    .replace(/%/g, ' percent ')
    .replace(/[’‘`']/g, '')
    .replace(/[-–—/]/g, ' ')
    .replace(/\d+/g, (digits) => ` ${numberWords(parseInt(digits, 10))} `)
    .replace(/[^a-z\s]/g, ' ');
  return cleaned.split(/\s+/).filter(Boolean);
}

function distance(a: string, b: string): number {
  if (a === b) return 0;
  const prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let diagonal = prev[0];
    prev[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const temp = prev[j];
      prev[j] = Math.min(prev[j] + 1, prev[j - 1] + 1, diagonal + (a[i - 1] === b[j - 1] ? 0 : 1));
      diagonal = temp;
    }
  }
  return prev[b.length];
}

const ALIASES: Record<string, string[]> = {
  one: ['won'],
  percent: ['person', 'percentage', 'percents', 'percentile', 'per', 'cent', 'parson'],
  population: ['populations'],
  businessman: ['businessmen', 'busyman'],
  athlete: ['athletes', 'athletic'],
  leader: ['leaders'],
  mentality: ['mentally'],
  warrior: ['warriors', 'worrier'],
};

export function spokenTokens(text: string): string[] {
  const joined = tokenize(text)
    .join(' ')
    .replace(/\bper cent\b/g, 'percent')
    .replace(/\bbusiness man\b/g, 'businessman')
  return joined.split(' ').filter(Boolean);
}

export function wordsMatch(spoken: string, target: string): boolean {
  if (spoken === target) return true;
  if (ALIASES[target]?.includes(spoken)) return true;
  const longest = Math.max(spoken.length, target.length);
  if (longest < 5) return false;
  return distance(spoken, target) <= (longest >= 9 ? 2 : 1);
}

export function alignWords(spoken: string[], target: string[]): boolean[] {
  const n = spoken.length;
  const m = target.length;
  const table: number[][] = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      table[i][j] = wordsMatch(spoken[i - 1], target[j - 1])
        ? table[i - 1][j - 1] + 1
        : Math.max(table[i - 1][j], table[i][j - 1]);
    }
  }
  const matched = new Array(m).fill(false);
  let i = n;
  let j = m;
  while (i > 0 && j > 0) {
    if (wordsMatch(spoken[i - 1], target[j - 1]) && table[i][j] === table[i - 1][j - 1] + 1) {
      matched[j - 1] = true;
      i -= 1;
      j -= 1;
    } else if (table[i - 1][j] >= table[i][j - 1]) {
      i -= 1;
    } else {
      j -= 1;
    }
  }
  return matched;
}

export function coverage(matched: boolean[]): number {
  if (matched.length === 0) return 0;
  return matched.filter(Boolean).length / matched.length;
}

export function typedProgress(typed: string, target: string[]): { correct: number; mistake: boolean } {
  const typedChars = tokenize(typed).join('');
  const targetChars = target.join('');
  let match = 0;
  while (match < typedChars.length && match < targetChars.length && typedChars[match] === targetChars[match]) {
    match += 1;
  }
  const mistake = match < typedChars.length;
  let correct = 0;
  let offset = 0;
  for (const word of target) {
    if (offset + word.length <= match) {
      correct += 1;
      offset += word.length;
    } else {
      break;
    }
  }
  return { correct, mistake };
}

export function typedComplete(typed: string, target: string[]): boolean {
  return tokenize(typed).join('') === target.join('');
}
