package ru.sklad.app.config;

import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import ru.sklad.app.repository.PurchaseOrderRepository;
import ru.sklad.app.entity.PurchaseOrder;

@Configuration
public class PurchaseOrderConfiguration {
    @Bean
    ApplicationRunner initializePurchaseDraft(PurchaseOrderRepository orders, ru.sklad.app.repository.ShopRepository shops) {
        return args -> { for (var shop : shops.findAll()) ru.sklad.app.util.SecurityUtils.inShop(shop, () -> { if (!orders.existsById(PurchaseOrder.currentDraftId())) orders.saveAndFlush(new PurchaseOrder()); return null; }); };
    }
}
