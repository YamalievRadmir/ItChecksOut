package ru.sklad.app;
import org.junit.jupiter.api.extension.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.sklad.app.entity.Shop;
import ru.sklad.app.repository.ShopRepository;
import ru.sklad.app.util.SecurityUtils;
public class TestShopContext implements BeforeEachCallback, AfterEachCallback {
 @SuppressWarnings("unchecked") private ThreadLocal<Shop> scope() throws Exception {
   var field=SecurityUtils.class.getDeclaredField("scope"); field.setAccessible(true); return (ThreadLocal<Shop>) field.get(null);
 }
 public void beforeEach(ExtensionContext context) throws Exception {
   var shops=SpringExtension.getApplicationContext(context).getBean(ShopRepository.class);
   Shop shop=new Shop(); shop.setName("Test shop"); shops.saveAndFlush(shop); scope().set(shop);
 }
 public void afterEach(ExtensionContext context) throws Exception { scope().remove(); }
}