import assert from 'node:assert';
import { encodeAp, decodeAp } from '../src/lib/apCodec';
import { tokenize, alignWords, coverage, typedComplete, typedProgress } from '../src/lib/speechMatch';
import { advance, startRun, baseXpForRung, workMinutes, upcomingBoundaries } from '../src/lib/pomodoro';
import { spokenTokens } from '../src/lib/speechMatch';
import { levelGoal, scaledXp, xpForStreak, currentStreak, longestStreak, totalXp, levelFromXp } from '../src/lib/xp';
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

const t2 = tokenize("top 1% of the population or the 99% with");
for (const typed of ['top 1% of the population or the 99% with', 'top one percent of the population or the 99 percent with', 'top 1 % of the population or the ninety nine% with', 'top1%of the population or the 99% with']) {
  assert(typedComplete(typed, t2), typed);
}
assert.equal(typedProgress('top 1', t2).mistake, false);
assert.equal(typedProgress('top 1%', t2).correct, 3);
assert.equal(typedProgress('top 2', t2).mistake, true);
assert.equal(typedProgress('top on', t2).mistake, false);
const heard = spokenTokens('this is the top one person of the population or the business man ninety nine per cent');
const tt = tokenize('this is the top 1% of the population or the businessman 99%');
assert.equal(coverage(alignWords(heard, tt)), 1);
assert.equal(coverage(alignWords(spokenTokens('top 1% eating of the population'), tokenize('top 1% of the population'))), 1);

assert.deepStrictEqual([1,2,3,4,5,6,10].map(levelGoal), [10,33,55,78,100,130,250]);
assert.equal(scaledXp(5, 1), 5);
assert.equal(scaledXp(10, 11), 15);

const t0 = 1_000_000;
let run = startRun(t0);
assert.equal(workMinutes(0), 15);
let step = advance(run, t0 + 14 * 60000);
assert.equal(step.events.length, 0);
step = advance(run, t0 + 15 * 60000);
assert.equal(step.events.length, 1);
assert.equal(step.state.phase, 'break');
step = advance(run, t0 + (15 + 5 + 25 + 5) * 60000 + 1);
assert.deepStrictEqual(step.events.map((e) => e.type), ['block', 'break-end', 'block', 'break-end']);
assert.equal(step.state.rung, 2);
assert.equal(step.state.phase, 'work');
assert.deepStrictEqual([0,1,2,3,4,9].map(workMinutes), [15,25,40,60,60,60]);
assert.deepStrictEqual([0,1,2].map(baseXpForRung), [5,10,15]);
assert.equal(upcomingBoundaries(run, 3).length, 3);

import { planBlock, PAGE, cameraAt, altitudeAt, segmentAt } from '../src/lib/stick/plan';
import { climbPose } from '../src/lib/stick/pose';
for (const seed of [1, 7, 99, 12345]) {
  for (const minutes of [15, 25, 40, 60]) {
    const plan = planBlock(seed, minutes * 60, 10);
    let prev = plan.segs[0];
    assert.equal(prev.t0, 0);
    for (const seg of plan.segs.slice(1)) {
      assert(Math.abs(seg.t0 - prev.t1) < 1e-9, 'segments contiguous');
      assert.equal(seg.g0, prev.g1);
      prev = seg;
    }
    assert(Math.abs(prev.t1 - minutes * 60) < 1e-6);
    for (const seg of plan.segs) {
      if (seg.kind === 'climb') {
        assert(Number.isInteger(seg.g0) && Number.isInteger(seg.g1));
        assert(Math.floor(seg.g0 / PAGE) === Math.floor((seg.g1 - 1) / PAGE), 'climb stays within a page');
      }
    }
    for (let t = 0; t < minutes * 60; t += 7.3) {
      const a = altitudeAt(plan, t);
      const b = altitudeAt(plan, Math.min(t + 0.05, minutes * 60));
      assert(b >= a - 1e-9, 'altitude never decreases');
      const c1 = cameraAt(plan, t).page;
      const c2 = cameraAt(plan, Math.min(t + 0.05, minutes * 60)).page;
      assert(c2 >= c1 - 1e-9 && c2 - c1 < 0.2, 'camera pans smoothly');
    }
    assert(plan.endAlt > 10);
  }
}
const dim = { g: 50, cx: 195, W: 390 };
const yOf = (a: number) => 600 - a * 50;
for (let alt = 0; alt <= 6; alt++) {
  const p = climbPose({ alt, y: yOf, dim, time: 0 });
  const hands = [p.hdL.y, p.hdR.y].map((y) => (600 - y) / 50);
  const feet = [p.ftL.y, p.ftR.y].map((y) => (600 - y) / 50);
  for (const h of [...hands, ...feet]) assert(Math.abs(h - Math.round(h)) < 0.35, 'limbs land on rungs at integer altitude ' + h);
}
console.log('ok');
