import React, { useEffect, useState } from 'react';
import { Alert, Modal, Pressable, ScrollView, StatusBar, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useKeepAwake } from 'expo-keep-awake';
import { Button, Card, SectionTitle } from '../components/ui';
import { ClimberScene } from '../components/ClimberScene';
import { BREAK_MINUTES, baseXpForRung, formatClockMs, phaseProgress, remainingMs, workMinutes } from '../lib/pomodoro';
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

function Active() {
  useKeepAwake();
  const { pomo, stopPomo, level, data, today } = useApp();
  const { width } = useWindowDimensions();
  const now = useNow(true);
  const [dark, setDark] = useState(false);
  if (!pomo) return null;

  const left = remainingMs(pomo, now);
  const progress = phaseProgress(pomo, now);
  const working = pomo.phase === 'work';
  const next = scaledXp(baseXpForRung(pomo.rung), level.level);
  const todayFocus = data.days[today]?.pomo?.minutes ?? 0;

  const confirmStop = () =>
    Alert.alert(
      'Stop the climb?',
      working ? 'The current block will not count. XP from finished checkpoints is already yours.' : 'Your finished checkpoints are already saved.',
      [
        { text: 'Keep climbing', style: 'cancel' },
        { text: 'Stop', style: 'destructive', onPress: stopPomo },
      ],
    );

  return (
    <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
      <View style={styles.topRow}>
        <View style={[styles.phasePill, { backgroundColor: (working ? colors.green : colors.gold) + '22' }]}>
          <MaterialCommunityIcons name={working ? 'arrow-up-bold' : 'coffee-outline'} size={16} color={working ? colors.green : colors.gold} />
          <Text style={[styles.phaseText, { color: working ? colors.green : colors.gold }]}>{working ? 'CLIMBING' : 'RESTING AT CHECKPOINT'}</Text>
        </View>
        <Pressable onPress={() => setDark(true)} style={styles.darkButton} hitSlop={8}>
          <MaterialCommunityIcons name="cellphone" size={18} color={colors.text} />
          <Text style={styles.darkText}>Black screen</Text>
        </Pressable>
      </View>

      <ClimberScene phase={pomo.phase} progress={progress} rung={pomo.rung} checkpointXp={next} width={width - 32} />

      <Text style={styles.timer}>{formatClockMs(left)}</Text>
      <Text style={styles.timerSub}>
        {working
          ? `${workMinutes(pomo.rung)} min block · checkpoint pays +${next} XP`
          : `${BREAK_MINUTES} min break · next climb is ${workMinutes(pomo.rung + 1)} min`}
      </Text>

      <View style={styles.statRow}>
        <View style={styles.stat}>
          <Text style={styles.statValue}>{pomo.blocks}</Text>
          <Text style={styles.statLabel}>Checkpoints</Text>
        </View>
        <View style={styles.stat}>
          <Text style={styles.statValue}>+{pomo.xp}</Text>
          <Text style={styles.statLabel}>XP this climb</Text>
        </View>
        <View style={styles.stat}>
          <Text style={styles.statValue}>{todayFocus}m</Text>
          <Text style={styles.statLabel}>Focus today</Text>
        </View>
      </View>

      <Button label="Stop climb" variant="danger" icon="stop" onPress={confirmStop} style={{ marginTop: 18 }} />

      <Modal visible={dark} animationType="fade" statusBarTranslucent onRequestClose={() => setDark(false)}>
        <StatusBar hidden />
        <Pressable style={styles.black} onPress={() => setDark(false)}>
          <Text style={styles.blackTime}>{formatClockMs(left)}</Text>
          <Text style={styles.blackSub}>{working ? `Rung ${pomo.rung + 1} · focus` : 'Break'}</Text>
          <Text style={styles.blackHint}>Tap anywhere to wake</Text>
        </Pressable>
      </Modal>
    </ScrollView>
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

export function Pomodoro() {
  const { pomo } = useApp();
  return pomo ? <Active /> : <Intro />;
}

const styles = StyleSheet.create({
  scroll: { padding: 16, paddingBottom: 30 },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  phasePill: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 12, paddingVertical: 7, borderRadius: 14 },
  phaseText: { fontSize: 11, fontWeight: '900', letterSpacing: 1.2 },
  darkButton: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 12, paddingVertical: 7, borderRadius: 14, borderWidth: 1, borderColor: colors.border },
  darkText: { color: colors.text, fontSize: 12, fontWeight: '800' },
  timer: { color: colors.text, fontSize: 64, fontWeight: '900', textAlign: 'center', marginTop: 16, letterSpacing: 2 },
  timerSub: { color: colors.muted, fontSize: 13, textAlign: 'center', marginTop: 2 },
  statRow: { flexDirection: 'row', gap: 10, marginTop: 16 },
  stat: { flex: 1, backgroundColor: colors.card, borderRadius: 18, borderWidth: 1, borderColor: colors.border, padding: 12, alignItems: 'center' },
  statValue: { color: colors.text, fontSize: 22, fontWeight: '900' },
  statLabel: { color: colors.muted, fontSize: 11, fontWeight: '700', marginTop: 2 },
  black: { flex: 1, backgroundColor: '#000', alignItems: 'center', justifyContent: 'center' },
  blackTime: { color: '#2E3A33', fontSize: 72, fontWeight: '900', letterSpacing: 3 },
  blackSub: { color: '#26302A', fontSize: 16, fontWeight: '700', marginTop: 4 },
  blackHint: { color: '#1B221E', fontSize: 12, marginTop: 40 },
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
