package ru.sklad.app.service;

import ru.sklad.app.entity.PurchaseOrder;
import ru.sklad.app.entity.PurchaseOrderItem;
import ru.sklad.app.repository.PurchaseOrderRepository;
import java.time.LocalDateTime;

/** Daily snapshots. Call inside a transaction while holding the shared draft's database lock. */
final class PurchaseOrderHistory {
    private PurchaseOrderHistory() {}

    static void remember(PurchaseOrderRepository orders, PurchaseOrder draft) {
        remember(orders, draft, LocalDateTime.now());
    }

    static void remember(PurchaseOrderRepository orders, PurchaseOrder draft, LocalDateTime timestamp) {
        if (draft.getItems().isEmpty()) return;
        var day = timestamp.toLocalDate();
        PurchaseOrder archived = orders.findFirstByIdNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                draft.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay()).orElseGet(() -> {
                    PurchaseOrder created = new PurchaseOrder(); created.setId((java.util.UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE));
                    created.setCreatedAt(timestamp);
                    return created;
                });
        // Flush orphan removals before recreating rows with the same (order, product) keys.
        if (!archived.getItems().isEmpty()) {
            archived.getItems().clear(); orders.saveAndFlush(archived);
        }
        if (archived.getCreatedAt().isBefore(timestamp)) archived.setCreatedAt(timestamp);
        archived.setInitialized(true); archived.setStatus("Сохранена");
        for (PurchaseOrderItem source : draft.getItems()) {
            PurchaseOrderItem item = new PurchaseOrderItem(); item.setOrder(archived);
            item.setProduct(source.getProduct()); item.setQuantity(source.getQuantity()); archived.getItems().add(item);
        }
        orders.saveAndFlush(archived);
    }
}
