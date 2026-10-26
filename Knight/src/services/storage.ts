import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_DATA_KEY, STORAGE_SETTINGS_KEY } from '../config';
import { AppData, DEFAULT_SETTINGS, EMPTY_DATA, Settings, sanitizeData } from '../store/types';

export async function loadData(): Promise<AppData> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_DATA_KEY);
    if (!raw) return { ...EMPTY_DATA };
    return sanitizeData(JSON.parse(raw));
  } catch {
    return { ...EMPTY_DATA };
  }
}

export async function saveData(data: AppData): Promise<void> {
  await AsyncStorage.setItem(STORAGE_DATA_KEY, JSON.stringify(data));
}

export async function loadSettings(): Promise<Settings> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_SETTINGS_KEY);
    if (!raw) return { ...DEFAULT_SETTINGS };
    return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export async function saveSettings(settings: Settings): Promise<void> {
  await AsyncStorage.setItem(STORAGE_SETTINGS_KEY, JSON.stringify(settings));
}
