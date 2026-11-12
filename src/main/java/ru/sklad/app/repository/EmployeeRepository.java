package ru.sklad.app.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.Employee;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends ShopRepositoryBase<Employee, Long> {
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<Employee> findAllByShopOrderByFullNameAscIdAsc(ru.sklad.app.entity.Shop shop);
    default List<Employee> findAllByOrderByFullNameAscIdAsc() { return findAllByShopOrderByFullNameAscIdAsc(ru.sklad.app.util.SecurityUtils.currentShop()); }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and e.id = :id")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    Optional<Employee> findLockedByShopAndId(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop, @Param("id") Long id);
    default Optional<Employee> findLockedById(@Param("id") Long id) { return findLockedByShopAndId(ru.sklad.app.util.SecurityUtils.currentShop(), id); }
}
