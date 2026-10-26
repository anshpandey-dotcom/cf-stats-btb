import React, { useEffect } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors } from '../theme';
import type { PomoNotice } from '../store/AppStore';

export function PomoToast({ notice, onPress, onDone }: { notice: PomoNotice | null; onPress: () => void; onDone: () => void }) {
  const insets = useSafeAreaInsets();
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(onDone, 6000);
    return () => clearTimeout(timer);
  }, [notice, onDone]);
  if (!notice) return null;
  const checkpoint = notice.kind === 'checkpoint';
  return (
    <Pressable onPress={onPress} style={[styles.toast, { top: insets.top + 8 }]}>
      <MaterialCommunityIcons name={checkpoint ? 'flag-checkered' : 'arrow-up-bold'} size={26} color={checkpoint ? colors.gold : colors.green} />
      <Text style={styles.text}>
        {checkpoint ? `Checkpoint ${notice.rung} reached  +${notice.xp} XP. Rest for 5 minutes.` : `Break over. Climb to rung ${notice.rung}.`}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  toast: {
    position: 'absolute',
    left: 14,
    right: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.cardHigh,
    borderColor: colors.gold,
    borderWidth: 1.5,
    borderRadius: 18,
    padding: 14,
    elevation: 12,
  },
  text: { color: colors.text, flex: 1, fontSize: 14, fontWeight: '700' },
});
