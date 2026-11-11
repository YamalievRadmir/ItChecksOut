package ru.sklad.app.util;

import org.springframework.security.core.context.SecurityContextHolder;

public final class RoleAccess {
    private RoleAccess() {}

    public static boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
