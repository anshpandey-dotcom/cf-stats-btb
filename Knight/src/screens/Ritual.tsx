import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RITUAL_SECTIONS } from '../lib/ritual';
import { useApp } from '../store/AppStore';
import { colors } from '../theme';
import type { Route } from '../components/Drawer';

export function Ritual({ go }: { go: (route: Route) => void }) {
  const { data, today, todayDone } = useApp();
  const record = data.days[today];

  return (
    <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
      <Text style={styles.title}>Morning Ritual</Text>
      <Text style={styles.sub}>
        {todayDone ? 'Today is conquered. Come back tomorrow.' : 'Complete the required step to lock in your streak.'}
      </Text>

      {RITUAL_SECTIONS.map((section, index) => {
        const done = !!record?.sections[section.id];
        const locked = !section.ready;
        return (
          <Pressable
            key={section.id}
            disabled={locked}
            onPress={() => go('declaration')}
            style={[styles.card, done && styles.cardDone, locked && styles.cardLocked]}
          >
            <View style={styles.index}>
              <Text style={styles.indexText}>{index + 1}</Text>
            </View>
            <View style={[styles.iconBox, { backgroundColor: (done ? colors.green : colors.gold) + '22' }]}>
              <MaterialCommunityIcons name={section.icon} size={26} color={done ? colors.green : locked ? colors.muted : colors.gold} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={[styles.cardTitle, locked && { color: colors.muted }]}>{section.title}</Text>
              <Text style={styles.cardSub}>{section.subtitle}</Text>
              {section.required ? <Text style={styles.required}>REQUIRED · STREAK + XP</Text> : null}
            </View>
            {done ? (
              <MaterialCommunityIcons name="check-circle" size={28} color={colors.green} />
            ) : locked ? (
              <View style={styles.soon}>
                <MaterialCommunityIcons name="lock-outline" size={14} color={colors.muted} />
                <Text style={styles.soonText}>SOON</Text>
              </View>
            ) : (
              <MaterialCommunityIcons name="chevron-right" size={28} color={colors.text} />
            )}
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { padding: 16, paddingBottom: 40 },
  title: { color: colors.text, fontSize: 28, fontWeight: '900' },
  sub: { color: colors.muted, fontSize: 14, marginTop: 4, marginBottom: 18 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.card,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14,
    marginBottom: 12,
  },
  cardDone: { borderColor: colors.green },
  cardLocked: { opacity: 0.6 },
  index: { width: 22, alignItems: 'center' },
  indexText: { color: colors.muted, fontWeight: '900', fontSize: 14 },
  iconBox: { width: 48, height: 48, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  cardTitle: { color: colors.text, fontSize: 17, fontWeight: '800' },
  cardSub: { color: colors.muted, fontSize: 12, marginTop: 2 },
  required: { color: colors.gold, fontSize: 10, fontWeight: '900', letterSpacing: 1, marginTop: 6 },
  soon: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  soonText: { color: colors.muted, fontSize: 11, fontWeight: '900', letterSpacing: 1 },
});
