import React, { useEffect, useState } from 'react';
import { Alert, Modal, Pressable, ScrollView, StatusBar, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useKeepAwake } from 'expo-keep-awake';
import { Button, Card, SectionTitle } from '../components/ui';
import { StickScene } from '../components/StickScene';
import type { IconName } from '../lib/ritual';
import { BREAK_MINUTES, baseXpForRung, formatClockMs, remainingMs, workMinutes } from '../lib/pomodoro';
import { bestPomo } from '../lib/stats';
import { scaledXp } from '../lib/xp';
import { useApp } from '../store/AppStore';
import { colors } from '../theme';

const PREVIEW_RUNGS = [0, 1, 2, 3, 4];

function useNow(active: boolean, interval = 500) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!active) return;
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), interval);
    return () => clearInterval(timer);
  }, [active, interval]);
  return now;
}

function RoundButton({ icon, onPress, tint = '#FFFFFF', label }: { icon: IconName; onPress: () => void; tint?: string; label: string }) {
  return (
    <Pressable onPress={onPress} accessibilityLabel={label} style={({ pressed }) => [styles.round, pressed && { opacity: 0.7, transform: [{ scale: 0.94 }] }]}>
      <MaterialCommunityIcons name={icon} size={30} color={tint} />
    </Pressable>
  );
}

function Active({ onBack }: { onBack: () => void }) {
  useKeepAwake();
  const { pomo, stopPomo, pausePomo, resumePomo, skipPomo, data, today } = useApp();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const now = useNow(true);
  const [dark, setDark] = useState(false);
  if (!pomo) return null;

  const left = remainingMs(pomo, now);
  const working = pomo.phase === 'work';
  const paused = !!pomo.pausedAt;
  const todayFocus = data.days[today]?.pomo?.minutes ?? 0;

  const confirmStop = () =>
    Alert.alert('Stop the climb?', working ? 'The current block will not count. Finished checkpoints are already saved.' : 'Your finished checkpoints are already saved.', [
      { text: 'Keep climbing', style: 'cancel' },
      { text: 'Stop', style: 'destructive', onPress: stopPomo },
    ]);

  return (
    <View style={styles.full}>
      <StatusBar barStyle="light-content" />
      <StickScene pomo={pomo} width={width} height={height} />

      <View style={[styles.topBar, { paddingTop: insets.top + 8 }]} pointerEvents="box-none">
        <Pressable onPress={onBack} style={styles.roundSmall} accessibilityLabel="Back">
          <MaterialCommunityIcons name="arrow-left" size={24} color="#FFFFFF" />
        </Pressable>
        <View style={styles.chip}>
          <MaterialCommunityIcons name={working ? 'arrow-up-bold' : 'coffee-outline'} size={16} color="#FFFFFF" />
          <Text style={styles.chipText}>{paused ? 'PAUSED' : working ? `CLIMBING · RUNG ${pomo.rung + 1}` : 'CHECKPOINT REST'}</Text>
        </View>
        <Pressable onPress={() => setDark(true)} style={styles.roundSmall} accessibilityLabel="Black screen">
          <MaterialCommunityIcons name="cellphone-off" size={22} color="#FFFFFF" />
        </Pressable>
      </View>

      <View style={[styles.timerWrap, { top: insets.top + 64 }]} pointerEvents="none">
        <Text style={styles.timer}>{formatClockMs(left)}</Text>
        <Text style={styles.sub}>
          {pomo.blocks} checkpoint{pomo.blocks === 1 ? '' : 's'} · +{pomo.xp} XP · {todayFocus}m focus today
        </Text>
      </View>

      <View style={[styles.controls, { paddingBottom: insets.bottom + 22 }]}>
        <RoundButton icon="skip-next" label="Skip" onPress={skipPomo} />
        <RoundButton icon={paused ? 'play' : 'pause'} label={paused ? 'Resume' : 'Pause'} onPress={paused ? resumePomo : pausePomo} />
        <RoundButton icon="stop" label="Stop" tint="#FF5A5A" onPress={confirmStop} />
      </View>

      <Modal visible={dark} animationType="fade" statusBarTranslucent onRequestClose={() => setDark(false)}>
        <StatusBar hidden />
        <Pressable style={styles.black} onPress={() => setDark(false)}>
          <Text style={styles.blackTime}>{formatClockMs(left)}</Text>
          <Text style={styles.blackSub}>{paused ? 'Paused' : working ? `Rung ${pomo.rung + 1} · focus` : 'Break'}</Text>
          <Text style={styles.blackHint}>Tap anywhere to wake</Text>
        </Pressable>
      </Modal>
    </View>
  );
}

function Intro() {
  const { startPomo, data, level } = useApp();
  const best = bestPomo(data.days);
  return (
    <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
      <View style={styles.introHero}>
        <MaterialCommunityIcons name="stairs-up" size={56} color={colors.flame} />
        <Text style={styles.introTitle}>Pomodoro Ladder</Text>
        <Text style={styles.introSub}>
          Focus climbs a rung at a time. Every finished block is a checkpoint with a 5 minute rest. Each rung is longer and pays more XP.
        </Text>
      </View>

      <SectionTitle>The ladder</SectionTitle>
      <Card>
        {PREVIEW_RUNGS.map((rung) => (
          <View key={rung} style={[styles.rungRow, rung > 0 && styles.rungDivider]}>
            <View style={styles.rungBadge}>
              <Text style={styles.rungNum}>{rung + 1}</Text>
            </View>
            <Text style={styles.rungText}>
              {workMinutes(rung)} min focus <Text style={styles.rungMuted}>+ {BREAK_MINUTES} min rest</Text>
            </Text>
            <Text style={styles.rungXp}>+{scaledXp(baseXpForRung(rung), level.level)} XP</Text>
          </View>
        ))}
        <Text style={styles.rungNote}>From rung 4 onward every block stays 60 minutes, and the XP keeps growing.</Text>
      </Card>

      <SectionTitle>Your best</SectionTitle>
      <View style={styles.statRow}>
        <View style={styles.stat}>
          <Text style={styles.statValue}>{best.block ? `${best.block}m` : '-'}</Text>
          <Text style={styles.statLabel}>Longest block</Text>
        </View>
        <View style={styles.stat}>
          <Text style={styles.statValue}>{best.rung || '-'}</Text>
          <Text style={styles.statLabel}>Highest rung</Text>
        </View>
      </View>

      <Button label="Start climbing" icon="arrow-up-bold" style={{ marginTop: 22 }} onPress={startPomo} />
      <Text style={styles.footNote}>The timer keeps running in the background and rings when you reach a checkpoint.</Text>
    </ScrollView>
  );
}

export function Pomodoro({ onBack }: { onBack: () => void }) {
  const { pomo } = useApp();
  return pomo ? <Active onBack={onBack} /> : <Intro />;
}

const styles = StyleSheet.create({
  full: { ...(StyleSheet.absoluteFill as object), backgroundColor: '#000' },
  topBar: { position: 'absolute', left: 0, right: 0, top: 0, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 14 },
  roundSmall: { width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(0,0,0,0.38)', alignItems: 'center', justifyContent: 'center' },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: 'rgba(0,0,0,0.38)', borderRadius: 18, paddingHorizontal: 14, paddingVertical: 9 },
  chipText: { color: '#FFFFFF', fontSize: 11, fontWeight: '900', letterSpacing: 1.3 },
  timerWrap: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  timer: { color: '#FFFFFF', fontSize: 64, fontWeight: '900', letterSpacing: 2, textShadowColor: 'rgba(0,0,0,0.45)', textShadowRadius: 8, textShadowOffset: { width: 0, height: 2 } },
  sub: { color: 'rgba(255,255,255,0.92)', fontSize: 13, fontWeight: '700', textShadowColor: 'rgba(0,0,0,0.5)', textShadowRadius: 6 },
  controls: { position: 'absolute', left: 0, right: 0, bottom: 0, flexDirection: 'row', justifyContent: 'center', gap: 26, paddingTop: 14 },
  round: { width: 68, height: 68, borderRadius: 34, backgroundColor: 'rgba(0,0,0,0.45)', alignItems: 'center', justifyContent: 'center', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.35)' },
  black: { flex: 1, backgroundColor: '#000', alignItems: 'center', justifyContent: 'center' },
  blackTime: { color: '#2E3A33', fontSize: 72, fontWeight: '900', letterSpacing: 3 },
  blackSub: { color: '#26302A', fontSize: 16, fontWeight: '700', marginTop: 4 },
  blackHint: { color: '#1B221E', fontSize: 12, marginTop: 40 },
  scroll: { padding: 16, paddingBottom: 30 },
  statRow: { flexDirection: 'row', gap: 10, marginTop: 16 },
  stat: { flex: 1, backgroundColor: colors.card, borderRadius: 18, borderWidth: 1, borderColor: colors.border, padding: 12, alignItems: 'center' },
  statValue: { color: colors.text, fontSize: 22, fontWeight: '900' },
  statLabel: { color: colors.muted, fontSize: 11, fontWeight: '700', marginTop: 2 },
  introHero: { alignItems: 'center', paddingVertical: 12 },
  introTitle: { color: colors.text, fontSize: 28, fontWeight: '900', marginTop: 8 },
  introSub: { color: colors.muted, fontSize: 14, lineHeight: 21, textAlign: 'center', marginTop: 8 },
  rungRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 11 },
  rungDivider: { borderTopWidth: 1, borderTopColor: colors.border },
  rungBadge: { width: 32, height: 32, borderRadius: 16, backgroundColor: colors.flame + '26', alignItems: 'center', justifyContent: 'center' },
  rungNum: { color: colors.flame, fontWeight: '900' },
  rungText: { color: colors.text, fontSize: 15, fontWeight: '700', flex: 1 },
  rungMuted: { color: colors.muted, fontWeight: '600', fontSize: 13 },
  rungXp: { color: colors.gold, fontWeight: '900', fontSize: 14 },
  rungNote: { color: colors.muted, fontSize: 12, marginTop: 8 },
  footNote: { color: colors.muted, fontSize: 12, textAlign: 'center', marginTop: 12 },
});
