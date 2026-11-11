package ru.sklad.app.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.repository.SaleItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

@Service
@PreAuthorize("@permissionService.canAccess('reorder')")
@Transactional(readOnly = true)
public class ReorderService {
    public static final BigDecimal LOW_STOCK_THRESHOLD = new BigDecimal("5");
    public static final int SALES_WINDOW_DAYS = 14;
    public static final int COVERAGE_DAYS = 14;
    public static final BigDecimal BASE_ORDER = new BigDecimal("10");

    private final ProductRepository products;
    private final SaleItemRepository sales;

    public ReorderService(ProductRepository products, SaleItemRepository sales) {
        this.products = products;
        this.sales = sales;
    }

    public List<ReorderRow> calculate(LocalDate today) {
        if (today == null) throw new IllegalArgumentException("Date is required");
        var consumption = new HashMap<Long, BigDecimal>();
        sales.findConsumption(today.minusDays(SALES_WINDOW_DAYS - 1).atStartOfDay(), today.plusDays(1).atStartOfDay())
                .forEach(item -> consumption.put(item.getProductId(), item.getQuantity()));
        return products.findLowStock(LOW_STOCK_THRESHOLD).stream().map(product -> {
            BigDecimal stock = product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
            BigDecimal sold = consumption.getOrDefault(product.getId(), BigDecimal.ZERO);
            BigDecimal daily = sold.divide(BigDecimal.valueOf(SALES_WINDOW_DAYS), 4, RoundingMode.HALF_UP);
            // Calculate from the unrounded sold quantity, so daily display rounding cannot change the order.
            BigDecimal order = sold.signum() == 0 ? BASE_ORDER
                    : sold.multiply(BigDecimal.valueOf(COVERAGE_DAYS))
                    .divide(BigDecimal.valueOf(SALES_WINDOW_DAYS), 2, RoundingMode.CEILING)
                    .subtract(stock).max(BigDecimal.ZERO);
            return new ReorderRow(product.getId(), product.getName(), product.getUnit(), stock, daily,
                    order.setScale(2, RoundingMode.CEILING));
        }).toList();
    }

    public record ReorderRow(Long productId, String name, String unit, BigDecimal stock,
                             BigDecimal dailyConsumption, BigDecimal recommendedOrder) {}
}
