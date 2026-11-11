package ru.sklad.app.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.Supplier;
import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findAllByOrderByNameAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Supplier s where s.id = :id")
    Optional<Supplier> findLockedById(@Param("id") Long id);
}
