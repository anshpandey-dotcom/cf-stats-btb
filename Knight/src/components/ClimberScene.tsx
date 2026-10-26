import React, { useEffect, useRef, useState } from 'react';
import { Animated, Easing, Image, ImageSourcePropType, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { CLIMB_FRAMES, FRAME_RATIO, REST_FRAMES } from '../lib/climberFrames';
import { colors } from '../theme';

type Props = { phase: 'work' | 'break'; progress: number; rung: number; checkpointXp: number; width: number };

const FrameImage = React.memo(function FrameImage({ source, visible, width, height }: { source: ImageSourcePropType; visible: boolean; width: number; height: number }) {
  return <Image source={source} fadeDuration={0} style={{ position: 'absolute', width, height, opacity: visible ? 1 : 0 }} />;
});

const FrameStack = React.memo(function FrameStack({ frames, index, width, height }: { frames: ImageSourcePropType[]; index: number; width: number; height: number }) {
  return (
    <View style={{ width, height }}>
      {frames.map((source, i) => (
        <FrameImage key={i} source={source} visible={i === index} width={width} height={height} />
      ))}
    </View>
  );
});

export function ClimberScene({ phase, progress, rung, checkpointXp, width }: Props) {
  const height = Math.round(width * FRAME_RATIO);
  const working = phase === 'work';
  const [frame, setFrame] = useState(0);
  const [mountClimb, setMountClimb] = useState(working);
  const [mountRest, setMountRest] = useState(!working);
  const mix = useRef(new Animated.Value(working ? 0 : 1)).current;

  useEffect(() => {
    if (working) setMountClimb(true);
    else setMountRest(true);
    Animated.timing(mix, { toValue: working ? 0 : 1, duration: 500, easing: Easing.inOut(Easing.quad), useNativeDriver: true }).start(({ finished }) => {
      if (!finished) return;
      if (working) setMountRest(false);
      else setMountClimb(false);
    });
  }, [working, mix]);

  useEffect(() => {
    setFrame(0);
    const count = working ? CLIMB_FRAMES.length : REST_FRAMES.length;
    const fps = working ? 20 : 15;
    const timer = setInterval(() => setFrame((f) => (f + 1) % count), 1000 / fps);
    return () => clearInterval(timer);
  }, [working]);

  const meterHeight = height - 76;
  const fill = meterHeight * (working ? progress : 1);
  const ahead = working && progress > 0.9;

  return (
    <View style={[styles.scene, { width, height }]}>
      {mountClimb ? <FrameStack frames={CLIMB_FRAMES} index={working ? frame : 0} width={width} height={height} /> : null}
      {mountRest ? (
        <Animated.View style={[StyleSheet.absoluteFill, { opacity: mix }]}>
          <FrameStack frames={REST_FRAMES} index={working ? 0 : frame} width={width} height={height} />
        </Animated.View>
      ) : null}

      <View style={styles.vignette} pointerEvents="none" />

      <View style={[styles.meter, { height: height - 28 }]}>
        <MaterialCommunityIcons name="flag-checkered" size={20} color={colors.gold} />
        <View style={styles.meterTrack}>
          <View style={[styles.meterFill, { height: fill }]} />
          {[0.25, 0.5, 0.75].map((tick) => (
            <View key={tick} style={[styles.tick, { bottom: meterHeight * tick }]} />
          ))}
        </View>
        <Text style={styles.meterXp}>+{checkpointXp}</Text>
      </View>

      <View style={styles.tag}>
        <Text style={styles.tagText}>RUNG {rung + 1}</Text>
      </View>

      {ahead ? (
        <View style={styles.banner}>
          <Text style={styles.bannerText}>CHECKPOINT AHEAD</Text>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  scene: { borderRadius: 28, borderWidth: 1, borderColor: colors.border, overflow: 'hidden', backgroundColor: colors.surface },
  vignette: { ...(StyleSheet.absoluteFill as object), borderRadius: 28, borderWidth: 18, borderColor: 'rgba(3,10,6,0.28)' },
  meter: { position: 'absolute', left: 10, top: 14, alignItems: 'center', width: 34, paddingVertical: 8, backgroundColor: 'rgba(6,16,10,0.62)', borderRadius: 17 },
  meterTrack: { flex: 1, width: 8, borderRadius: 4, backgroundColor: 'rgba(255,255,255,0.12)', marginVertical: 6, justifyContent: 'flex-end' },
  meterFill: { width: 8, borderRadius: 4, backgroundColor: colors.green },
  tick: { position: 'absolute', left: -4, width: 16, height: 2, backgroundColor: 'rgba(255,255,255,0.35)' },
  meterXp: { color: colors.gold, fontSize: 11, fontWeight: '900' },
  tag: { position: 'absolute', right: 12, top: 12, backgroundColor: 'rgba(6,16,10,0.7)', borderRadius: 10, paddingHorizontal: 10, paddingVertical: 5, borderWidth: 1, borderColor: colors.border },
  tagText: { color: colors.gold, fontSize: 11, fontWeight: '900', letterSpacing: 1.2 },
  banner: { position: 'absolute', alignSelf: 'center', bottom: 14, backgroundColor: 'rgba(245,179,1,0.92)', borderRadius: 12, paddingHorizontal: 14, paddingVertical: 6 },
  bannerText: { color: '#1A1200', fontSize: 11, fontWeight: '900', letterSpacing: 1.4 },
});
