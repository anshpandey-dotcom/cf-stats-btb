import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { Settings } from '../store/types';

const REMINDER_CHANNEL = 'knight-reminder';
const ALARM_CHANNEL = 'knight-alarm';
const LIVE_CHANNEL = 'knight-live';
const HORIZON_DAYS = 14;

const REMINDER_LINES = [
  'Rise, warrior. Your declaration is waiting.',
  'The 1% are already awake. Say it. Own it.',
  'One minute now buys you the whole day. Begin the ritual.',
  'Your streak is watching. Do not break the chain.',
];

const ALARM_LINES = [
  'WAKE UP. The warrior mentality starts now.',
  'Up. Out of bed. Morning Ritual time.',
];

export function setupNotificationHandling() {
  Notifications.setNotificationHandler({
    handleNotification: async (notification) => {
      const quiet = String(notification.request.content.data?.kind ?? '').startsWith('pomo');
      return {
        shouldShowBanner: !quiet,
        shouldShowList: !quiet,
        shouldPlaySound: !quiet,
        shouldSetBadge: false,
      };
    },
  });
}

async function ensureChannels() {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(REMINDER_CHANNEL, {
    name: 'Morning Ritual reminder',
    importance: Notifications.AndroidImportance.HIGH,
    vibrationPattern: [0, 250, 200, 250],
    lightColor: '#22C55E',
  });
  await Notifications.setNotificationChannelAsync(LIVE_CHANNEL, {
    name: 'Pomodoro running',
    importance: Notifications.AndroidImportance.LOW,
    vibrationPattern: [0],
    sound: null,
  });
  await Notifications.setNotificationChannelAsync(ALARM_CHANNEL, {
    name: 'Morning alarm',
    importance: Notifications.AndroidImportance.MAX,
    sound: 'default',
    vibrationPattern: [0, 800, 400, 800, 400, 800, 400, 800],
    bypassDnd: true,
    lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
    audioAttributes: {
      usage: Notifications.AndroidAudioUsage.ALARM,
      contentType: Notifications.AndroidAudioContentType.SONIFICATION,
    },
  });
}

export async function notificationsGranted(): Promise<boolean> {
  const status = await Notifications.getPermissionsAsync();
  return status.granted;
}

export async function requestNotifications(): Promise<boolean> {
  await ensureChannels();
  const current = await Notifications.getPermissionsAsync();
  if (current.granted) return true;
  const asked = await Notifications.requestPermissionsAsync();
  return asked.granted;
}

export async function exactAlarmsGranted(): Promise<boolean> {
  if (Platform.OS !== 'android') return true;
  try {
    const status = await Notifications.getExactAlarmPermissionsAsync();
    return status.granted;
  } catch {
    return true;
  }
}

export async function requestExactAlarms(): Promise<boolean> {
  if (Platform.OS !== 'android') return true;
  try {
    const status = await Notifications.requestExactAlarmPermissionsAsync();
    return status.granted;
  } catch {
    return false;
  }
}

function slot(base: Date, dayShift: number, hour: number, minute: number): Date {
  const date = new Date(base);
  date.setDate(date.getDate() + dayShift);
  date.setHours(hour, minute, 0, 0);
  return date;
}

async function cancelKind(kind: string) {
  const pending = await Notifications.getAllScheduledNotificationsAsync();
  await Promise.all(
    pending
      .filter((item) => item.content.data?.kind === kind)
      .map((item) => Notifications.cancelScheduledNotificationAsync(item.identifier)),
  );
}

export async function cancelPomoAlerts() {
  await cancelKind('pomo');
}

export async function schedulePomoAlerts(boundaries: { at: number; phase: 'work' | 'break'; rung: number }[]) {
  await cancelKind('pomo');
  if (!(await notificationsGranted())) return;
  await ensureChannels();
  const now = Date.now();
  for (const item of boundaries) {
    if (item.at <= now + 2000) continue;
    const isWork = item.phase === 'work';
    await Notifications.scheduleNotificationAsync({
      content: {
        title: isWork ? 'Checkpoint reached' : 'Break over',
        body: isWork ? `Rung ${item.rung + 1} conquered. Sit and rest for 5 minutes.` : 'Back on the ladder. Keep climbing.',
        data: { kind: 'pomo', route: 'pomodoro' },
        priority: Notifications.AndroidNotificationPriority.MAX,
      },
      trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: new Date(item.at), channelId: ALARM_CHANNEL },
    });
  }
}

export async function rescheduleAll(settings: Settings, doneToday: boolean): Promise<number> {
  await cancelKind('reminder');
  if (!settings.reminderEnabled && !settings.alarmEnabled) return 0;
  const granted = await notificationsGranted();
  if (!granted) return 0;
  await ensureChannels();

  const now = new Date();
  let count = 0;
  for (let i = 0; i < HORIZON_DAYS; i++) {
    const skip = i === 0 && doneToday;
    if (skip) continue;
    if (settings.reminderEnabled) {
      const when = slot(now, i, settings.reminderHour, settings.reminderMinute);
      if (when.getTime() > now.getTime() + 5000) {
        await Notifications.scheduleNotificationAsync({
          content: {
            title: 'Knight · Morning Ritual',
            body: REMINDER_LINES[(now.getDate() + i) % REMINDER_LINES.length],
            data: { kind: 'reminder', route: 'declaration' },
          },
          trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: when, channelId: REMINDER_CHANNEL },
        });
        count += 1;
      }
    }
    if (settings.alarmEnabled) {
      const when = slot(now, i, settings.alarmHour, settings.alarmMinute);
      if (when.getTime() > now.getTime() + 5000) {
        await Notifications.scheduleNotificationAsync({
          content: {
            title: 'Knight · Alarm',
            body: ALARM_LINES[(now.getDate() + i) % ALARM_LINES.length],
            data: { kind: 'reminder', route: 'declaration' },
            priority: Notifications.AndroidNotificationPriority.MAX,
          },
          trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: when, channelId: ALARM_CHANNEL },
        });
        count += 1;
      }
    }
  }
  return count;
}

export async function setupPomoCategory() {
  try {
    await Notifications.setNotificationCategoryAsync('pomo', [
      { identifier: 'skip', buttonTitle: 'Skip', options: { opensAppToForeground: true } },
      { identifier: 'stop', buttonTitle: 'Stop', options: { opensAppToForeground: true } },
    ]);
  } catch {}
}

export async function showPomoLive(info: { phase: 'work' | 'break'; rung: number; endsAt: number; paused: boolean }) {
  if (!(await notificationsGranted())) return;
  await ensureChannels();
  const time = new Date(info.endsAt).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  const label = info.phase === 'work' ? 'Climbing' : 'Resting at checkpoint';
  await Notifications.scheduleNotificationAsync({
    identifier: 'pomo-live',
    content: {
      title: 'Knight · Pomodoro Ladder',
      body: info.paused ? `Paused · rung ${info.rung + 1}` : `${label} · rung ${info.rung + 1} · until ${time}`,
      data: { kind: 'pomo-live', route: 'pomodoro' },
      categoryIdentifier: 'pomo',
      sticky: true,
      autoDismiss: false,
    },
    trigger: { channelId: LIVE_CHANNEL },
  });
}

export async function hidePomoLive() {
  try {
    await Notifications.dismissNotificationAsync('pomo-live');
    await Notifications.cancelScheduledNotificationAsync('pomo-live');
  } catch {}
}
