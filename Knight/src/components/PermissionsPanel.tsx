import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Platform, StyleSheet, Text, View } from 'react-native';
import { ExpoSpeechRecognitionModule } from 'expo-speech-recognition';
import { Button, Row } from './ui';
import { describeFolder, pickDocumentsFolder } from '../services/folder';
import {
  exactAlarmsGranted,
  notificationsGranted,
  requestExactAlarms,
  requestNotifications,
} from '../services/reminders';
import { useApp } from '../store/AppStore';
import { colors } from '../theme';

type Status = { notifications: boolean; alarms: boolean; mic: boolean };

function Chip({ ok }: { ok: boolean }) {
  return (
    <View style={[styles.chip, { backgroundColor: ok ? colors.green + '26' : colors.danger + '26' }]}>
      <Text style={[styles.chipText, { color: ok ? colors.green : colors.danger }]}>{ok ? 'ON' : 'OFF'}</Text>
    </View>
  );
}

export function PermissionsPanel() {
  const { settings, updateSettings } = useApp();
  const [status, setStatus] = useState<Status>({ notifications: false, alarms: false, mic: false });

  const refresh = useCallback(async () => {
    const [notifications, alarms] = await Promise.all([notificationsGranted(), exactAlarmsGranted()]);
    let mic = false;
    try {
      const result = await ExpoSpeechRecognitionModule.getPermissionsAsync();
      mic = result.granted;
    } catch {}
    setStatus({ notifications, alarms, mic });
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const allowNotifications = async () => {
    await requestNotifications();
    refresh();
  };

  const allowAlarms = async () => {
    await requestExactAlarms();
    refresh();
  };

  const allowMic = async () => {
    try {
      await ExpoSpeechRecognitionModule.requestPermissionsAsync();
    } catch {}
    refresh();
  };

  const chooseFolder = async () => {
    try {
      const uri = await pickDocumentsFolder();
      if (uri) updateSettings({ folderUri: uri });
      else Alert.alert('Folder not selected', 'Pick your Documents folder and tap "Use this folder" to enable the Knight backup file.');
    } catch (e: any) {
      Alert.alert('Folder access failed', String(e?.message ?? e));
    }
  };

  return (
    <View>
      <Row
        icon="bell-outline"
        title="Notifications"
        subtitle="Daily Morning Ritual reminder"
        right={status.notifications ? <Chip ok /> : <Button compact label="Allow" onPress={allowNotifications} />}
      />
      {Platform.OS === 'android' ? (
        <Row
          icon="alarm"
          title="Exact alarms"
          subtitle="Lets the alarm ring on the exact minute"
          right={status.alarms ? <Chip ok /> : <Button compact label="Allow" onPress={allowAlarms} />}
        />
      ) : null}
      <Row
        icon="microphone-outline"
        title="Microphone & speech"
        subtitle="Voice declaration"
        right={status.mic ? <Chip ok /> : <Button compact label="Allow" onPress={allowMic} />}
      />
      <Row
        icon="folder-open-outline"
        title="Documents folder"
        subtitle={settings.folderUri ? describeFolder(settings.folderUri) : 'Saves your data as a .ap file'}
        right={
          settings.folderUri ? (
            <Button compact variant="ghost" label="Change" onPress={chooseFolder} />
          ) : (
            <Button compact label="Allow" onPress={chooseFolder} />
          )
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  chip: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 8 },
  chipText: { fontSize: 12, fontWeight: '900', letterSpacing: 1 },
});
