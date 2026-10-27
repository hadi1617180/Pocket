const CACHE_NAME = 'pocket-ios-v2';
const ASSETS = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.json',
  './assets/ic_pocket_logo.png',
  './assets/developer_hadi_photo.jpg',
  './assets/wallet_icon_bank.png',
  './assets/wallet_icon_bkash.png',
  './assets/wallet_icon_brac.png',
  './assets/wallet_icon_card.png',
  './assets/wallet_icon_cash.png',
  './assets/wallet_icon_mtb.png',
  './assets/wallet_icon_nagad.png',
  './assets/wallet_icon_rapid_pass.png',
  './assets/wallet_icon_rocket.png',
  './assets/wallet_icon_savings.png'
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
  event.respondWith(
    caches.match(event.request).then((cached) => {
      return (
        cached ||
        fetch(event.request).catch(() => caches.match('./index.html'))
      );
    })
  );
});
