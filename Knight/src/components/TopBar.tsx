import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme';

type Props = { title: string; onMenu?: () => void; onBack?: () => void; onShare?: () => void };

export function TopBar({ title, onMenu, onBack, onShare }: Props) {
  const handler = onBack ?? onMenu;
  return (
    <View style={styles.bar}>
      <Pressable onPress={handler} hitSlop={12} style={styles.iconButton} android_ripple={{ color: '#ffffff22', radius: 24 }}>
        <MaterialCommunityIcons name={onBack ? 'arrow-left' : 'menu'} size={28} color={colors.text} />
      </Pressable>
      <Text style={styles.title}>{title}</Text>
      {onShare ? (
        <Pressable onPress={onShare} hitSlop={12} style={styles.iconButton} android_ripple={{ color: '#ffffff22', radius: 24 }}>
          <MaterialCommunityIcons name="share-variant-outline" size={24} color={colors.text} />
        </Pressable>
      ) : (
        <View style={styles.iconButton} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 12, height: 56 },
  iconButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 22 },
  title: { color: colors.text, fontSize: 20, fontWeight: '900', letterSpacing: 2.2, textTransform: 'uppercase' },
});
