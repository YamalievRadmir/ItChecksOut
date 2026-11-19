package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.CallbackRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CallbackRequestRepository extends ShopRepositoryBase<CallbackRequest, UUID> {
    @Query("select c from CallbackRequest c where c.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} "
            + "and c.completedAt is null order by c.createdAt desc, c.id")
    List<CallbackRequest> findPending();

    @Modifying
    @Query("update CallbackRequest c set c.completedAt = :now "
            + "where c.id = :id and c.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and c.completedAt is null")
    int complete(@Param("id") UUID id, @Param("now") Instant now);
}
