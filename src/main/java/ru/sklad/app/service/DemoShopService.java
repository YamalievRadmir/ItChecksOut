package ru.sklad.app.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.util.SecurityUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Service
public class DemoShopService {
 @org.springframework.beans.factory.annotation.Value("${app.demo-login.enabled:false}") private boolean enabled;
 private final DemoShopDataService data;
 private final ShopRepository shops; private final UserRepository users; private final ProductRepository products;
 private final CustomerRepository customers; private final SaleRepository sales; private final PasswordEncoder encoder;
 public DemoShopService(ShopRepository shops, UserRepository users, ProductRepository products,
 CustomerRepository customers, SaleRepository sales, PasswordEncoder encoder, DemoShopDataService data) {
 this.data=data;
 this.shops=shops; this.users=users; this.products=products; this.customers=customers; this.sales=sales; this.encoder=encoder;
 }
 @Transactional
 public synchronized void prepare() {
   if (!enabled) throw new org.springframework.security.access.AccessDeniedException("Demo login is disabled");
   var existing=users.findByUsername("demo");
   if(existing.isPresent()) {
     var user=existing.get();
     if(user.getShop()==null || !"Демо-магазин".equals(user.getShop().getName()) || !encoder.matches("demo",user.getPassword()))
       throw new IllegalStateException("Логин demo уже занят другой учётной записью");
     user.getShop().setDemo(true); shops.save(user.getShop());
     return;
   }
   Shop shop=new Shop(); shop.setName("Демо-магазин"); shop.setDemo(true); shops.saveAndFlush(shop);
   data.populate(shop);
   User user=new User(); user.setUsername("demo"); user.setPassword(encoder.encode("demo")); user.setRole("ADMIN"); user.setShop(shop); users.save(user);
 }
}