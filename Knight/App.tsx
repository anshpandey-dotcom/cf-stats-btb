import React, { useCallback, useEffect, useState } from 'react';
import { BackHandler, StyleSheet, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import * as Notifications from 'expo-notifications';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { Celebration } from './src/components/Celebration';
import { Drawer, Route } from './src/components/Drawer';
import { StatsHeader } from './src/components/StatsHeader';
import { TopBar } from './src/components/TopBar';
import { Dashboard } from './src/screens/Dashboard';
import { Declaration } from './src/screens/Declaration';
import { Onboarding } from './src/screens/Onboarding';
import { Pomodoro } from './src/screens/Pomodoro';
import { Ritual } from './src/screens/Ritual';
import { Settings } from './src/screens/Settings';
import { setupNotificationHandling } from './src/services/reminders';
import { AppProvider, Reward, useApp } from './src/store/AppStore';
import { colors } from './src/theme';

setupNotificationHandling();

const TITLES: Record<Route, string> = {
  dashboard: 'Knight',
  ritual: 'Morning Ritual',
  declaration: 'Declaration',
  pomodoro: 'Pomodoro Ladder',
  settings: 'Settings',
};

function Shell() {
  const insets = useSafeAreaInsets();
  const { ready } = useApp();
  const [route, setRoute] = useState<Route>('dashboard');
  const [menuOpen, setMenuOpen] = useState(false);
  const [reward, setReward] = useState<Reward | null>(null);

  const go = useCallback((next: Route) => {
    setMenuOpen(false);
    setRoute(next);
  }, []);

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (menuOpen) {
        setMenuOpen(false);
        return true;
      }
      if (route === 'declaration') {
        setRoute('ritual');
        return true;
      }
      if (route !== 'dashboard') {
        setRoute('dashboard');
        return true;
      }
      return false;
    });
    return () => sub.remove();
  }, [menuOpen, route]);

  useEffect(() => {
    const open = (response: Notifications.NotificationResponse | null) => {
      const target = response?.notification.request.content.data?.route;
      if (target === 'declaration') setRoute('declaration');
    };
    open(Notifications.getLastNotificationResponse());
    const sub = Notifications.addNotificationResponseReceivedListener(open);
    return () => sub.remove();
  }, []);

  const handleComplete = useCallback((earned: Reward | null) => {
    if (earned) setReward(earned);
  }, []);

  if (!ready) return <View style={styles.root} />;

  const nested = route === 'declaration';

  return (
    <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
      <StatusBar style="light" />
      <TopBar
        title={TITLES[route]}
        onMenu={() => setMenuOpen(true)}
        onBack={nested ? () => setRoute('ritual') : undefined}
      />
      {route === 'dashboard' || route === 'ritual' ? <StatsHeader /> : null}
      <View style={{ flex: 1 }}>
        {route === 'dashboard' ? <Dashboard go={go} /> : null}
        {route === 'ritual' ? <Ritual go={go} /> : null}
        {route === 'declaration' ? <Declaration onComplete={handleComplete} /> : null}
        {route === 'pomodoro' ? <Pomodoro /> : null}
        {route === 'settings' ? <Settings /> : null}
      </View>
      <Drawer open={menuOpen} current={route} onClose={() => setMenuOpen(false)} onNavigate={go} />
      <Celebration
        reward={reward}
        onClose={() => {
          setReward(null);
          setRoute('dashboard');
        }}
      />
      <Onboarding />
    </View>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <AppProvider>
        <Shell />
      </AppProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
});
