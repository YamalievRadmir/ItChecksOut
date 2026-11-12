package ru.sklad.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationRunner;
import com.vaadin.flow.server.VaadinServiceInitListener;
import ru.sklad.app.entity.RoleSettings;
import ru.sklad.app.repository.RoleSettingsRepository;
import ru.sklad.app.service.PermissionService;

@Configuration
public class PermissionConfiguration {
    @Bean
    ApplicationRunner initializeRoleSettings(RoleSettingsRepository repository) {
        return args -> {
            if (!repository.existsById("SELLER")) repository.save(new RoleSettings("SELLER", "sales,products,animals,supplies,notes"));
            if (!repository.existsById("LOADER")) repository.save(new RoleSettings("LOADER", ""));
            var seller = repository.findById("SELLER").orElseThrow();
            if (!Boolean.TRUE.equals(seller.getInventoryAccessInitialized())) {
                var routes = new java.util.LinkedHashSet<>(java.util.Arrays.asList(seller.getAllowedViews().split(",")));
                routes.remove(""); routes.add("inventory"); seller.setAllowedViews(String.join(",", routes));
                seller.setInventoryAccessInitialized(true); repository.save(seller);
            }
        };
    }
    @Bean
    VaadinServiceInitListener permissionNavigation(PermissionService permissions) {
        return event -> event.getSource().addUIInitListener(init -> init.getUI().addBeforeEnterListener(enter -> {
            String route = enter.getLocation().getPath();
            if (permissions.isSuperAdmin() && PermissionService.VIEWS.containsKey(route.split("/")[0])) {
                enter.forwardTo("super-admin"); return;
            }
            if (PermissionService.VIEWS.containsKey(route) && !permissions.canAccess(route)) {
                enter.rerouteTo("no-access");
            }
        }));
    }
}
