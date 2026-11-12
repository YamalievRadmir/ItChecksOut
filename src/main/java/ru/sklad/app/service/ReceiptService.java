package ru.sklad.app.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.util.DiscountCalculator;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@PreAuthorize("@permissionService.canAccess('sales-history')")
public class ReceiptService {
    public record Line(Long itemId, Long productId, String name, String unit,
                       BigDecimal quantity, BigDecimal price, BigDecimal sum) {}
    public record Receipt(Long id, LocalDateTime date, List<Line> lines, BigDecimal total,
                          BigDecimal discount, BigDecimal payable, String payment, String revision, boolean editable) {}
    public record Input(Long itemId, Long productId, BigDecimal quantity, BigDecimal price) {}
    public record Choice(Long id, String name, BigDecimal price) {}
    private static final BigDecimal MAX = new BigDecimal("99999999.99");
    private final SaleRepository sales;
    private final ProductRepository products;
    private final PermissionService permissions;
    private final AuditService audit;

    public ReceiptService(SaleRepository sales, ProductRepository products, PermissionService permissions, AuditService audit) {
        this.sales = sales; this.products = products; this.permissions = permissions; this.audit = audit;
    }

    public Receipt get(Long id) {
        return snapshot(sales.findById(id).orElseThrow(() -> new IllegalArgumentException("Чек не найден")));
    }

    @PreAuthorize("@permissionService.canAccess('sales-history') and @permissionService.hasPermission('canEditReceipts')")
    public List<Choice> choices() {
        return products.findAllByShop(ru.sklad.app.util.SecurityUtils.currentShop()).stream().sorted(Comparator.comparing(Product::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(p -> new Choice(p.getId(), p.getName(), p.getRetailPrice())).toList();
    }

    @Transactional
    @PreAuthorize("@permissionService.canAccess('sales-history') and @permissionService.hasPermission('canEditReceipts')")
    public Receipt update(Long id, String revision, List<Input> input, BigDecimal discount, String payment) {
        Sale sale = sales.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Чек не найден"));
        if (!Objects.equals(revision(sale), revision))
            throw new IllegalArgumentException("Чек уже изменен другим пользователем. Закройте карточку и откройте ее заново");
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("В чеке должна быть хотя бы одна позиция");
        if (!Set.of("CASH", "CASHLESS").contains(Objects.toString(payment, "")))
            throw new IllegalArgumentException("Выберите способ оплаты");
        String before = revision(sale);
        Map<Long, SaleItem> originals = sale.getItems().stream().collect(Collectors.toMap(SaleItem::getId, i -> i));
        Set<Long> retained = new HashSet<>();
        Map<Long, BigDecimal> oldQuantities = new TreeMap<>(), newQuantities = new TreeMap<>();
        sale.getItems().forEach(i -> oldQuantities.merge(i.getProduct().getId(), i.getQuantity(), BigDecimal::add));
        BigDecimal total = BigDecimal.ZERO;
        for (Input line : input) {
            if (line == null || line.productId() == null) throw new IllegalArgumentException("Выберите товар");
            decimal(line.quantity(), true, "Количество"); decimal(line.price(), false, "Цена");
            if (line.itemId() != null) {
                SaleItem original = originals.get(line.itemId());
                if (original == null || !retained.add(line.itemId()) || !original.getProduct().getId().equals(line.productId()))
                    throw new IllegalArgumentException("Некорректная позиция чека");
            }
            newQuantities.merge(line.productId(), line.quantity(), BigDecimal::add);
            total = total.add(line.quantity().multiply(line.price()).setScale(2, RoundingMode.HALF_UP));
        }
        if (discount == null || discount.scale() > 2) throw new IllegalArgumentException("Укажите скидку в рублях с точностью до копеек");
        BigDecimal discountSum = DiscountCalculator.discount(total, discount, "руб.");
        Set<Long> ids = new TreeSet<>(oldQuantities.keySet()); ids.addAll(newQuantities.keySet());
        Map<Long, Product> locked = new LinkedHashMap<>();
        Map<Long, BigDecimal> stocks = new LinkedHashMap<>();
        for (Long productId : ids) {
            Product product = products.findLockedById(productId).orElseThrow(() -> new IllegalArgumentException("Товар больше не существует"));
            BigDecimal remaining = InventoryService.stock(product).add(oldQuantities.getOrDefault(productId, BigDecimal.ZERO))
                    .subtract(newQuantities.getOrDefault(productId, BigDecimal.ZERO));
            if (remaining.signum() < 0) throw new IllegalArgumentException("Недостаточно товара: " + product.getName()
                    + ". Можно указать не более " + InventoryService.stock(product).add(oldQuantities.getOrDefault(productId, BigDecimal.ZERO)).stripTrailingZeros().toPlainString());
            if (remaining.compareTo(MAX) > 0) throw new IllegalArgumentException("Остаток превышает допустимое значение: " + product.getName());
            locked.put(productId, product); stocks.put(productId, remaining);
        }
        // Apply only after every position and every resulting stock has been validated.
        sale.getItems().removeIf(i -> !retained.contains(i.getId()));
        for (Input line : input) {
            SaleItem item = line.itemId() == null ? new SaleItem() : originals.get(line.itemId());
            Product product = locked.get(line.productId());
            if (line.itemId() == null) {
                item.setProduct(product); item.setProductName(product.getName()); item.setUnit(product.getUnit());
                item.setPurchasePrice(product.getPurchasePrice()); sale.addItem(item);
            }
            item.setQuantity(line.quantity().setScale(2)); item.setRetailPrice(line.price().setScale(2));
            item.setTotalSum(item.getQuantity().multiply(item.getRetailPrice()).setScale(2, RoundingMode.HALF_UP));
        }
        stocks.forEach((productId, stock) -> locked.get(productId).setStockQuantity(stock));
        sale.setTotalSum(total); sale.setDiscountSum(discountSum); sale.setFinalSum(total.subtract(discountSum));
        sale.setPaymentMethod(payment); sales.saveAndFlush(sale);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Продажа", "#" + sale.getId(), "Редактирование чека. До: " + before + "; После: " + revision(sale));
        return snapshot(sale);
    }

    private static void decimal(BigDecimal value, boolean positive, String label) {
        if (value == null || value.signum() < 0 || (positive && value.signum() == 0)
                || value.compareTo(MAX) > 0 || value.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException(label + ": " + (positive ? "больше нуля" : "не меньше нуля") + ", максимум 99999999,99 и два знака после запятой");
    }

    private Receipt snapshot(Sale sale) {
        var lines = sale.getItems().stream().sorted(Comparator.comparing(SaleItem::getId))
                .map(i -> new Line(i.getId(), i.getProduct().getId(), i.getProductName(), i.getUnit(), i.getQuantity(), i.getRetailPrice(), i.getTotalSum())).toList();
        return new Receipt(sale.getId(), sale.getSaleDate(), lines, sale.getTotalSum(), sale.getDiscountSum(),
                sale.getFinalSum(), sale.getPaymentMethod(), revision(sale), permissions.hasPermission("canEditReceipts"));
    }

    private static String revision(Sale sale) {
        return sale.getSaleDate() + "|" + sale.getTotalSum() + "|" + sale.getDiscountSum() + "|" + sale.getFinalSum() + "|" + sale.getPaymentMethod()
                + sale.getItems().stream().sorted(Comparator.comparing(SaleItem::getId))
                .map(i -> "|" + i.getId() + ":" + i.getProduct().getId() + ":" + i.getQuantity() + ":" + i.getRetailPrice() + ":" + i.getTotalSum()).collect(Collectors.joining());
    }
}
