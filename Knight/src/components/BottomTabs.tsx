import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme';
import type { IconName } from '../lib/ritual';
import type { Route } from './Drawer';

const TABS: { route: Route; label: string; icon: IconName }[] = [
  { route: 'stats', label: 'Stats', icon: 'chart-line' },
  { route: 'home', label: 'Home', icon: 'chess-knight' },
  { route: 'calm', label: 'Calm', icon: 'meditation' },
];

export function BottomTabs({ current, onSelect }: { current: Route; onSelect: (route: Route) => void }) {
  return (
    <View style={styles.bar}>
      {TABS.map((tab) => {
        const active = tab.route === current;
        const center = tab.route === 'home';
        return (
          <Pressable key={tab.route} style={styles.item} onPress={() => onSelect(tab.route)} android_ripple={{ color: '#ffffff18', borderless: true }}>
            {center ? (
              <View style={[styles.center, active && styles.centerOn]}>
                <MaterialCommunityIcons name={tab.icon} size={30} color={active ? '#04140A' : colors.green} />
              </View>
            ) : (
              <MaterialCommunityIcons name={tab.icon} size={26} color={active ? colors.green : colors.muted} />
            )}
            <Text style={[styles.label, active && { color: colors.green }]}>{tab.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    paddingTop: 6,
    paddingBottom: 6,
  },
  item: { flex: 1, alignItems: 'center', justifyContent: 'flex-end', gap: 2, minHeight: 56 },
  center: {
    width: 54,
    height: 54,
    borderRadius: 27,
    marginTop: -22,
    backgroundColor: colors.card,
    borderWidth: 2,
    borderColor: colors.green,
    alignItems: 'center',
    justifyContent: 'center',
  },
  centerOn: { backgroundColor: colors.green },
  label: { color: colors.muted, fontSize: 11, fontWeight: '800', letterSpacing: 0.6 },
});
