package ru.sklad.app.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.sklad.app.entity.RoleSettings;
import ru.sklad.app.repository.RoleSettingsRepository;

@Service("permissionService")
public class PermissionService {
    public static final Map<String, String> ACTIONS;
    public static final Map<String, String> VIEWS;
    static {
        var actions = new LinkedHashMap<String, String>();
        actions.put("canCreateProduct", "Создавать товар");
        actions.put("canEditProduct", "Редактировать товар");
        actions.put("canSeePurchasePrice", "Видеть закупочную цену");
        actions.put("canGiveDiscount", "Давать скидку");
        actions.put("canMakeSupply", "Оприходовать товар");
        actions.put("canSeeReports", "Видеть отчеты");
        actions.put("canEditReceipts", "Разрешить продавцу редактировать проведенные чеки");
        ACTIONS = Collections.unmodifiableMap(actions);
        var views = new LinkedHashMap<String, String>();
        views.put("products", "Товары");
        views.put("supplies", "Поставки");
        views.put("sales", "Продажи");
        views.put("deliveries", "Доставка");
        views.put("notes", "Заметки");
        views.put("inventory", "Инвентаризация");
        views.put("animals", "Справочник");
        views.put("reorder", "К закупке");
        views.put("sales-history", "История продаж");
        views.put("reports", "Отчеты");
        views.put("employees", "Сотрудники");
        views.put("settings", "Настройки");
        VIEWS = Collections.unmodifiableMap(views);
    }
    private final RoleSettingsRepository repository;
    private final AuditService audit;
    public PermissionService(RoleSettingsRepository repository, AuditService audit) { this.repository = repository; this.audit = audit; }

    public boolean isSuperAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
    private String shopRole(String role) { return role + "@" + ru.sklad.app.util.SecurityUtils.currentShop().getId(); }
    public Set<String> allowed(String role) {
        if (isSuperAdmin()) return Set.of();
        return repository.findById(shopRole(role)).or(() -> repository.findById(role)).map(settings -> {
            Set<String> result = new LinkedHashSet<>();
            for (String route : settings.getAllowedViews().split(",")) {
                if (VIEWS.containsKey(route.trim())) result.add(route.trim());
            }
            return result;
        }).orElseGet(LinkedHashSet::new);
    }
    public boolean canAccess(String route) {
        return canAccess(SecurityContextHolder.getContext().getAuthentication(), route);
    }
    public boolean canAccess(Authentication authentication, String route) {
        if (authentication == null || !authentication.isAuthenticated() || !VIEWS.containsKey(route)) return false;
        var roles = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        if (roles.contains("ROLE_SUPER_ADMIN")) return false;
        if (roles.contains("ROLE_ADMIN")) return true;
        if (roles.contains("ROLE_LOADER")) return false;
        if (route.equals("reports") && !hasPermission(authentication, "canSeeReports")) return false;
        return roles.stream().filter(r -> r.startsWith("ROLE_"))
                .anyMatch(r -> allowed(r.substring(5)).contains(route));
    }
    public String landingRoute() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"))) return "super-admin";
        return VIEWS.keySet().stream().filter(this::canAccess).findFirst().orElse("no-access");
    }
    public Set<String> sellerActions() {
        if (isSuperAdmin()) return Set.of();
        return repository.findById(shopRole("SELLER")).or(() -> repository.findById("SELLER")).map(settings -> {
            Set<String> actions = new LinkedHashSet<>(Arrays.asList(settings.getAllowedActions().split(",")));
            actions.retainAll(ACTIONS.keySet());
            return actions;
        }).orElseGet(LinkedHashSet::new);
    }
    public boolean hasPermission(String permissionName) {
        return hasPermission(SecurityContextHolder.getContext().getAuthentication(), permissionName);
    }
    public boolean hasPermission(Authentication authentication, String permissionName) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        var roles = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        if (roles.contains("ROLE_SUPER_ADMIN")) return false;
        if (roles.contains("ROLE_ADMIN")) return true;
        return ACTIONS.containsKey(permissionName) && !roles.contains("ROLE_LOADER")
                && roles.contains("ROLE_SELLER") && sellerActions().contains(permissionName);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void saveSeller(Set<String> routes) {
        if (!VIEWS.keySet().containsAll(routes)) throw new IllegalArgumentException("Неизвестный маршрут");
        RoleSettings settings = repository.findById(shopRole("SELLER")).orElseGet(() -> new RoleSettings(shopRole("SELLER"), ""));
        settings.setAllowedViews(String.join(",", VIEWS.keySet().stream().filter(routes::contains).toList()));
        repository.save(settings);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Права роли", "SELLER", "Разрешенные вкладки: " + settings.getAllowedViews());
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void saveSeller(Set<String> routes, Set<String> actions) {
        if (!VIEWS.keySet().containsAll(routes) || !ACTIONS.keySet().containsAll(actions))
            throw new IllegalArgumentException("Неизвестное разрешение");
        RoleSettings settings = repository.findById(shopRole("SELLER")).orElseGet(() -> new RoleSettings(shopRole("SELLER"), ""));
        settings.setAllowedViews(String.join(",", VIEWS.keySet().stream().filter(routes::contains).toList()));
        settings.setAllowedActions(String.join(",", ACTIONS.keySet().stream().filter(actions::contains).toList()));
        repository.save(settings);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Права роли", "SELLER", "Вкладки: " + settings.getAllowedViews() + "; Действия: " + settings.getAllowedActions());
    }
}
