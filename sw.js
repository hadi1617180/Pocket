const CACHE_NAME = 'pocket-web-v4';
const ASSETS = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.json',
  './ios-pocket/assets/ic_pocket_logo.png',
  './ios-pocket/assets/developer_hadi_photo.jpg',
  './ios-pocket/assets/wallet_icon_bank.png',
  './ios-pocket/assets/wallet_icon_bkash.png',
  './ios-pocket/assets/wallet_icon_brac.png',
  './ios-pocket/assets/wallet_icon_card.png',
  './ios-pocket/assets/wallet_icon_cash.png',
  './ios-pocket/assets/wallet_icon_mtb.png',
  './ios-pocket/assets/wallet_icon_nagad.png',
  './ios-pocket/assets/wallet_icon_rapid_pass.png',
  './ios-pocket/assets/wallet_icon_rocket.png',
  './ios-pocket/assets/wallet_icon_savings.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;

  event.respondWith(
    fetch(event.request)
      .then((networkResponse) => {
        if (networkResponse && networkResponse.status === 200) {
          const clone = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        }
        return networkResponse;
      })
      .catch(() => caches.match(event.request).then((cached) => cached || caches.match('./index.html')))
  );
});
