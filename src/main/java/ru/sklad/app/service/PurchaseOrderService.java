package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional
@PreAuthorize("@permissionService.canAccess('reorder')")
public class PurchaseOrderService {
    public record Row(Long productId, String name, String unit, BigDecimal stock, BigDecimal quantity) {}
    public record Draft(Long id, LocalDateTime createdAt, String status, List<Row> rows) {}
    private final PurchaseOrderRepository orders;
    private final ProductRepository products;
    private final ReorderService reorder;
    private final AuditService audit;
    public PurchaseOrderService(PurchaseOrderRepository orders, ProductRepository products, ReorderService reorder, AuditService audit) {
        this.orders = orders; this.products = products; this.reorder = reorder;
        this.audit = audit;
    }
    private PurchaseOrder draft() {
        PurchaseOrder order = orders.findLockedById(PurchaseOrder.currentDraftId()).orElseGet(() -> orders.saveAndFlush(new PurchaseOrder()));
        if (order.getCreatedAt().toLocalDate().isBefore(LocalDate.now())) {
            PurchaseOrderHistory.remember(orders, order, order.getCreatedAt());
            order.setCreatedAt(LocalDateTime.now()); orders.saveAndFlush(order);
        }
        if (!order.isInitialized()) {
            order.setInitialized(true); orders.saveAndFlush(order);
            audit.safeLogAction("СОЗДАНИЕ", "Заявка на закупку", "#" + order.getId(), "Сформирован первоначальный черновик");
        }
        return order;
    }
    private Draft snapshot(PurchaseOrder order) {
        return new Draft(order.getId(), order.getCreatedAt(), order.getStatus(), order.getItems().stream()
                .map(i -> new Row(i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getUnit(),
                        InventoryService.stock(i.getProduct()), i.getQuantity())).toList());
    }
    public Draft load() { return snapshot(draft()); }
    // Called from the till after the failed checkout transaction has rolled back.
    // Automatic replenishment is allowed even when the seller cannot open the reorder screen.
    @PreAuthorize("@permissionService.canAccess('sales')")
    public Draft addShortages(Map<Long, BigDecimal> shortages) {
        if (shortages == null || shortages.isEmpty()) throw new IllegalArgumentException("Нет недостающих товаров");
        for (var entry : shortages.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().signum() <= 0)
                throw new IllegalArgumentException("Укажите недостающее количество");
            validateQuantity(entry.getValue());
        }
        Draft result = null;
        for (Long productId : shortages.keySet().stream().sorted().toList()) result = add(productId, shortages.get(productId));
        return result;
    }
    public PendingSupply prepareSupply() {
        PurchaseOrder order = draft();
        if (order.getItems().isEmpty()) throw new IllegalArgumentException("Список заказа пуст");
        for (var item : order.getItems()) {
            validateQuantity(item.getQuantity());
            if (item.getQuantity().signum() == 0)
                throw new IllegalArgumentException("Укажите количество больше нуля для товара: " + item.getProduct().getName());
        }
        return new PendingSupply(order.getId(), order.getItems().stream().map(item -> new PendingSupply.Line(
                item.getId(), item.getProduct().getId(), item.getProduct().getName(), item.getProduct().getUnit(), item.getQuantity())).toList());
    }
    @Transactional(readOnly = true)
    public List<Product> catalog(String search) {
        return products.findByNameContainingIgnoreCase(search == null ? "" : search.trim()).stream()
                .sorted(Comparator.comparing(p -> Objects.toString(p.getName(), ""), String.CASE_INSENSITIVE_ORDER)).toList();
    }
    @Transactional(readOnly = true)
    public List<Product> searchProducts(String search, int page, int pageSize) {
        return products.findByNameContainingIgnoreCase(search == null ? "" : search.trim(),
                org.springframework.data.domain.PageRequest.of(page, pageSize));
    }
    private void appendRecommendations(PurchaseOrder order) {
        Set<Long> existing = new HashSet<>(); order.getItems().forEach(i -> existing.add(i.getProduct().getId()));
        for (var row : reorder.calculate(LocalDate.now())) {
            if (!existing.contains(row.productId())) addItem(order, products.findById(row.productId()).orElseThrow(), row.recommendedOrder());
        }
    }
    private void addItem(PurchaseOrder order, Product product, BigDecimal quantity) {
        PurchaseOrderItem item = new PurchaseOrderItem(); item.setOrder(order); item.setProduct(product); item.setQuantity(quantity);
        order.getItems().add(item);
    }
    public Draft add(Long productId) {
        return add(productId, ReorderService.BASE_ORDER);
    }
    public Draft add(Long productId, BigDecimal quantity) {
        if (productId == null) throw new IllegalArgumentException("Выберите товар");
        validateQuantity(quantity);
        if (quantity.signum() == 0) throw new IllegalArgumentException("Количество для добавления должно быть больше нуля");
        PurchaseOrder order = draft();
        Product product = products.findById(productId).orElseThrow(() -> new IllegalArgumentException("Товар больше не существует"));
        var existing = order.getItems().stream().filter(i -> i.getProduct().getId().equals(productId)).findFirst();
        BigDecimal before = existing.map(PurchaseOrderItem::getQuantity).orElse(BigDecimal.ZERO);
        BigDecimal total = before.add(quantity); validateQuantity(total);
        if (existing.isPresent()) existing.get().setQuantity(total); else addItem(order, product, total);
        orders.saveAndFlush(order);
        PurchaseOrderHistory.remember(orders, order);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Заявка на закупку", "#" + order.getId(), "Добавлен товар: " + product.getName()
                + "; Добавлено: " + quantity + "; Количество в заказе: " + before + " → " + total);
        return snapshot(order);
    }
    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() < 0 || quantity.compareTo(new BigDecimal("99999999.99")) > 0
                || quantity.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Введите заказ от 0 до 99999999,99, не более двух знаков после запятой");
    }
    public Draft changeQuantity(Long productId, BigDecimal quantity) {
        validateQuantity(quantity);
        PurchaseOrder order = draft();
        PurchaseOrderItem item = order.getItems().stream().filter(i -> i.getProduct().getId().equals(productId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Позиция уже удалена. Обновите список"));
        BigDecimal before = item.getQuantity();
        item.setQuantity(quantity); orders.saveAndFlush(order);
        PurchaseOrderHistory.remember(orders, order);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Заявка на закупку", "#" + order.getId(), item.getProduct().getName() + "; Количество: " + before + " → " + quantity);
        return snapshot(order);
    }
    public Draft remove(Long productId) {
        PurchaseOrder order = draft();
        if (order.getItems().size() == 1 && order.getItems().get(0).getProduct().getId().equals(productId))
            PurchaseOrderHistory.remember(orders, order);
        boolean removed = order.getItems().removeIf(i -> i.getProduct().getId().equals(productId));
        orders.saveAndFlush(order);
        if (removed) PurchaseOrderHistory.remember(orders, order);
        if (removed) audit.safeLogAction("ИЗМЕНЕНИЕ", "Заявка на закупку", "#" + order.getId(), "Удалена позиция товара #" + productId);
        return snapshot(order);
    }
    public Draft addCalculated() {
        PurchaseOrder order = draft(); appendRecommendations(order); orders.saveAndFlush(order);
        PurchaseOrderHistory.remember(orders, order);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Заявка на закупку", "#" + order.getId(), "Добавлены позиции по расчету");
        return snapshot(order);
    }

    public Draft repeatYesterday() {
        PurchaseOrder order = draft();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        PurchaseOrder source = orders.findFirstByIdNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                PurchaseOrder.currentDraftId(), yesterday.atStartOfDay(), yesterday.plusDays(1).atStartOfDay())
                .filter(saved -> !saved.getItems().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("Вчерашних заявок не найдено"));
        // Read and validate before replacing the current draft.
        var copied = source.getItems().stream().map(item -> new java.util.AbstractMap.SimpleImmutableEntry<>(
                item.getProduct(), item.getQuantity())).toList();
        copied.forEach(item -> validateQuantity(item.getValue()));
        PurchaseOrderHistory.remember(orders, order);
        order.getItems().clear(); orders.saveAndFlush(order);
        copied.forEach(item -> addItem(order, item.getKey(), item.getValue()));
        orders.saveAndFlush(order); PurchaseOrderHistory.remember(orders, order);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Заявка на закупку", "#" + order.getId(),
                "Повторена заявка #" + source.getId() + " за " + yesterday + "; Позиций: " + copied.size());
        return snapshot(order);
    }

    public Draft clear() {
        PurchaseOrder order = draft(); int count = order.getItems().size();
        PurchaseOrderHistory.remember(orders, order);
        order.getItems().clear(); orders.saveAndFlush(order);
        if (count > 0) audit.safeLogAction("ИЗМЕНЕНИЕ", "Заявка на закупку", "#" + order.getId(),
                "Очищен текущий заказ; Позиций: " + count);
        return snapshot(order);
    }
}
