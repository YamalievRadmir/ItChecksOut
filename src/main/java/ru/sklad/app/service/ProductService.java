package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.sklad.app.entity.Product;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.util.RoleAccess;
import java.util.*;

@Service
public class ProductService {
    public record Relations(Set<Long> alternatives, Set<Long> additives) {}
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Relations relations(Long id) {
        if (id == null) return new Relations(Set.of(), Set.of());
        Product p = products.findById(id).orElseThrow();
        return new Relations(ids(p.getAlternativeProducts()), ids(p.getRecommendedAdditives()));
    }
    private static Set<Long> ids(Collection<Product> values) {
        return values.stream().map(Product::getId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
    private final ProductRepository products;
    private final PermissionService permissions;
    private final AuditService audit;
    public ProductService(ProductRepository products, PermissionService permissions, AuditService audit) {
        this.products = products; this.permissions = permissions; this.audit = audit;
    }
    private Map<String, Object> values(Product p) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("Название", p.getName()); values.put("Штрихкод", p.getBarcode()); values.put("Категория", p.getCategory());
        values.put("Ед. изм.", p.getUnit()); values.put("Вид животного", p.getAnimalType()); values.put("Возраст", p.getAge());
        values.put("Остаток", p.getStockQuantity()); values.put("Цена закупа", p.getPurchasePrice()); values.put("Цена розница", p.getRetailPrice());
        return values;
    }
    @Transactional
    @PreAuthorize("@permissionService.canAccess('products')")
    public Product save(Product proposed) {
        if (proposed == null || proposed.getName() == null || proposed.getName().isBlank())
            throw new IllegalArgumentException("Product name is required");
        for (var value : java.util.Arrays.asList(proposed.getStockQuantity(), proposed.getRetailPrice(), proposed.getPurchasePrice())) {
            if (value != null && (value.signum() < 0 || value.compareTo(new java.math.BigDecimal("99999999.99")) > 0 || value.stripTrailingZeros().scale() > 2))
                throw new IllegalArgumentException("Invalid quantity or price");
        }
        boolean create = proposed.getId() == null;
        if (!permissions.hasPermission(create ? "canCreateProduct" : "canEditProduct"))
            throw new org.springframework.security.access.AccessDeniedException("Нет прав на изменение товара");
        // Serialize relation edits in ID order, including reverse links and concurrent sales.
        if (RoleAccess.isAdmin()) products.findAllForUpdate();
        Product product = create ? new Product() : products.findLockedById(proposed.getId()).orElseThrow();
        if (!create && product.getVersion() != proposed.getVersion())
            throw new org.springframework.dao.OptimisticLockingFailureException("Product changed; reopen the editor");
        Map<String, Object> before = values(product);
        product.setName(proposed.getName()); product.setStockQuantity(proposed.getStockQuantity()); product.setRetailPrice(proposed.getRetailPrice());
        if (RoleAccess.isAdmin() || create) {
            product.setBarcode(proposed.getBarcode()); product.setCategory(proposed.getCategory()); product.setUnit(proposed.getUnit());
        }
        if (RoleAccess.isAdmin()) {
            product.setAnimalType(proposed.getAnimalType()); product.setAge(proposed.getAge()); product.setPurchasePrice(proposed.getPurchasePrice());
            Set<Long> beforeAlternatives = ids(product.getAlternativeProducts());
            Set<Long> beforeAdditives = ids(product.getRecommendedAdditives());
            Set<Long> nextAlternatives = ids(proposed.getAlternativeProducts());
            Set<Long> nextAdditives = ids(proposed.getRecommendedAdditives());
            if (nextAlternatives.contains(product.getId()) || nextAdditives.contains(product.getId()))
                throw new IllegalArgumentException("Товар не может рекомендовать сам себя");
            products.save(product);
            Set<Long> affected = new TreeSet<>(beforeAlternatives); affected.addAll(nextAlternatives);
            for (Long id : affected) {
                Product other = products.findById(id).orElseThrow(() -> new IllegalArgumentException("Товар больше не существует"));
                if (nextAlternatives.contains(id)) other.getAlternativeProducts().add(product);
                else other.getAlternativeProducts().removeIf(p -> Objects.equals(p.getId(), product.getId()));
            }
            product.getAlternativeProducts().clear();
            for (Long id : nextAlternatives) product.getAlternativeProducts().add(products.findById(id).orElseThrow());
            product.getRecommendedAdditives().clear();
            for (Long id : nextAdditives) product.getRecommendedAdditives().add(products.findById(id).orElseThrow());
            if (!beforeAlternatives.equals(nextAlternatives) || !beforeAdditives.equals(nextAdditives))
                audit.safeLogAction("ИЗМЕНЕНИЕ", "Товар", product.getName(), "Альтернативы: " + beforeAlternatives + " → " + nextAlternatives
                        + "; Добавки: " + beforeAdditives + " → " + nextAdditives);
        }
        Map<String, Object> after = values(product);
        String details = after.entrySet().stream().filter(e -> !same(before.get(e.getKey()), e.getValue()))
                .map(e -> e.getKey() + ": " + Objects.toString(before.get(e.getKey()), "—") + " → " + Objects.toString(e.getValue(), "—"))
                .collect(java.util.stream.Collectors.joining("; "));
        Product saved = products.saveAndFlush(product);
        if (create || !details.isEmpty()) audit.safeLogAction(create ? "СОЗДАНИЕ" : "ИЗМЕНЕНИЕ", "Товар", saved.getName() + " (#" + saved.getId() + ")", details);
        return saved;
    }
    private boolean same(Object a, Object b) {
        if (a instanceof java.math.BigDecimal x && b instanceof java.math.BigDecimal y) return x.compareTo(y) == 0;
        return Objects.equals(a, b);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        for (Product other : products.findAllForUpdate()) {
            other.getAlternativeProducts().removeIf(p -> Objects.equals(p.getId(), id));
            other.getRecommendedAdditives().removeIf(p -> Objects.equals(p.getId(), id));
        }
        Product product = products.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Товар больше не существует"));
        audit.safeLogAction("УДАЛЕНИЕ", "Товар", product.getName() + " (#" + id + ")", "Удаление карточки товара");
        products.delete(product); products.flush();
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void clearWarehouse() {
        var shop = ru.sklad.app.util.SecurityUtils.currentShop();
        for (Product product : products.findAllForUpdate()) {
            product.getAlternativeProducts().clear(); product.getRecommendedAdditives().clear();
        }
        products.flush();
        products.deleteAllByShop(shop);
        products.flush();
        audit.safeLogAction("УДАЛЕНИЕ", "Товары", "Склад", "Удалены все товары текущего магазина");
    }}
