package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.Inventory;
import ru.sklad.app.repository.InventoryRepository;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.entity.Product;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER') and @permissionService.canAccess('inventory')")
public class InventoryService {
    private final ProductRepository products;
    private final InventoryRepository inventories;
    private final AuditService audit;
    @org.springframework.beans.factory.annotation.Autowired private ru.sklad.app.repository.InventoryDraftRepository drafts;
    public InventoryService(ProductRepository products, InventoryRepository inventories, AuditService audit) {
        this.products = products; this.inventories = inventories;
        this.audit = audit;
    }
    public List<Product> products() { return products.findAllByShop(ru.sklad.app.util.SecurityUtils.businessShop()); }
    public List<Inventory> history() { return inventories.findAllByOrderByInventoryDateDescIdDesc(); }
    public List<ru.sklad.app.entity.InventoryDraft> drafts() { return drafts.findAll().stream().sorted(Comparator.comparing(ru.sklad.app.entity.InventoryDraft::getId).reversed()).toList(); }
    public ru.sklad.app.entity.InventoryDraft draft(Long id) { return drafts.findById(id).orElseThrow(() -> new IllegalArgumentException("Черновик не найден")); }
    @Transactional
    public Long saveDraft(Long id, LocalDate date, String employee, List<ru.sklad.app.util.InventoryData.Item> items) {
        if(date==null || employee==null || employee.isBlank() || employee.length()>255) throw new IllegalArgumentException("Укажите дату и проводящего");
        var draft = id==null ? new ru.sklad.app.entity.InventoryDraft() : draft(id);
        draft.setDate(date); draft.setEmployeeName(employee.trim()); draft.setItemsData(ru.sklad.app.util.InventoryData.encode(items)); drafts.saveAndFlush(draft); return draft.getId();
    }
    @Transactional
    public void deleteDraft(Long id) { drafts.delete(draft(id)); }
    public static BigDecimal stock(Product product) {
        return product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
    }
    @Transactional
    public int complete(LocalDate date, String employee, String notes,
                        Map<Long, BigDecimal> actual, Map<Long, BigDecimal> expected) {
        if (date == null || date.getYear() < 1 || date.getYear() > 9999 || employee == null
                || employee.isBlank() || employee.trim().length() > 255)
            throw new IllegalArgumentException("Укажите дату и ФИО проводящего (до 255 символов)");
        if (actual == null || actual.isEmpty()) throw new IllegalArgumentException("Введите фактический остаток хотя бы для одного товара");
        if (actual.keySet().stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("Product ID is required");
        Map<Product, BigDecimal> updates = new LinkedHashMap<>();
        for (Long id : actual.keySet().stream().sorted().toList()) {
            BigDecimal value = actual.get(id);
            if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("99999999.99")) > 0
                    || value.stripTrailingZeros().scale() > 2)
                throw new IllegalArgumentException("Остаток должен быть от 0 до 99999999,99 с точностью до двух знаков");
            Product product = products.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Товар больше не существует. Обновите страницу"));
            if (expected == null || expected.get(id) == null || stock(product).compareTo(expected.get(id)) != 0)
                throw new IllegalArgumentException("Остаток товара «" + product.getName() + "» изменился. Обновите страницу и повторите сверку");
            updates.put(product, value);
        }
        var snapshot = updates.entrySet().stream().map(entry -> {
            Product p = entry.getKey();
            return new ru.sklad.app.util.InventoryData.Item(p.getId(), p.getName(), stock(p), entry.getValue(), Objects.requireNonNullElse(p.getPurchasePrice(), BigDecimal.ZERO), Objects.requireNonNullElse(p.getRetailPrice(), BigDecimal.ZERO));
        }).toList();
        updates.forEach((product, quantity) -> product.setStockQuantity(quantity));
        products.saveAll(updates.keySet());
        Inventory inventory = new Inventory();
        inventory.setInventoryDate(date.atTime(LocalTime.now().withNano(0)));
        inventory.setEmployeeName(employee.trim()); inventory.setNotes(notes == null ? "" : notes.trim());
        inventory.setItemsData(ru.sklad.app.util.InventoryData.encode(snapshot));
        inventories.saveAndFlush(inventory);
        audit.safeLogAction("СОЗДАНИЕ", "Инвентаризация", "#" + inventory.getId(), "Проводил: " + employee + "; Фактические остатки (ID → количество): " + actual + "; Учетные: " + expected);
        return updates.size();
    }
}
