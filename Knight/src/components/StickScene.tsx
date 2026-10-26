import React, { useEffect, useMemo, useState } from 'react';
import Svg, { Circle, Line, Path, Polyline, Rect, Text as SvgText } from 'react-native-svg';
import { buildFrame, Op } from '../lib/stick/scene';
import type { PomoState } from '../lib/pomodoro';

type Props = { pomo: PomoState; width: number; height: number };

const Shape = React.memo(function Shape({ op }: { op: Op }) {
  switch (op.t) {
    case 'rect':
      return <Rect x={op.x} y={op.y} width={op.w} height={op.h} rx={op.rx} fill={op.f} opacity={op.o} />;
    case 'circle':
      return <Circle cx={op.x} cy={op.y} r={op.r} fill={op.f} opacity={op.o} />;
    case 'line':
      return <Line x1={op.x1} y1={op.y1} x2={op.x2} y2={op.y2} stroke={op.c} strokeWidth={op.w} strokeLinecap="round" opacity={op.o} />;
    case 'poly': {
      const pts: string[] = [];
      for (let i = 0; i < op.pts.length; i += 2) pts.push(`${op.pts[i].toFixed(1)},${op.pts[i + 1].toFixed(1)}`);
      return <Polyline points={pts.join(' ')} fill="none" stroke={op.c} strokeWidth={op.w} strokeLinecap="round" strokeLinejoin="round" opacity={op.o} />;
    }
    case 'path':
      return <Path d={op.d} fill={op.f} opacity={op.o} />;
    case 'text':
      return (
        <>
          <SvgText x={op.x} y={op.y} fontSize={op.size} fontWeight="900" textAnchor="middle" fill={op.stroke} stroke={op.stroke} strokeWidth={op.sw} strokeLinejoin="round" opacity={op.o}>
            {op.s}
          </SvgText>
          <SvgText x={op.x} y={op.y} fontSize={op.size} fontWeight="900" textAnchor="middle" fill={op.f} opacity={op.o}>
            {op.s}
          </SvgText>
        </>
      );
  }
});

export function StickScene({ pomo, width, height }: Props) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (pomo.pausedAt) {
      setNow(pomo.pausedAt);
      return;
    }
    const timer = setInterval(() => setNow(Date.now()), 50);
    return () => clearInterval(timer);
  }, [pomo.pausedAt]);

  const frame = useMemo(
    () =>
      buildFrame({
        W: width,
        H: height,
        now: pomo.pausedAt ?? now,
        phase: pomo.phase,
        phaseStart: pomo.phaseStart,
        phaseEnd: pomo.phaseEnd,
        runStart: pomo.runStart,
        rung: pomo.rung,
        lastXp: pomo.lastXp ?? 0,
      }),
    [pomo.phase, pomo.phaseStart, pomo.phaseEnd, pomo.runStart, pomo.rung, pomo.lastXp, pomo.pausedAt, now, width, height],
  );

  return (
    <Svg width={width} height={height} viewBox={`0 0 ${width} ${height}`}>
      {frame.ops.map((op, i) => (
        <Shape key={i} op={op} />
      ))}
    </Svg>
  );
}
