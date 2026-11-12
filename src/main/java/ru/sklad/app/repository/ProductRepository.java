package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import ru.sklad.app.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends ShopRepositoryBase<Product, Long> {
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    default void deleteAllByShop(ru.sklad.app.entity.Shop shop) { deleteAll(findAllByShop(shop)); }    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "shop")
    @Query("select p from Product p where p.shop = :shop and p.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
    List<Product> findAllByShop(@Param("shop") ru.sklad.app.entity.Shop shop);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} order by p.id")
    List<Product> findAllForUpdate();
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<Product> findTop2ByShopAndNameIgnoreCaseOrderByIdAsc(ru.sklad.app.entity.Shop shop, String name);
    default List<Product> findTop2ByNameIgnoreCaseOrderByIdAsc(String name) { return findTop2ByShopAndNameIgnoreCaseOrderByIdAsc(ru.sklad.app.util.SecurityUtils.currentShop(), name); }

    interface InventoryTotals {
        java.math.BigDecimal getPurchaseTotal();
        java.math.BigDecimal getRetailTotal();
    }

    @Query("select coalesce(sum(coalesce(p.stockQuantity, 0) * coalesce(p.purchasePrice, 0)), 0) "
            + "as purchaseTotal, coalesce(sum(coalesce(p.stockQuantity, 0) * coalesce(p.retailPrice, 0)), 0) "
            + "as retailTotal from Product p where p.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
    InventoryTotals calculateInventoryTotals();

    @Query("select p from Product p where p.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and coalesce(p.stockQuantity, 0) <= :threshold order by p.name, p.id")
    List<Product> findLowStock(@Param("threshold") java.math.BigDecimal threshold);

    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    boolean existsByShopAndName(ru.sklad.app.entity.Shop shop, String name);
    default boolean existsByName(String name) { return existsByShopAndName(ru.sklad.app.util.SecurityUtils.currentShop(), name); }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and p.id = :id")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    Optional<Product> findLockedByShopAndId(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop, @Param("id") Long id);
    default Optional<Product> findLockedById(@Param("id") Long id) { return findLockedByShopAndId(ru.sklad.app.util.SecurityUtils.currentShop(), id); }

    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    Optional<Product> findByShopAndBarcode(ru.sklad.app.entity.Shop shop, String barcode);
    default Optional<Product> findByBarcode(String barcode) { return findByShopAndBarcode(ru.sklad.app.util.SecurityUtils.currentShop(), barcode); }

    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<Product> findByShopAndNameContainingIgnoreCase(ru.sklad.app.entity.Shop shop, String name);
    default List<Product> findByNameContainingIgnoreCase(String name) { return findByShopAndNameContainingIgnoreCase(ru.sklad.app.util.SecurityUtils.currentShop(), name); }
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<Product> findByShopAndNameContainingIgnoreCase(ru.sklad.app.entity.Shop shop, String name, org.springframework.data.domain.Pageable pageable);
    default List<Product> findByNameContainingIgnoreCase(String name, org.springframework.data.domain.Pageable pageable) { return findByShopAndNameContainingIgnoreCase(ru.sklad.app.util.SecurityUtils.currentShop(), name, pageable); }

    @Query("select p from Product p where p.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and (lower(p.name) like lower(concat('%', :name, '%')) or lower(p.category) like lower(concat('%', :category, '%')))")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<Product> findByShopAndNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(ru.sklad.app.entity.Shop shop, String name, String category);
    default List<Product> findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(String name, String category) { return findByShopAndNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(ru.sklad.app.util.SecurityUtils.currentShop(), name, category); }
}
