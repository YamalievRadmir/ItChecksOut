package ru.sklad.app;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import ru.sklad.app.entity.*;
import ru.sklad.app.service.*;
import ru.sklad.app.repository.RoleSettingsRepository;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SellerAccessLevelTest {
 @Test void migrationPreservesLegacyActionsAndExplicitDenials() {
  var settings = new RoleSettings("SELLER", "products,supplies,sales,notes,animals");
  settings.setAllowedActions("canMakeSupply,canGiveDiscount");
  settings.migrateAccessLevels();
  assertEquals(AccessLevel.VIEW, settings.getProductsAccess());
  assertEquals(AccessLevel.EDIT, settings.getSuppliesAccess());
  assertEquals(AccessLevel.EDIT, settings.getSalesAccess());
  assertTrue(settings.getCanGiveDiscount());
  settings.setSuppliesAccess(AccessLevel.NONE); settings.setCanGiveDiscount(false);
  settings.migrateAccessLevels();
  assertEquals(AccessLevel.NONE, settings.getSuppliesAccess()); assertFalse(settings.getCanGiveDiscount());
 }
 @AfterEach void clear() { SecurityContextHolder.clearContext(); }
 @Test void levelsSeparateNavigationFromWritesAndProtectMoney() {
  var settings = new RoleSettings("SELLER", "");
  var service = spy(new PermissionService(mock(RoleSettingsRepository.class), mock(AuditService.class)));
  doReturn(settings).when(service).sellerSettings();
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("seller", "", AuthorityUtils.createAuthorityList("ROLE_SELLER")));
  settings.setProductsAccess(AccessLevel.NONE); assertFalse(service.canAccess("products")); assertFalse(service.canEdit("products"));
  settings.setProductsAccess(AccessLevel.VIEW); assertTrue(service.canAccess("products")); assertFalse(service.hasPermission("canEditProduct"));
  settings.setProductsAccess(AccessLevel.EDIT); assertTrue(service.hasPermission("canCreateProduct")); assertFalse(service.hasPermission("canSeePurchasePrice"));
  settings.setSuppliesAccess(AccessLevel.VIEW); assertFalse(service.hasPermission("canMakeSupply"));
  settings.setSuppliesAccess(AccessLevel.EDIT); assertTrue(service.hasPermission("canMakeSupply"));
  settings.setHistoryAccess(true); assertTrue(service.canAccess("sales-history")); assertFalse(service.hasPermission("canEditReceipts"));
  settings.setEmployeesAccess(true); assertTrue(service.canAccess("employees")); assertFalse(service.canEdit("employees"));
  settings.setCanGiveDiscount(false); assertFalse(service.hasPermission("canGiveDiscount"));
  settings.setCanGiveDiscount(true); assertTrue(service.hasPermission("canGiveDiscount"));
 }
}
