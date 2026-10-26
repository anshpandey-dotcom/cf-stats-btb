import React, { useState } from 'react';
import { Alert, Linking, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import { Button, Card, Row, SectionTitle } from '../components/ui';
import { PermissionsPanel } from '../components/PermissionsPanel';
import { TimeStepper } from '../components/TimeStepper';
import { INSTAGRAM_URL } from '../config';
import { decodeAp, encodeAp } from '../lib/apCodec';
import { formatLong } from '../lib/dates';
import { readFromFolder } from '../services/folder';
import { requestExactAlarms, requestNotifications } from '../services/reminders';
import { useApp } from '../store/AppStore';
import { AppData, sanitizeData } from '../store/types';
import { colors } from '../theme';

function ago(at: number | null): string {
  if (!at) return 'not yet';
  const seconds = Math.max(1, Math.round((Date.now() - at) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m ago`;
  return new Date(at).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}

export function Settings() {
  const { data, settings, today, sync, updateSettings, replaceData, resetAll, shiftDay, syncNow } = useApp();
  const [busy, setBusy] = useState<string | null>(null);

  const run = async (label: string, task: () => Promise<void>) => {
    setBusy(label);
    try {
      await task();
    } catch (e: any) {
      Alert.alert('Something went wrong', String(e?.message ?? e));
    } finally {
      setBusy(null);
    }
  };

  const confirmReplace = (next: AppData, source: string) => {
    const count = Object.values(next.days).filter((d) => d.done).length;
    Alert.alert(
      'Replace current data?',
      `${source} has ${count} completed day${count === 1 ? '' : 's'}. Your current data on this phone will be overwritten.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Replace', style: 'destructive', onPress: () => replaceData(next) },
      ],
    );
  };

  const toggleReminder = async (value: boolean) => {
    if (value && !(await requestNotifications())) {
      Alert.alert('Notifications are off', 'Allow notifications for Knight in system settings to get reminders.');
      return;
    }
    updateSettings({ reminderEnabled: value });
  };

  const toggleAlarm = async (value: boolean) => {
    if (value) {
      if (!(await requestNotifications())) {
        Alert.alert('Notifications are off', 'Allow notifications for Knight so the alarm can ring.');
        return;
      }
      await requestExactAlarms();
    }
    updateSettings({ alarmEnabled: value });
  };

  const exportData = () =>
    run('export', async () => {
      const stamp = today.replace(/-/g, '');
      const uri = `${FileSystem.cacheDirectory}knight-${stamp}.ap`;
      await FileSystem.writeAsStringAsync(uri, encodeAp(data));
      if (!(await Sharing.isAvailableAsync())) throw new Error('Sharing is not available on this device');
      await Sharing.shareAsync(uri, { mimeType: 'application/octet-stream', dialogTitle: 'Export Knight data' });
    });

  const importData = () =>
    run('import', async () => {
      const picked = await DocumentPicker.getDocumentAsync({ type: '*/*', copyToCacheDirectory: true });
      if (picked.canceled || !picked.assets?.[0]) return;
      const text = await FileSystem.readAsStringAsync(picked.assets[0].uri);
      confirmReplace(sanitizeData(decodeAp(text)), 'This file');
    });

  const restoreFromFolder = () =>
    run('folder', async () => {
      if (!settings.folderUri) return;
      const found = await readFromFolder(settings.folderUri);
      if (!found) {
        Alert.alert('Nothing to restore', 'No knight.ap file was found in Documents/Knight yet.');
        return;
      }
      confirmReplace(found, 'The Documents backup');
    });

  return (
    <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
      <SectionTitle style={{ marginTop: 4 }}>Reminders</SectionTitle>
      <Card>
        <Row
          icon="bell-ring-outline"
          title="Daily notification"
          subtitle="A nudge to start your ritual"
          right={<Switch value={settings.reminderEnabled} onValueChange={toggleReminder} trackColor={{ true: colors.greenDeep, false: colors.border }} thumbColor={settings.reminderEnabled ? colors.green : colors.muted} />}
        />
        {settings.reminderEnabled ? (
          <View style={styles.timeRow}>
            <Text style={styles.timeLabel}>Time</Text>
            <TimeStepper hour={settings.reminderHour} minute={settings.reminderMinute} onChange={(h, m) => updateSettings({ reminderHour: h, reminderMinute: m })} />
          </View>
        ) : null}
        <View style={styles.divider} />
        <Row
          icon="alarm"
          title="Wake-up alarm"
          subtitle="Loud alarm sound and long vibration"
          tint={colors.flame}
          right={<Switch value={settings.alarmEnabled} onValueChange={toggleAlarm} trackColor={{ true: colors.greenDeep, false: colors.border }} thumbColor={settings.alarmEnabled ? colors.green : colors.muted} />}
        />
        {settings.alarmEnabled ? (
          <View style={styles.timeRow}>
            <Text style={styles.timeLabel}>Time</Text>
            <TimeStepper hour={settings.alarmHour} minute={settings.alarmMinute} onChange={(h, m) => updateSettings({ alarmHour: h, alarmMinute: m })} />
          </View>
        ) : null}
      </Card>

      <SectionTitle>Permissions</SectionTitle>
      <Card>
        <PermissionsPanel />
      </Card>

      <SectionTitle>Backup & data</SectionTitle>
      <Card>
        <Row
          icon="content-save-outline"
          title="Local autosave"
          subtitle={`Every change is saved on this phone · ${ago(sync.local)}`}
        />
        <View style={styles.divider} />
        <Row
          icon="folder-outline"
          title="Documents/Knight/knight.ap"
          subtitle={
            settings.folderUri
              ? sync.folder.error
                ? `Failed: ${sync.folder.error}`
                : `Last written ${ago(sync.folder.at)}`
              : 'Allow the Documents folder under Permissions first'
          }
          tint={settings.folderUri && sync.folder.error ? colors.danger : colors.green}
        />
        {settings.folderUri ? (
          <View style={styles.buttons}>
            <Button compact variant="ghost" icon="upload" label="Save now" onPress={() => run('save', syncNow)} />
            <Button compact variant="ghost" icon="backup-restore" label="Restore" onPress={restoreFromFolder} />
          </View>
        ) : null}
        <View style={styles.divider} />
        <Row icon="export" title="Export data" subtitle="Share your .ap file anywhere" />
        <View style={styles.buttons}>
          <Button compact variant="ghost" icon="export" label="Export" onPress={exportData} />
          <Button compact variant="ghost" icon="import" label="Import" onPress={importData} />
        </View>
        {busy ? <Text style={styles.busy}>Working...</Text> : null}
      </Card>

      <SectionTitle>Test tools</SectionTitle>
      <Card>
        <Row
          icon="calendar-refresh"
          title="App date"
          subtitle={`${formatLong(today)}${settings.dayOffset ? `  (${settings.dayOffset > 0 ? '+' : ''}${settings.dayOffset} days)` : ''}`}
          tint={colors.gold}
        />
        <View style={styles.buttons}>
          <Button compact variant="ghost" icon="calendar-minus" label="Previous day" onPress={() => shiftDay(-1)} />
          <Button compact variant="gold" icon="calendar-plus" label="Next day" onPress={() => shiftDay(1)} />
          <Button compact variant="ghost" label="Today" onPress={() => shiftDay(0)} />
        </View>
        <View style={styles.divider} />
        <Row icon="delete-outline" title="Reset all progress" subtitle="Clears streak, XP and history" tint={colors.danger} />
        <View style={styles.buttons}>
          <Button
            compact
            variant="danger"
            label="Reset data"
            onPress={() =>
              Alert.alert('Reset everything?', 'This clears all streaks, XP and history on this phone.', [
                { text: 'Cancel', style: 'cancel' },
                { text: 'Reset', style: 'destructive', onPress: resetAll },
              ])
            }
          />
        </View>
      </Card>

      <View style={styles.footer}>
        <Text style={styles.madeBy}>
          Made By{' '}
          <Text style={styles.link} onPress={() => Linking.openURL(INSTAGRAM_URL)}>
            Ansh
          </Text>
        </Text>
        <Text style={styles.credits}>
          Knight model by Quaternius (CC0). Knight icon from Font Awesome Free (CC BY 4.0).
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { padding: 16, paddingBottom: 40 },
  divider: { height: 1, backgroundColor: colors.border, marginVertical: 8 },
  timeRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingLeft: 52, paddingBottom: 4 },
  timeLabel: { color: colors.muted, fontSize: 14, fontWeight: '700' },
  buttons: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, paddingLeft: 52, paddingBottom: 6 },
  busy: { color: colors.gold, textAlign: 'center', marginTop: 8 },
  footer: { alignItems: 'center', marginTop: 34 },
  madeBy: { color: colors.muted, fontSize: 15, fontWeight: '600' },
  link: { color: colors.link, fontWeight: '900' },
  credits: { color: colors.muted, fontSize: 11, marginTop: 14, textAlign: 'center', paddingHorizontal: 24 },
});
