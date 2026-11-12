package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.PurchaseOrder;
import java.util.Optional;
import java.time.LocalDateTime;

public interface PurchaseOrderRepository extends ShopRepositoryBase<PurchaseOrder, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and o.id = :id")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    Optional<PurchaseOrder> findLockedByShopAndId(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop, @Param("id") Long id);
    default Optional<PurchaseOrder> findLockedById(@Param("id") Long id) { return findLockedByShopAndId(ru.sklad.app.util.SecurityUtils.currentShop(), id); }

    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    Optional<PurchaseOrder> findFirstByShopAndIdNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(ru.sklad.app.entity.Shop shop,
            Long draftId, LocalDateTime from, LocalDateTime to);
    default Optional<PurchaseOrder> findFirstByIdNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Long draftId, LocalDateTime from, LocalDateTime to) { return findFirstByShopAndIdNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(ru.sklad.app.util.SecurityUtils.currentShop(), draftId, from, to); }

    @Query("select coalesce(max(o.id), 1) from PurchaseOrder o where o.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
    Long findMaximumId();
}
