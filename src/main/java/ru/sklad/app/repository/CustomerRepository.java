package ru.sklad.app.repository;

import ru.sklad.app.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByOrderByNameAscIdAsc();
    Optional<Customer> findByPhone(String phone);
    @Query("select c from Customer c where lower(c.name) like lower(concat('%', :term, '%')) "
            + "or (:phone <> '' and c.phone like concat('%', :phone, '%')) order by c.name, c.id")
    List<Customer> search(@Param("term") String term, @Param("phone") String phone, Pageable pageable);
}
