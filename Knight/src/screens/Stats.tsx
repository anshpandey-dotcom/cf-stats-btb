import React, { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Button, Card } from '../components/ui';
import { dateFromKey, formatShort } from '../lib/dates';
import { PERIODS, PeriodId, allTime, bestPomo, buildBars, sumRange, trendFor, windowFor } from '../lib/stats';
import { shareStats } from '../services/share';
import { useApp } from '../store/AppStore';
import { colors } from '../theme';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function labelFor(period: PeriodId, end: string): string {
  const date = dateFromKey(end);
  if (period === 'daily') return WEEKDAYS[date.getDay()];
  if (period === 'weekly') return formatShort(end);
  return MONTHS[date.getMonth()];
}

function Tile({ icon, tint, value, label }: { icon: any; tint: string; value: string; label: string }) {
  return (
    <View style={styles.tile}>
      <MaterialCommunityIcons name={icon} size={22} color={tint} />
      <Text style={styles.tileValue}>{value}</Text>
      <Text style={styles.tileLabel}>{label}</Text>
    </View>
  );
}

export function Stats() {
  const { data, today, streak, longest, xp } = useApp();
  const [period, setPeriod] = useState<PeriodId>('weekly');
  const config = PERIODS.find((p) => p.id === period)!;
  const days = data.days;

  const bars = useMemo(
    () => buildBars(days, today, config.bucketDays, config.buckets, (end) => labelFor(period, end)),
    [days, today, config, period],
  );
  const max = Math.max(1, ...bars.map((b) => b.value));
  const windowDays = windowFor(period);
  const trend = useMemo(() => trendFor(days, today, windowDays), [days, today, windowDays]);
  const totals = useMemo(() => (period === 'lifetime' ? allTime(days) : sumRange(days, today, windowDays)), [days, today, period, windowDays]);
  const best = useMemo(() => bestPomo(days), [days]);

  const trendColor = trend.direction === 'up' ? colors.green : trend.direction === 'down' ? colors.danger : colors.muted;
  const trendIcon = trend.direction === 'up' ? 'trending-up' : trend.direction === 'down' ? 'trending-down' : 'trending-neutral';
  const compareLabel =
    period === 'daily' ? 'yesterday' : period === 'weekly' ? 'the previous 7 days' : period === 'yearly' ? 'the previous year' : 'the previous 30 days';
  const verdict =
    trend.direction === 'up'
      ? `You are improving. ${trend.percent}% more XP than ${compareLabel}.`
      : trend.direction === 'down'
        ? `Performance dropped ${trend.percent}% against ${compareLabel}. Time to push.`
        : trend.current === 0
          ? 'No activity yet. Complete a ritual to start your chart.'
          : `Holding steady versus ${compareLabel}.`;

  return (
    <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
        {PERIODS.map((item) => (
          <Pressable key={item.id} onPress={() => setPeriod(item.id)} style={[styles.chip, period === item.id && styles.chipOn]}>
            <Text style={[styles.chipText, period === item.id && { color: '#04140A' }]}>{item.label}</Text>
          </Pressable>
        ))}
      </ScrollView>

      <Card style={{ marginTop: 14 }}>
        <View style={styles.trendRow}>
          <View style={[styles.trendIcon, { backgroundColor: trendColor + '22' }]}>
            <MaterialCommunityIcons name={trendIcon} size={28} color={trendColor} />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={[styles.trendTitle, { color: trendColor }]}>
              {trend.direction === 'up' ? 'Improving' : trend.direction === 'down' ? 'Dropping' : 'Steady'}
              {trend.direction !== 'flat' ? `  ${trend.direction === 'up' ? '+' : '-'}${trend.percent}%` : ''}
            </Text>
            <Text style={styles.trendText}>{verdict}</Text>
          </View>
        </View>
      </Card>

      <Card style={{ marginTop: 14 }}>
        <Text style={styles.cardTitle}>XP earned</Text>
        <View style={styles.chart}>
          {bars.map((bar, i) => (
            <View key={`${bar.end}-${i}`} style={styles.barCol}>
              <Text style={styles.barValue}>{bar.value > 0 ? bar.value : ''}</Text>
              <View style={styles.barTrack}>
                <View
                  style={[
                    styles.bar,
                    { height: `${Math.max(bar.value > 0 ? 6 : 0, (bar.value / max) * 100)}%` },
                    i === bars.length - 1 && { backgroundColor: colors.gold },
                  ]}
                />
              </View>
              <Text style={styles.barLabel} numberOfLines={1}>{bar.label}</Text>
            </View>
          ))}
        </View>
      </Card>

      <View style={styles.grid}>
        <Tile icon="lightning-bolt" tint={colors.gold} value={totals.xp.toLocaleString('en-US')} label="XP" />
        <Tile
          icon="weather-sunset-up"
          tint={colors.green}
          value={period === 'lifetime' ? `${totals.ritualDays}` : `${totals.ritualDays}/${totals.days}`}
          label="Rituals done"
        />
        <Tile icon="timer-outline" tint={colors.flame} value={`${totals.focusMinutes}m`} label="Focus time" />
        <Tile icon="meditation" tint={colors.link} value={`${totals.calmMinutes}m`} label="Mindful time" />
      </View>

      <Card style={{ marginTop: 14 }}>
        <Text style={styles.cardTitle}>Records</Text>
        <View style={styles.record}>
          <MaterialCommunityIcons name="fire" size={22} color={colors.flame} />
          <Text style={styles.recordLabel}>Current streak</Text>
          <Text style={styles.recordValue}>{streak} days</Text>
        </View>
        <View style={styles.record}>
          <MaterialCommunityIcons name="trophy-outline" size={22} color={colors.gold} />
          <Text style={styles.recordLabel}>Longest streak</Text>
          <Text style={styles.recordValue}>{longest} days</Text>
        </View>
        <View style={styles.record}>
          <MaterialCommunityIcons name="timer-sand" size={22} color={colors.green} />
          <Text style={styles.recordLabel}>Longest focus block</Text>
          <Text style={styles.recordValue}>{best.block ? `${best.block} min` : '-'}</Text>
        </View>
        <View style={styles.record}>
          <MaterialCommunityIcons name="stairs-up" size={22} color={colors.flame} />
          <Text style={styles.recordLabel}>Highest ladder rung</Text>
          <Text style={styles.recordValue}>{best.rung || '-'}</Text>
        </View>
      </Card>

      <Button
        label="Share my stats"
        icon="share-variant-outline"
        variant="gold"
        style={{ marginTop: 18 }}
        onPress={() => shareStats({ days, xp, streak, longest }).catch(() => {})}
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { padding: 16, paddingBottom: 30 },
  chips: { gap: 8 },
  chip: { paddingHorizontal: 16, paddingVertical: 9, borderRadius: 20, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border },
  chipOn: { backgroundColor: colors.green, borderColor: colors.green },
  chipText: { color: colors.muted, fontWeight: '800', fontSize: 13 },
  trendRow: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  trendIcon: { width: 52, height: 52, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  trendTitle: { fontSize: 20, fontWeight: '900' },
  trendText: { color: colors.muted, fontSize: 13, marginTop: 3, lineHeight: 18 },
  cardTitle: { color: colors.text, fontSize: 16, fontWeight: '800' },
  chart: { flexDirection: 'row', alignItems: 'flex-end', gap: 6, height: 190, marginTop: 14 },
  barCol: { flex: 1, height: '100%', alignItems: 'center', justifyContent: 'flex-end' },
  barValue: { color: colors.muted, fontSize: 9, fontWeight: '700', height: 14 },
  barTrack: { flex: 1, width: '100%', justifyContent: 'flex-end', alignItems: 'center' },
  bar: { width: '78%', backgroundColor: colors.green, borderTopLeftRadius: 6, borderTopRightRadius: 6 },
  barLabel: { color: colors.muted, fontSize: 9, fontWeight: '700', marginTop: 6, width: '100%', textAlign: 'center' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 14 },
  tile: { width: '48%', flexGrow: 1, backgroundColor: colors.card, borderRadius: 20, borderWidth: 1, borderColor: colors.border, padding: 14, gap: 4 },
  tileValue: { color: colors.text, fontSize: 24, fontWeight: '900' },
  tileLabel: { color: colors.muted, fontSize: 12, fontWeight: '700' },
  record: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 10 },
  recordLabel: { color: colors.muted, flex: 1, fontSize: 14, fontWeight: '600' },
  recordValue: { color: colors.text, fontSize: 15, fontWeight: '900' },
});
