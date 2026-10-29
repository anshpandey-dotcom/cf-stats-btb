const NAME = 'unknown-chat';
let handle;

function open() {
  if (handle) return handle;
  handle = new Promise((resolve, reject) => {
    const req = indexedDB.open(NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      db.createObjectStore('messages', { keyPath: 'id' }).createIndex('ts', 'ts');
      db.createObjectStore('triggers', { keyPath: 'id' });
      db.createObjectStore('meta');
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return handle;
}

async function run(store, mode, fn) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, mode);
    const result = fn(tx.objectStore(store));
    tx.oncomplete = () => resolve(result && 'result' in result ? result.result : undefined);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

export const messages = {
  put: (m) => run('messages', 'readwrite', (s) => s.put(m)),
  get: (id) => run('messages', 'readonly', (s) => s.get(id)),
  all: async () => (await run('messages', 'readonly', (s) => s.getAll())).sort((a, b) => a.ts - b.ts),
  ids: async () => run('messages', 'readonly', (s) => s.getAllKeys())
};

export const triggers = {
  put: (t) => run('triggers', 'readwrite', (s) => s.put(t)),
  remove: (id) => run('triggers', 'readwrite', (s) => s.delete(id)),
  all: () => run('triggers', 'readonly', (s) => s.getAll()),
  get: (id) => run('triggers', 'readonly', (s) => s.get(id)),
  replaceAll: async (list) => {
    await run('triggers', 'readwrite', (s) => s.clear());
    for (const t of list) await triggers.put(t);
  }
};

export const meta = {
  get: (key) => run('meta', 'readonly', (s) => s.get(key)),
  set: (key, value) => run('meta', 'readwrite', (s) => s.put(value, key))
};
