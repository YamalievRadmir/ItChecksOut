package ru.sklad.app.repository;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.Product;

/** Deliberately exposes only the public catalog projection, independent of a login session. */
public interface PublicCatalogRepository extends Repository<Product, Long> {
    interface Item {
        String getName();
        String getCategory();
        BigDecimal getPrice();
        BigDecimal getStock();
        String getUnit();
        String getDescription();
    }
    @Query("select p.name as name, p.category as category, p.retailPrice as price, "
            + "coalesce(p.stockQuantity, 0) as stock, p.unit as unit, p.description as description "
            + "from Product p where p.shop.id = :shopId and p.shop.isActive = true order by p.name, p.id")
    List<Item> catalog(@Param("shopId") Long shopId);
}