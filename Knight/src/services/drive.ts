import { GoogleSignin } from '@react-native-google-signin/google-signin';
import { DATA_FILE_NAME, DATA_FOLDER_NAME, DRIVE_SCOPE } from '../config';
import { decodeAp, encodeAp } from '../lib/apCodec';
import { AppData, sanitizeData } from '../store/types';

const API = 'https://www.googleapis.com/drive/v3';
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3';

let configured = false;

function configure() {
  if (configured) return;
  GoogleSignin.configure({ scopes: [DRIVE_SCOPE] });
  configured = true;
}

async function accessToken(): Promise<string> {
  configure();
  const tokens = await GoogleSignin.getTokens();
  return tokens.accessToken;
}

async function call(path: string, init: RequestInit = {}, base = API): Promise<Response> {
  const token = await accessToken();
  const response = await fetch(`${base}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, ...(init.headers ?? {}) },
  });
  if (response.status === 401) {
    await GoogleSignin.clearCachedAccessToken(token);
    const retryToken = await accessToken();
    return fetch(`${base}${path}`, {
      ...init,
      headers: { Authorization: `Bearer ${retryToken}`, ...(init.headers ?? {}) },
    });
  }
  return response;
}

async function check(response: Response, label: string): Promise<Response> {
  if (!response.ok) throw new Error(`${label} failed (${response.status})`);
  return response;
}

export async function connectDrive(): Promise<string> {
  configure();
  await GoogleSignin.hasPlayServices();
  const result: any = await GoogleSignin.signIn();
  const email = result?.data?.user?.email ?? result?.user?.email;
  if (!email) throw new Error('Google sign-in was cancelled');
  return email;
}

export async function disconnectDrive(): Promise<void> {
  configure();
  try {
    await GoogleSignin.signOut();
  } catch {}
}

export function driveSetupHint(error: unknown): string {
  const text = String((error as any)?.message ?? error);
  if (/DEVELOPER_ERROR|\b10\b/.test(text)) {
    return 'Google Drive is not set up for this build yet. Create an Android OAuth client for package com.anshpandey.knight with this build\'s SHA-1, and enable the Drive API.';
  }
  return text;
}

async function findFirst(query: string): Promise<string | null> {
  const url = `/files?spaces=drive&fields=files(id)&pageSize=1&q=${encodeURIComponent(query)}`;
  const response = await check(await call(url), 'Drive lookup');
  const json = await response.json();
  return json.files?.[0]?.id ?? null;
}

async function ensureFolder(): Promise<string> {
  const existing = await findFirst(
    `name='${DATA_FOLDER_NAME}' and mimeType='application/vnd.google-apps.folder' and trashed=false`,
  );
  if (existing) return existing;
  const response = await check(
    await call('/files?fields=id', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: DATA_FOLDER_NAME, mimeType: 'application/vnd.google-apps.folder' }),
    }),
    'Drive folder',
  );
  return (await response.json()).id;
}

export async function uploadToDrive(data: AppData): Promise<void> {
  const folderId = await ensureFolder();
  const fileId = await findFirst(`name='${DATA_FILE_NAME}' and '${folderId}' in parents and trashed=false`);
  const payload = encodeAp(data);
  if (fileId) {
    await check(
      await call(
        `/files/${fileId}?uploadType=media`,
        { method: 'PATCH', headers: { 'Content-Type': 'application/octet-stream' }, body: payload },
        UPLOAD,
      ),
      'Drive upload',
    );
    return;
  }
  const boundary = 'knightboundary7731';
  const metadata = JSON.stringify({ name: DATA_FILE_NAME, parents: [folderId] });
  const body =
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadata}\r\n` +
    `--${boundary}\r\nContent-Type: application/octet-stream\r\n\r\n${payload}\r\n--${boundary}--`;
  await check(
    await call(
      '/files?uploadType=multipart',
      { method: 'POST', headers: { 'Content-Type': `multipart/related; boundary=${boundary}` }, body },
      UPLOAD,
    ),
    'Drive upload',
  );
}

export async function downloadFromDrive(): Promise<AppData | null> {
  const folderId = await findFirst(
    `name='${DATA_FOLDER_NAME}' and mimeType='application/vnd.google-apps.folder' and trashed=false`,
  );
  if (!folderId) return null;
  const fileId = await findFirst(`name='${DATA_FILE_NAME}' and '${folderId}' in parents and trashed=false`);
  if (!fileId) return null;
  const response = await check(await call(`/files/${fileId}?alt=media`), 'Drive download');
  return sanitizeData(decodeAp(await response.text()));
}
