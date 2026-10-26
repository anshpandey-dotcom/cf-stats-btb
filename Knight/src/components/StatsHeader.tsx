import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useApp } from '../store/AppStore';
import { titleForLevel } from '../lib/xp';
import { colors, radius } from '../theme';

export function StatsHeader() {
  const { streak, xp, level, todayDone } = useApp();
  const fill = Math.min(1, level.into / level.needed);
  return (
    <View style={styles.wrap}>
      <View style={styles.pill}>
        <MaterialCommunityIcons name="fire" size={28} color={streak > 0 ? colors.flame : colors.muted} />
        <View>
          <Text style={styles.value}>{streak}</Text>
          <Text style={styles.label}>{todayDone ? 'DAY STREAK' : streak > 0 ? 'KEEP IT ALIVE' : 'START TODAY'}</Text>
        </View>
      </View>
      <View style={[styles.pill, { flex: 1.5 }]}>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>{level.level}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <View style={styles.xpLine}>
            <Text style={styles.value}>{xp.toLocaleString('en-US')} <Text style={styles.xpUnit}>XP</Text></Text>
            <Text style={styles.level}>{titleForLevel(level.level).toUpperCase()}</Text>
          </View>
          <View style={styles.track}>
            <View style={[styles.fill, { width: `${Math.max(fill * 100, 4)}%` }]} />
          </View>
          <Text style={styles.next}>{level.needed - level.into} XP to level {level.level + 1}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flexDirection: 'row', gap: 10, paddingHorizontal: 16, paddingBottom: 8 },
  pill: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  badge: { width: 38, height: 38, borderRadius: 19, backgroundColor: colors.gold, alignItems: 'center', justifyContent: 'center' },
  badgeText: { color: '#1A1200', fontWeight: '900', fontSize: 17 },
  value: { color: colors.text, fontSize: 20, fontWeight: '900' },
  xpUnit: { color: colors.gold, fontSize: 12, fontWeight: '900' },
  label: { color: colors.muted, fontSize: 9, fontWeight: '800', letterSpacing: 1 },
  xpLine: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  level: { color: colors.gold, fontSize: 10, fontWeight: '900', letterSpacing: 1 },
  track: { height: 6, borderRadius: 3, backgroundColor: colors.bg, marginTop: 5, overflow: 'hidden' },
  fill: { height: 6, borderRadius: 3, backgroundColor: colors.gold },
  next: { color: colors.muted, fontSize: 10, marginTop: 3 },
});
