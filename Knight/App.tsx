import React, { useCallback, useEffect, useRef, useState } from 'react';
import { BackHandler, Platform, StyleSheet, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import * as Notifications from 'expo-notifications';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { BottomTabs } from './src/components/BottomTabs';
import { Celebration, LevelUp } from './src/components/Celebration';
import { Drawer, Route } from './src/components/Drawer';
import { PomoToast } from './src/components/PomoToast';
import { StatsHeader } from './src/components/StatsHeader';
import { TopBar } from './src/components/TopBar';
import { titleForLevel } from './src/lib/xp';
import { Breathe, Calm } from './src/screens/Calm';
import { Declaration } from './src/screens/Declaration';
import { Home } from './src/screens/Home';
import { Onboarding } from './src/screens/Onboarding';
import { Pomodoro } from './src/screens/Pomodoro';
import { Ritual } from './src/screens/Ritual';
import { Settings } from './src/screens/Settings';
import { Stats } from './src/screens/Stats';
import { setupNotificationHandling, setupPomoCategory } from './src/services/reminders';
import { shareLevel, shareStats } from './src/services/share';
import { AppProvider, Reward, useApp } from './src/store/AppStore';
import { colors } from './src/theme';

setupNotificationHandling();
setupPomoCategory();

const TITLES: Record<Route, string> = {
  home: 'Knight',
  stats: 'Stats',
  calm: 'Calm',
  ritual: 'Morning Ritual',
  declaration: 'Declaration',
  pomodoro: 'Pomodoro Ladder',
  breathe: 'Breathe',
  settings: 'Settings',
};

const TABS: Route[] = ['home', 'stats', 'calm'];
const BACK_TARGET: Partial<Record<Route, Route>> = {
  declaration: 'ritual',
  ritual: 'home',
  pomodoro: 'home',
  breathe: 'calm',
  settings: 'home',
  stats: 'home',
  calm: 'home',
};

function Shell() {
  const insets = useSafeAreaInsets();
  const { ready, data, xp, streak, longest, level, pomoNotice, clearPomoNotice, pomo, skipPomo, stopPomo } = useApp();
  const [route, setRoute] = useState<Route>('home');
  const [menuOpen, setMenuOpen] = useState(false);
  const [reward, setReward] = useState<Reward | null>(null);
  const [levelUp, setLevelUp] = useState<number | null>(null);
  const lastLevel = useRef<number | null>(null);

  const go = useCallback((next: Route) => {
    setMenuOpen(false);
    setRoute(next);
  }, []);

  useEffect(() => {
    if (!ready) return;
    if (lastLevel.current !== null && level.level > lastLevel.current) setLevelUp(level.level);
    lastLevel.current = level.level;
  }, [level.level, ready]);

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (menuOpen) {
        setMenuOpen(false);
        return true;
      }
      const target = BACK_TARGET[route];
      if (target) {
        setRoute(target);
        return true;
      }
      return false;
    });
    return () => sub.remove();
  }, [menuOpen, route]);

  useEffect(() => {
    if (Platform.OS === 'web') return;
    const open = (response: Notifications.NotificationResponse | null) => {
      if (!response) return;
      const action = response.actionIdentifier;
      if (action === 'skip') skipPomo();
      else if (action === 'stop') stopPomo();
      const target = response.notification.request.content.data?.route;
      if (target === 'declaration' || target === 'pomodoro') setRoute(target);
    };
    open(Notifications.getLastNotificationResponse());
    const sub = Notifications.addNotificationResponseReceivedListener(open);
    return () => sub.remove();
  }, [skipPomo, stopPomo]);

  const handleComplete = useCallback((earned: Reward | null) => {
    if (earned) setReward(earned);
  }, []);

  if (!ready) return <View style={styles.root} />;

  const fullPomo = route === 'pomodoro' && !!pomo;
  const isTab = TABS.includes(route);
  const backTarget = BACK_TARGET[route];
  const snapshot = { days: data.days, xp, streak, longest };

  if (fullPomo) {
    return (
      <View style={styles.root}>
        <StatusBar style="light" />
        <Pomodoro onBack={() => setRoute('home')} />
        <Celebration reward={reward} onClose={() => { setReward(null); setRoute('home'); }} />
        <LevelUp level={reward ? null : levelUp} title={titleForLevel(levelUp ?? 1)} onShare={() => shareLevel(snapshot).catch(() => {})} onClose={() => setLevelUp(null)} />
      </View>
    );
  }

  return (
    <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
      <StatusBar style="light" />
      <TopBar
        title={TITLES[route]}
        onMenu={() => setMenuOpen(true)}
        onBack={!isTab && backTarget ? () => setRoute(backTarget) : undefined}
        onShare={isTab ? () => shareStats(snapshot).catch(() => {}) : undefined}
      />
      {isTab || route === 'ritual' ? <StatsHeader /> : null}
      <View style={{ flex: 1 }}>
        {route === 'home' ? <Home go={go} /> : null}
        {route === 'stats' ? <Stats /> : null}
        {route === 'calm' ? <Calm go={go} /> : null}
        {route === 'breathe' ? <Breathe onExit={() => setRoute('calm')} /> : null}
        {route === 'ritual' ? <Ritual go={go} /> : null}
        {route === 'declaration' ? <Declaration onComplete={handleComplete} /> : null}
        {route === 'pomodoro' ? <Pomodoro onBack={() => setRoute('home')} /> : null}
        {route === 'settings' ? <Settings /> : null}
      </View>
      {isTab ? <BottomTabs current={route} onSelect={go} /> : null}
      <Drawer open={menuOpen} current={route} onClose={() => setMenuOpen(false)} onNavigate={go} />
      <PomoToast
        notice={route === 'pomodoro' ? null : pomoNotice}
        onPress={() => {
          clearPomoNotice();
          setRoute('pomodoro');
        }}
        onDone={clearPomoNotice}
      />
      <Celebration
        reward={reward}
        onClose={() => {
          setReward(null);
          setRoute('home');
        }}
      />
      <LevelUp
        level={reward ? null : levelUp}
        title={titleForLevel(levelUp ?? 1)}
        onShare={() => shareLevel(snapshot).catch(() => {})}
        onClose={() => setLevelUp(null)}
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
