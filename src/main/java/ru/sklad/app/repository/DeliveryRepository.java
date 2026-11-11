package ru.sklad.app.repository;

import ru.sklad.app.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    long countByDeliveryDateAndStatus(java.time.LocalDate date, String status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    @EntityGraph(attributePaths = "customer")
    List<Delivery> findByDeliveryDateOrderByDeliveryTimeAscIdAsc(java.time.LocalDate date);
    @EntityGraph(attributePaths = "customer")
    List<Delivery> findAllByOrderByDeliveryDateDescDeliveryTimeDescIdDesc();
    @Override
    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Optional<Delivery> findById(Long id);
    Optional<Delivery> findFirstByCustomerIdOrderByDeliveryDateDescDeliveryTimeDescIdDesc(Long customerId);
    Optional<Delivery> findFirstByCustomerIdAndIdNotOrderByDeliveryDateDescDeliveryTimeDescIdDesc(Long customerId, Long id);
}
