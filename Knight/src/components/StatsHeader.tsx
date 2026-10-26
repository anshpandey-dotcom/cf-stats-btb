import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useApp } from '../store/AppStore';
import { levelFromXp } from '../lib/xp';
import { colors, radius } from '../theme';

export function StatsHeader() {
  const { streak, xp, todayDone } = useApp();
  const level = levelFromXp(xp);
  const fill = Math.min(1, level.into / level.needed);
  return (
    <View style={styles.wrap}>
      <View style={styles.pill}>
        <MaterialCommunityIcons name="fire" size={26} color={streak > 0 ? colors.flame : colors.muted} />
        <View>
          <Text style={styles.value}>{streak}</Text>
          <Text style={styles.label}>{todayDone ? 'DAY STREAK' : streak > 0 ? 'KEEP IT ALIVE' : 'START TODAY'}</Text>
        </View>
      </View>
      <View style={[styles.pill, { flex: 1.35 }]}>
        <MaterialCommunityIcons name="lightning-bolt" size={26} color={colors.gold} />
        <View style={{ flex: 1 }}>
          <View style={styles.xpLine}>
            <Text style={styles.value}>{xp.toLocaleString('en-US')}</Text>
            <Text style={styles.level}>LVL {level.level}</Text>
          </View>
          <View style={styles.track}>
            <View style={[styles.fill, { width: `${Math.max(fill * 100, 3)}%` }]} />
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flexDirection: 'row', gap: 10, paddingHorizontal: 16, paddingBottom: 6 },
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
  value: { color: colors.text, fontSize: 22, fontWeight: '900' },
  label: { color: colors.muted, fontSize: 9, fontWeight: '800', letterSpacing: 1 },
  xpLine: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  level: { color: colors.gold, fontSize: 11, fontWeight: '900', letterSpacing: 1 },
  track: { height: 6, borderRadius: 3, backgroundColor: colors.bg, marginTop: 6, overflow: 'hidden' },
  fill: { height: 6, borderRadius: 3, backgroundColor: colors.gold },
});
