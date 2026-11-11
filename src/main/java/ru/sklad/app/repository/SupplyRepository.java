package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sklad.app.entity.Supply;

import java.util.List;

public interface SupplyRepository extends JpaRepository<Supply, Long> {
    @EntityGraph(attributePaths = "product")
    List<Supply> findAllByOrderBySupplyDateDescIdDesc();

    boolean existsByReceiptKey(String receiptKey);

    @Query("select distinct s.supplier from Supply s where s.supplier is not null and s.supplier <> '' order by s.supplier")
    List<String> findSuppliers();
}
