import React, { useEffect, useRef } from 'react';
import { Animated, Easing, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme';

type Props = { phase: 'work' | 'break'; progress: number; rung: number; checkpointXp: number; width: number };

const HEIGHT = 340;
const RUNG_GAP = 42;
const FIGURE_TOP = 108;
const HIP_Y = 70;

function Limb({
  x,
  y,
  w,
  h,
  color,
  rotate,
}: {
  x: number;
  y: number;
  w: number;
  h: number;
  color: string;
  rotate: Animated.AnimatedInterpolation<string> | string;
}) {
  return (
    <Animated.View
      style={{
        position: 'absolute',
        left: x - w / 2,
        top: y,
        width: w,
        height: h,
        borderRadius: w / 2,
        backgroundColor: color,
        transform: [{ translateY: -h / 2 }, { rotate: rotate as any }, { translateY: h / 2 }],
      }}
    />
  );
}

export function ClimberScene({ phase, progress, rung, checkpointXp, width }: Props) {
  const flow = useRef(new Animated.Value(0)).current;
  const swing = useRef(new Animated.Value(0)).current;
  const bob = useRef(new Animated.Value(0)).current;
  const climbing = phase === 'work';

  useEffect(() => {
    flow.setValue(0);
    swing.setValue(0);
    bob.setValue(0);
    let loops: Animated.CompositeAnimation[] = [];
    if (climbing) {
      loops = [
        Animated.loop(Animated.timing(flow, { toValue: 1, duration: 1300, easing: Easing.linear, useNativeDriver: true })),
        Animated.loop(Animated.timing(swing, { toValue: 1, duration: 1300, easing: Easing.linear, useNativeDriver: true })),
      ];
    } else {
      loops = [
        Animated.loop(
          Animated.sequence([
            Animated.timing(swing, { toValue: 1, duration: 1400, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
            Animated.timing(swing, { toValue: 0, duration: 1400, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
          ]),
        ),
        Animated.loop(
          Animated.sequence([
            Animated.timing(bob, { toValue: 1, duration: 2200, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
            Animated.timing(bob, { toValue: 0, duration: 0, useNativeDriver: true }),
          ]),
        ),
      ];
    }
    loops.forEach((l) => l.start());
    return () => loops.forEach((l) => l.stop());
  }, [climbing, flow, swing, bob]);

  const centerX = width / 2;
  const ladderHalf = 46;
  const rungs = Array.from({ length: Math.ceil(HEIGHT / RUNG_GAP) + 2 }, (_, i) => i);

  const armA = climbing
    ? swing.interpolate({ inputRange: [0, 0.5, 1], outputRange: ['150deg', '200deg', '150deg'] })
    : swing.interpolate({ inputRange: [0, 1], outputRange: ['-8deg', '6deg'] });
  const armB = climbing
    ? swing.interpolate({ inputRange: [0, 0.5, 1], outputRange: ['210deg', '160deg', '210deg'] })
    : swing.interpolate({ inputRange: [0, 1], outputRange: ['8deg', '-6deg'] });
  const legA = climbing
    ? swing.interpolate({ inputRange: [0, 0.5, 1], outputRange: ['-28deg', '22deg', '-28deg'] })
    : swing.interpolate({ inputRange: [0, 1], outputRange: ['72deg', '88deg'] });
  const legB = climbing
    ? swing.interpolate({ inputRange: [0, 0.5, 1], outputRange: ['22deg', '-28deg', '22deg'] })
    : swing.interpolate({ inputRange: [0, 1], outputRange: ['-88deg', '-72deg'] });

  const ledgeTarget = FIGURE_TOP + HIP_Y + 14;
  const arrive = climbing ? Math.min(1, Math.max(0, (progress - 0.88) / 0.12)) : 1;
  const ledgeTop = -30 + (ledgeTarget + 30) * arrive;
  const showLedge = !climbing || progress > 0.88;
  const figureX = centerX;
  const fillHeight = (HEIGHT - 60) * (climbing ? progress : 1);

  return (
    <View style={[styles.scene, { width }]}>
      <View style={styles.meter}>
        <MaterialCommunityIcons name="flag-checkered" size={22} color={colors.gold} />
        <View style={styles.meterTrack}>
          <View style={[styles.meterFill, { height: fillHeight }]} />
          {[0.25, 0.5, 0.75].map((tick) => (
            <View key={tick} style={[styles.tick, { bottom: (HEIGHT - 60) * tick }]} />
          ))}
        </View>
        <Text style={styles.meterXp}>+{checkpointXp}</Text>
      </View>

      <View style={[styles.rail, { left: centerX - ladderHalf }]} />
      <View style={[styles.rail, { left: centerX + ladderHalf - 8 }]} />
      <View style={[StyleSheet.absoluteFill, { overflow: 'hidden' }]} pointerEvents="none">
        <Animated.View
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: -RUNG_GAP,
            transform: [{ translateY: flow.interpolate({ inputRange: [0, 1], outputRange: [0, RUNG_GAP] }) }],
          }}
        >
          {rungs.map((i) => (
            <View key={i} style={{ height: RUNG_GAP, justifyContent: 'center' }}>
              <View style={[styles.rung, { left: centerX - ladderHalf + 6, width: ladderHalf * 2 - 12 }]} />
            </View>
          ))}
        </Animated.View>
      </View>

      {showLedge ? (
        <View style={[styles.ledge, { top: ledgeTop, left: centerX - 78, width: 156 }]}>
          <MaterialCommunityIcons name="flag-variant" size={26} color={colors.gold} style={styles.flag} />
        </View>
      ) : null}

      <View style={[styles.figure, { left: figureX - 30, top: FIGURE_TOP }]}>
        <Limb x={30 - 13} y={26} w={8} h={34} color="#9FE8B8" rotate={armA} />
        <Limb x={30 - 8} y={HIP_Y} w={10} h={42} color="#2F6B47" rotate={legA} />
        <Limb x={30 + 8} y={HIP_Y} w={10} h={42} color="#2F6B47" rotate={legB} />
        <View style={styles.torso} />
        <View style={styles.head}>
          <View style={styles.visor} />
        </View>
        <View style={styles.plume} />
        <Limb x={30 + 13} y={26} w={8} h={34} color="#9FE8B8" rotate={armB} />
      </View>

      {!climbing ? (
        <Animated.View
          style={[
            styles.zzz,
            {
              left: figureX + 26,
              opacity: bob.interpolate({ inputRange: [0, 0.8, 1], outputRange: [1, 0.6, 0] }),
              transform: [{ translateY: bob.interpolate({ inputRange: [0, 1], outputRange: [0, -34] }) }],
            },
          ]}
        >
          <Text style={styles.zzzText}>Zzz</Text>
        </Animated.View>
      ) : null}

      <View style={styles.tag}>
        <Text style={styles.tagText}>RUNG {rung + 1}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  scene: {
    height: HEIGHT,
    backgroundColor: colors.surface,
    borderRadius: 28,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  meter: { position: 'absolute', left: 14, top: 14, bottom: 14, alignItems: 'center', width: 36 },
  meterTrack: { flex: 1, width: 8, borderRadius: 4, backgroundColor: colors.bg, marginVertical: 6, justifyContent: 'flex-end', overflow: 'visible' },
  meterFill: { width: 8, borderRadius: 4, backgroundColor: colors.green },
  tick: { position: 'absolute', left: -4, width: 16, height: 2, backgroundColor: colors.border },
  meterXp: { color: colors.gold, fontSize: 11, fontWeight: '900' },
  rail: { position: 'absolute', top: 0, bottom: 0, width: 8, backgroundColor: '#6B4A2B', borderRadius: 4 },
  rung: { position: 'absolute', height: 7, borderRadius: 3, backgroundColor: '#9A7040' },
  ledge: { position: 'absolute', height: 12, borderRadius: 6, backgroundColor: '#B8863F', borderBottomWidth: 4, borderBottomColor: '#6B4A2B' },
  flag: { position: 'absolute', right: 8, top: -26 },
  figure: { position: 'absolute', width: 60, height: 130 },
  torso: { position: 'absolute', left: 19, top: 24, width: 22, height: 50, borderRadius: 9, backgroundColor: colors.green },
  head: { position: 'absolute', left: 16, top: 0, width: 28, height: 28, borderRadius: 14, backgroundColor: '#C9D6CF', justifyContent: 'center', alignItems: 'center' },
  visor: { width: 16, height: 5, borderRadius: 2, backgroundColor: '#20352A', marginTop: 2 },
  plume: { position: 'absolute', left: 26, top: -9, width: 8, height: 12, borderTopLeftRadius: 6, borderTopRightRadius: 6, backgroundColor: colors.gold },
  zzz: { position: 'absolute', top: FIGURE_TOP - 8 },
  zzzText: { color: colors.muted, fontSize: 18, fontWeight: '900' },
  tag: { position: 'absolute', right: 14, top: 14, backgroundColor: colors.bg, borderRadius: 10, paddingHorizontal: 10, paddingVertical: 5, borderWidth: 1, borderColor: colors.border },
  tagText: { color: colors.gold, fontSize: 11, fontWeight: '900', letterSpacing: 1.2 },
});
