import React, { useEffect, useRef } from 'react';
import { Animated, Easing, Modal, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { Button } from './ui';
import { STREAK_TIERS, nextMilestone } from '../lib/xp';
import { colors } from '../theme';
import type { Reward } from '../store/AppStore';

export function Celebration({ reward, onClose }: { reward: Reward | null; onClose: () => void }) {
  const scale = useRef(new Animated.Value(0.3)).current;
  const fade = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (!reward) return;
    scale.setValue(0.3);
    fade.setValue(0);
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    Animated.parallel([
      Animated.spring(scale, { toValue: 1, friction: 5, tension: 80, useNativeDriver: true }),
      Animated.timing(fade, { toValue: 1, duration: 300, easing: Easing.out(Easing.quad), useNativeDriver: true }),
    ]).start();
  }, [reward, scale, fade]);

  if (!reward) return null;
  const tierHit = STREAK_TIERS.some((t) => t.min === reward.streak && reward.streak > 1);
  const next = nextMilestone(reward.streak);

  return (
    <Modal transparent animationType="fade" visible onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <Animated.View style={[styles.card, { opacity: fade, transform: [{ scale }] }]}>
          <MaterialCommunityIcons name="shield-star" size={72} color={colors.gold} />
          <Text style={styles.headline}>RITUAL COMPLETE</Text>
          <Text style={styles.xp}>+{reward.xp} XP</Text>
          <View style={styles.streakRow}>
            <MaterialCommunityIcons name="fire" size={26} color={colors.flame} />
            <Text style={styles.streak}>{reward.streak} day streak</Text>
          </View>
          {tierHit ? <Text style={styles.tier}>New streak tier unlocked. Rewards just went up.</Text> : null}
          {next ? (
            <Text style={styles.next}>
              {next.min - reward.streak} more day{next.min - reward.streak === 1 ? '' : 's'} to reach {next.xp} XP a day
            </Text>
          ) : (
            <Text style={styles.next}>Maximum tier. You are the 1%.</Text>
          )}
          <Button label="Conquer the day" icon="sword-cross" onPress={onClose} style={{ alignSelf: 'stretch', marginTop: 22 }} />
        </Animated.View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.82)', alignItems: 'center', justifyContent: 'center', padding: 24 },
  card: {
    alignSelf: 'stretch',
    backgroundColor: colors.card,
    borderColor: colors.green,
    borderWidth: 2,
    borderRadius: 28,
    padding: 26,
    alignItems: 'center',
  },
  headline: { color: colors.green, fontSize: 14, fontWeight: '900', letterSpacing: 3, marginTop: 8 },
  xp: { color: colors.gold, fontSize: 56, fontWeight: '900', marginVertical: 4 },
  streakRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  streak: { color: colors.text, fontSize: 20, fontWeight: '800' },
  tier: { color: colors.gold, fontSize: 14, fontWeight: '700', marginTop: 12, textAlign: 'center' },
  next: { color: colors.muted, fontSize: 13, marginTop: 10, textAlign: 'center' },
});
