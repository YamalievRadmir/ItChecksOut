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
        actions.put("canSeeRetailPrice", "Видеть цену розницы");
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
        if ("ADMIN".equals(role)) return VIEWS.keySet();
        if (!"SELLER".equals(role)) return Set.of();
        var result = new LinkedHashSet<String>();
        var settings = sellerSettings();
        for (String route : VIEWS.keySet()) if (sellerLevel(settings, route) != ru.sklad.app.entity.AccessLevel.NONE) result.add(route);
        return result;
    }
    public RoleSettings sellerSettings() { return repository.findById(shopRole("SELLER")).or(() -> repository.findById("SELLER")).orElseGet(RoleSettings::new); }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public RoleSettings loadSellerAccessSettings() {
        String key = shopRole("SELLER");
        RoleSettings settings = repository.findById(key).orElseGet(() -> {
            RoleSettings created = new RoleSettings(key, "");
            created.setAllowedActions("");
            created.migrateAccessLevels();
            return created;
        });
        settings.migrateAccessLevels();
        return repository.save(settings);
    }
    public ru.sklad.app.entity.AccessLevel sellerLevel(String route) { return sellerLevel(sellerSettings(), route); }
    public ru.sklad.app.entity.AccessLevel sellerLevel(RoleSettings s, String route) {
        s.migrateAccessLevels();
        var level = switch(route) {
case "products" -> s.getProductsAccess();
case "supplies" -> s.getSuppliesAccess();
case "deliveries" -> s.getDeliveriesAccess();
case "notes" -> s.getNotesAccess();
case "animals" -> s.getAnimalsAccess();
case "sales" -> s.getSalesAccess();
        default -> (ru.sklad.app.entity.AccessLevel) null; };
        if (level != null) return level;
        Boolean flag = switch(route) {
case "inventory" -> s.getInventoryAccess();
case "reports" -> s.getReportsAccess();
case "sales-history" -> s.getHistoryAccess();
case "employees" -> s.getEmployeesAccess();
        default -> null; };
        if (flag != null) return flag ? ru.sklad.app.entity.AccessLevel.VIEW : ru.sklad.app.entity.AccessLevel.NONE;
        if (route.equals("settings") || route.equals("reorder")) return ru.sklad.app.entity.AccessLevel.NONE;
        return Arrays.asList(Objects.toString(s.getAllowedViews(), "").split(",")).contains(route) ? ru.sklad.app.entity.AccessLevel.VIEW : ru.sklad.app.entity.AccessLevel.NONE;
    }
    public boolean canEdit(String route) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        if (auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN"))) return true;
        return canAccess(route) && sellerLevel(route) == ru.sklad.app.entity.AccessLevel.EDIT;
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void saveSellerAccess(Map<String, ru.sklad.app.entity.AccessLevel> levels, Set<String> flags, boolean discount) {
        RoleSettings s = repository.findById(shopRole("SELLER")).orElseGet(() -> new RoleSettings(shopRole("SELLER"), ""));
s.setProductsAccess(Objects.requireNonNull(levels.get("products")));
s.setSuppliesAccess(Objects.requireNonNull(levels.get("supplies")));
s.setDeliveriesAccess(Objects.requireNonNull(levels.get("deliveries")));
s.setNotesAccess(Objects.requireNonNull(levels.get("notes")));
s.setAnimalsAccess(Objects.requireNonNull(levels.get("animals")));
s.setSalesAccess(Objects.requireNonNull(levels.get("sales")));
s.setInventoryAccess(flags.contains("inventory"));
s.setReportsAccess(flags.contains("reports"));
s.setHistoryAccess(flags.contains("sales-history"));
s.setEmployeesAccess(flags.contains("employees"));
        s.setCanGiveDiscount(discount); repository.save(s);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Права роли", "SELLER", levels + "; " + flags + "; скидки: " + discount);
    }

    public boolean canAccess(String route) { return canAccess(SecurityContextHolder.getContext().getAuthentication(), route); }
    public boolean canAccess(Authentication auth, String route) {
        if (auth == null || !auth.isAuthenticated() || route == null) return false;
        String path = route.replaceFirst("^/+", "");
        var roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        if (roles.contains("ROLE_SUPER_ADMIN")) return path.equals("super-admin") || path.startsWith("super-admin/");
        return VIEWS.containsKey(path) && (roles.contains("ROLE_ADMIN") || (roles.contains("ROLE_SELLER") && sellerLevel(path) != ru.sklad.app.entity.AccessLevel.NONE));
    }
    public String landingRoute() { return isSuperAdmin() ? "super-admin" : canAccess("sales") ? "sales" : VIEWS.keySet().stream().filter(this::canAccess).findFirst().orElse("no-access"); }
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
        if (roles.contains("ROLE_SELLER")) return switch(permissionName) {
case "canCreateProduct", "canEditProduct" -> canEdit("products");
case "canSeePurchasePrice", "canEditReceipts" -> false;
case "canSeeRetailPrice" -> true;
case "canMakeSupply" -> canEdit("supplies");
case "canSeeReports" -> canAccess("reports");
case "canGiveDiscount" -> Boolean.TRUE.equals(sellerSettings().getCanGiveDiscount());
default -> false; };
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
