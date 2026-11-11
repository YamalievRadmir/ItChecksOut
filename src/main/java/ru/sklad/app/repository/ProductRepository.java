package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import ru.sklad.app.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p order by p.id")
    List<Product> findAllForUpdate();
    List<Product> findTop2ByNameIgnoreCaseOrderByIdAsc(String name);

    interface InventoryTotals {
        java.math.BigDecimal getPurchaseTotal();
        java.math.BigDecimal getRetailTotal();
    }

    @Query("select coalesce(sum(coalesce(p.stockQuantity, 0) * coalesce(p.purchasePrice, 0)), 0) "
            + "as purchaseTotal, coalesce(sum(coalesce(p.stockQuantity, 0) * coalesce(p.retailPrice, 0)), 0) "
            + "as retailTotal from Product p")
    InventoryTotals calculateInventoryTotals();

    @Query("select p from Product p where coalesce(p.stockQuantity, 0) <= :threshold order by p.name, p.id")
    List<Product> findLowStock(@Param("threshold") java.math.BigDecimal threshold);

    boolean existsByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findLockedById(@Param("id") Long id);

    Optional<Product> findByBarcode(String barcode);

    List<Product> findByNameContainingIgnoreCase(String name);
    List<Product> findByNameContainingIgnoreCase(String name, org.springframework.data.domain.Pageable pageable);

    List<Product> findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(String name, String category);
}
