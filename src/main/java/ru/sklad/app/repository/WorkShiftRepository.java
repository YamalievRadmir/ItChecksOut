package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.WorkShift;
import java.time.LocalDate;
import java.util.List;

public interface WorkShiftRepository extends ShopRepositoryBase<WorkShift, Long> {
    @EntityGraph(attributePaths = "employee")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<WorkShift> findByShopAndShiftDateBetween(ru.sklad.app.entity.Shop shop, LocalDate start, LocalDate end);
    default List<WorkShift> findByShiftDateBetween(LocalDate start, LocalDate end) { return findByShopAndShiftDateBetween(ru.sklad.app.util.SecurityUtils.currentShop(), start, end); }

    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<WorkShift> findByShopAndEmployeeIdAndShiftDateBetween(ru.sklad.app.entity.Shop shop, Long employeeId, LocalDate start, LocalDate end);
    default List<WorkShift> findByEmployeeIdAndShiftDateBetween(Long employeeId, LocalDate start, LocalDate end) { return findByShopAndEmployeeIdAndShiftDateBetween(ru.sklad.app.util.SecurityUtils.currentShop(), employeeId, start, end); }

    @Modifying
    @Query("delete from WorkShift s where s.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and s.employee.id = :employeeId")
    int deleteForEmployee(@Param("employeeId") Long employeeId);
}
