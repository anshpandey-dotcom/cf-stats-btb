import assert from 'node:assert';
import { encodeAp, decodeAp } from '../src/lib/apCodec';
import { tokenize, alignWords, coverage, typedComplete, typedProgress } from '../src/lib/speechMatch';
import { xpForStreak, currentStreak, longestStreak, totalXp, levelFromXp } from '../src/lib/xp';
import { addDays, todayKey } from '../src/lib/dates';

const data = { a: 1, days: { '2026-01-01': { xp: 5, note: 'नमस्ते ₹ 😀 "q"' } } };
const enc = encodeAp(data);

assert.deepStrictEqual(decodeAp(enc), data);
assert.throws(() => decodeAp(enc.slice(0, 40) + 'x' + enc.slice(41)));
assert.deepStrictEqual(decodeAp('{"x":1}'), { x: 1 });
for (let n = 0; n < 200; n++) { const o = { s: 'x'.repeat(n) }; assert.deepStrictEqual(decodeAp(encodeAp(o)), o); }

const target = tokenize("This is another day of my life, and I can choose to be in the top 1% of the population, or I can be the 99% with a normal, easy life.");
console.log(target.join(' '));
const said = tokenize("this is another day of my life and i can choose to be in the top one percent of the population or i can be the ninety nine percent with a normal easy life");
assert.equal(coverage(alignWords(said, target)), 1);
const half = tokenize("this is another day of my life and i can choose to be in the top one percent");
console.log(coverage(alignWords(half, target)));
assert(typedComplete("this is another day of my life and i can choose to be in the top 1 % of the population or i can be the 99 % with a normal easy life", target));
console.log(typedProgress("this is anotha", target), typedProgress("this is another ", target), typedProgress("this is another d", target));

assert.deepStrictEqual([1,2,3,6,7,13,14,27,28,89,90,200].map(xpForStreak), [5,5,25,25,50,50,100,100,500,500,1000,1000]);
const days: any = {}; let k = '2026-03-01';
for (let i = 0; i < 5; i++) { days[k] = { done: true, xp: 5, streak: i+1, sections: {} }; k = addDays(k, 1); }
assert.equal(currentStreak(days, '2026-03-05'), 5);
assert.equal(currentStreak(days, '2026-03-06'), 5);
assert.equal(currentStreak(days, '2026-03-07'), 0);
assert.equal(longestStreak(days), 5);
assert.equal(addDays('2026-02-28', 1), '2026-03-01');
console.log(levelFromXp(0), levelFromXp(100), levelFromXp(260), todayKey(3));
console.log('ok');
