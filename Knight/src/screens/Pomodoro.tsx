import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme';

export function Pomodoro() {
  return (
    <View style={styles.wrap}>
      <View style={styles.badge}>
        <MaterialCommunityIcons name="stairs-up" size={64} color={colors.flame} />
      </View>
      <Text style={styles.title}>Pomodoro Ladder</Text>
      <Text style={styles.body}>
        Focus sessions that climb level by level. The design is coming right after the Morning Ritual is locked in.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  badge: { width: 120, height: 120, borderRadius: 60, backgroundColor: colors.flame + '1F', alignItems: 'center', justifyContent: 'center' },
  title: { color: colors.text, fontSize: 26, fontWeight: '900', marginTop: 22 },
  body: { color: colors.muted, fontSize: 15, textAlign: 'center', marginTop: 10, lineHeight: 22 },
});
