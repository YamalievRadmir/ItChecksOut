package ru.sklad.app.config;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
public class GlobalRouteFilter implements VaadinServiceInitListener {
    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(ui -> ui.getUI().addBeforeEnterListener(navigation -> {
            String path = navigation.getLocation().getPath();
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                if (!"login".equals(path)) navigation.rerouteTo("login");
                return;
            }
            boolean superAdmin = auth.getAuthorities().stream()
                    .anyMatch(role -> "ROLE_SUPER_ADMIN".equals(role.getAuthority()));
            boolean shopUser = auth.getAuthorities().stream()
                    .anyMatch(role -> "ROLE_ADMIN".equals(role.getAuthority()) || "ROLE_SELLER".equals(role.getAuthority()));
            if (superAdmin && !path.equals("super-admin") && !path.startsWith("super-admin/") && !path.equals("login")) {
                navigation.rerouteTo("super-admin");
            } else if (shopUser && (path.isEmpty() || path.equals("super-admin") || path.startsWith("super-admin/"))) {
                navigation.rerouteTo("sales");
            }
        }));
    }
}