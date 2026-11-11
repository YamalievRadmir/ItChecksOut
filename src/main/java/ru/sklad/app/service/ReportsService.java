package ru.sklad.app.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.repository.SaleItemRepository;
import ru.sklad.app.repository.SaleRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.math.RoundingMode;
import ru.sklad.app.entity.Sale;
import ru.sklad.app.entity.SaleItem;
import ru.sklad.app.util.RoleAccess;

@Service
@PreAuthorize("@permissionService.canAccess('reports')")
@Transactional(readOnly = true)
public class ReportsService {
    private final ProductRepository products;
    private final SaleItemRepository sales;
    private final SaleRepository saleRepository;

    public ReportsService(ProductRepository products, SaleItemRepository sales, SaleRepository saleRepository) {
        this.products = products;
        this.sales = sales;
        this.saleRepository = saleRepository;
    }

    public Inventory inventory() {
        var totals = products.calculateInventoryTotals();
        return new Inventory(totals.getPurchaseTotal(), totals.getRetailTotal());
    }

    public ChartData chartData(LocalDate today) {
        if (today == null) throw new IllegalArgumentException("Date is required");
        var end = today.plusDays(1).atStartOfDay();
        var dailyTotals = new HashMap<LocalDate, BigDecimal>();
        saleRepository.findDailyRevenue(today.minusDays(13).atStartOfDay(), end)
                .forEach(day -> dailyTotals.put(day.getDay(), day.getRevenue()));
        var points = new ArrayList<DayRevenue>();
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            points.add(new DayRevenue(day, dailyTotals.getOrDefault(day, BigDecimal.ZERO)));
        }
        var start = today.minusDays(29).atStartOfDay();
        return new ChartData(points, sales.findTopProductsNet(start, end, PageRequest.of(0, 5)),
                sales.findCategoryRevenue(start, end));
    }

    public record DayRevenue(LocalDate day, BigDecimal revenue) {}
    public record DaySales(LocalDate day, BigDecimal quantity, BigDecimal revenue,
                           BigDecimal profit, BigDecimal purchase) {}
    public record DayProduct(Long productId, String name, String unit, BigDecimal quantity,
                             BigDecimal salePrice, BigDecimal revenue, BigDecimal purchasePrice, BigDecimal profit) {}

    public List<DaySales> dailySales(LocalDate from, LocalDate to) {
        validatePeriod(from, to);
        boolean admin = RoleAccess.isAdmin();
        Map<LocalDate, Amounts> days = new TreeMap<>(Comparator.reverseOrder());
        for (Sale sale : saleRepository.findBySaleDateGreaterThanEqualAndSaleDateLessThanOrderBySaleDateDescIdDesc(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            Amounts day = days.computeIfAbsent(sale.getSaleDate().toLocalDate(), ignored -> new Amounts());
            day.revenue = day.revenue.add(sale.getFinalSum());
            for (SaleItem item : sale.getItems()) {
                day.quantity = day.quantity.add(item.getQuantity());
                if (admin) day.cost = day.cost.add(cost(item));
            }
        }
        return days.entrySet().stream().map(entry -> {
            Amounts day = entry.getValue();
            return new DaySales(entry.getKey(), day.quantity, day.revenue,
                    admin ? day.revenue.subtract(day.cost) : null, admin ? day.cost : null);
        }).toList();
    }

    public List<DayProduct> dayProducts(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Выберите день");
        boolean admin = RoleAccess.isAdmin();
        Map<ProductKey, Amounts> totals = new LinkedHashMap<>();
        for (Sale sale : saleRepository.findBySaleDateGreaterThanEqualAndSaleDateLessThanOrderBySaleDateDescIdDesc(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay())) {
            BigDecimal remainingRevenue = sale.getFinalSum(), remainingGross = sale.getTotalSum();
            for (int i = 0; i < sale.getItems().size(); i++) {
                SaleItem item = sale.getItems().get(i);
                // Assign the last line the remainder so line totals always equal the discounted check total.
                BigDecimal revenue = i == sale.getItems().size() - 1 ? remainingRevenue
                        : remainingGross.signum() <= 0 ? BigDecimal.ZERO
                        : item.getTotalSum().multiply(remainingRevenue).divide(remainingGross, 2, RoundingMode.HALF_UP)
                                .min(remainingRevenue).max(BigDecimal.ZERO);
                remainingRevenue = remainingRevenue.subtract(revenue);
                remainingGross = remainingGross.subtract(item.getTotalSum());
                ProductKey key = new ProductKey(item.getProduct().getId(), item.getProductName(), item.getUnit());
                Amounts row = totals.computeIfAbsent(key, ignored -> new Amounts());
                row.quantity = row.quantity.add(item.getQuantity()); row.revenue = row.revenue.add(revenue);
                if (admin) row.cost = row.cost.add(cost(item));
            }
        }
        return totals.entrySet().stream().map(entry -> {
            ProductKey key = entry.getKey(); Amounts row = entry.getValue();
            return new DayProduct(key.id(), key.name(), key.unit(), row.quantity,
                    average(row.revenue, row.quantity), row.revenue, admin ? average(row.cost, row.quantity) : null,
                    admin ? row.revenue.subtract(row.cost) : null);
        }).sorted(Comparator.comparing(DayProduct::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(DayProduct::productId)).toList();
    }

    private record ProductKey(Long id, String name, String unit) {}
    private static class Amounts {
        BigDecimal quantity = BigDecimal.ZERO, revenue = BigDecimal.ZERO, cost = BigDecimal.ZERO;
    }
    private static BigDecimal average(BigDecimal amount, BigDecimal quantity) {
        return quantity.signum() == 0 ? BigDecimal.ZERO : amount.divide(quantity, 2, RoundingMode.HALF_UP);
    }
    private static BigDecimal cost(SaleItem item) {
        BigDecimal price = item.getPurchasePrice();
        if (price == null && item.getProduct() != null) price = item.getProduct().getPurchasePrice();
        return (price == null ? BigDecimal.ZERO : price).multiply(item.getQuantity());
    }
    private static void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to))
            throw new IllegalArgumentException("Укажите период: дата «С» должна быть не позже даты «По»");
    }
    public record ChartData(List<DayRevenue> daily, List<SaleItemRepository.ProductSales> topProducts,
                            List<SaleItemRepository.CategorySales> categories) {}

    public SalesReport sales(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Укажите период: дата «С» должна быть не позже даты «По»");
        }
        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();
        return new SalesReport(sales.findTopProducts(start, end, PageRequest.of(0, 10)),
                sales.sumRevenue(start, end), saleRepository.sumRevenueByPaymentMethod(start, end, "CASH"),
                saleRepository.sumRevenueByPaymentMethod(start, end, "CASHLESS"));
    }

    public record Inventory(BigDecimal purchase, BigDecimal retail) {
        public BigDecimal profit() { return retail.subtract(purchase); }
    }

    public record SalesReport(List<SaleItemRepository.ProductSales> topProducts, BigDecimal revenue,
                              BigDecimal cashRevenue, BigDecimal cashlessRevenue) {}
}
