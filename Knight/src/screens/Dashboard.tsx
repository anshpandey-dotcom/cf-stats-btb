import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Card } from '../components/ui';
import { WeekStrip } from '../components/WeekStrip';
import { RITUAL_SECTIONS } from '../lib/ritual';
import { formatLong } from '../lib/dates';
import { nextMilestone } from '../lib/xp';
import { useApp } from '../store/AppStore';
import { colors } from '../theme';
import type { Route } from '../components/Drawer';

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 5) return 'Still night, warrior';
  if (hour < 12) return 'Good morning, warrior';
  if (hour < 17) return 'Stay sharp, warrior';
  return 'Finish strong, warrior';
}

export function Dashboard({ go }: { go: (route: Route) => void }) {
  const { data, today, streak, todayDone } = useApp();
  const record = data.days[today];
  const required = RITUAL_SECTIONS.filter((s) => s.required);
  const finished = required.filter((s) => record?.sections[s.id]).length;
  const next = nextMilestone(streak);

  return (
    <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
      <Text style={styles.greet}>{greeting()}</Text>
      <Text style={styles.date}>{formatLong(today)}</Text>

      <Pressable onPress={() => go('ritual')} style={{ marginTop: 20 }}>
        <View style={[styles.hero, todayDone && styles.heroDone]}>
          <View style={styles.heroTop}>
            <View style={styles.heroIcon}>
              <MaterialCommunityIcons name={todayDone ? 'check-decagram' : 'weather-sunset-up'} size={30} color={colors.gold} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.heroTitle}>Morning Ritual</Text>
              <Text style={styles.heroSub}>
                {todayDone ? `Done today · +${record?.xp ?? 0} XP earned` : 'Win the morning, win the day'}
              </Text>
            </View>
            <MaterialCommunityIcons name="chevron-right" size={28} color={colors.text} />
          </View>
          <View style={styles.track}>
            <View style={[styles.fill, { width: `${(finished / required.length) * 100}%` }]} />
          </View>
          <Text style={styles.heroFoot}>
            {todayDone
              ? next
                ? `${next.min - streak} day${next.min - streak === 1 ? '' : 's'} to ${next.xp} XP a day`
                : 'Maximum streak tier reached'
              : `${finished}/${required.length} required · tap to begin`}
          </Text>
        </View>
      </Pressable>

      <Pressable onPress={() => go('pomodoro')} style={{ marginTop: 14 }}>
        <Card style={styles.pomo}>
          <View style={[styles.heroIcon, { backgroundColor: colors.flame + '22' }]}>
            <MaterialCommunityIcons name="stairs-up" size={28} color={colors.flame} />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.pomoTitle}>Pomodoro Ladder</Text>
            <Text style={styles.heroSub}>Climb focus levels, one session at a time</Text>
          </View>
          <View style={styles.soon}>
            <Text style={styles.soonText}>SOON</Text>
          </View>
        </Card>
      </Pressable>

      <Card style={{ marginTop: 14 }}>
        <Text style={styles.cardTitle}>Last 7 days</Text>
        <View style={{ height: 12 }} />
        <WeekStrip days={data.days} today={today} />
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { padding: 16, paddingBottom: 40 },
  greet: { color: colors.text, fontSize: 28, fontWeight: '900', marginTop: 8 },
  date: { color: colors.muted, fontSize: 14, marginTop: 4 },
  hero: {
    backgroundColor: colors.cardHigh,
    borderRadius: 26,
    borderWidth: 1.5,
    borderColor: colors.green,
    padding: 18,
  },
  heroDone: { borderColor: colors.gold },
  heroTop: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  heroIcon: { width: 52, height: 52, borderRadius: 16, backgroundColor: colors.gold + '22', alignItems: 'center', justifyContent: 'center' },
  heroTitle: { color: colors.text, fontSize: 22, fontWeight: '900' },
  heroSub: { color: colors.muted, fontSize: 13, marginTop: 2 },
  track: { height: 8, borderRadius: 4, backgroundColor: colors.bg, marginTop: 16, overflow: 'hidden' },
  fill: { height: 8, borderRadius: 4, backgroundColor: colors.green },
  heroFoot: { color: colors.muted, fontSize: 12, marginTop: 8, fontWeight: '600' },
  pomo: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  pomoTitle: { color: colors.text, fontSize: 18, fontWeight: '800' },
  soon: { backgroundColor: colors.bg, paddingHorizontal: 10, paddingVertical: 5, borderRadius: 8, borderWidth: 1, borderColor: colors.border },
  soonText: { color: colors.muted, fontSize: 11, fontWeight: '900', letterSpacing: 1.2 },
  cardTitle: { color: colors.text, fontSize: 16, fontWeight: '800' },
});
