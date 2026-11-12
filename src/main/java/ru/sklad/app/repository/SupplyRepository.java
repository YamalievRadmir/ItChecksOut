package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sklad.app.entity.Supply;

import java.util.List;

public interface SupplyRepository extends ShopRepositoryBase<Supply, Long> {
    @EntityGraph(attributePaths = "product")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<Supply> findAllByShopOrderBySupplyDateDescIdDesc(ru.sklad.app.entity.Shop shop);
    default List<Supply> findAllByOrderBySupplyDateDescIdDesc() { return findAllByShopOrderBySupplyDateDescIdDesc(ru.sklad.app.util.SecurityUtils.currentShop()); }

    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    boolean existsByShopAndReceiptKey(ru.sklad.app.entity.Shop shop, String receiptKey);
    default boolean existsByReceiptKey(String receiptKey) { return existsByShopAndReceiptKey(ru.sklad.app.util.SecurityUtils.currentShop(), receiptKey); }

    @Query("select distinct s.supplier from Supply s where s.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and s.supplier is not null and s.supplier <> '' order by s.supplier")
    List<String> findSuppliers();
}
