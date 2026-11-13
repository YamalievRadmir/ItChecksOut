package ru.sklad.app.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.Supplier;
import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends ShopRepositoryBase<Supplier, Long> {
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop()?.id")
    List<Supplier> findAllByShopOrderByNameAscIdAsc(ru.sklad.app.entity.Shop shop);
    default List<Supplier> findAllByOrderByNameAscIdAsc() { return findAllByShopOrderByNameAscIdAsc(ru.sklad.app.util.SecurityUtils.currentShop()); }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Supplier s where s.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and s.id = :id")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop()?.id")
    Optional<Supplier> findLockedByShopAndId(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop, @Param("id") Long id);
    default Optional<Supplier> findLockedById(@Param("id") Long id) { return findLockedByShopAndId(ru.sklad.app.util.SecurityUtils.currentShop(), id); }
}
