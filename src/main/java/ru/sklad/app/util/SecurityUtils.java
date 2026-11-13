package ru.sklad.app.util;

import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import ru.sklad.app.entity.Shop;
import ru.sklad.app.repository.UserRepository;

@Component
public class SecurityUtils {
    private final UserRepository users;
    private static SecurityUtils instance;
    private static final ThreadLocal<Shop> scope = new ThreadLocal<>();
    public static Shop currentShop() { if (scope.get() != null) return scope.get(); if (instance == null) return null; return instance.requireCurrentShop(); }
    public static <T> T inShop(Shop shop, java.util.function.Supplier<T> action) { Shop previous = scope.get(); scope.set(shop); try { return action.get(); } finally { if (previous == null) scope.remove(); else scope.set(previous); } }
    public SecurityUtils(UserRepository users) { this.users = users; instance = this; }
    public ru.sklad.app.entity.User getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
            return null;
        var user = users.findForSecurityByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Unknown account"));
        if (!user.isEnabled()) throw new AccessDeniedException("Account disabled");
        return user;
    }
    public Shop getCurrentShop() {
        var user = getCurrentUser();
        if (user == null) return null;
        if ("SUPER_ADMIN".equals(user.getRole())) return null;
        if (user.getShop() == null) return null;
        if (!user.getShop().isActive())
            throw new AccessDeniedException("Shop disabled or missing");
        return user.getShop();
    }
    public Shop requireCurrentShop() {
        Shop shop = scope.get() != null ? scope.get() : getCurrentShop();

        return shop;
    }
    public static Shop businessShop() {
        Shop shop = currentShop();
        if (shop == null) throw new AccessDeniedException("Business operation requires a shop account");
        return shop;
    }
}