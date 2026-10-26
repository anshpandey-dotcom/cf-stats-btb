import AsyncStorage from '@react-native-async-storage/async-storage';
import { PomoState } from '../lib/pomodoro';

const KEY = 'knight.pomo.v1';

export async function loadPomo(): Promise<PomoState | null> {
  try {
    const raw = await AsyncStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as PomoState) : null;
  } catch {
    return null;
  }
}

export async function savePomo(state: PomoState | null): Promise<void> {
  try {
    if (state) await AsyncStorage.setItem(KEY, JSON.stringify(state));
    else await AsyncStorage.removeItem(KEY);
  } catch {}
}
