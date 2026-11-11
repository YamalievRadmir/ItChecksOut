package ru.sklad.app.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.Product;
import ru.sklad.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(readOnly = true)
@PreAuthorize("@permissionService.canAccess('sales') or @permissionService.canAccess('deliveries')")
public class ProductAlternativeService {
    private final ProductRepository products;
    public ProductAlternativeService(ProductRepository products) { this.products = products; }

    public Optional<Product> find(Long productId, BigDecimal needed, Map<Long, BigDecimal> cart) {
        return choices(productId, needed, cart).stream().findFirst();
    }

    public List<Product> additives(Long productId) {
        if (productId == null) return List.of();
        return products.findById(productId).map(p -> p.getRecommendedAdditives().stream()
                .sorted(Comparator.comparing(Product::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList()).orElseGet(List::of);
    }

    public List<Product> choices(Long productId, BigDecimal needed, Map<Long, BigDecimal> cart) {
        if (productId == null || cart == null) return List.of();
        Product source = products.findById(productId).orElse(null);
        if (source == null || needed == null || needed.signum() <= 0) return List.of();
        return source.getAlternativeProducts().stream().filter(p -> !p.getId().equals(productId)
                        && p.getRetailPrice() != null && p.getRetailPrice().signum() >= 0
                        && InventoryService.stock(p).subtract(Objects.requireNonNullElse(cart.get(p.getId()), BigDecimal.ZERO)).compareTo(needed) >= 0)
                .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Product::getId))
                .toList();
    }

}
