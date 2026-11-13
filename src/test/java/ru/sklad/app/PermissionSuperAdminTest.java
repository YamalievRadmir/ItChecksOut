package ru.sklad.app;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.sklad.app.repository.RoleSettingsRepository;
import ru.sklad.app.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionSuperAdminTest {
    @Test void superAdminNeverQueriesShopPermissions() {
        var repository = mock(RoleSettingsRepository.class);
        var permissions = new PermissionService(repository, mock(AuditService.class));
        var auth = new UsernamePasswordAuthenticationToken("root", "", AuthorityUtils.createAuthorityList("ROLE_SUPER_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            assertTrue(permissions.canAccess("/super-admin"));
            assertTrue(permissions.canAccess(auth, "/super-admin/users"));
            assertFalse(permissions.canAccess("/sales"));
            assertFalse(permissions.canAccess("/super-admin-other"));
            assertTrue(permissions.allowed("SELLER").contains("super-admin"));
            assertTrue(permissions.sellerActions().isEmpty());
            assertFalse(permissions.hasPermission("canMakeSupply"));
            verifyNoInteractions(repository);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
