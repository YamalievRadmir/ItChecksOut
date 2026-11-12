package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.controller.SiteOrderController.Order;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class SiteOrderService {
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final DeliveryRepository deliveries;
    private final AuditService audit;
    public SiteOrderService(CustomerRepository customers, ProductRepository products, DeliveryRepository deliveries, AuditService audit) {
        this.customers = customers; this.products = products; this.deliveries = deliveries; this.audit = audit;
    }
    private static void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("Invalid text");
    }
    @Transactional
    public Delivery create(Order order) {
        if (order == null) throw new IllegalArgumentException("Order is required");
        text(order.customerName(), 255); text(order.phone(), 50); text(order.address(), 255);
        String phone = DeliveryService.normalizePhone(order.phone());
        if (phone.length() < 5 || phone.length() > 15 || order.items() == null || order.items().isEmpty()
                || order.items().size() > 100 || (order.comment() != null && order.comment().length() > 4000))
            throw new IllegalArgumentException("Invalid order");
        for (var item : order.items()) {
            if (item == null) throw new IllegalArgumentException("Invalid item");
            text(item.productName(), 255);
            if (item.quantity() == null || item.quantity().signum() <= 0 || item.quantity().stripTrailingZeros().scale() > 2
                    || item.quantity().compareTo(new BigDecimal("99999999.99")) > 0) throw new IllegalArgumentException("Invalid quantity");
        }
        Customer customer = customers.findByPhone(phone).orElseGet(() -> {
            Customer c = new Customer(); c.setName(order.customerName().trim());
            c.setPhone(phone); c.setAddress(order.address().trim()); return customers.saveAndFlush(c);
        });
        Delivery delivery = new Delivery(); delivery.setCustomer(customer);
        delivery.setDeliveryDate(LocalDate.now().plusDays(1)); delivery.setDeliveryTime(LocalTime.of(9, 0));
        delivery.setStatus("Новая"); delivery.setPaymentMethod("Нал");
        StringBuilder comment = new StringBuilder("Заказ с agro-korma-shop.ru\nИмя: ").append(order.customerName().trim())
                .append("\nАдрес доставки: ").append(order.address().trim());
        if (order.comment() != null && !order.comment().isBlank()) comment.append("\n").append(order.comment().trim());
        Map<Long, DeliveryItem> lines = new LinkedHashMap<>();
        for (var input : order.items()) {
            var found = products.findTop2ByNameIgnoreCaseOrderByIdAsc(input.productName().trim());
            if (found.size() != 1 || found.get(0).getRetailPrice() == null || found.get(0).getRetailPrice().signum() < 0) {
                comment.append("\nКлиент заказал товар, которого нет в базе или требуется уточнение: ")
                        .append(input.productName()).append(" — ").append(input.quantity().toPlainString());
                continue;
            }
            Product product = found.get(0);
            DeliveryItem item = lines.computeIfAbsent(product.getId(), ignored -> {
                DeliveryItem line = new DeliveryItem(); line.setDelivery(delivery); line.setProduct(product);
                line.setQuantity(BigDecimal.ZERO); return line;
            });
            item.setQuantity(item.getQuantity().add(input.quantity()));
            if (item.getQuantity().compareTo(new BigDecimal("99999999.99")) > 0) throw new IllegalArgumentException("Quantity overflow");
        }
        delivery.getItems().addAll(lines.values());
        BigDecimal total = lines.values().stream().map(line -> line.getProduct().getRetailPrice().multiply(line.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(new BigDecimal("99999999999999999.99")) > 0) throw new IllegalArgumentException("Total overflow");
        delivery.setTotalSum(total); delivery.setComment(comment.toString());
        Delivery saved = deliveries.saveAndFlush(delivery);
        audit.safeLogAction("СОЗДАНИЕ", "Доставка", "#" + saved.getId(), "Заказ с сайта; позиций: " + lines.size());
        return saved;
    }
}
