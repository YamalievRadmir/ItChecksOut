package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.Sale;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

public interface SaleRepository extends ShopRepositoryBase<Sale, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and s.id = :id")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop()?.id")
    java.util.Optional<Sale> findLockedByShopAndId(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop, @Param("id") Long id);
    default java.util.Optional<Sale> findLockedById(@Param("id") Long id) { return findLockedByShopAndId(ru.sklad.app.util.SecurityUtils.currentShop(), id); }
    interface DailyRevenue {
        LocalDate getDay();
        BigDecimal getRevenue();
    }

    @Query("select cast(s.saleDate as LocalDate) as day, sum(coalesce(s.finalSum, s.totalSum)) as revenue "
            + "from Sale s where s.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and s.saleDate >= :start and s.saleDate < :end "
            + "group by cast(s.saleDate as LocalDate) order by cast(s.saleDate as LocalDate)")
    List<DailyRevenue> findDailyRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(coalesce(s.finalSum, s.totalSum)), 0) from Sale s "
            + "where s.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and s.saleDate >= :start and s.saleDate < :end "
            + "and coalesce(s.paymentMethod, 'CASH') = :method")
    BigDecimal sumRevenueByPaymentMethod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                         @Param("method") String method);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop()?.id")
    List<Sale> findAllByShopOrderBySaleDateDescIdDesc(ru.sklad.app.entity.Shop shop);
    default List<Sale> findAllByOrderBySaleDateDescIdDesc() { return findAllByShopOrderBySaleDateDescIdDesc(ru.sklad.app.util.SecurityUtils.currentShop()); }

    @EntityGraph(attributePaths = {"items", "items.product"})
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop()?.id")
    List<Sale> findByShopAndSaleDateGreaterThanEqualAndSaleDateLessThanOrderBySaleDateDescIdDesc(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop,
            LocalDateTime start, LocalDateTime end);
    default List<Sale> findBySaleDateGreaterThanEqualAndSaleDateLessThanOrderBySaleDateDescIdDesc(
            LocalDateTime start, LocalDateTime end) { return findByShopAndSaleDateGreaterThanEqualAndSaleDateLessThanOrderBySaleDateDescIdDesc(ru.sklad.app.util.SecurityUtils.currentShop(), start, end); }
}
