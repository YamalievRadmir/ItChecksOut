package ru.sklad.app;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.access.AccessDeniedException;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.*;
import ru.sklad.app.util.SecurityUtils;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false)
@org.junit.jupiter.api.extension.ExtendWith(TestShopContext.class)
@Import({SecurityUtils.class,ShopSellerService.class,ShopSellerServiceTest.Config.class})
class ShopSellerServiceTest {
 @TestConfiguration @EnableMethodSecurity static class Config { @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();} }
 @Autowired ShopSellerService service;@Autowired UserRepository users;@Autowired ShopRepository shops;@Autowired PasswordEncoder encoder;
 @MockitoBean AuditService audit;
 @BeforeEach void admin(){role("ADMIN");}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 void role(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("operator","",AuthorityUtils.createAuthorityList("ROLE_"+role)));}
 User seed(String name,String role,Shop shop){User user=new User();user.setUsername(name);user.setPassword("hash");user.setRole(role);user.setShop(shop);return users.saveAndFlush(user);}
 @Test void createsSellerAndEditsLoginAndPasswordWithoutChangingRoleOrShop(){
  service.save(null," seller ","initial password");User user=service.list().get(0);
  assertEquals("seller",user.getUsername());assertEquals("SELLER",user.getRole());assertEquals(SecurityUtils.currentShop().getId(),user.getShop().getId());
  assertTrue(encoder.matches("initial password",user.getPassword()));String hash=user.getPassword();
  service.save(user.getId(),"renamed","");assertEquals(hash,users.findById(user.getId()).orElseThrow().getPassword());
  service.save(user.getId(),"renamed","changed password");assertTrue(encoder.matches("changed password",users.findById(user.getId()).orElseThrow().getPassword()));
 }
 @Test void listsOnlyOwnSellersAndRejectsForeignOrAdminTargets(){
  Shop current=SecurityUtils.currentShop();Shop other=new Shop();other.setName("Other");shops.saveAndFlush(other);
  User own=seed("own","SELLER",current);User foreign=seed("foreign","SELLER",other);User admin=seed("admin","ADMIN",current);
  assertEquals(java.util.List.of(own.getId()),service.list().stream().map(User::getId).toList());
  assertThrows(AccessDeniedException.class,()->service.save(foreign.getId(),"hacked","safe password123"));
  assertThrows(AccessDeniedException.class,()->service.save(admin.getId(),"hacked","safe password123"));
  assertThrows(IllegalArgumentException.class,()->service.save(own.getId(),"foreign",""));
  assertEquals("foreign",users.findById(foreign.getId()).orElseThrow().getUsername());
 }
 @Test void sellerAndSuperAdminCannotManageShopSellers(){
  for(String role:java.util.List.of("SELLER","SUPER_ADMIN")){
   role(role);assertThrows(AccessDeniedException.class,()->service.list());assertThrows(AccessDeniedException.class,()->service.save(null,"forbidden","password"));
  }
 }
}