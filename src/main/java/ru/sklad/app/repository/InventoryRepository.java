package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sklad.app.entity.Inventory;
import java.util.List;

public interface InventoryRepository extends ShopRepositoryBase<Inventory, Long> {
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<Inventory> findAllByShopOrderByInventoryDateDescIdDesc(ru.sklad.app.entity.Shop shop);
    default List<Inventory> findAllByOrderByInventoryDateDescIdDesc() { return findAllByShopOrderByInventoryDateDescIdDesc(ru.sklad.app.util.SecurityUtils.currentShop()); }
}
