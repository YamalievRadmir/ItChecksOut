package ru.sklad.app.repository;

import ru.sklad.app.entity.HeldCart;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface HeldCartRepository extends ShopRepositoryBase<HeldCart, Long> {
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<HeldCart> findAllByShopOrderByCreatedAtDescIdDesc(ru.sklad.app.entity.Shop shop);
    default List<HeldCart> findAllByOrderByCreatedAtDescIdDesc() { return findAllByShopOrderByCreatedAtDescIdDesc(ru.sklad.app.util.SecurityUtils.currentShop()); }
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HeldCart h where h.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and h.id = :id")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    Optional<HeldCart> findLockedByShopAndId(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop, @Param("id") Long id);
    default Optional<HeldCart> findLockedById(@Param("id") Long id) { return findLockedByShopAndId(ru.sklad.app.util.SecurityUtils.currentShop(), id); }
}
