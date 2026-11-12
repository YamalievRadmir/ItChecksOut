package ru.sklad.app;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.*;
import ru.sklad.app.util.SecurityUtils;
import javax.sql.DataSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({SecurityUtils.class, ProductService.class, PermissionService.class, AuditService.class})
class ProductAuthenticatedEditTest {
 @Autowired ProductRepository products; @Autowired ShopRepository shops; @Autowired UserRepository users;
 @Autowired ProductService service; @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AuditLogRepository audit; @Autowired DataSource data;
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void editingUnderRealPrincipalDoesNotReenterHibernateFlush() throws Exception {
  Shop shop=new Shop();shop.setName("Edit regression");shops.saveAndFlush(shop);
  User admin=new User();admin.setUsername("edit-admin");admin.setRole("ADMIN");admin.setPassword("unused");admin.setShop(shop);users.saveAndFlush(admin);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("edit-admin","",AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
  Product product=new Product();product.setName("Before edit");product.setRetailPrice(BigDecimal.TEN);product.setStockQuantity(BigDecimal.TEN);product.setPurchasePrice(BigDecimal.ONE);
  product=products.saveAndFlush(product); product.setName("After edit");product.setRetailPrice(new BigDecimal("15"));
  Product saved=service.save(product);
  assertEquals("After edit",products.findById(saved.getId()).orElseThrow().getName());assertEquals(shop.getId(),saved.getShop().getId());
  assertEquals(1,audit.findAll().size());
  try(var connection=data.getConnection();var columns=connection.getMetaData().getColumns(null,"PUBLIC","ERROR_LOG","SHOP_ID")){
   assertTrue(columns.next());assertEquals(java.sql.DatabaseMetaData.columnNullable,columns.getInt("NULLABLE"));
  }
 }
 @Test void failedAuditDoesNotRollbackProductEdit() {
  Shop shop=new Shop();shop.setName("Audit failure regression");shops.saveAndFlush(shop);
  User admin=new User();admin.setUsername("audit-failure-admin");admin.setRole("ADMIN");admin.setPassword("unused");admin.setShop(shop);users.saveAndFlush(admin);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin.getUsername(),"",AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
  Product product=new Product();product.setName("Before audit failure");product.setRetailPrice(BigDecimal.TEN);product.setStockQuantity(BigDecimal.TEN);product.setPurchasePrice(BigDecimal.ONE);
  product=products.saveAndFlush(product);product.setName("Edit survives audit failure");
  org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated log storage failure")).when(audit).save(org.mockito.ArgumentMatchers.any(AuditLog.class));
  Product saved=service.save(product);
  assertEquals("Edit survives audit failure",products.findById(saved.getId()).orElseThrow().getName());
 }
}