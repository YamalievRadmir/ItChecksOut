// Retirement worker: updates previously installed Vaadin workers, without caching or intercepting requests.
// Remove this file before enabling @PWA for production.
self.addEventListener('install', event => event.waitUntil(self.skipWaiting()));
self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const scope = self.registration.scope;
    const keys = await caches.keys();
    await Promise.all(keys.filter(key => key.startsWith('workbox-') && key.endsWith(scope))
      .map(key => caches.delete(key)));
    await self.registration.unregister();
    const windows = await self.clients.matchAll({type: 'window', includeUncontrolled: true});
    const login = new URL('login', scope);
    await Promise.all(windows.filter(client => {
      const url = new URL(client.url);
      return url.origin === login.origin && url.pathname === login.pathname;
    }).map(client => client.navigate(client.url)));
  })());
});
