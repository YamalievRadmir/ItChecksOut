package ru.sklad.app.config;

import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import ru.sklad.app.repository.PurchaseOrderRepository;
import ru.sklad.app.entity.PurchaseOrder;

@Configuration
public class PurchaseOrderConfiguration {
    @Bean
    ApplicationRunner initializePurchaseDraft(PurchaseOrderRepository orders) {
        return args -> { if (!orders.existsById(1L)) orders.saveAndFlush(new PurchaseOrder()); };
    }
}
