package ru.sklad.app.repository;
import ru.sklad.app.entity.ErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {
    List<ErrorLog> findAllByOrderByTimestampDescIdDesc(Pageable pageable);
}
