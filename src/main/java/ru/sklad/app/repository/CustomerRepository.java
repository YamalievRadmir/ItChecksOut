package ru.sklad.app.repository;

import ru.sklad.app.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends ShopRepositoryBase<Customer, Long> {
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop()?.id")
    List<Customer> findAllByShopOrderByNameAscIdAsc(ru.sklad.app.entity.Shop shop);
    default List<Customer> findAllByOrderByNameAscIdAsc() { return findAllByShopOrderByNameAscIdAsc(ru.sklad.app.util.SecurityUtils.currentShop()); }
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop()?.id")
    Optional<Customer> findByShopAndPhone(ru.sklad.app.entity.Shop shop, String phone);
    default Optional<Customer> findByPhone(String phone) { return findByShopAndPhone(ru.sklad.app.util.SecurityUtils.currentShop(), phone); }
    @Query("select c from Customer c where c.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and (lower(c.name) like lower(concat('%', :term, '%')) "
            + "or (:phone <> '' and c.phone like concat('%', :phone, '%'))) order by c.name, c.id")
    List<Customer> search(@Param("term") String term, @Param("phone") String phone, Pageable pageable);
}
