package ru.sklad.app;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.transaction.annotation.*;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.*;
import ru.sklad.app.util.SecurityUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({SecurityUtils.class,SalesService.class,SupplyService.class,BatchSupplyService.class,InventoryService.class,ProductionStockSecurityTest.Config.class})
class ProductionStockSecurityTest {
 @TestConfiguration @EnableMethodSecurity static class Config {}
 @Autowired ProductRepository products;@Autowired ShopRepository shops;@Autowired UserRepository users;@Autowired SupplyRepository supplies;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean SaleRepository saleRecords;
 @Autowired SalesService sales;@Autowired SupplyService supply;@Autowired BatchSupplyService batch;@Autowired InventoryService inventory;
 @MockitoBean(name="permissionService") PermissionService permissions;@MockitoBean AuditService audit;
 Shop own,other;
 @BeforeEach void setup(){
  when(permissions.canAccess(anyString())).thenReturn(true);when(permissions.hasPermission(anyString())).thenReturn(true);
  own=shop("Own");other=shop("Other");User user=new User();user.setUsername(UUID.randomUUID().toString());user.setRole("ADMIN");user.setPassword("unused");user.setShop(own);users.saveAndFlush(user);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(),"",AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 Shop shop(String name){Shop shop=new Shop();shop.setName(name);return shops.saveAndFlush(shop);}
 Product product(Shop shop,String name){return SecurityUtils.inShop(shop,()->{Product p=new Product();p.setName(name);p.setStockQuantity(BigDecimal.TEN);p.setRetailPrice(BigDecimal.TEN);p.setPurchasePrice(BigDecimal.ONE);return products.saveAndFlush(p);});}
 @Test void foreignIdsCannotBeSoldReceivedOrInventoried(){
  Product local=product(own,"Local"),foreign=product(other,"Foreign");
  assertTrue(products.findById(foreign.getId()).isEmpty());
  assertThrows(IllegalArgumentException.class,()->sales.conductSale(Map.of(local.getId(),BigDecimal.ONE,foreign.getId(),BigDecimal.ONE),Map.of(local.getId(),BigDecimal.TEN,foreign.getId(),BigDecimal.TEN)));
  assertThrows(IllegalArgumentException.class,()->supply.receive(foreign.getId(),BigDecimal.ONE,LocalDate.now(),null));
  assertThrows(IllegalArgumentException.class,()->inventory.complete(LocalDate.now(),"Admin","",Map.of(foreign.getId(),BigDecimal.ONE),Map.of(foreign.getId(),BigDecimal.TEN)));
  assertEquals(0,products.findById(local.getId()).orElseThrow().getStockQuantity().compareTo(BigDecimal.TEN));
  SecurityUtils.inShop(other,()->{assertEquals(0,products.findById(foreign.getId()).orElseThrow().getStockQuantity().compareTo(BigDecimal.TEN));return null;});
 }
 @Test void explicitForeignShopAndUnscopedExampleQueriesAreRejected(){
  product(other,"Foreign");
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->products.existsByShopAndName(other,"Foreign"));
  assertThrows(UnsupportedOperationException.class,()->products.findAll(org.springframework.data.domain.Example.of(new Product())));
 }
 @Test void batchSupplyRollsBackEarlierLineWhenLaterLineFails(){
  Product first=product(own,"First"),second=product(own,"Second");
  var lines=List.of(new BatchSupplyService.PartyLine(first.getId(),BigDecimal.ONE,BigDecimal.ONE),new BatchSupplyService.PartyLine(second.getId(),BigDecimal.ONE,BigDecimal.ONE.negate()));
  assertThrows(IllegalArgumentException.class,()->batch.receiveParty(UUID.randomUUID(),null,lines,LocalDate.now(),null,null));
  assertEquals(0,supplies.count());
  assertEquals(0,products.findById(first.getId()).orElseThrow().getStockQuantity().compareTo(BigDecimal.TEN));
 }
 @Test void failedSaleSaveRollsBackAllStockChanges() {
  Product first=product(own,"Sale first"),second=product(own,"Sale second");
  doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated sale storage failure")).when(saleRecords).save(any(Sale.class));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->sales.conductSale(Map.of(first.getId(),BigDecimal.ONE,second.getId(),BigDecimal.ONE),Map.of(first.getId(),BigDecimal.TEN,second.getId(),BigDecimal.TEN)));
  assertEquals(0,saleRecords.count());
  assertEquals(0,products.findById(first.getId()).orElseThrow().getStockQuantity().compareTo(BigDecimal.TEN));
  assertEquals(0,products.findById(second.getId()).orElseThrow().getStockQuantity().compareTo(BigDecimal.TEN));
 }
}