package ru.sklad.app;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.*;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.*;
import ru.sklad.app.util.SecurityUtils;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({SecurityUtils.class,AuditService.class,ErrorLogService.class,LogShopIsolationTest.Config.class})
class LogShopIsolationTest {
 @TestConfiguration @EnableMethodSecurity static class Config {}
 @Autowired ShopRepository shops; @Autowired UserRepository users;
 @Autowired AuditService audit; @Autowired ErrorLogService errors;
 @Autowired AuditLogRepository auditLogs; @Autowired ErrorLogRepository errorLogs;
 @AfterEach void clear() { SecurityContextHolder.clearContext(); }
 void authenticate(String username) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username,"",AuthorityUtils.createAuthorityList("ROLE_ADMIN"))); }
 @Test void logsFollowCurrentShopAndBackgroundStaysUnassigned() throws Exception {
  for(String name:java.util.List.of("first-log-user","second-log-user")) {
   Shop shop=new Shop();shop.setName(name);shops.saveAndFlush(shop);
   User user=new User();user.setUsername(name);user.setPassword("unused");user.setRole("ADMIN");user.setShop(shop);users.saveAndFlush(user);
   authenticate(name);audit.logAction("UPDATE","Product",name,"details"); errors.record(new IllegalStateException(name));
  }
  SecurityContextHolder.clearContext();
  // A background task must not adopt an explicit bootstrap shop context either.
  Shop first=shops.findAll().get(0);
  SecurityUtils.inShop(first,()->{audit.logAction("BACKGROUND","System","worker","details");new ru.sklad.app.config.GlobalExceptionHandler(errors).report(new IllegalStateException("background"));return null;});
  superAdmin();
  assertEquals(3,auditLogs.findAll().size());assertEquals(3,errorLogs.findAll().size());
  assertEquals(1,auditLogs.findAll().stream().filter(log->log.getShop()==null).count());
  assertEquals(1,errorLogs.findAll().stream().filter(log->log.getShop()==null).count());
  for(String name:java.util.List.of("first-log-user","second-log-user")) {
   authenticate(name);
   var localErrors=errors.list(0);assertEquals(1,localErrors.size());assertEquals(name,localErrors.get(0).getUsername());
   var localAudit=audit.search(LocalDate.now().minusDays(1),LocalDate.now().plusDays(1),"",0);
   assertEquals(1,localAudit.size());assertEquals(name,localAudit.get(0).getUsername());
  }
 }
 void superAdmin() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("root","",AuthorityUtils.createAuthorityList("ROLE_SUPER_ADMIN"))); }
 @Test void repositoriesRejectGlobalLogReadsByShopAdmin() {
   authenticate("ordinary-admin");
   assertThrows(org.springframework.security.access.AccessDeniedException.class,()->auditLogs.findAll());
   assertThrows(org.springframework.security.access.AccessDeniedException.class,()->errorLogs.findAll());
   assertThrows(org.springframework.security.access.AccessDeniedException.class,()->auditLogs.count());
 } @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @Test void successfulActionAuditIsNotWrittenWhenBusinessTransactionRollsBack() {
   superAdmin();
   long before=auditLogs.count();
   new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->{
     audit.safeLogAction("UPDATE","Product","rolled back","details");status.setRollbackOnly();
   });
   assertEquals(before,auditLogs.count());
 }
}