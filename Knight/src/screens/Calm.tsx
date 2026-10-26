import React, { useEffect, useRef, useState } from 'react';
import { Animated, Easing, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useKeepAwake } from 'expo-keep-awake';
import * as Haptics from 'expo-haptics';
import { Button, Card, SectionTitle } from '../components/ui';
import { CALM_SOUNDS, SoundId, playEffect, startAmbient, stopAmbient } from '../services/sounds';
import { useApp } from '../store/AppStore';
import { colors } from '../theme';
import type { Route } from '../components/Drawer';
import type { IconName } from '../lib/ritual';

const DURATIONS = [1, 2, 5, 10];
const SOUND_ICONS: Record<SoundId, IconName> = {
  om: 'om',
  ocean: 'waves',
  rain: 'weather-rainy',
  bowl: 'bell-ring-outline',
  none: 'volume-off',
};

export function Calm({ go }: { go: (route: Route) => void }) {
  const { settings, updateSettings, data, today } = useApp();
  const calmToday = data.days[today]?.calm;

  return (
    <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
      <Text style={styles.title}>Breathe</Text>
      <Text style={styles.lead}>Slow inhale, longer exhale. Calm the mind before you conquer.</Text>

      <SectionTitle>Duration</SectionTitle>
      <View style={styles.rowWrap}>
        {DURATIONS.map((minutes) => (
          <Pressable key={minutes} onPress={() => updateSettings({ calmMinutes: minutes })} style={[styles.chip, settings.calmMinutes === minutes && styles.chipOn]}>
            <Text style={[styles.chipText, settings.calmMinutes === minutes && { color: '#04140A' }]}>{minutes} min</Text>
          </Pressable>
        ))}
      </View>

      <SectionTitle>Sound</SectionTitle>
      <Card style={{ paddingVertical: 6 }}>
        {CALM_SOUNDS.map((sound, index) => {
          const on = settings.calmSound === sound.id;
          return (
            <Pressable
              key={sound.id}
              onPress={() => {
                updateSettings({ calmSound: sound.id });
                startAmbient(sound.id, sound.id !== 'bowl' ? true : true);
                setTimeout(stopAmbient, 3500);
              }}
              style={[styles.soundRow, index > 0 && styles.soundDivider]}
            >
              <View style={[styles.soundIcon, on && { backgroundColor: colors.green + '33' }]}>
                <MaterialCommunityIcons name={SOUND_ICONS[sound.id]} size={22} color={on ? colors.green : colors.muted} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.soundTitle}>{sound.label}</Text>
                <Text style={styles.soundHint}>{sound.hint}</Text>
              </View>
              {on ? <MaterialCommunityIcons name="check-circle" size={24} color={colors.green} /> : null}
            </Pressable>
          );
        })}
      </Card>
      <Text style={styles.tapHint}>Tap a sound to hear a short preview.</Text>

      <Button
        label={`Begin ${settings.calmMinutes}-minute session`}
        icon="meditation"
        style={{ marginTop: 22 }}
        onPress={() => {
          stopAmbient();
          go('breathe');
        }}
      />
      {calmToday ? (
        <Text style={styles.today}>
          Today: {calmToday.minutes} min mindful · {calmToday.sessions} session{calmToday.sessions === 1 ? '' : 's'} · +{calmToday.xp} XP
        </Text>
      ) : null}
    </ScrollView>
  );
}

const INHALE_MS = 4000;
const EXHALE_MS = 6000;

export function Breathe({ onExit }: { onExit: () => void }) {
  useKeepAwake();
  const { settings, recordCalm } = useApp();
  const total = settings.calmMinutes * 60000;
  const scale = useRef(new Animated.Value(0.55)).current;
  const startedAt = useRef(Date.now());
  const [now, setNow] = useState(Date.now());
  const [reward, setReward] = useState<number | null>(null);
  const finished = useRef(false);

  useEffect(() => {
    startAmbient(settings.calmSound as SoundId);
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(scale, { toValue: 1, duration: INHALE_MS, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
        Animated.timing(scale, { toValue: 0.55, duration: EXHALE_MS, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
      ]),
    );
    loop.start();
    const tick = setInterval(() => setNow(Date.now()), 250);
    return () => {
      loop.stop();
      clearInterval(tick);
      stopAmbient();
    };
  }, [scale, settings.calmSound]);

  const elapsed = now - startedAt.current;
  const cycle = elapsed % (INHALE_MS + EXHALE_MS);
  const inhale = cycle < INHALE_MS;
  const remaining = Math.max(0, total - elapsed);

  const lastPhase = useRef(true);
  useEffect(() => {
    if (finished.current || lastPhase.current === inhale) return;
    lastPhase.current = inhale;
    Haptics.selectionAsync().catch(() => {});
  }, [inhale]);

  useEffect(() => {
    if (finished.current || remaining > 0) return;
    finished.current = true;
    stopAmbient();
    playEffect('bell').catch(() => {});
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    setReward(recordCalm(settings.calmMinutes));
  }, [remaining, recordCalm, settings.calmMinutes]);

  const minutes = Math.floor(remaining / 60000);
  const seconds = Math.ceil((remaining % 60000) / 1000) % 60;

  if (reward !== null) {
    return (
      <View style={styles.center}>
        <MaterialCommunityIcons name="meditation" size={90} color={colors.green} />
        <Text style={styles.doneTitle}>Session complete</Text>
        <Text style={styles.doneXp}>+{reward} XP</Text>
        <Text style={styles.sub}>{settings.calmMinutes} minutes of stillness. Carry it into your day.</Text>
        <Button label="Done" icon="check" onPress={onExit} style={{ alignSelf: 'stretch', marginTop: 28 }} />
      </View>
    );
  }

  return (
    <View style={styles.center}>
      <View style={styles.orbWrap}>
        <Animated.View style={[styles.orbOuter, { transform: [{ scale }] }]} />
        <Animated.View style={[styles.orbInner, { transform: [{ scale: scale.interpolate({ inputRange: [0.55, 1], outputRange: [0.5, 0.88] }) }] }]} />
        <Text style={styles.phase}>{inhale ? 'Inhale' : 'Exhale'}</Text>
      </View>
      <Text style={styles.time}>{String(minutes).padStart(2, '0')}:{String(seconds).padStart(2, '0')}</Text>
      <Text style={styles.sub}>{inhale ? 'Breathe in through the nose' : 'Release slowly through the mouth'}</Text>
      <Button label="End session" variant="ghost" onPress={onExit} style={{ marginTop: 32 }} />
    </View>
  );
}

const styles = StyleSheet.create({
  scroll: { padding: 16, paddingBottom: 30 },
  title: { color: colors.text, fontSize: 28, fontWeight: '900' },
  sub: { color: colors.muted, fontSize: 14, marginTop: 4, lineHeight: 20, textAlign: 'center' },
  lead: { color: colors.muted, fontSize: 14, marginTop: 4, lineHeight: 20 },
  rowWrap: { flexDirection: 'row', gap: 8 },
  chip: { flex: 1, alignItems: 'center', paddingVertical: 12, borderRadius: 16, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border },
  chipOn: { backgroundColor: colors.green, borderColor: colors.green },
  chipText: { color: colors.text, fontWeight: '800', fontSize: 15 },
  soundRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 11 },
  soundDivider: { borderTopWidth: 1, borderTopColor: colors.border },
  soundIcon: { width: 40, height: 40, borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg },
  soundTitle: { color: colors.text, fontSize: 16, fontWeight: '700' },
  soundHint: { color: colors.muted, fontSize: 12, marginTop: 1 },
  tapHint: { color: colors.muted, fontSize: 12, marginTop: 8, textAlign: 'center' },
  today: { color: colors.muted, textAlign: 'center', marginTop: 16, fontSize: 13 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 28 },
  orbWrap: { width: 280, height: 280, alignItems: 'center', justifyContent: 'center' },
  orbOuter: { position: 'absolute', width: 280, height: 280, borderRadius: 140, backgroundColor: colors.green + '26', borderWidth: 2, borderColor: colors.green + '88' },
  orbInner: { position: 'absolute', width: 280, height: 280, borderRadius: 140, backgroundColor: colors.green + '55' },
  phase: { color: colors.text, fontSize: 28, fontWeight: '900', letterSpacing: 1 },
  time: { color: colors.gold, fontSize: 44, fontWeight: '900', marginTop: 28 },
  doneTitle: { color: colors.text, fontSize: 26, fontWeight: '900', marginTop: 16 },
  doneXp: { color: colors.gold, fontSize: 48, fontWeight: '900', marginVertical: 6 },
});
