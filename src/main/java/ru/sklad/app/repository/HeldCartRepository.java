package ru.sklad.app.repository;

import ru.sklad.app.entity.HeldCart;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface HeldCartRepository extends JpaRepository<HeldCart, Long> {
    List<HeldCart> findAllByOrderByCreatedAtDescIdDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HeldCart h where h.id = :id")
    Optional<HeldCart> findLockedById(@Param("id") Long id);
}
