package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sklad.app.entity.Inventory;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findAllByOrderByInventoryDateDescIdDesc();
}
