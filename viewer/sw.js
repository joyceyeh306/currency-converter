const CACHE='ajo-where-v3';
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(['./index.html','./manifest.webmanifest','./apple-touch-icon.png?v=20260924c']))));
self.addEventListener('fetch',e=>{if(e.request.method!=='GET')return;e.respondWith(fetch(e.request).catch(()=>caches.match(e.request)))});
