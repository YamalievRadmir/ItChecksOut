package ru.sklad.app.repository;

import ru.sklad.app.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends ShopRepositoryBase<Delivery, Long> {
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    long countByShopAndDeliveryDateAndStatus(ru.sklad.app.entity.Shop shop, java.time.LocalDate date, String status);
    default long countByDeliveryDateAndStatus(java.time.LocalDate date, String status) { return countByShopAndDeliveryDateAndStatus(ru.sklad.app.util.SecurityUtils.currentShop(), date, status); }
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Delivery d where d.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and d.id = :id")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    Optional<Delivery> findLockedByShopAndId(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop, @org.springframework.data.repository.query.Param("id") Long id);
    default Optional<Delivery> findLockedById(@org.springframework.data.repository.query.Param("id") Long id) { return findLockedByShopAndId(ru.sklad.app.util.SecurityUtils.currentShop(), id); }
    @EntityGraph(attributePaths = "customer")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<Delivery> findByShopAndDeliveryDateOrderByDeliveryTimeAscIdAsc(ru.sklad.app.entity.Shop shop, java.time.LocalDate date);
    default List<Delivery> findByDeliveryDateOrderByDeliveryTimeAscIdAsc(java.time.LocalDate date) { return findByShopAndDeliveryDateOrderByDeliveryTimeAscIdAsc(ru.sklad.app.util.SecurityUtils.currentShop(), date); }
    @EntityGraph(attributePaths = "customer")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<Delivery> findAllByShopOrderByDeliveryDateDescDeliveryTimeDescIdDesc(ru.sklad.app.entity.Shop shop);
    default List<Delivery> findAllByOrderByDeliveryDateDescDeliveryTimeDescIdDesc() { return findAllByShopOrderByDeliveryDateDescDeliveryTimeDescIdDesc(ru.sklad.app.util.SecurityUtils.currentShop()); }
    @Override
    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    @org.springframework.data.jpa.repository.Query("select d from Delivery d where d.id = :id and d.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
    Optional<Delivery> findById(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    Optional<Delivery> findFirstByShopAndCustomerIdOrderByDeliveryDateDescDeliveryTimeDescIdDesc(ru.sklad.app.entity.Shop shop, Long customerId);
    default Optional<Delivery> findFirstByCustomerIdOrderByDeliveryDateDescDeliveryTimeDescIdDesc(Long customerId) { return findFirstByShopAndCustomerIdOrderByDeliveryDateDescDeliveryTimeDescIdDesc(ru.sklad.app.util.SecurityUtils.currentShop(), customerId); }
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    Optional<Delivery> findFirstByShopAndCustomerIdAndIdNotOrderByDeliveryDateDescDeliveryTimeDescIdDesc(ru.sklad.app.entity.Shop shop, Long customerId, Long id);
    default Optional<Delivery> findFirstByCustomerIdAndIdNotOrderByDeliveryDateDescDeliveryTimeDescIdDesc(Long customerId, Long id) { return findFirstByShopAndCustomerIdAndIdNotOrderByDeliveryDateDescDeliveryTimeDescIdDesc(ru.sklad.app.util.SecurityUtils.currentShop(), customerId, id); }
}
