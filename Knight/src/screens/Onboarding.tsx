import React from 'react';
import { Image, Modal, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Button } from '../components/ui';
import { PermissionsPanel } from '../components/PermissionsPanel';
import { useApp } from '../store/AppStore';
import { colors } from '../theme';

export function Onboarding() {
  const { ready, settings, updateSettings } = useApp();
  if (!ready || settings.onboarded) return null;
  return (
    <Modal animationType="slide" visible onRequestClose={() => {}}>
      <View style={styles.root}>
        <ScrollView contentContainerStyle={styles.scroll}>
          <Image source={require('../../assets/icon.png')} style={styles.logo} />
          <Text style={styles.title}>Welcome to Knight</Text>
          <Text style={styles.sub}>
            Win the morning and the day follows. Turn these on now so reminders, voice and your backup file work from day one.
          </Text>
          <View style={styles.panel}>
            <PermissionsPanel />
          </View>
        </ScrollView>
        <View style={styles.footer}>
          <Button label="Enter the arena" icon="sword-cross" onPress={() => updateSettings({ onboarded: true })} />
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  scroll: { padding: 24, paddingTop: 64 },
  logo: { width: 96, height: 96, borderRadius: 26, alignSelf: 'center' },
  title: { color: colors.text, fontSize: 30, fontWeight: '900', textAlign: 'center', marginTop: 20 },
  sub: { color: colors.muted, fontSize: 15, lineHeight: 22, textAlign: 'center', marginTop: 10 },
  panel: { backgroundColor: colors.card, borderRadius: 24, borderWidth: 1, borderColor: colors.border, padding: 14, marginTop: 28 },
  footer: { padding: 20, paddingBottom: 32 },
});
