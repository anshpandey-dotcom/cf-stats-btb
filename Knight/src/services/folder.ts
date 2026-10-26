import { Platform } from 'react-native';
import { StorageAccessFramework as SAF } from 'expo-file-system/legacy';
import { DATA_FILE_NAME, DATA_FOLDER_NAME } from '../config';
import { decodeAp, encodeAp } from '../lib/apCodec';
import { AppData, sanitizeData } from '../store/types';

const MIME = 'application/octet-stream';

function lastSegment(uri: string): string {
  const decoded = decodeURIComponent(uri);
  return decoded.split(/[/:]/).pop() ?? '';
}

export async function pickDocumentsFolder(): Promise<string | null> {
  if (Platform.OS !== 'android') return null;
  const result = await SAF.requestDirectoryPermissionsAsync(SAF.getUriForDirectoryInRoot('Documents'));
  return result.granted ? result.directoryUri : null;
}

async function ensureKnightFolder(rootUri: string): Promise<string> {
  const children = await SAF.readDirectoryAsync(rootUri);
  const existing = children.find((uri) => lastSegment(uri) === DATA_FOLDER_NAME);
  if (existing) return existing;
  return SAF.makeDirectoryAsync(rootUri, DATA_FOLDER_NAME);
}

async function findDataFile(folderUri: string): Promise<string | null> {
  const children = await SAF.readDirectoryAsync(folderUri);
  return children.find((uri) => lastSegment(uri) === DATA_FILE_NAME) ?? null;
}

export async function writeToFolder(rootUri: string, data: AppData): Promise<void> {
  const folder = await ensureKnightFolder(rootUri);
  let file = await findDataFile(folder);
  if (!file) file = await SAF.createFileAsync(folder, DATA_FILE_NAME, MIME);
  await SAF.writeAsStringAsync(file, encodeAp(data));
}

export async function readFromFolder(rootUri: string): Promise<AppData | null> {
  const folder = await ensureKnightFolder(rootUri);
  const file = await findDataFile(folder);
  if (!file) return null;
  const text = await SAF.readAsStringAsync(file);
  return sanitizeData(decodeAp(text));
}

export function describeFolder(uri: string | null): string {
  if (!uri) return 'Not connected';
  const decoded = decodeURIComponent(uri);
  const tail = decoded.split('tree/').pop() ?? decoded;
  return `${tail.replace(':', '/')}/${DATA_FOLDER_NAME}/${DATA_FILE_NAME}`;
}
