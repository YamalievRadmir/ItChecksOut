package ru.sklad.app;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.DemoShopService;
import ru.sklad.app.util.SecurityUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false, properties={"app.demo-login.enabled=true","spring.jpa.properties.hibernate.generate_statistics=true","logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@Import({SecurityUtils.class, DemoShopService.class, ru.sklad.app.service.DemoShopDataService.class, ru.sklad.app.service.DemoResetService.class, TenantIsolationTest.Config.class})
class TenantIsolationTest {
 @TestConfiguration static class Config { @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(); } }
 @Autowired ShopRepository shops; @Autowired ProductRepository products; @Autowired CustomerRepository customers;
 @Autowired SaleRepository sales; @Autowired SaleItemRepository items; @Autowired UserRepository users;
 @Autowired DemoShopService demo;
 @Autowired ru.sklad.app.service.DemoResetService reset;
 @Test void nightlyResetPreservesOtherShopsAndDemoAccount() {
   Shop real=shop("Real");
   Product realProduct=SecurityUtils.inShop(real,()->product("Keep me"));
   demo.prepare();
   User account=users.findByUsername("demo").orElseThrow();
   Shop demoShop=account.getShop();
   Long oldProduct=SecurityUtils.inShop(demoShop,()->products.findAll().get(0).getId());
   for(int i=0;i<2;i++) {
     reset.resetDemoShop();
     SecurityUtils.inShop(demoShop,()->{
       assertEquals(12,products.count()); assertEquals(3,customers.count()); assertEquals(2,sales.count());
       assertTrue(products.findById(oldProduct).isEmpty()); return null;
     });
   }
   assertEquals(account.getId(),users.findByUsername("demo").orElseThrow().getId());
   SecurityUtils.inShop(real,()->{assertTrue(products.findById(realProduct.getId()).isPresent()); return null;});
 }
 @Autowired PurchaseOrderRepository orders;
 @Test void superAdminCannotReadBusinessData() {
   User user=new User(); user.setUsername("super"); user.setPassword("unused"); user.setRole("SUPER_ADMIN"); users.saveAndFlush(user);
   var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
   context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("super", "", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
   try {
     assertNull(new SecurityUtils(users).getCurrentShop());
     assertThrows(RuntimeException.class,()->products.findAll());
   } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
 }
 @Test void purchaseDraftsAreSeparateForEachShop() {
   Shop a=shop("A"), b=shop("B");
   var first=SecurityUtils.inShop(a,()->orders.saveAndFlush(new PurchaseOrder()));
   var second=SecurityUtils.inShop(b,()->orders.saveAndFlush(new PurchaseOrder()));
   assertNotEquals(first.getId(),second.getId());
   SecurityUtils.inShop(a,()->{assertTrue(orders.findById(second.getId()).isEmpty());assertEquals(1,orders.count());return null;});
 }
 Shop shop(String name) { Shop shop=new Shop(); shop.setName(name); return shops.saveAndFlush(shop); }
 Product product(String name) { Product p=new Product(); p.setName(name); p.setCategory("Common"); p.setRetailPrice(BigDecimal.TEN); p.setStockQuantity(BigDecimal.TEN); return products.saveAndFlush(p); }
 @Test void allSearchesAndIdsStayInsideShop() {
   Shop a=shop("A"), b=shop("B");
   Product first=SecurityUtils.inShop(a,()->product("First"));
   Product second=SecurityUtils.inShop(b,()->product("Second"));
   SecurityUtils.inShop(a,()->{
     assertEquals(a.getId(),first.getShop().getId());
     assertEquals(1,products.count()); assertEquals(1,products.findAll().size());
     assertTrue(products.findById(second.getId()).isEmpty());
     assertEquals(java.util.List.of(first.getId()), products.findAllById(java.util.List.of(first.getId(),second.getId())).stream().map(Product::getId).toList());
     assertTrue(products.findLockedById(second.getId()).isEmpty());
     assertFalse(products.existsByName("Second"));
     assertEquals(1, products.findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase("Second","Common").size());
     assertEquals(0,products.calculateInventoryTotals().getRetailTotal().compareTo(new BigDecimal("100")));
     products.deleteById(second.getId()); products.flush(); return null;
   });
   SecurityUtils.inShop(b,()->{assertTrue(products.findById(second.getId()).isPresent());return null;});
 }
 @Test void demoSeedsOnlyItsOwnShopAndSaleItems() {
   Shop real=shop("Real"); SecurityUtils.inShop(real,()->product("Real product"));
   demo.prepare(); demo.prepare();
   var user=users.findByUsername("demo").orElseThrow();
   assertNotEquals(real.getId(),user.getShop().getId());
   SecurityUtils.inShop(user.getShop(),()->{
     assertEquals(12,products.count()); assertEquals(3,customers.count()); assertEquals(2,sales.count());
     assertEquals(2,items.count());
     for(var line:items.findAll()) { assertEquals(user.getShop().getId(),line.getShop().getId()); assertEquals(line.getShop().getId(),line.getSale().getShop().getId()); }
     return null;
   });
   SecurityUtils.inShop(real,()->{assertEquals(1,products.count()); assertEquals(0,sales.count()); return null;});
 }
 @Test void rejectsSaleContainingAnotherShopsProduct() {
   Shop a=shop("A"), b=shop("B"); Product foreign=SecurityUtils.inShop(b,()->product("Foreign"));
   SecurityUtils.inShop(a,()->{
     Sale sale=new Sale(); sale.setSaleDate(LocalDateTime.now()); sale.setTotalSum(BigDecimal.TEN);
     SaleItem line=new SaleItem(); line.setProduct(foreign); line.setProductName("Foreign"); line.setQuantity(BigDecimal.ONE);
     line.setRetailPrice(BigDecimal.TEN); line.setTotalSum(BigDecimal.TEN); sale.addItem(line);
     assertThrows(RuntimeException.class,()->sales.saveAndFlush(sale)); return null;
   });
 }
 @Autowired jakarta.persistence.EntityManager em;
 @Test void recommendationsUseBatchFetchInsteadOfPerProductQueries() {
   Shop shop=shop("Batch fetch");
   SecurityUtils.inShop(shop,()->{
     java.util.List<Product> list=new java.util.ArrayList<>();
     for(int i=0;i<20;i++) list.add(product("Batch "+i));
     for(int i=0;i<19;i++) {list.get(i).getAlternativeProducts().add(list.get(i+1));list.get(i).getRecommendedAdditives().add(list.get(i+1));}
     em.flush();em.clear();
     var statistics=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();statistics.clear();
     var loaded=products.findAllByShop(shop);
     loaded.forEach(p->{p.getAlternativeProducts().size();p.getRecommendedAdditives().size();});
     assertEquals(20,loaded.size());
     assertTrue(statistics.getPrepareStatementCount()<=4,"Unexpected query count: "+statistics.getPrepareStatementCount());
     return null;
   });
 }
}