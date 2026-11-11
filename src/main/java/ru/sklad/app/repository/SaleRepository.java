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

public interface SaleRepository extends JpaRepository<Sale, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    java.util.Optional<Sale> findLockedById(@Param("id") Long id);
    interface DailyRevenue {
        LocalDate getDay();
        BigDecimal getRevenue();
    }

    @Query("select cast(s.saleDate as LocalDate) as day, sum(coalesce(s.finalSum, s.totalSum)) as revenue "
            + "from Sale s where s.saleDate >= :start and s.saleDate < :end "
            + "group by cast(s.saleDate as LocalDate) order by cast(s.saleDate as LocalDate)")
    List<DailyRevenue> findDailyRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(coalesce(s.finalSum, s.totalSum)), 0) from Sale s "
            + "where s.saleDate >= :start and s.saleDate < :end "
            + "and coalesce(s.paymentMethod, 'CASH') = :method")
    BigDecimal sumRevenueByPaymentMethod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                         @Param("method") String method);

    @EntityGraph(attributePaths = "items")
    List<Sale> findAllByOrderBySaleDateDescIdDesc();

    @EntityGraph(attributePaths = "items")
    List<Sale> findBySaleDateGreaterThanEqualAndSaleDateLessThanOrderBySaleDateDescIdDesc(
            LocalDateTime start, LocalDateTime end);
}
