import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { formatClock } from '../lib/dates';
import { colors } from '../theme';

type Props = { hour: number; minute: number; onChange: (hour: number, minute: number) => void };

function Arrow({ icon, onPress }: { icon: 'chevron-up' | 'chevron-down'; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} hitSlop={8} style={styles.arrow} android_ripple={{ color: '#ffffff22', radius: 18 }}>
      <MaterialCommunityIcons name={icon} size={22} color={colors.green} />
    </Pressable>
  );
}

export function TimeStepper({ hour, minute, onChange }: Props) {
  const stepHour = (d: number) => onChange((hour + d + 24) % 24, minute);
  const stepMinute = (d: number) => {
    const total = (hour * 60 + minute + d + 1440) % 1440;
    onChange(Math.floor(total / 60), total % 60);
  };
  return (
    <View style={styles.wrap}>
      <View style={styles.col}>
        <Arrow icon="chevron-up" onPress={() => stepHour(1)} />
        <Arrow icon="chevron-down" onPress={() => stepHour(-1)} />
      </View>
      <Text style={styles.time}>{formatClock(hour, minute)}</Text>
      <View style={styles.col}>
        <Arrow icon="chevron-up" onPress={() => stepMinute(5)} />
        <Arrow icon="chevron-down" onPress={() => stepMinute(-5)} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  col: { alignItems: 'center' },
  arrow: { width: 32, height: 26, alignItems: 'center', justifyContent: 'center' },
  time: { color: colors.text, fontSize: 18, fontWeight: '800', minWidth: 92, textAlign: 'center' },
});
