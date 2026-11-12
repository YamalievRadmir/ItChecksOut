package ru.sklad.app.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.util.SecurityUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Service
@RequiredArgsConstructor
public class DemoShopDataService {
 private final ProductRepository products;
 private final CustomerRepository customers;
 private final SaleRepository sales;
 @Transactional
 public void populate(Shop shop) {
   if (!shop.isDemo()) throw new IllegalArgumentException("Магазин не является демонстрационным");
   SecurityUtils.inShop(shop, () -> {
     java.util.List<Product> catalog=new java.util.ArrayList<>();
     String[] categories={"Комбикорма","Зерно","Добавки"};
     for(int i=1;i<=12;i++) {
       Product product=new Product(); product.setName("Демо-товар "+i); product.setCategory(categories[(i-1)%3]);
       product.setUnit("кг"); product.setStockQuantity(BigDecimal.valueOf(20+i));
       product.setPurchasePrice(BigDecimal.valueOf(50+i*10)); product.setRetailPrice(BigDecimal.valueOf(80+i*15));
       catalog.add(products.save(product));
     }
     for(int i=1;i<=3;i++) {
       Customer customer=new Customer(); customer.setName("Демо-клиент "+i); customer.setPhone("7900000000"+i);
       customer.setAddress("Демонстрационная улица, "+i); customers.save(customer);
     }
     for(int i=0;i<2;i++) {
       Product product=catalog.get(i); Sale sale=new Sale(); sale.setSaleDate(LocalDateTime.now().minusHours(i));
       sale.setTotalSum(product.getRetailPrice()); sale.setFinalSum(product.getRetailPrice());
       SaleItem item=new SaleItem(); item.setProduct(product); item.setProductName(product.getName()); item.setUnit(product.getUnit());
       item.setQuantity(BigDecimal.ONE); item.setRetailPrice(product.getRetailPrice());
       item.setPurchasePrice(product.getPurchasePrice()); item.setTotalSum(product.getRetailPrice()); sale.addItem(item);
       sales.save(sale); product.setStockQuantity(product.getStockQuantity().subtract(BigDecimal.ONE));
     }
     products.flush(); customers.flush(); sales.flush(); return null;
   });

 }
}
