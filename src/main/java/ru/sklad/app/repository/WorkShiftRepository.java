package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.WorkShift;
import java.time.LocalDate;
import java.util.List;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {
    @EntityGraph(attributePaths = "employee")
    List<WorkShift> findByShiftDateBetween(LocalDate start, LocalDate end);

    List<WorkShift> findByEmployeeIdAndShiftDateBetween(Long employeeId, LocalDate start, LocalDate end);

    @Modifying
    @Query("delete from WorkShift s where s.employee.id = :employeeId")
    int deleteForEmployee(@Param("employeeId") Long employeeId);
}
