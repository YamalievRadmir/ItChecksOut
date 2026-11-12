package ru.sklad.app.repository;
import ru.sklad.app.entity.ErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface ErrorLogRepository extends org.springframework.data.repository.Repository<ErrorLog, Long> {
    <S extends ErrorLog> S save(S entity);
    <S extends ErrorLog> S saveAndFlush(S entity);
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    java.util.List<ErrorLog> findAll();
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    org.springframework.data.domain.Page<ErrorLog> findAll(Pageable pageable);
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    long count();
    @org.springframework.data.jpa.repository.Query("select e from ErrorLog e where e.shop = :shop and e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
    List<ErrorLog> findAllByShop(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop, Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select e from ErrorLog e where e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} order by e.timestamp desc, e.id desc")
    List<ErrorLog> findAllByOrderByTimestampDescIdDesc(Pageable pageable);
}
