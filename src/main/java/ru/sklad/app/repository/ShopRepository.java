package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sklad.app.entity.Shop;

public interface ShopRepository extends JpaRepository<Shop, Long> {
}