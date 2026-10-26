import React, { useEffect, useRef } from 'react';
import { Animated, Dimensions, Easing, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors } from '../theme';
import type { IconName } from '../lib/ritual';

export type Route = 'dashboard' | 'ritual' | 'declaration' | 'pomodoro' | 'settings';

const WIDTH = Math.min(Dimensions.get('window').width * 0.8, 320);

const ITEMS: { route: Route; label: string; icon: IconName }[] = [
  { route: 'dashboard', label: 'Dashboard', icon: 'view-dashboard-outline' },
  { route: 'ritual', label: 'Morning Ritual', icon: 'weather-sunset-up' },
  { route: 'pomodoro', label: 'Pomodoro Ladder', icon: 'stairs-up' },
  { route: 'settings', label: 'Settings', icon: 'cog-outline' },
];

type Props = { open: boolean; current: Route; onClose: () => void; onNavigate: (route: Route) => void };

export function Drawer({ open, current, onClose, onNavigate }: Props) {
  const insets = useSafeAreaInsets();
  const progress = useRef(new Animated.Value(0)).current;
  const [mounted, setMounted] = React.useState(open);

  useEffect(() => {
    if (open) setMounted(true);
    Animated.timing(progress, {
      toValue: open ? 1 : 0,
      duration: 240,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished && !open) setMounted(false);
    });
  }, [open, progress]);

  if (!mounted) return null;

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Animated.View style={[styles.scrim, { opacity: progress }]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />
      </Animated.View>
      <Animated.View
        style={[
          styles.panel,
          { paddingTop: insets.top + 20, paddingBottom: insets.bottom + 16 },
          { transform: [{ translateX: progress.interpolate({ inputRange: [0, 1], outputRange: [-WIDTH, 0] }) }] },
        ]}
      >
        <View style={styles.brand}>
          <Image source={require('../../assets/icon.png')} style={styles.logo} />
          <View>
            <Text style={styles.brandName}>KNIGHT</Text>
            <Text style={styles.brandTag}>Rise. Conquer. Repeat.</Text>
          </View>
        </View>
        <View style={{ marginTop: 28 }}>
          {ITEMS.map((item) => {
            const active = item.route === current || (item.route === 'ritual' && current === 'declaration');
            return (
              <Pressable
                key={item.route}
                onPress={() => onNavigate(item.route)}
                style={[styles.item, active && styles.itemActive]}
                android_ripple={{ color: '#ffffff18' }}
              >
                <MaterialCommunityIcons name={item.icon} size={24} color={active ? colors.green : colors.muted} />
                <Text style={[styles.itemText, active && { color: colors.text }]}>{item.label}</Text>
              </Pressable>
            );
          })}
        </View>
        <View style={{ flex: 1 }} />
        <Text style={styles.version}>Knight · V1 test build</Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  scrim: { ...StyleSheet.absoluteFill as object, backgroundColor: 'rgba(0,0,0,0.6)' },
  panel: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: 0,
    width: WIDTH,
    backgroundColor: colors.surface,
    borderRightWidth: 1,
    borderRightColor: colors.border,
    paddingHorizontal: 18,
  },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  logo: { width: 52, height: 52, borderRadius: 14 },
  brandName: { color: colors.text, fontSize: 22, fontWeight: '900', letterSpacing: 3 },
  brandTag: { color: colors.muted, fontSize: 12, marginTop: 2 },
  item: { flexDirection: 'row', alignItems: 'center', gap: 16, paddingVertical: 14, paddingHorizontal: 14, borderRadius: 14, marginBottom: 4 },
  itemActive: { backgroundColor: colors.card },
  itemText: { color: colors.muted, fontSize: 16, fontWeight: '700' },
  version: { color: colors.muted, fontSize: 12, textAlign: 'center' },
});
