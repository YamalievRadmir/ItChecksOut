package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class DemoHistoryGenerator {
    private final ProductRepository products;
    private final SaleRepository sales;
    private final SupplyRepository supplies;
    private final EmployeeRepository employees;
    private final WorkShiftRepository shifts;
    public DemoHistoryGenerator(ProductRepository products, SaleRepository sales, SupplyRepository supplies,
                                EmployeeRepository employees, WorkShiftRepository shifts) {
        this.products = products; this.sales = sales; this.supplies = supplies; this.employees = employees; this.shifts = shifts;
    }
    public record Result(int sales, int supplies, int employees, int shifts) {}
    private record Event(LocalDateTime time, Product incoming, int quantity) {}

    @Transactional
    public Result generate(List<Product> catalog, LocalDateTime now, Random random) {
        if (sales.count() > 0) return new Result(0, 0, 0, 0);
        if (catalog.isEmpty()) throw new IllegalArgumentException("Сначала импортируйте товары");
        LocalDate first = now.toLocalDate().minusDays(29);
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < 25; i++) events.add(new Event(first.plusDays(random.nextInt(30)).atStartOfDay(),
                catalog.get(random.nextInt(catalog.size())), 10 + random.nextInt(41)));
        for (int i = 0; i < 125; i++) {
            LocalDate day = first.plusDays(random.nextInt(30));
            int bound = day.equals(now.toLocalDate()) ? now.toLocalTime().toSecondOfDay() + 1 : 86400;
            events.add(new Event(day.atStartOfDay().plusSeconds(random.nextInt(bound)), null, 0));
        }
        events.sort(Comparator.comparing(Event::time));
        int createdSales = 0, createdSupplies = 0;
        for (Event event : events) {
            if (event.incoming() != null) {
                Product product = event.incoming(); BigDecimal quantity = BigDecimal.valueOf(event.quantity());
                product.setStockQuantity(product.getStockQuantity().add(quantity));
                Supply supply = new Supply(); supply.setProduct(product); supply.setQuantity(quantity);
                supply.setPurchasePrice(product.getPurchasePrice()); supply.setTotalSum(quantity.multiply(product.getPurchasePrice()).setScale(2, RoundingMode.HALF_UP));
                supply.setSupplyDate(event.time().toLocalDate()); supply.setSupplier("Тестовый поставщик");
                supply.setInvoiceNumber("ТЕСТ-" + now.toLocalDate() + "-" + (++createdSupplies));
                supply.setReceiptKey(UUID.randomUUID().toString()); supplies.save(supply);
                continue;
            }
            List<Product> available = new ArrayList<>(catalog.stream().filter(p -> p.getStockQuantity().compareTo(BigDecimal.ONE) >= 0).toList());
            if (available.isEmpty()) continue;
            Collections.shuffle(available, random);
            List<Product> chosen = available.subList(0, 1 + random.nextInt(Math.min(3, available.size())));
            Sale sale = new Sale(); sale.setSaleDate(event.time()); sale.setPaymentMethod(random.nextBoolean() ? "CASH" : "CASHLESS");
            BigDecimal total = BigDecimal.ZERO;
            for (Product product : chosen) {
                BigDecimal quantity = BigDecimal.valueOf(1 + random.nextInt(Math.min(5, product.getStockQuantity().intValue())));
                SaleItem item = new SaleItem(); item.setProduct(product); item.setProductName(product.getName()); item.setUnit(product.getUnit());
                item.setQuantity(quantity); item.setRetailPrice(product.getRetailPrice()); item.setPurchasePrice(product.getPurchasePrice());
                item.setTotalSum(quantity.multiply(product.getRetailPrice()).setScale(2, RoundingMode.HALF_UP));
                sale.addItem(item); total = total.add(item.getTotalSum());
            }
            if (sale.getItems().stream().anyMatch(item -> item.getProduct().getStockQuantity().compareTo(item.getQuantity()) < 0)) continue;
            BigDecimal discount = random.nextInt(4) == 0 ? total.multiply(random.nextBoolean() ? new BigDecimal("0.05") : new BigDecimal("0.10"))
                    .setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            sale.setTotalSum(total); sale.setDiscountSum(discount); sale.setFinalSum(total.subtract(discount));
            sale.getItems().forEach(item -> item.getProduct().setStockQuantity(item.getProduct().getStockQuantity().subtract(item.getQuantity())));
            sales.save(sale); createdSales++;
        }
        products.saveAll(catalog); products.flush();
        int createdEmployees = 0, createdShifts = 0;
        List<Employee> staff = employees.findAllByOrderByFullNameAscIdAsc();
        for (int i = 0; i < 3; i++) {
            String name = i < 2 ? "Тестовый продавец " + (i + 1) : "Тестовый грузчик";
            Employee employee = staff.stream().filter(e -> name.equals(e.getFullName())).findFirst().orElse(null);
            if (employee == null) {
                employee = new Employee(); employee.setFullName(name); employee.setRole(i < 2 ? "Продавец" : "Грузчик");
                employee.setBaseSalary(BigDecimal.valueOf(i < 2 ? 1500 : 1300)); employee.setPhone(""); employee = employees.save(employee); createdEmployees++;
            }
            Set<LocalDate> existing = new HashSet<>();
            shifts.findByEmployeeIdAndShiftDateBetween(employee.getId(), now.toLocalDate().withDayOfMonth(1), now.toLocalDate())
                    .forEach(shift -> existing.add(shift.getShiftDate()));
            for (int day = 1; day <= now.getDayOfMonth(); day++) {
                LocalDate date = now.toLocalDate().withDayOfMonth(day); if (existing.contains(date)) continue;
                WorkShift shift = new WorkShift(); shift.setEmployee(employee); shift.setShiftDate(date);
                shift.setWorked(random.nextInt(10) < 8); shifts.save(shift); createdShifts++;
            }
        }
        return new Result(createdSales, createdSupplies, createdEmployees, createdShifts);
    }
}
