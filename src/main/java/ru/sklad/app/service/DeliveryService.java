package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
@PreAuthorize("@permissionService.canAccess('deliveries')")
public class DeliveryService {
    public long countNewToday() {
        return deliveries.countByDeliveryDateAndStatus(LocalDate.now(), "Новая");
    }
    private final DeliveryRepository deliveries;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final AuditService audit;
    public DeliveryService(DeliveryRepository deliveries, CustomerRepository customers, ProductRepository products,
                           org.springframework.context.ApplicationEventPublisher events, AuditService audit) {
        this.deliveries = deliveries;
        this.customers = customers;
        this.products = products;
        this.events = events;
        this.audit = audit;
    }
    public record Line(Product product, BigDecimal quantity) {}
    public List<Delivery> list() { return deliveries.findAllByOrderByDeliveryDateDescDeliveryTimeDescIdDesc(); }
    public List<Delivery> list(LocalDate date) {
        return date == null ? List.of() : deliveries.findByDeliveryDateOrderByDeliveryTimeAscIdAsc(date);
    }
    public List<Customer> customers() { return customers.findAllByOrderByNameAscIdAsc(); }
    private void validateCustomer(String name, String address, String phone) {
        if (name == null || name.isBlank() || name.trim().length() > 255 || address == null
                || address.isBlank() || address.trim().length() > 255 || phone.length() < 5 || phone.length() > 15)
            throw new IllegalArgumentException("Укажите имя, адрес и корректный телефон клиента");
    }
    @Transactional
    public Customer createCustomer(String name, String address, String phone) {
        String normalized = normalizePhone(phone);
        validateCustomer(name, address, normalized);
        if (customers.findByPhone(normalized).isPresent()) throw new IllegalArgumentException("Заказчик с таким телефоном уже существует");
        Customer customer = new Customer();
        customer.setName(name.trim()); customer.setAddress(address.trim()); customer.setPhone(normalized);
        Customer saved = customers.saveAndFlush(customer);
        audit.safeLogAction("СОЗДАНИЕ", "Клиент", saved.getName() + " (#" + saved.getId() + ")", "Создан заказчик доставки");
        return saved;
    }
    private Delivery lockNew(Long id) {
        Delivery delivery = deliveries.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Заявка больше не существует"));
        if (!"Новая".equals(delivery.getStatus())) throw new IllegalArgumentException("Заявка уже выполнена или отменена");
        return delivery;
    }
    @Transactional
    public void complete(Long id) {
        Delivery delivery = lockNew(id);
        if (delivery.getItems().isEmpty()) throw new IllegalArgumentException("В заявке нет товаров");
        Map<Long, BigDecimal> quantities = new TreeMap<>();
        for (DeliveryItem item : delivery.getItems()) {
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0)
                throw new IllegalArgumentException("Проверьте количество товаров в заявке");
            quantities.merge(item.getProduct().getId(), item.getQuantity(), BigDecimal::add);
        }
        Map<Product, BigDecimal> remaining = new LinkedHashMap<>();
        quantities.forEach((productId, quantity) -> {
            Product product = products.findLockedById(productId).orElseThrow(() -> new IllegalArgumentException("Товар больше не существует"));
            BigDecimal stock = product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
            if (stock.compareTo(quantity) < 0) throw new IllegalArgumentException("Недостаточно товара: " + product.getName() + ". Остаток: " + stock);
            remaining.put(product, stock.subtract(quantity));
        });
        remaining.forEach((product, stock) -> { product.setStockQuantity(stock); products.save(product); });
        delivery.setStatus("Выполнена"); deliveries.saveAndFlush(delivery);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Доставка", "#" + id, "Статус: Новая → Выполнена; товары списаны: " + quantities);
    }
    @Transactional
    public void cancel(Long id) {
        Delivery delivery = lockNew(id);
        delivery.setStatus("Отменена"); deliveries.saveAndFlush(delivery);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Доставка", "#" + id, "Статус: Новая → Отменена; без списания товаров");
    }
    public Delivery get(Long id) {
        return deliveries.findById(id).orElseThrow(() -> new IllegalArgumentException("Заявка больше не существует"));
    }
    public List<Line> repeat(Long customerId, Long editingId) {
        var last = editingId == null
                ? deliveries.findFirstByCustomerIdOrderByDeliveryDateDescDeliveryTimeDescIdDesc(customerId)
                : deliveries.findFirstByCustomerIdAndIdNotOrderByDeliveryDateDescDeliveryTimeDescIdDesc(customerId, editingId);
        return last.map(d -> get(d.getId()).getItems().stream()
                .map(i -> new Line(i.getProduct(), i.getQuantity())).toList()).orElseGet(List::of);
    }
    public static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }
    @Transactional
    public Delivery save(Long id, Long customerId, String name, String address, String phone,
                         LocalDate date, LocalTime time, String payment, String comment, String status,
                         Map<Long, BigDecimal> quantities) {
        if (date == null || date.getYear() < 1 || date.getYear() > 9999 || time == null
                || !Set.of("Нал", "Безнал").contains(Objects.toString(payment, ""))
                || !"Новая".equals(status))
            throw new IllegalArgumentException("Проверьте дату, время, оплату и статус заявки");
        if (quantities == null || quantities.isEmpty()) throw new IllegalArgumentException("Добавьте товары в заявку");
        Delivery delivery = id == null ? new Delivery() : lockNew(id);
        Customer customer;
        if (customerId != null) {
            customer = customers.findById(customerId).orElseThrow(() -> new IllegalArgumentException("Клиент больше не существует"));
        } else {
            String normalized = normalizePhone(phone);
            validateCustomer(name, address, normalized);
            customer = customers.findByPhone(normalized).orElseGet(() -> {
                Customer created = new Customer();
                created.setName(name.trim()); created.setAddress(address.trim()); created.setPhone(normalized);
                Customer saved = customers.save(created);
                audit.safeLogAction("СОЗДАНИЕ", "Клиент", saved.getName() + " (#" + saved.getId() + ")", "Создан при оформлении доставки");
                return saved;
            });
        }
        List<DeliveryItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var line : quantities.entrySet()) {
            BigDecimal quantity = line.getValue();
            if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(new BigDecimal("99999999.99")) > 0
                    || quantity.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Укажите положительное количество с точностью до двух знаков");
            Product product = products.findById(line.getKey()).orElseThrow(() -> new IllegalArgumentException("Товар больше не существует"));
            if (product.getRetailPrice() == null || product.getRetailPrice().signum() < 0)
                throw new IllegalArgumentException("Проверьте розничную цену товара: " + product.getName());
            DeliveryItem item = new DeliveryItem();
            item.setDelivery(delivery); item.setProduct(product); item.setQuantity(quantity);
            items.add(item);
            total = total.add(product.getRetailPrice().multiply(quantity));
        }
        if (total.compareTo(new BigDecimal("99999999999999999.99")) > 0) throw new IllegalArgumentException("Сумма заявки слишком велика");
        delivery.setCustomer(customer); delivery.setDeliveryDate(date); delivery.setDeliveryTime(time);
        delivery.setPaymentMethod(payment); delivery.setComment(comment); delivery.setStatus(status);
        delivery.setTotalSum(total.setScale(2, RoundingMode.HALF_UP));
        delivery.getItems().clear(); delivery.getItems().addAll(items);
        Delivery saved = deliveries.saveAndFlush(delivery);
        audit.safeLogAction(id == null ? "СОЗДАНИЕ" : "ИЗМЕНЕНИЕ", "Доставка", "#" + saved.getId(),
                "Клиент: " + customer.getName() + "; Дата: " + date + " " + time + "; Товары (ID → количество): " + quantities + "; Сумма: " + saved.getTotalSum());
        if (id == null) events.publishEvent(new DeliveryCreatedEvent(notification(saved), saved.getShop().getId()));
        return saved;
    }
    private String notification(Delivery delivery) {
        Customer customer = delivery.getCustomer();
        String lines = delivery.getItems().stream().map(item ->
                html(item.getProduct().getName()) + " — "
                        + item.getQuantity().stripTrailingZeros().toPlainString().replace('.', ',')
                        + (item.getProduct().getUnit() == null ? "" : " " + html(item.getProduct().getUnit())))
                .collect(java.util.stream.Collectors.joining(", "));
        return "🚚 <b>Новая доставка!</b>\nКлиент: " + html(customer.getName())
                + "\nАдрес: " + html(customer.getAddress())
                + "\nВремя: " + delivery.getDeliveryDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                + " " + delivery.getDeliveryTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                + "\nТелефон: " + html(customer.getPhone()) + "\nСостав заказа: " + lines;
    }
    private static String html(String value) {
        return org.springframework.web.util.HtmlUtils.htmlEscape(Objects.toString(value, ""));
    }
}
