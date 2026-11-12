package ru.sklad.app;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.AuthorityUtils;
import ru.sklad.app.service.SuperAdminService;
import ru.sklad.app.repository.*;
import ru.sklad.app.entity.*;
import ru.sklad.app.util.SecurityUtils;
import jakarta.persistence.EntityManager;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false)
@Import({SuperAdminService.class, SuperAdminServiceTest.Config.class})
class SuperAdminServiceTest {
 @TestConfiguration @EnableMethodSecurity static class Config {
  @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(); }
 }
 @Autowired SuperAdminService service; @Autowired UserRepository users; @Autowired ShopRepository shops;
 @Autowired EntityManager em; @Autowired PasswordEncoder encoder;
 void role(String role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("operator","",AuthorityUtils.createAuthorityList("ROLE_"+role))); }
 @AfterEach void clear() { SecurityContextHolder.clearContext(); }
 Shop shop(String name) { Shop shop=new Shop();shop.setName(name); return shops.saveAndFlush(shop); }
 User user(String name, Shop shop) { User user=new User();user.setUsername(name); user.setRole("ADMIN");user.setShop(shop);user.setPassword(encoder.encode("original"));return users.saveAndFlush(user); }
 @Test void editsUsernameAndPasswordWithoutChangingShopOrRole() {
  role("SUPER_ADMIN"); Shop shop=shop("One");User user=user("first",shop);String old=user.getPassword();
  service.updateUser(user.getId(),"renamed",""); em.clear();
  User loaded=users.findById(user.getId()).orElseThrow();assertEquals(old,loaded.getPassword());
  service.updateUser(user.getId(),"renamed","new password");em.clear();loaded=users.findById(user.getId()).orElseThrow();
  assertTrue(encoder.matches("new password",loaded.getPassword()));assertEquals("ADMIN",loaded.getRole());assertEquals(shop.getId(),loaded.getShop().getId());
  user("taken",shop);assertThrows(IllegalArgumentException.class,()->service.updateUser(user.getId(),"taken",""));
 }
 @Test void superAdminSeesUsersAndLogsFromEveryShop() {
  role("SUPER_ADMIN");
  for(String name:java.util.List.of("One","Two")) {
   Shop shop=shop(name);user(name,shop);
   SecurityUtils.inShop(shop,()->{AuditLog audit=new AuditLog();audit.setUsername(name);em.persist(audit);em.flush();return null;});
   ErrorLog error=new ErrorLog();error.setShop(shop);error.setMessage(name);em.persist(error);
  }
  em.flush(); assertEquals(2,service.users().size());assertEquals(2,service.audit(0).size());assertEquals(2,service.errors(0).size());
 }
 @Test void resetsPasswordAndBlocksWithoutDeletingAccount() {
  role("SUPER_ADMIN"); User user=user("target",shop("Target"));
  String password=service.resetPassword(user.getId()); em.clear();
  assertEquals(16,password.length()); assertTrue(encoder.matches(password,users.findById(user.getId()).orElseThrow().getPassword()));
  service.blockUser(user.getId()); em.clear(); assertFalse(users.findById(user.getId()).orElseThrow().isEnabled());
 } @Test void shopAdminCannotReadGlobalDataOrEditUsers() {
  role("ADMIN");
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.resetPassword(1L));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.blockUser(1L));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.users());
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.audit(0));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.errors(0));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.updateUser(1L,"changed","password"));
 }
}