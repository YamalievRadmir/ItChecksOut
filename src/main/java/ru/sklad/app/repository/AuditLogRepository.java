package ru.sklad.app.repository;
import ru.sklad.app.entity.AuditLog;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
public interface AuditLogRepository extends org.springframework.data.repository.Repository<AuditLog, Long> {
    <S extends AuditLog> S save(S entity);
    <S extends AuditLog> S saveAndFlush(S entity);
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    java.util.List<AuditLog> findAll();
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    org.springframework.data.domain.Page<AuditLog> findAll(Pageable pageable);
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    long count();
    @org.springframework.data.jpa.repository.Query("select e from AuditLog e where e.shop = :shop and e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
    List<AuditLog> findAllByShop(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop, Pageable pageable);
    @Query("select a from AuditLog a where a.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and a.timestamp >= :start and a.timestamp < :end "
            + "and lower(a.username) like lower(concat('%', :username, '%')) order by a.timestamp desc, a.id desc")
    List<AuditLog> search(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                          @Param("username") String username, Pageable pageable);
}
