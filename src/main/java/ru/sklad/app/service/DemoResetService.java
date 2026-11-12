package ru.sklad.app.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.Shop;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoResetService {
    private final EntityManager entityManager;
    private final DemoShopDataService data;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void resetDemoShop() {
        for (Shop shop : entityManager.createQuery("select s from Shop s where s.demo = true", Shop.class).getResultList()) {
            // Explicit shop predicates also work on the scheduler thread without authentication.
            for (String entity : java.util.List.of("SaleItem", "Sale", "DeliveryItem", "Delivery",
                    "Customer", "Supply", "HeldCart", "PurchaseOrderItem", "PurchaseOrder",
                    "Note", "Inventory", "AuditLog", "ErrorLog", "WorkShift", "Employee", "Supplier", "AnimalGuide")) {
                entityManager.createQuery("delete from " + entity + " e where e.shop = :shop")
                        .setParameter("shop", shop).executeUpdate();
            }
            // Remove both directions of product links before removing the catalog.
            for (String[] link : new String[][]{{"product_alternatives", "alternative_id"}, {"product_additives", "additive_id"}}) {
                entityManager.createNativeQuery("delete from " + link[0]
                        + " where product_id in (select id from products where shop_id = :id) or " + link[1]
                        + " in (select id from products where shop_id = :id)")
                        .setParameter("id", shop.getId()).executeUpdate();
            }
            entityManager.createQuery("delete from Product p where p.shop = :shop").setParameter("shop", shop).executeUpdate();
            entityManager.flush();
            data.populate(shop);
            entityManager.flush();
            log.info("Демо-магазин успешно сброшен и заполнен новыми данными");
        }
    }
}
