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
    private String shopRole(String role) { return role + "@" + ru.sklad.app.util.SecurityUtils.businessShop().getId(); }
    public Set<String> allowed(String role) {
        if (isSuperAdmin() || "SUPER_ADMIN".equals(role)) return Set.of("super-admin");
        return Set.of("ADMIN", "SELLER").contains(role) ? VIEWS.keySet() : Set.of();
    }
    public boolean canAccess(String route) { return canAccess(SecurityContextHolder.getContext().getAuthentication(), route); }
    public boolean canAccess(Authentication auth, String route) {
        if (auth == null || !auth.isAuthenticated() || route == null) return false;
        String path = route.replaceFirst("^/+", "");
        var roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        if (roles.contains("ROLE_SUPER_ADMIN")) return path.equals("super-admin") || path.startsWith("super-admin/");
        return VIEWS.containsKey(path) && (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SELLER"));
    }
    public String landingRoute() { return isSuperAdmin() ? "super-admin" : canAccess("sales") ? "sales" : "no-access"; }
    public Set<String> sellerActions() {
        if (isSuperAdmin() || ru.sklad.app.util.SecurityUtils.currentShop() == null) return Set.of();
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
