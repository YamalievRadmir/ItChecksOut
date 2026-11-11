package ru.sklad.app;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.*;
import java.util.Map;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SalesAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean PermissionService permissionService() { return mock(PermissionService.class); }
        @Bean ProductRepository products() { return mock(ProductRepository.class); }
        @Bean SaleRepository sales() { return mock(SaleRepository.class); }
        @Bean SalesService service(ProductRepository p, SaleRepository s, PermissionService permissions) {
            return new SalesService(p,s,permissions,mock(AuditService.class));
        }
    }
    @Test void forbiddenRoleCannotUseAnyCheckoutOverload() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("loader", "", AuthorityUtils.createAuthorityList("ROLE_LOADER")));
            var service = context.getBean(SalesService.class);
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.conductSale(Map.of(),Map.of()));
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.conductSale(Map.of(),Map.of(),BigDecimal.ZERO,"%"));
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.conductSale(Map.of(),Map.of(),BigDecimal.ZERO,"%","CASH"));
            verifyNoInteractions(context.getBean(ProductRepository.class), context.getBean(SaleRepository.class));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
