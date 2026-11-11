package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.Product;
import ru.sklad.app.entity.Sale;
import ru.sklad.app.entity.SaleItem;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.repository.SaleRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Map;
import java.time.LocalDateTime;
import ru.sklad.app.util.DiscountCalculator;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;

@Service
@PreAuthorize("@permissionService.canAccess('sales')")
public class SalesService {
    public record HistorySummary(java.util.List<Sale> sales, BigDecimal revenue, BigDecimal profit) {}
    private final ProductRepository repository;
    private final SaleRepository sales;
    private final PermissionService permissions;
    private final AuditService audit;

    public SalesService(ProductRepository repository, SaleRepository sales, PermissionService permissions, AuditService audit) {
        this.repository = repository;
        this.sales = sales;
        this.permissions = permissions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionService.canAccess('sales-history')")
    public java.util.List<Sale> history() {
        return sales.findAllByOrderBySaleDateDescIdDesc();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionService.canAccess('sales-history')")
    public java.util.List<Sale> history(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Дата «С» должна быть не позже даты «По»");
        }
        if (from == null && to == null) return sales.findAllByOrderBySaleDateDescIdDesc();
        var start = (from == null ? LocalDate.of(1, 1, 1) : from).atStartOfDay();
        var end = (to == null ? LocalDate.of(9999, 12, 31) : to).plusDays(1).atStartOfDay();
        return sales.findBySaleDateGreaterThanEqualAndSaleDateLessThanOrderBySaleDateDescIdDesc(start, end);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionService.canAccess('sales-history')")
    public HistorySummary historyWithTotals(LocalDate from, LocalDate to) {
        var history = history(from, to);
        BigDecimal revenue = history.stream().map(Sale::getFinalSum).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Do not calculate or return profit to users without the report permission.
        BigDecimal profit = null;
        if (permissions.hasPermission("canSeeReports")) {
            BigDecimal cost = BigDecimal.ZERO;
            for (Sale sale : history) {
                for (SaleItem item : sale.getItems()) {
                    BigDecimal purchasePrice = item.getPurchasePrice();
                    if (purchasePrice == null && item.getProduct() != null) purchasePrice = item.getProduct().getPurchasePrice();
                    cost = cost.add((purchasePrice == null ? BigDecimal.ZERO : purchasePrice).multiply(item.getQuantity()));
                }
            }
            profit = revenue.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        }
        return new HistorySummary(history, revenue.setScale(2, RoundingMode.HALF_UP), profit);
    }

    @Transactional
    public Sale conductSale(Map<Long, BigDecimal> cart, Map<Long, BigDecimal> displayedPrices) {
        return conductSale(cart, displayedPrices, BigDecimal.ZERO, "%");
    }

    @Transactional
    public Sale conductSale(Map<Long, BigDecimal> cart, Map<Long, BigDecimal> displayedPrices,
                            BigDecimal discountValue, String discountType) {
        return conductSale(cart, displayedPrices, discountValue, discountType, "CASH");
    }

    @Transactional
    public Sale conductSale(Map<Long, BigDecimal> cart, Map<Long, BigDecimal> displayedPrices,
                            BigDecimal discountValue, String discountType, String paymentMethod) {
        if (discountValue != null && discountValue.signum() != 0 && !permissions.hasPermission("canGiveDiscount")) {
            throw new IllegalArgumentException("У вас нет прав на предоставление скидки");
        }
        if (!"CASH".equals(paymentMethod) && !"CASHLESS".equals(paymentMethod)) {
            throw new IllegalArgumentException("Выберите способ оплаты");
        }
        if (cart == null || cart.isEmpty()) {
            throw new IllegalArgumentException("Корзина пуста");
        }
        if (cart.keySet().stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("Product ID is required");
        var products = new ArrayList<Product>();
        var shortages = new ArrayList<InsufficientStockException.Shortage>();
        // Lock in a stable order; validate every line before changing any stock.
        for (Long id : cart.keySet().stream().sorted().toList()) {
            BigDecimal quantity = cart.get(id);
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalArgumentException("Количество должно быть больше нуля");
            }
            quantity = quantity.setScale(2, RoundingMode.UNNECESSARY);
            Product product = repository.findLockedById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Товар больше не существует"));
            if (product.getRetailPrice() == null || product.getRetailPrice().signum() < 0) {
                throw new IllegalArgumentException("Проверьте розничную цену товара: " + product.getName());
            }
            if (displayedPrices == null || displayedPrices.get(id) == null
                    || product.getRetailPrice().compareTo(displayedPrices.get(id)) != 0) {
                throw new IllegalArgumentException("Цена изменилась: " + product.getName()
                        + ". Корзина обновлена, проверьте итог и проведите продажу повторно.");
            }
            BigDecimal stock = product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
            if (stock.compareTo(quantity) < 0) {
                shortages.add(new InsufficientStockException.Shortage(product.getId(), product.getName(), stock, quantity.subtract(stock)));
            }
            products.add(product);
        }
        if (!shortages.isEmpty()) throw new InsufficientStockException(shortages);
        Sale sale = new Sale();
        sale.setPaymentMethod(paymentMethod);
        sale.setSaleDate(LocalDateTime.now());
        BigDecimal total = BigDecimal.ZERO;
        for (Product product : products) {
            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setUnit(product.getUnit());
            item.setQuantity(cart.get(product.getId()).setScale(2, RoundingMode.UNNECESSARY));
            item.setRetailPrice(product.getRetailPrice());
            item.setPurchasePrice(product.getPurchasePrice());
            item.setTotalSum(item.getRetailPrice().multiply(item.getQuantity()).setScale(2, RoundingMode.HALF_UP));
            sale.addItem(item);
            total = total.add(item.getTotalSum());
        }
        sale.setTotalSum(total);
        sale.setDiscountSum(DiscountCalculator.discount(total, discountValue, discountType));
        sale.setFinalSum(total.subtract(sale.getDiscountSum()));
        for (Product product : products) {
            product.setStockQuantity(InventoryService.stock(product).subtract(cart.get(product.getId())));
            repository.save(product);
        }
        Sale saved = sales.save(sale);
        audit.logAction("СОЗДАНИЕ", "Продажа", "#" + saved.getId(), "Товары (ID → количество): " + cart + "; Итог: " + saved.getFinalSum() + "; Оплата: " + paymentMethod);
        return saved;
    }
}
