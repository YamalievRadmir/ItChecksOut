package ru.sklad.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.component.page.Viewport;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@com.vaadin.flow.component.page.Push(value = com.vaadin.flow.shared.communication.PushMode.AUTOMATIC,
        transport = com.vaadin.flow.shared.ui.Transport.WEBSOCKET)
@EnableScheduling
@Theme("my-theme")
@Viewport("width=device-width, initial-scale=1, viewport-fit=cover")
public class SkladApplication implements AppShellConfigurator {
    // PWA stays disabled during development. Remove the retirement worker before enabling PWA for deployment.
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addFavIcon("icon", "favicon.png", "");
        settings.addInlineWithContents(Inline.Position.PREPEND, """
                (async () => {
                  if (!('serviceWorker' in navigator)) return;
                  const scope = new URL('.', document.baseURI).href;
                  const worker = new URL('sw.js', scope).href;
                  try {
                    const registrations = await navigator.serviceWorker.getRegistrations();
                    const owned = registrations.filter(r => r.scope === scope &&
                      [r.active, r.waiting, r.installing].some(w => w && w.scriptURL === worker));
                    const controlled = navigator.serviceWorker.controller?.scriptURL === worker;
                    await Promise.all(owned.map(r => r.unregister()));
                    if ('caches' in window) {
                      const keys = await caches.keys();
                      await Promise.all(keys.filter(k => k.startsWith('workbox-') && k.endsWith(scope))
                        .map(k => caches.delete(k)));
                    }
                    if (owned.length && controlled && location.pathname === new URL('login', scope).pathname) {
                      location.reload();
                    }
                  } catch (error) { console.warn('Не удалось очистить старый PWA-кеш', error); }
                })();
                """, Inline.Wrapping.JAVASCRIPT);
    }

    public static void main(String[] args) {
        SpringApplication.run(SkladApplication.class, args);
    }
}
