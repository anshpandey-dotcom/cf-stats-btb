import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { addDays, dateFromKey } from '../lib/dates';
import { DayMap } from '../lib/xp';
import { colors } from '../theme';

const LETTERS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

export function WeekStrip({ days, today }: { days: DayMap; today: string }) {
  const keys = Array.from({ length: 7 }, (_, i) => addDays(today, i - 6));
  return (
    <View style={styles.row}>
      {keys.map((key) => {
        const done = !!days[key]?.done;
        const isToday = key === today;
        return (
          <View key={key} style={styles.cell}>
            <Text style={styles.letter}>{LETTERS[dateFromKey(key).getDay()]}</Text>
            <View style={[styles.dot, done && styles.dotDone, isToday && !done && styles.dotToday]}>
              {done ? <MaterialCommunityIcons name="check" size={16} color="#04140A" /> : null}
            </View>
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'space-between' },
  cell: { alignItems: 'center', gap: 6 },
  letter: { color: colors.muted, fontSize: 11, fontWeight: '800' },
  dot: { width: 30, height: 30, borderRadius: 15, borderWidth: 1.5, borderColor: colors.border, alignItems: 'center', justifyContent: 'center' },
  dotDone: { backgroundColor: colors.green, borderColor: colors.green },
  dotToday: { borderColor: colors.gold },
});
