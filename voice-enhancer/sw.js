const CACHE = "cleanaf-v3";
const SHELL = [
  "./",
  "index.html",
  "css/style.css",
  "js/boot.js",
  "js/app.js",
  "js/audio.js",
  "js/cleanup.js",
  "js/denoiser.js",
  "js/fft.js",
  "js/media.js",
  "js/pipeline.js",
  "js/player.js",
  "js/recorder.js",
  "js/waveform.js",
  "js/worker.js",
  "manifest.webmanifest",
  "icons/icon-192.png",
  "icons/icon-512.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

function isolate(response) {
  if (!response || response.status === 0 || response.type === "opaque") return response;
  const headers = new Headers(response.headers);
  headers.set("Cross-Origin-Embedder-Policy", "require-corp");
  headers.set("Cross-Origin-Opener-Policy", "same-origin");
  headers.set("Cross-Origin-Resource-Policy", "same-origin");
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}

const heavy = (url) => /\/(vendor|models)\//.test(url.pathname);

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  if (req.headers.has("range")) return;
  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      if (heavy(url)) {
        const hit = await cache.match(req);
        if (hit) return isolate(hit);
        const res = await fetch(req);
        if (res.ok) cache.put(req, res.clone()).catch(() => {});
        return isolate(res);
      }
      try {
        const res = await fetch(req);
        if (res.ok) cache.put(req, res.clone()).catch(() => {});
        return isolate(res);
      } catch {
        const hit = await cache.match(req, { ignoreSearch: true });
        if (hit) return isolate(hit);
        throw new Error("offline");
      }
    })()
  );
});
