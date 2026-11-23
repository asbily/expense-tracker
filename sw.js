const CACHE_NAME = "asbily-android-v7";
const CORE_ASSETS = [
  "/",
  "/index.html",
  "/manifest.json",
  "/asbily-logo.jpg",
  "/public/icons/icon-192.png",
  "/public/icons/icon-512.png",
  "/public/icons/icon-maskable-512.png",
  "/public/icons/apple-touch-icon.png"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(CORE_ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;

  if (event.request.mode === "navigate") {
    event.respondWith(
      fetch(event.request)
        .then((res) => {
          if (res && res.status === 200) {
            const copy = res.clone();
            const cacheKey = (url.pathname === "/" || url.pathname === "/index.html") ? "/index.html" : event.request;
            caches.open(CACHE_NAME).then((cache) => cache.put(cacheKey, copy));
          }
          return res;
        })
        .catch(() => caches.match(event.request).then((cached) => cached || caches.match("/index.html")))
    );
    return;
  }

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((res) => {
        if (res && res.status === 200) {
          const copy = res.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
        }
        return res;
      });
    })
  );
});

self.addEventListener("push", (event) => {
  let payload = {};
  if (event.data) {
    try {
      payload = event.data.json();
    } catch {
      payload = { body: event.data.text() };
    }
  }
  const title = String(payload.title || "Asbily Alert");
  const body = String(payload.body || payload.message || "You have a new financial alert.");
  const targetView = typeof payload.view === "string" ? payload.view : "home";
  const targetAction = typeof payload.action === "string" ? payload.action : "";

  event.waitUntil(
    self.registration.showNotification(title, {
      body,
      icon: payload.icon || "/public/icons/icon-192.png",
      badge: "/public/icons/icon-192.png",
      tag: payload.tag || "asbily-push-alert",
      renotify: true,
      vibrate: [140, 60, 140],
      data: { view: targetView, action: targetAction }
    })
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const data = event.notification && event.notification.data ? event.notification.data : {};
  const targetView = typeof data.view === "string" ? data.view : "home";
  const targetAction = typeof data.action === "string" ? data.action : "";
  const query = targetAction === "openNotifs"
    ? "/?notif=1"
    : (targetView && targetView !== "home" ? "/?view=" + encodeURIComponent(targetView) : "/");

  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url && client.url.startsWith(self.location.origin) && "focus" in client) {
          return client.focus().then(() => {
            client.postMessage({
              type: "OPEN_NOTIF_TARGET",
              view: targetView,
              action: targetAction
            });
          });
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(query);
      }
      return undefined;
    })
  );
});
