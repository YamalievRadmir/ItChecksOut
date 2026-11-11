package ru.sklad.app.repository;
import ru.sklad.app.entity.AuditLog;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("select a from AuditLog a where a.timestamp >= :start and a.timestamp < :end "
            + "and lower(a.username) like lower(concat('%', :username, '%')) order by a.timestamp desc, a.id desc")
    List<AuditLog> search(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                          @Param("username") String username, Pageable pageable);
}
