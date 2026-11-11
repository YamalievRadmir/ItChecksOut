package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import ru.sklad.app.entity.SaleItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    @Query("select i from SaleItem i where i.sale.id = :saleId order by i.id")
    List<SaleItem> findBySaleId(@Param("saleId") Long saleId);
    interface ProductConsumption {
        Long getProductId();
        BigDecimal getQuantity();
    }

    @Query("select i.product.id as productId, sum(i.quantity) as quantity from SaleItem i "
            + "where i.sale.saleDate >= :start and i.sale.saleDate < :end group by i.product.id")
    List<ProductConsumption> findConsumption(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    String NET_REVENUE = "sum(case when s.totalSum > 0 then "
            + "i.totalSum * coalesce(s.finalSum, s.totalSum) / s.totalSum else 0 end)";

    interface CategorySales {
        String getCategory();
        BigDecimal getRevenue();
    }

    @Query("select p.id as productId, p.name as productName, p.unit as unit, "
            + "sum(i.quantity) as quantity, " + NET_REVENUE + " as revenue "
            + "from SaleItem i join i.product p join i.sale s "
            + "where s.saleDate >= :start and s.saleDate < :end "
            + "group by p.id, p.name, p.unit order by " + NET_REVENUE + " desc, p.id asc")
    List<ProductSales> findTopProductsNet(@Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end, Pageable pageable);

    @Query("select coalesce(nullif(trim(p.category), ''), 'Без категории') as category, "
            + NET_REVENUE + " as revenue from SaleItem i join i.product p join i.sale s "
            + "where s.saleDate >= :start and s.saleDate < :end "
            + "group by coalesce(nullif(trim(p.category), ''), 'Без категории') "
            + "order by " + NET_REVENUE + " desc")
    List<CategorySales> findCategoryRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    interface ProductSales {
        Long getProductId();
        String getProductName();
        String getUnit();
        BigDecimal getQuantity();
        BigDecimal getRevenue();
    }

    @Query("select p.id as productId, p.name as productName, p.unit as unit, "
            + "sum(i.quantity) as quantity, sum(i.totalSum) as revenue "
            + "from SaleItem i join i.product p join i.sale s "
            + "where s.saleDate >= :start and s.saleDate < :end "
            + "group by p.id, p.name, p.unit order by sum(i.totalSum) desc, p.id asc")
    List<ProductSales> findTopProducts(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end, Pageable pageable);

    @Query("select coalesce(sum(i.totalSum), 0) from SaleItem i "
            + "where i.sale.saleDate >= :start and i.sale.saleDate < :end")
    BigDecimal sumRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
