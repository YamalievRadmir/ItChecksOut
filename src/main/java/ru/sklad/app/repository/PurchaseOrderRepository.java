package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.PurchaseOrder;
import java.util.Optional;
import java.time.LocalDateTime;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findLockedById(@Param("id") Long id);

    Optional<PurchaseOrder> findFirstByIdNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Long draftId, LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(max(o.id), 1) from PurchaseOrder o")
    Long findMaximumId();
}
